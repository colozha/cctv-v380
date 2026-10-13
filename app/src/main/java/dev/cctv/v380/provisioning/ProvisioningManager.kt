package dev.cctv.v380.provisioning

import android.net.Network
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.session.*
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
    val profile: CameraProfile? = null)

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
    private var sequence = 0L

    private fun step(vararg ownedSecrets: CharArray, block: suspend () -> Unit) {
        val previous = work
        val epoch = ++sequence
        work = scope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            try { block() }
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

    fun choose(camera: DiscoveredCamera) {
        clearCredential()
        mutable.value = mutable.value.copy(phase = ProvisioningPhase.CREDENTIALS, camera = camera, error = null)
    }

    fun authenticate(name: String, username: String, password: CharArray, saveAccess: Boolean) = step(password) {
        try {
            val verifying = state.value.phase == ProvisioningPhase.VERIFICATION_CREDENTIALS
            val camera = state.value.camera ?: throw CameraFailure(SessionError.IdentityMismatch)
            val previous = if (verifying) target else withContext(Dispatchers.IO) { store.load().firstOrNull { it.deviceId == camera.deviceId } }
            val candidate = (previous ?: CameraProfile(name = name, deviceId = camera.deviceId)).copy(
                name = name, host = camera.host, port = camera.port, username = username,
                platformType = camera.platformType, discoveryGeneration = camera.generation,
                authenticationMode = if (camera.platformType == 1) AuthenticationMode.USER_PASSWORD else AuthenticationMode.AUTO)
            mutable.value = state.value.copy(phase = ProvisioningPhase.AUTHENTICATING, error = null)
            session.connect(candidate, password.copyOf())
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
            session.changeLanPassword(old, newPassword)
            // A password-change ACK is also verified with the newly chosen credential before storage.
            session.connect(profile.copy(authenticationMode = AuthenticationMode.LOCAL_PAIRING), newPassword.copyOf())
            clearCredential()
            credential = newPassword.copyOf()
            target = session.connectedProfile
            remember = true
            mutable.value = state.value.copy(phase = ProvisioningPhase.READY_AP, profile = target, error = null)
        } finally { old.fill('\u0000'); newPassword.fill('\u0000') }
    }

    fun useApDirectly() = step { finish() }
    fun discardPending() = step {
        session.close(); clearCredential(); wifi.releaseAp(); target = null; apNetwork = null
        withContext(Dispatchers.IO) { store.clearPendingSetup() }
        mutable.value = ProvisioningState()
    }

    fun configureWifi(ssid: String, password: CharArray) = step(password) {
        try {
            check(state.value.phase == ProvisioningPhase.READY_AP)
            require(ssid.toByteArray(Charsets.UTF_8).size in 1..32)
            val profile = target ?: throw CameraFailure(SessionError.AuthenticationFailed)
            withContext(Dispatchers.IO) { store.savePendingSetup(profile, ssid) }
            mutable.value = state.value.copy(phase = ProvisioningPhase.SENDING_WIFI, targetSsid = ssid,
                error = null, mayHaveConfigured = true)
            var uncertain = false
            try { session.configureWifi(ssid, password) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                when (CameraSession.reason(error)) {
                    SessionError.NetworkUnavailable, SessionError.Timeout -> uncertain = true
                    SessionError.InvalidInput, SessionError.DeviceRejected, SessionError.WifiConfigurationRejected -> {
                        withContext(Dispatchers.IO) { store.clearPendingSetup() }
                        mutable.value = state.value.copy(phase = ProvisioningPhase.READY_AP,
                            error = if (CameraSession.reason(error) == SessionError.InvalidInput) SessionError.InvalidInput
                                else SessionError.WifiConfigurationRejected, mayHaveConfigured = false)
                        return@step
                    }
                    else -> throw error
                }
            }
            session.close()
            wifi.releaseAp()
            mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN,
                error = if (uncertain) SessionError.Timeout else null)
        } finally { password.fill('\u0000') }
    }

    /** Manual retry repeats discovery/login only, never the credential-bearing Wi-Fi command. */
    fun verifyLan() = step {
        val expected = target ?: throw CameraFailure(SessionError.IdentityMismatch)
        mutable.value = state.value.copy(phase = ProvisioningPhase.VERIFYING, error = null)
        session.diagnostics.record(DiagnosticStage.VERIFY_LAN, DiagnosticStatus.STARTED)
        try {
            withTimeout(120_000) {
                while (currentCoroutineContext().isActive) {
                    val link = runCatching { wifi.resolve() }.getOrNull()
                    if (link == null || link.network == apNetwork) { delay(500); continue }
                    val found = discovery.scan(link).firstOrNull { it.deviceId == expected.deviceId }
                    if (found == null) { delay(500); continue }
                    target = expected.copy(host = found.host, port = found.port,
                        platformType = found.platformType, discoveryGeneration = found.generation)
                    val password = credential
                    if (password == null) {
                        mutable.value = state.value.copy(phase = ProvisioningPhase.VERIFICATION_CREDENTIALS,
                            camera = found, error = null)
                        return@withTimeout
                    }
                    session.connect(target!!, password.copyOf())
                    finish()
                    return@withTimeout
                }
            }
        } catch (_: TimeoutCancellationException) {
            mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN,
                error = SessionError.WifiVerificationTimeout)
        }
    }

    private suspend fun finish() {
        val connected = session.connectedProfile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        if (session.state.value !is SessionState.Ready) throw CameraFailure(SessionError.AuthenticationFailed)
        if (connected.deviceId != target?.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val password = credential
        withContext(Dispatchers.IO) { store.save(connected, password, remember); store.clearPendingSetup() }
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
        clearCredential()
        wifi.releaseAp()
        if (state.value.mayHaveConfigured) mutable.value = state.value.copy(phase = ProvisioningPhase.WAITING_LAN)
        else mutable.value = state.value.copy(phase = if (state.value.camera != null) ProvisioningPhase.CREDENTIALS else ProvisioningPhase.IDLE)
    }

    fun cancel() {
        sequence++
        work?.cancel(); work = null
        clearCredential(); wifi.releaseAp(); target = null; apNetwork = null
        mutable.value = ProvisioningState()
    }
    private fun clearCredential() { credential?.fill('\u0000'); credential = null }
}
