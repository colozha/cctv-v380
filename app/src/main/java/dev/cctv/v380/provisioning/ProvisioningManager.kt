package dev.cctv.v380.provisioning

import android.net.Network
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.session.*
import dev.cctv.v380.protocol.ApProtocol
import dev.cctv.v380.protocol.IoTProtocolException
import dev.cctv.v380.transport.*
import dev.cctv.v380.ui.ProfileStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProvisioningPhase { IDLE, CONNECTING_AP, DISCOVERING, CHOOSE_CAMERA, CREDENTIALS, AUTHENTICATING, READY_AP,
    SENDING_WIFI, WAITING_LAN, VERIFYING, VERIFICATION_CREDENTIALS, COMPLETE, ERROR }
data class ProvisioningState(val phase: ProvisioningPhase = ProvisioningPhase.IDLE,
    val cameras: List<DiscoveredCamera> = emptyList(), val camera: DiscoveredCamera? = null,
    val targetSsid: String = "", val error: SessionError? = null, val mayHaveConfigured: Boolean = false,
    val profile: CameraProfile? = null, val routerCheck: RouterCheck? = null)

enum class RouterCheck { MATCHED, WRONG_NETWORK, CONFIRMATION_REQUIRED }

/** Missing SSID is not evidence of a match. Confirmation applies only to the current Network. */
fun routerCheck(targetSsid: String, activeSsid: String?, confirmed: Boolean): RouterCheck = when {
    activeSsid != null -> if (activeSsid == targetSsid) RouterCheck.MATCHED else RouterCheck.WRONG_NETWORK
    confirmed -> RouterCheck.MATCHED
    else -> RouterCheck.CONFIRMATION_REQUIRED
}

/** A Wi-Fi ACK is provisional. Completion requires the same device identity and a successful login. */
class ProvisioningManager(
    private val wifi: WifiNetwork, private val discovery: LanDiscovery, private val session: CameraSession,
    private val store: ProfileStore, private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(ProvisioningState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var credential: CharArray? = null
    private var remember = false
    private var target: CameraProfile? = null
    private var apNetwork: Network? = null
    @Volatile private var sequence = 0L
    private var authenticatedLink: WifiLink? = null
    private var apAuthenticationRevision = -1L
    private var verificationLink: WifiLink? = null
    private var confirmationNetwork: Network? = null

    private fun step(vararg ownedSecrets: CharArray, block: suspend () -> Unit) {
        val previous = work
        val epoch = ++sequence
        work = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            try { currentCoroutineContext().ensureActive(); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (epoch == sequence) mutable.value = mutable.value.copy(phase = ProvisioningPhase.ERROR,
                    error = CameraSession.reason(error))
            }
        }.also { job -> job.invokeOnCompletion { ownedSecrets.forEach { it.fill('\u0000') } } }
    }

    init { resumePending() }
    fun resumePending() = step {
        val pending = withContext(Dispatchers.IO) { store.loadPendingSetup() }
        if (pending != null) {
            target = pending.profile
            mutable.value = ProvisioningState(ProvisioningPhase.WAITING_LAN,
                targetSsid = pending.targetSsid, mayHaveConfigured = true, profile = pending.profile)
        }
    }

    fun connectAp(ssid: String, password: CharArray) = step(password) {
        try {
            session.close()
            clearCredential()
            mutable.value = ProvisioningState(ProvisioningPhase.CONNECTING_AP)
            val link = wifi.connectAp(ssid, password)
            apNetwork = link.network
            discover()
        } finally { password.fill('\u0000') }
    }

    /** API26-28 or an AP selected in Android Settings. Does not assume a fixed gateway. */
    fun discoverCurrentAp() = step {
        session.close()
        clearCredential(); authenticatedLink = null; verificationLink = null; confirmationNetwork = null
        apNetwork = wifi.resolve().network
        discover()
    }

    private suspend fun discover() {
        mutable.value = ProvisioningState(ProvisioningPhase.DISCOVERING)
        val link = wifi.resolve()
        session.diagnostics.selectNetwork(link.localAddress, link.gateway)
        val cameras = discovery.scan(link)
        session.diagnostics.record(DiagnosticStage.DISCOVERY, bytes = cameras.size)
        mutable.value = ProvisioningState(ProvisioningPhase.CHOOSE_CAMERA, cameras = cameras,
            error = if (cameras.isEmpty()) SessionError.NetworkUnavailable else null)
    }

    fun choose(camera: DiscoveredCamera) = step {
        check(!state.value.mayHaveConfigured)
        clearCredential()
        authenticatedLink = null; verificationLink = null
        mutable.value = mutable.value.copy(phase = ProvisioningPhase.CREDENTIALS, camera = camera, error = null)
    }

    fun authenticate(name: String, username: String, password: CharArray, saveAccess: Boolean) = step(password) {
        try {
            val verifying = state.value.phase == ProvisioningPhase.VERIFICATION_CREDENTIALS
            val link = if (verifying) requireVerificationNetwork() else wifi.resolve().also {
                if (it.network != apNetwork) throw CameraFailure(SessionError.NetworkUnavailable)
            }
            val camera = state.value.camera ?: throw CameraFailure(SessionError.IdentityMismatch)
            val previous = if (verifying) target else withContext(Dispatchers.IO) { store.load().firstOrNull { it.deviceId == camera.deviceId } }
            val candidate = (previous ?: CameraProfile(name = name, deviceId = camera.deviceId)).copy(
                name = name, host = camera.host, port = camera.port, username = username,
                platformType = camera.platformType, discoveryGeneration = camera.generation,
                authenticationMode = if (camera.platformType == 1) AuthenticationMode.USER_PASSWORD else AuthenticationMode.AUTO)
            mutable.value = state.value.copy(phase = ProvisioningPhase.AUTHENTICATING, error = null)
            session.connect(candidate, password.copyOf())
            currentCoroutineContext().ensureActive()
            if (!wifi.isCurrent(link)) throw CameraFailure(SessionError.NetworkUnavailable)
            if (verifying) requireVerificationNetwork()
            else { authenticatedLink = link; apAuthenticationRevision = session.authenticationRevision.value }
            clearCredential()
            credential = password.copyOf()
            remember = saveAccess
            target = session.connectedProfile ?: throw CameraFailure(SessionError.IdentityMismatch)
            if (verifying) finish()
            else mutable.value = state.value.copy(phase = ProvisioningPhase.READY_AP, profile = target, error = null)
        } finally { password.fill('\u0000') }
    }

    /** The recovered new-platform fresh PIN-binding path needs a cloud-issued owner bind token. */
    fun requestFreshPairing() {
        val camera = state.value.camera ?: return
        mutable.value = state.value.copy(error = if (camera.platformType == 2) SessionError.CloudRequired else SessionError.PairingRequired)
    }

    fun pairAndSaveAccess(newPassword: CharArray) = step(newPassword) {
        val old = credential?.copyOf() ?: throw CameraFailure(SessionError.AuthenticationFailed)
        try {
            val profile = target ?: throw CameraFailure(SessionError.AuthenticationFailed)
            mutable.value = state.value.copy(phase = ProvisioningPhase.AUTHENTICATING, error = null)
            val link = authenticatedLink ?: throw CameraFailure(SessionError.NetworkUnavailable)
            if (!wifi.isCurrent(link) || session.authenticationRevision.value != apAuthenticationRevision)
                throw CameraFailure(SessionError.NetworkUnavailable)
            session.changeLanPassword(old, newPassword)
            // A password-change ACK is also verified with the newly chosen credential before storage.
            session.connect(profile.copy(authenticationMode = AuthenticationMode.LOCAL_PAIRING), newPassword.copyOf())
            clearCredential()
            credential = newPassword.copyOf()
            target = session.connectedProfile
            if (!wifi.isCurrent(link)) throw CameraFailure(SessionError.NetworkUnavailable)
            apAuthenticationRevision = session.authenticationRevision.value
            mutable.value = state.value.copy(phase = ProvisioningPhase.READY_AP, profile = target, error = null)
        } finally { old.fill('\u0000'); newPassword.fill('\u0000') }
    }

    fun useApDirectly() = step { finish() }
    fun discardPending() = step {
        session.close(); clearCredential(); wifi.releaseAp(); target = null; apNetwork = null
        authenticatedLink = null; verificationLink = null; confirmationNetwork = null
        withContext(Dispatchers.IO) { store.clearPendingSetup() }
        mutable.value = ProvisioningState()
    }

    fun configureWifi(ssid: String, password: CharArray) = step(password) {
        check(state.value.phase == ProvisioningPhase.READY_AP)
        val profile = target ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val link = authenticatedLink ?: throw CameraFailure(SessionError.NetworkUnavailable)
        val epoch = sequence
        val revision = apAuthenticationRevision
        var started = false
        var pending = false
        var rejected = false
        var cancelled = false
        mutable.value = state.value.copy(phase = ProvisioningPhase.SENDING_WIFI, error = null, routerCheck = null)
        try {
            ApProtocol.validateWifiInput(ssid, password,
                legacy = profile.platformType == 1 || profile.protocolProfileId == "legacy-v2")
            session.configureWifi(profile.id, profile.deviceId, revision, ssid, password,
                networkValid = { epoch == sequence && wifi.isCurrent(link) && link.network == apNetwork },
                beforeWrite = {
                    // Cancellation may arrive while AtomicFile finishes. Keep an uncertain marker in that case.
                    withContext(NonCancellable + Dispatchers.IO) { store.savePendingSetup(profile, ssid); pending = true }
                    if (epoch == sequence) mutable.value = state.value.copy(targetSsid = ssid, mayHaveConfigured = true)
                }, onWriteStarted = { started = true })
        } catch (error: CancellationException) { cancelled = true; throw error }
        catch (error: Exception) {
            rejected = CameraSession.reason(error) == SessionError.WifiConfigurationRejected ||
                error is IoTProtocolException && error.deviceCode != null
            if (epoch == sequence) mutable.value = state.value.copy(error = CameraSession.reason(error))
        } finally {
            withContext(NonCancellable) {
                if (!started || rejected) {
                    if (pending) withContext(Dispatchers.IO) { store.clearPendingSetup() }
                    if (epoch == sequence && !cancelled) mutable.value = state.value.copy(phase = ProvisioningPhase.READY_AP,
                        mayHaveConfigured = false)
                } else {
                    session.close(); wifi.releaseAp(); authenticatedLink = null
                    if (epoch == sequence) mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN,
                        mayHaveConfigured = true)
                }
            }
        }
    }

    /** Manual retry repeats discovery/login only, never the credential-bearing Wi-Fi command. */
    fun verifyLan(confirmedRouter: Boolean = false) = step {
        val expected = target ?: throw CameraFailure(SessionError.IdentityMismatch)
        verificationLink = null
        mutable.value = state.value.copy(phase = ProvisioningPhase.VERIFYING, error = null, routerCheck = null)
        session.diagnostics.record(DiagnosticStage.VERIFY_LAN, DiagnosticStatus.STARTED)
        try {
            withTimeout(120_000) {
                while (currentCoroutineContext().isActive) {
                    val link = runCatching { wifi.resolve() }.getOrNull()
                    if (link == null) { delay(500); continue }
                    if (link.network == apNetwork) {
                        mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN, routerCheck = RouterCheck.WRONG_NETWORK)
                        return@withTimeout
                    }
                    val check = routerCheck(state.value.targetSsid, wifi.currentSsid(link),
                        confirmedRouter && confirmationNetwork == link.network)
                    if (check != RouterCheck.MATCHED) {
                        confirmationNetwork = if (check == RouterCheck.CONFIRMATION_REQUIRED) link.network else null
                        mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN, routerCheck = check)
                        return@withTimeout
                    }
                    val found = discovery.scan(link).firstOrNull { it.deviceId == expected.deviceId }
                    if (!wifi.isCurrent(link)) { delay(500); continue }
                    if (found == null) { delay(500); continue }
                    verificationLink = link
                    requireVerificationNetwork()
                    target = expected.copy(host = found.host, port = found.port,
                        platformType = found.platformType, discoveryGeneration = found.generation)
                    val password = credential
                    if (password == null) {
                        mutable.value = state.value.copy(phase = ProvisioningPhase.VERIFICATION_CREDENTIALS,
                            camera = found, error = null)
                        return@withTimeout
                    }
                    session.connect(target!!, password.copyOf())
                    requireVerificationNetwork()
                    finish()
                    return@withTimeout
                }
            }
        } catch (_: TimeoutCancellationException) {
            mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN,
                error = SessionError.WifiVerificationTimeout)
        }
    }

    private fun requireVerificationNetwork(): WifiLink {
        val link = verificationLink ?: throw CameraFailure(SessionError.NetworkUnavailable)
        if (!wifi.isCurrent(link) || link.network == apNetwork ||
            routerCheck(state.value.targetSsid, wifi.currentSsid(link), confirmationNetwork == link.network) != RouterCheck.MATCHED)
            throw CameraFailure(SessionError.NetworkUnavailable)
        return link
    }

    private suspend fun finish() {
        val connected = session.connectedProfile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        if (session.state.value !is SessionState.Ready) throw CameraFailure(SessionError.AuthenticationFailed)
        currentCoroutineContext().ensureActive()
        if (state.value.mayHaveConfigured) requireVerificationNetwork()
        if (connected.id != target?.id || connected.deviceId != target?.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val password = credential
        withContext(Dispatchers.IO) { store.save(connected, password, remember); store.clearPendingSetup() }
        currentCoroutineContext().ensureActive()
        val saved = connected.copy(hasSavedPassword = remember && password != null)
        target = saved
        mutable.value = state.value.copy(phase = ProvisioningPhase.COMPLETE, profile = saved, error = null)
        session.diagnostics.record(DiagnosticStage.VERIFY_LAN)
        clearCredential()
    }

    /** A known system Wi-Fi handoff may retain in-memory credential for at most30s in the ViewModel. */
    fun onBackground(expectedSystemHandoff: Boolean) {
        if (expectedSystemHandoff) return
        work?.cancel()
        authenticatedLink = null; verificationLink = null; confirmationNetwork = null
        clearCredential()
        wifi.releaseAp()
        if (state.value.mayHaveConfigured) mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN)
        else mutable.value = state.value.copy(phase = if (state.value.camera != null) ProvisioningPhase.CREDENTIALS else ProvisioningPhase.IDLE)
    }

    fun cancel() {
        sequence++
        // Keep the last job so a subsequent resume waits for its pending-marker cleanup.
        work?.cancel()
        clearCredential(); wifi.releaseAp(); target = null; apNetwork = null
        authenticatedLink = null; verificationLink = null; confirmationNetwork = null
        mutable.value = ProvisioningState()
    }
    private fun clearCredential() { credential?.fill('\u0000'); credential = null }
}
