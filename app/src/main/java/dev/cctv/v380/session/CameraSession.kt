package dev.cctv.v380.session

import android.view.Surface
import dev.cctv.v380.BuildConfig
import dev.cctv.v380.control.Direction
import dev.cctv.v380.control.PtzWatchdog
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.media.VideoDecoder
import dev.cctv.v380.media.RecordingTimestampException
import dev.cctv.v380.media.RecordingExportException
import dev.cctv.v380.protocol.*
import dev.cctv.v380.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** One foreground owner, one selected protocol. Experimental adapters are available only in debug. */
class CameraSession(
    private val channelFactory: (CameraProfile) -> TcpChannel,
    private val resolveTarget: suspend (CameraProfile) -> CameraProfile = { it },
    val diagnostics: Diagnostics = Diagnostics(),
    private val verifyApNetwork: suspend (CameraProfile, String) -> (() -> Boolean) = { _, _ ->
        throw CameraFailure(SessionError.FeatureUnsupported)
    },
) {
    constructor(wifi: WifiNetwork, discovery: LanDiscovery = LanDiscovery(wifi), diagnostics: Diagnostics = Diagnostics()) :
        this({ wifi.resolve().let { link ->
            diagnostics.selectNetwork(link.localAddress, link.gateway)
            TcpChannel(link.socket())
        } }, { profile ->
            if (profile.protocolProfileId in setOf("legacy-v2", "iot-lan") || profile.platformType != null) profile
            else {
                val found = discovery.scan(target = InetAddress.getByName(profile.host))
                val matched = found.firstOrNull { it.deviceId == profile.deviceId }
                    ?: throw CameraFailure(if (found.any { it.host == profile.host }) SessionError.IdentityMismatch else SessionError.UnsupportedProtocol)
                profile.copy(host = matched.host, port = matched.port, platformType = matched.platformType,
                    discoveryGeneration = matched.generation)
            }
        }, diagnostics, { target, ssid ->
            val link = wifi.resolve()
            if (wifi.currentSsid(link) != ssid) throw CameraFailure(SessionError.NetworkUnavailable)
            val found = discovery.scan(link, InetAddress.getByName(target.host))
            if (found.none { it.deviceId == target.deviceId && it.host == target.host && it.platformType == 2 })
                throw CameraFailure(SessionError.IdentityMismatch)
            val check = { wifi.isCurrent(link) && wifi.currentSsid(link) == ssid }
            if (!check()) throw CameraFailure(SessionError.NetworkUnavailable)
            check
        })

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Mutex()
    private val generation = AtomicLong()
    private val failedStreamGeneration = AtomicLong(-1)
    private val requestIds = AtomicInteger(100)
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableCapabilities = MutableStateFlow<CameraCapabilities?>(null)
    val capabilities = mutableCapabilities.asStateFlow()
    @Volatile private var profile: CameraProfile? = null
    val connectedProfile: CameraProfile? get() = profile
    @Volatile private var commandChannel: TcpChannel? = null
    @Volatile private var streamChannel: TcpChannel? = null
    private var legacy: LegacyLogin? = null
    private var iot: IoTSession? = null
    private var mediaCipher: LegacyMediaCipher? = null
    @Volatile private var decoder: VideoDecoder? = null
    @Volatile private var surface: Surface? = null
    private var decoderCodec: VideoCodec? = null
    private var decoderDimensions: Pair<Int, Int>? = null
    private var streamJob: Job? = null
    private var reconnectJob: Job? = null
    private var secret: CharArray? = null
    private var quality = Quality.HD
    private var watchdog: PtzWatchdog? = null
    private var firstFrame = CompletableDeferred<Unit>()
    private var iotMediaInfo: IoTMediaInfo? = null
    private var activeRecording: RecordingSegment? = null
    private val mutableRecordingPosition = MutableStateFlow(0L)
    val recordingPosition = mutableRecordingPosition.asStateFlow()
    private class Reply(val id: Int, val method: Int, val result: CompletableDeferred<JSONObject>)
    @Volatile private var pendingReply: Reply? = null
    private val mutableApAccess = MutableStateFlow<ApHotspotAccess?>(null)
    val apAccess = mutableApAccess.asStateFlow()
    @Volatile private var authenticatedIoT: IoTSession? = null
    @Volatile private var accessRevoked = true
    @Volatile private var apNetworkValid: (() -> Boolean)? = null
    private var softApSupported: Boolean? = null
    @Volatile private var managementRevision = 0L
    private val mutableAuthenticationRevision = MutableStateFlow(0L)
    val authenticationRevision = mutableAuthenticationRevision.asStateFlow()
    @Volatile private var areaCapturing = false
    private val mutableVideoGeometry = MutableStateFlow<VideoGeometry?>(null)
    val videoGeometry = mutableVideoGeometry.asStateFlow()
    private val renderedAt = MutableStateFlow(0L)
    private val mutableAreaReference = MutableStateFlow<AreaReference?>(null)
    val areaReference = mutableAreaReference.asStateFlow()
    @Volatile private var patrolActive: Boolean? = null
    val manualPtzAllowed: Boolean get() = !maintenancePending && !areaCapturing && (capabilities.value?.management?.patrol != true || patrolActive == false)
    private val submittedManagementActions = mutableSetOf<String>()
    @Volatile var maintenancePending: Boolean = false
        private set
    fun blockMaintenanceWrites(blocked: Boolean) { maintenancePending = blocked }

    fun isAuthenticated(profileId: String): Boolean = !accessRevoked && profile?.id == profileId &&
        (legacy != null || iot != null && iot === authenticatedIoT) && when (state.value) {
            is SessionState.Ready, is SessionState.Streaming, is SessionState.Negotiating,
            is SessionState.LoadingRecording, is SessionState.PlayingRecording, is SessionState.RecordingEnded -> true
            else -> false
        }

    fun canEditAp(profileId: String): Boolean = BuildConfig.DEBUG && isAuthenticated(profileId) &&
        state.value is SessionState.Ready && softApSupported == true && mutableApAccess.value?.deviceId == profile?.deviceId &&
        apNetworkValid?.invoke() == true

    fun invalidateApAccess() { mutableApAccess.value = null; apNetworkValid = null }

    internal fun apReconnectPassword(profileId: String): CharArray {
        if (!canEditAp(profileId)) throw CameraFailure(SessionError.AuthenticationFailed)
        return secret?.copyOf() ?: throw CameraFailure(SessionError.AuthenticationFailed)
    }

    private suspend fun fetchApSnapshot(): ApHotspotProtocol.Snapshot = ApHotspotProtocol.parseSnapshot(
        exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE, ApHotspotProtocol.readParams())
            ?: throw IoTProtocolException(IoTFailure.MalformedPacket))

    suspend fun readApHotspot(profileId: String): ApHotspotAccess = operations.withLock {
        invalidateApAccess()
        if (!isAuthenticated(profileId) || state.value !is SessionState.Ready)
            throw CameraFailure(SessionError.AuthenticationFailed)
        if (softApSupported != true) throw CameraFailure(SessionError.FeatureUnsupported)
        val target = profile!!
        val owner = authenticatedIoT
        val epoch = generation.get()
        try {
            val snapshot = fetchApSnapshot()
            val proof = verifyApNetwork(target, snapshot.ssid)
            currentCoroutineContext().ensureActive()
            if (owner == null || owner !== iot || epoch != generation.get() || !isAuthenticated(profileId) || !proof())
                throw CameraFailure(SessionError.NetworkUnavailable)
            apNetworkValid = proof
            ApHotspotAccess(snapshot.ssid, target.deviceId).also { mutableApAccess.value = it }
        } catch (error: Exception) {
            invalidateApAccess()
            if (error is IoTProtocolException && error.kind in setOf(IoTFailure.AuthenticationFailed, IoTFailure.SessionExpired))
                fail(error, DiagnosticStage.AP_EDIT)
            throw error
        }
    }

    suspend fun verifyApReadback(profileId: String, ssid: String, password: CharArray): Boolean = operations.withLock {
        if (!canEditAp(profileId) || mutableApAccess.value?.ssid != ssid) throw CameraFailure(SessionError.AuthenticationFailed)
        ApHotspotProtocol.verifyReadback(exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE,
            ApHotspotProtocol.verifyParams()) ?: throw IoTProtocolException(IoTFailure.MalformedPacket), ssid, password)
    }

    /** Marker callback precedes the sole write; retry/reconnect must never call this method implicitly. */
    suspend fun configureApHotspot(profileId: String, deviceId: String, ssid: String, password: CharArray,
        beforeWrite: suspend () -> Unit, onWriteStarted: () -> Unit = {}): ApWriteResult = try { operations.withLock {
        if (maintenancePending || !canEditAp(profileId) || profile?.deviceId != deviceId) throw CameraFailure(SessionError.AuthenticationFailed)
        val previous = mutableApAccess.value!!.ssid
        require(ApHotspotProtocol.inputError(ssid, password, previous) == null)
        val owner = authenticatedIoT!!
        val epoch = generation.get()
        fun checkOwner() {
            if (owner !== iot || epoch != generation.get() || !canEditAp(profileId))
                throw CameraFailure(SessionError.AuthenticationFailed)
        }
        val latest = fetchApSnapshot()
        if (latest.ssid != previous) { invalidateApAccess(); throw CameraFailure(SessionError.IdentityMismatch) }
        checkOwner()
        beforeWrite()
        currentCoroutineContext().ensureActive()
        checkOwner()
        var started = false
        try {
            exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE,
                ApHotspotProtocol.actionParams(ssid, password, previous), beforeSend = {
                    currentCoroutineContext().ensureActive(); checkOwner(); started = true; onWriteStarted()
                }, strictCorrelation = true)
            diagnostics.record(DiagnosticStage.AP_EDIT)
            ApWriteResult.ACCEPTED
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            diagnostics.record(DiagnosticStage.AP_EDIT, DiagnosticStatus.FAILED, reason = reason(error))
            if (!started) throw error
            if (error is IoTProtocolException && error.kind in setOf(IoTFailure.DeviceRejected, IoTFailure.PermissionDenied,
                    IoTFailure.AuthenticationFailed, IoTFailure.SessionExpired)) ApWriteResult.REJECTED
            else ApWriteResult.UNKNOWN
        } finally {
            invalidateApAccess()
            if (started) withContext(NonCancellable) { shutdown(); mutableState.value = SessionState.Idle }
        }
    } } catch (error: Exception) {
        invalidateApAccess()
        if (error is IoTProtocolException && error.kind in setOf(IoTFailure.AuthenticationFailed, IoTFailure.SessionExpired))
            fail(error, DiagnosticStage.AP_EDIT)
        throw error
    } finally { password.fill('\u0000') }

    suspend fun connect(target: CameraProfile, password: CharArray): CameraCapabilities {
        accessRevoked = true; invalidateApAccess()
        var ownedEpoch: Long? = null
        return try {
            reconnectJob?.cancelAndJoin()
            operations.withLock {
                shutdown()
                ownedEpoch = generation.get()
                require(target.validationError() == null)
                if (!BuildConfig.DEBUG) throw CameraFailure(SessionError.UnsupportedProtocol)
                mutableState.value = SessionState.Connecting
                diagnostics.record(DiagnosticStage.DISCOVERY, DiagnosticStatus.STARTED)
                val resolved = resolveTarget(target)
                profile = resolved
                secret = password.copyOf()
                authenticate(resolved, password)
            }
        } catch (cancelled: CancellationException) {
            if (ownedEpoch != null) withContext(NonCancellable) { operations.withLock {
                if (ownedEpoch != null && ownedEpoch == generation.get()) { shutdown(); mutableState.value = SessionState.Idle }
            } }
            throw cancelled
        } catch (error: Exception) {
            if (ownedEpoch != null) withContext(NonCancellable) { operations.withLock {
                if (ownedEpoch != null && ownedEpoch == generation.get()) { shutdown(); fail(error) }
            } }
            throw CameraFailure(reason(error))
        }
        finally { password.fill('\u0000') }
    }

    private suspend fun authenticate(target: CameraProfile, password: CharArray): CameraCapabilities {
        val epoch = generation.get()
        val family = when {
            target.protocolProfileId == "legacy-v2" -> 1
            target.protocolProfileId == "iot-lan" -> 2
            else -> target.platformType
        }
        if (family !in setOf(1, 2)) throw CameraFailure(SessionError.UnsupportedProtocol)
        mutableState.value = SessionState.Authenticating
        diagnostics.record(DiagnosticStage.AUTH, DiagnosticStatus.STARTED)
        val channel = channelFactory(target).also { commandChannel = it }
        val caps: CameraCapabilities
        try {
            channel.connect(target.host, target.port)
            if (family == 1) {
                val id = target.deviceId.toLongOrNull()?.takeIf { it in 1..0xffffffffL }
                    ?: throw CameraFailure(SessionError.UnsupportedProtocol)
                if (target.username.isBlank()) throw CameraFailure(SessionError.PairingRequired)
                val deadline = deadlineAfterMillis()
                val request = LegacyLanProtocol.authRequest(target.username, password, id)
                try { channel.write(request, deadline) } finally { request.fill(0) }
                val bytes = channel.readExact(LegacyLanProtocol.AUTH_RESPONSE_SIZE, deadline)
                val login = try { LegacyLanProtocol.parseAuthResponse(bytes) } finally { bytes.fill(0) }
                if (epoch != generation.get()) throw CancellationException("Superseded camera session")
                legacy = login
                caps = CameraCapabilities(emptySet(), Quality.entries.toSet(), login.ptz, wifiConfiguration = true)
                diagnostics.record(DiagnosticStage.AUTH, protocolVersion = login.protocolVersion)
            } else {
                val newSession = IoTLanProtocol.authenticate(channel, password)
                if (epoch != generation.get()) { newSession.close(); throw CancellationException("Superseded camera session") }
                iot = newSession
                diagnostics.record(DiagnosticStage.AUTH)
                caps = CameraCapabilities(emptySet(), Quality.entries.toSet(), false, wifiConfiguration = true)
            }
        } finally { channel.close(); if (commandChannel === channel) commandChannel = null }
        val resolvedCaps = if (iot != null) {
            try {
                val data = exchangeIoT(IoTCommonProtocol.ABILITY_REQUEST, IoTCommonProtocol.ABILITY_RESPONSE,
                    IoTControlProtocol.abilityParams())
                val ability = IoTControlProtocol.parseAbilities(data ?: throw IoTProtocolException(IoTFailure.MalformedPacket))
                softApSupported = ApHotspotProtocol.supportsSoftAp(data)
                val management = try { CameraManagementProtocol.abilities(data) }
                    catch (_: IoTProtocolException) { ManagementAbilities() }
                diagnostics.record(DiagnosticStage.CAPABILITIES)
                caps.copy(ptz = ability.ptz, horizontalPtz = ability.horizontalPtz, verticalPtz = ability.verticalPtz,
                    recordingQualities = IoTControlProtocol.recordingQualities(data), management = management)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IoTProtocolException) { diagnostics.record(DiagnosticStage.CAPABILITIES, DiagnosticStatus.FAILED); caps }
        } else caps
        if (epoch != generation.get()) throw CancellationException("Superseded camera session")
        mutableCapabilities.value = resolvedCaps
        patrolActive = if (resolvedCaps.management.patrol == false) false else null
        authenticatedIoT = iot
        managementRevision++
        mutableAuthenticationRevision.value = managementRevision
        submittedManagementActions.clear()
        accessRevoked = false
        mutableState.value = SessionState.Ready(resolvedCaps)
        return resolvedCaps
    }

    suspend fun play(renderSurface: Surface, value: Quality = quality) = operations.withLock {
        if (surface === renderSurface && state.value is SessionState.Streaming && quality == value) return@withLock
        surface = renderSurface
        try { startStream(value); withTimeout(5000) { firstFrame.await() } }
        catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) { stopStream(); fail(SocketTimeoutException(), DiagnosticStage.STREAM) }
            else { stopStream(); throw cancelled }
        } catch (error: Exception) { stopStream(); fail(error, DiagnosticStage.STREAM) }
    }

    suspend fun changeQuality(value: Quality) {
        val active = surface ?: return
        play(active, value)
    }

    private suspend fun startStream(value: Quality) {
        val target = profile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        if (legacy == null && iot == null) throw CameraFailure(SessionError.AuthenticationFailed)
        stopStream()
        quality = value
        mutableState.value = SessionState.Negotiating(value)
        diagnostics.record(DiagnosticStage.STREAM, DiagnosticStatus.STARTED)
        firstFrame = CompletableDeferred()
        val epoch = generation.get()
        val channel = channelFactory(target).also { streamChannel = it }
        channel.connect(target.host, target.port)
        val selector = if (value == Quality.HD) 1 else 0
        val login = legacy
        if (login != null) {
            val deadline = deadlineAfterMillis()
            channel.write(LegacyLanProtocol.streamStart(target.deviceId.toLong(), login, 0, selector), deadline)
            val info = LegacyLanProtocol.parseStreamResponse(channel.readExact(LegacyLanProtocol.STREAM_RESPONSE_MIN_SIZE, deadline))
            channel.write(LegacyLanProtocol.streamContinue(), deadline)
            mediaCipher = LegacyMediaCipher(login.protocolVersion, info.communicationVersion, login.handle)
            val parser = LegacyV2FrameParser(allowInitialResponsePadding = true)
            streamJob = scope.launch { guardedReader(epoch) {
                var malformed = 0
                val arrivalStart = System.nanoTime()
                while (isActive && epoch == generation.get()) {
                    val chunk = channel.readSome()
                    val frames = try { parser.feed(chunk) } catch (error: LegacyMalformedPacketException) {
                        decoder?.resetToKeyframe()
                        if (++malformed >= 3) throw error
                        diagnostics.record(DiagnosticStage.FRAME, DiagnosticStatus.FAILED)
                        emptyList()
                    }
                    for (frame in frames) {
                        if (frame.codec == null || epoch != generation.get()) continue
                        val plain = mediaCipher?.decrypt(frame) ?: continue
                        malformed = 0
                        offerFrame(EncodedFrame(frame.codec, frame.channel, frame.keyframe,
                            (System.nanoTime() - arrivalStart) / 1000, plain), info.width, info.height, epoch)
                    }
                }
            } }
        } else {
            val session = iot ?: throw CameraFailure(SessionError.AuthenticationFailed)
            val key = session.keyBytes()
            iotMediaInfo = null
            val id = requestIds.incrementAndGet()
            val reply = Reply(id, IoTCommonProtocol.LIVE_CONTROL_RESPONSE, CompletableDeferred())
            pendingReply = reply
            val request = IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.LIVE_CONTROL_REQUEST,
                requestId = id, params = IoTMediaProtocol.liveParams(selector), secureType = 2,
                sessionId = session.sessionId, key = key)
            streamJob = scope.launch { guardedReader(epoch) { readIoTStream(channel, session, key, epoch) } }
            try {
                channel.write(request)
                withTimeout(5000) { reply.result.await() }
            } finally { request.fill(0); if (pendingReply === reply) pendingReply = null }
        }
        watchdog = PtzWatchdog(scope, { direction -> sendMotion(direction, epoch) })
    }

    private suspend fun readIoTStream(channel: TcpChannel, session: IoTSession, key: ByteArray, epoch: Long) {
        val arrivalStart = System.nanoTime()
        var malformedMedia = 0
        var detail = DiagnosticDetail.READ_HEADER
        var currentHeader: IoTHeader? = null
        try {
            while (currentCoroutineContext().isActive && epoch == generation.get()) {
                detail = DiagnosticDetail.READ_HEADER
                currentHeader = null
                val deadline = deadlineAfterMillis()
                val header = IoTCommonProtocol.parseHeader(channel.readExact(16, deadline), allowUnknownCommand = true)
                currentHeader = header
                detail = DiagnosticDetail.READ_BODY
                val body = channel.readExact(header.bodySize, deadline)
                try {
                    if (header.command == IoTCommonProtocol.COMMAND_SHUTDOWN) {
                        detail = DiagnosticDetail.DEVICE_SHUTDOWN
                        throw java.io.EOFException("Camera ended the stream")
                    }
                    if (header.command == IoTCommonProtocol.COMMAND_MEDIA) {
                        detail = DiagnosticDetail.MEDIA_PACKET
                        val frame = try { IoTMediaProtocol.parseFrame(header, body, key) }
                        catch (error: IoTProtocolException) {
                            if (error.kind != IoTFailure.MalformedPacket) throw error
                            recordFailure(error, DiagnosticStage.FRAME, detail, header.command, body.size)
                            decoder?.resetToKeyframe()
                            if (++malformedMedia >= 3) throw error
                            continue // The outer length is known: discard this frame, not the TCP alignment.
                        }
                        malformedMedia = 0
                        if (frame.audio || frame.channel != 0) continue
                        val info = iotMediaInfo ?: continue
                        val video = info.views.getOrNull(frame.channel) ?: info.video
                        val codec = when (video.codecId) {
                            IoTMediaProtocol.CODEC_AVC -> VideoCodec.AVC
                            IoTMediaProtocol.CODEC_HEVC -> VideoCodec.HEVC
                            else -> throw CameraFailure(SessionError.UnsupportedCodec)
                        }
                        offerFrame(EncodedFrame(codec, frame.channel, frame.keyframe,
                            (System.nanoTime() - arrivalStart) / 1000, frame.bytes), video.width, video.height, epoch)
                    } else if (header.command in setOf(IoTCommonProtocol.COMMAND_IOT, IoTCommonProtocol.COMMAND_NOTIFY)) {
                        detail = DiagnosticDetail.CONTROL_JSON
                        val json = IoTCommonProtocol.decodeJson(header, body, key)
                        val method = json.optInt("method_id", -1)
                        if (header.command == IoTCommonProtocol.COMMAND_NOTIFY && method == IoTCommonProtocol.DECODE_FEEDBACK) {
                            val params = IoTControlProtocol.decodeFeedbackParams(json) ?: continue
                            detail = DiagnosticDetail.DECODE_FEEDBACK
                            val feedback = IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.DECODE_FEEDBACK,
                                requestId = requestIds.incrementAndGet(), params = params, secureType = 2,
                                sessionId = session.sessionId, key = key)
                            try { channel.write(feedback, deadlineAfterMillis(1000)) }
                            finally { feedback.fill(0) }
                            diagnostics.record(DiagnosticStage.STREAM, detail = detail)
                        } else if (method == IoTCommonProtocol.LIVE_INFORMATION) {
                            detail = DiagnosticDetail.MEDIA_INFO
                            iotMediaInfo = IoTMediaProtocol.parseMediaInfo(json)
                            diagnostics.record(DiagnosticStage.STREAM, bytes = body.size)
                        } else {
                            val reply = pendingReply
                            if (reply != null && json.optInt("id", -1) == reply.id) {
                                val response = IoTCommonProtocol.validateResponse(json, reply.id, methodId = reply.method)
                                if (response.code == 1000 && header.secureType != 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
                                response.requireSuccess()
                                reply.result.complete(json)
                            } else if (json.optJSONObject("result")?.optInt("code") == 1104)
                                throw IoTProtocolException(IoTFailure.SessionExpired, 1104)
                        }
                    } else diagnostics.record(DiagnosticStage.STREAM, detail = DiagnosticDetail.IGNORED_PACKET,
                        command = header.command, bytes = header.bodySize)
                } finally { body.fill(0) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            recordFailure(error, DiagnosticStage.STREAM, detail, currentHeader?.command, currentHeader?.bodySize)
            throw error
        } finally { key.fill(0) }
    }

    private fun offerFrame(frame: EncodedFrame, width: Int, height: Int, epoch: Long) {
        if (epoch != generation.get()) return
        val renderSurface = surface?.takeIf { it.isValid } ?: return
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 33_554_432)
        if (decoder == null || decoderCodec != frame.codec || decoderDimensions != width to height) {
            decoder?.close()
            decoderCodec = frame.codec
            decoderDimensions = width to height
            mutableVideoGeometry.value = null
            decoder = VideoDecoder(renderSurface, width, height, frame.codec, paced = activeRecording != null, onGeometry = {
                if (generation.get() == epoch) mutableVideoGeometry.value = it
            }, onRendered = {
                if (generation.get() == epoch) renderedAt.value = it
            }, onFirstFrame = {
                if (generation.get() == epoch) {
                    mutableCapabilities.value = mutableCapabilities.value?.copy(codecs = setOf(frame.codec))
                    if (mutableState.value !is SessionState.RecordingEnded)
                        mutableState.value = activeRecording?.let { SessionState.PlayingRecording(it) } ?: SessionState.Streaming(quality)
                    diagnostics.record(DiagnosticStage.FRAME)
                    firstFrame.complete(Unit)
                }
            }, onError = { error -> if (generation.get() == epoch) {
                firstFrame.completeExceptionally(CameraFailure(error))
                scope.launch { operations.withLock { if (generation.get() == epoch) { stopStream(); fail(CameraFailure(error), DiagnosticStage.FRAME, DiagnosticDetail.DECODER) } } }
            } })
        }
        if (activeRecording == null) decoder?.offer(frame)
    }

    private suspend fun guardedReader(epoch: Long, block: suspend CoroutineScope.() -> Unit) {
        try { coroutineScope(block) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { streamFailure(epoch, error, DiagnosticStage.STREAM) }
    }

    /** Claim one recovery per generation before a PTZ write error can replace Streaming with Error. */
    private fun streamFailure(epoch: Long, error: Exception, stage: DiagnosticStage) {
        if (generation.get() != epoch || failedStreamGeneration.getAndAccumulate(epoch) { old, next -> maxOf(old, next) } >= epoch) return
        pendingReply?.result?.completeExceptionally(error)
        firstFrame.completeExceptionally(error)
        if (!areaCapturing && state.value is SessionState.Streaming && retryable(error)) {
            recordFailure(error, stage)
            reconnectJob = scope.launch { recover(epoch) }
        } else scope.launch { operations.withLock {
            if (generation.get() == epoch) { stopStream(); fail(error, stage) }
        } }
    }

    private suspend fun recover(epoch: Long) = operations.withLock {
        if (generation.get() != epoch) return@withLock
        val target = profile ?: return@withLock
        val password = secret?.copyOf() ?: return@withLock
        try {
            stopStream()
            iot?.close(); iot = null; legacy = null
            for ((index, wait) in listOf(1000L, 2000L, 4000L).withIndex()) {
                mutableState.value = SessionState.Reconnecting(index + 1)
                diagnostics.record(DiagnosticStage.RECONNECT, DiagnosticStatus.STARTED, code = index + 1)
                delay(wait)
                try {
                    authenticate(target, password)
                    if (surface?.isValid != true) return@withLock
                    startStream(quality)
                    withTimeout(5000) { firstFrame.await() }
                    return@withLock
                } catch (cancelled: CancellationException) {
                    if (cancelled !is TimeoutCancellationException) throw cancelled
                    stopStream(); iot?.close(); iot = null; legacy = null
                } catch (error: Exception) {
                    stopStream(); iot?.close(); iot = null; legacy = null
                    if (!retryable(error)) { fail(error, DiagnosticStage.RECONNECT); return@withLock }
                }
            }
            fail(SocketTimeoutException(), DiagnosticStage.RECONNECT)
        } finally { password.fill('\u0000') }
    }

    suspend fun move(direction: Direction) {
        val caps = capabilities.value ?: return
        if (!manualPtzAllowed || state.value !is SessionState.Streaming || !caps.ptz) return
        if (direction in setOf(Direction.LEFT, Direction.RIGHT) && !caps.horizontalPtz) return
        if (direction in setOf(Direction.UP, Direction.DOWN) && !caps.verticalPtz) return
        val epoch = generation.get()
        invalidateAreaReference()
        try { watchdog?.move(direction) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { streamFailure(epoch, error, DiagnosticStage.PTZ) }
    }
    suspend fun renewPtz() {
        if (areaCapturing) return
        val epoch = generation.get()
        try { watchdog?.renew(resend = iot != null) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { streamFailure(epoch, error, DiagnosticStage.PTZ) }
    }
    suspend fun stopPtz() { runCatching { watchdog?.stop() } }
    private suspend fun sendMotion(direction: Direction?, epoch: Long) {
        if (epoch != generation.get()) return
        val channel = streamChannel ?: return
        val session = iot
        val request = if (session == null) LegacyLanProtocol.ptz(0, direction) else {
            val key = session.keyBytes()
            try { IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.ACTION_REQUEST,
                requestId = requestIds.incrementAndGet(), params = IoTControlProtocol.ptzParams(when (direction) {
                    Direction.UP -> IoTPtzOperation.Up; Direction.DOWN -> IoTPtzOperation.Down
                    Direction.LEFT -> IoTPtzOperation.Left; Direction.RIGHT -> IoTPtzOperation.Right
                    null -> IoTPtzOperation.Stop
                }), secureType = 2, sessionId = session.sessionId, key = key) } finally { key.fill(0) }
        }
        try { channel.write(request, deadlineAfterMillis(500)); diagnostics.record(DiagnosticStage.PTZ) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            recordFailure(error, DiagnosticStage.PTZ, DiagnosticDetail.PTZ_WRITE)
            streamFailure(epoch, error, DiagnosticStage.PTZ)
            throw error
        } finally { request.fill(0) }
    }

    suspend fun configureWifi(ssid: String, password: CharArray) = try { operations.withLock {
        if (maintenancePending) throw CameraFailure(SessionError.FeatureUnsupported)
        if (state.value !is SessionState.Ready) throw CameraFailure(SessionError.AuthenticationFailed)
        val target = profile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val channel = channelFactory(target).also { commandChannel = it }
        try {
            channel.connect(target.host, target.port)
            val deadline = deadlineAfterMillis()
            val session = iot
            if (session == null) {
                val login = legacy ?: throw CameraFailure(SessionError.AuthenticationFailed)
                val response = ApProtocol.configureLegacyWifi(target.deviceId, login.handle, ssid, password,
                    write = { channel.write(it, deadline) }, readExact = { channel.readExact(it, deadline) })
                if (!response.acknowledged) throw CameraFailure(SessionError.WifiConfigurationRejected)
            } else {
                val key = session.keyBytes()
                try { ApProtocol.configureWifi(ssid, password, requestIds.incrementAndGet(), session.sessionId,
                    sessionKey = key, write = { channel.write(it, deadline) }, readExact = { channel.readExact(it, deadline) }).requireSuccess() }
                finally { key.fill(0) }
            }
            diagnostics.record(DiagnosticStage.PROVISIONING)
        } catch (error: Exception) { diagnostics.record(DiagnosticStage.PROVISIONING, DiagnosticStatus.FAILED); throw error }
        finally { password.fill('\u0000'); channel.close(); if (commandChannel === channel) commandChannel = null }
    } } finally { password.fill('\u0000') }

    /** Changes an authenticated LAN password; never resets ownership or imports vendor credentials. */
    suspend fun changeLanPassword(oldPassword: CharArray, newPassword: CharArray) = operations.withLock {
        if (maintenancePending) throw CameraFailure(SessionError.FeatureUnsupported)
        val target = profile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val session = iot ?: throw CameraFailure(SessionError.UnsupportedProtocol)
        val key = session.keyBytes()
        val channel = channelFactory(target).also { commandChannel = it }
        try {
            channel.connect(target.host, target.port)
            val deadline = deadlineAfterMillis()
            ApProtocol.changeLanPassword(oldPassword, newPassword, requestIds.incrementAndGet(), session.sessionId,
                java.security.SecureRandom().nextInt(127) + 1, key,
                write = { channel.write(it, deadline) }, readExact = { channel.readExact(it, deadline) }).requireSuccess()
        } finally { channel.close(); key.fill(0); if (commandChannel === channel) commandChannel = null }
    }

    private suspend fun exchangeIoT(method: Int, responseMethod: Int, params: JSONObject,
        beforeSend: suspend () -> Unit = {}, strictCorrelation: Boolean = false): JSONObject? {
        val target = profile ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val session = iot ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val key = session.keyBytes()
        val channel = channelFactory(target).also { commandChannel = it }
        try {
            channel.connect(target.host, target.port)
            val deadline = deadlineAfterMillis()
            val id = requestIds.incrementAndGet()
            val request = IoTCommonProtocol.encodeRequest(methodId = method, requestId = id, params = params,
                secureType = 2, sessionId = session.sessionId, key = key)
            try { beforeSend(); channel.write(request, deadline) } finally { request.fill(0) }
            val header = IoTCommonProtocol.parseHeader(channel.readExact(16, deadline))
            if (header.command != IoTCommonProtocol.COMMAND_IOT || header.sessionId !in setOf(0, session.sessionId))
                throw IoTProtocolException(IoTFailure.MalformedPacket)
            val body = channel.readExact(header.bodySize, deadline)
            try {
                if (header.secureType != 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
                val json = IoTCommonProtocol.decodeJson(header, body, key)
                if (strictCorrelation && (IoTCommonProtocol.exactInt(json, "id") != id ||
                        IoTCommonProtocol.exactInt(json, "chn") !in setOf(0, 1) || IoTCommonProtocol.exactInt(json, "method_id") != responseMethod))
                    throw IoTProtocolException(IoTFailure.MalformedPacket)
                val response = IoTCommonProtocol.validateResponse(json, id, methodId = responseMethod)
                return response.requireSuccess()
            } finally { body.fill(0) }
        } finally { channel.close(); key.fill(0); if (commandChannel === channel) commandChannel = null }
    }

    suspend fun stopMedia() = operations.withLock {
        stopStream()
        mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
    }

    private fun requireIoT() {
        if (profile == null) throw CameraFailure(SessionError.AuthenticationFailed)
        if (iot == null) throw CameraFailure(SessionError.FeatureUnsupported)
    }

    private suspend fun fetchSettings(): CameraSettings {
        requireIoT()
        val values = linkedMapOf<Int, Any>()
        val rejected = linkedMapOf<Int, Int>()
        for (ids in CameraSettingsProtocol.properties.chunked(10)) {
            val data = exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE,
                CameraSettingsProtocol.getParams(ids)) ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
            val result = CameraSettingsProtocol.parseProperties(data, ids)
            values.putAll(result.values); rejected.putAll(result.rejected)
        }
        return CameraSettings(values.toMap(), rejected.toMap())
    }

    suspend fun readSettings(): CameraSettings = operations.withLock {
        fetchSettings().also { diagnostics.record(DiagnosticStage.SETTINGS) }
    }

    suspend fun readRecordingStorage(): CameraSettings = operations.withLock {
        requireIoT()
        val ids = listOf(CameraSettingsProtocol.SD_STATUS, CameraSettingsProtocol.SD_CAPACITY)
        val data = exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE,
            CameraSettingsProtocol.getParams(ids)) ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        CameraSettingsProtocol.parseProperties(data, ids)
    }

    suspend fun saveSettings(changes: Map<Int, Any>, before: CameraSettings): CameraSettings = operations.withLock {
        if (maintenancePending) throw CameraFailure(SessionError.FeatureUnsupported)
        requireIoT()
        val params = CameraSettingsProtocol.setParams(changes, before, capabilities.value?.recordingQualities.orEmpty())
        val data = exchangeIoT(CameraSettingsProtocol.SET, CameraSettingsProtocol.SET_RESPONSE, params)
            ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        CameraSettingsProtocol.validateSet(data, changes.keys)
        val after = fetchSettings()
        if (changes.any { (id, value) -> after.values[id] != value }) throw CameraFailure(SessionError.DeviceRejected)
        diagnostics.record(DiagnosticStage.SETTINGS)
        after
    }

    suspend fun reverseImage(before: CameraSettings): CameraSettings = operations.withLock {
        if (maintenancePending) throw CameraFailure(SessionError.FeatureUnsupported)
        requireIoT()
        val previous = before.int(CameraSettingsProtocol.FLIP_MODE) ?: throw CameraFailure(SessionError.FeatureUnsupported)
        invalidateAreaReference()
        exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE, CameraSettingsProtocol.flipParams())
        val after = fetchSettings()
        if (after.int(CameraSettingsProtocol.FLIP_MODE) == null || after.int(CameraSettingsProtocol.FLIP_MODE) == previous)
            throw CameraFailure(SessionError.DeviceRejected)
        diagnostics.record(DiagnosticStage.SETTINGS)
        after
    }

    /** Acknowledgement only. The caller owns the durable, single-send marker and status monitoring. */
    suspend fun sendSdFormat() = operations.withLock {
        if (!CameraSettingsProtocol.FORMAT_STATUS_VERIFIED) throw CameraFailure(SessionError.FeatureUnsupported)
        requireIoT()
        stopStream()
        mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
        exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE, CameraSettingsProtocol.formatParams())
        diagnostics.record(DiagnosticStage.SD_FORMAT)
        Unit
    }

    private fun requireManagement(profileId: String, revision: Long? = null) {
        if (!isAuthenticated(profileId) || iot == null || iot !== authenticatedIoT ||
            revision != null && managementRevision != revision) throw CameraFailure(SessionError.AuthenticationFailed)
    }

    private fun requireStoppedPatrol(settings: CameraSettings) {
        val ability = capabilities.value?.management ?: throw CameraFailure(SessionError.FeatureUnsupported)
        val patrol = settings.values[CameraManagementProtocol.PATROL] as? CameraPatrol
        if (patrol?.enabled == true || patrol?.timerEnabled == true ||
            patrol == null && ability.patrol != false && ability.motorControl != 0)
            throw CameraFailure(SessionError.FeatureUnsupported)
    }

    private suspend fun fetchManagement(group: ManagementGroup): CameraSettings {
        if (group == ManagementGroup.MOTOR) patrolActive = null
        val data = exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE,
            CameraManagementProtocol.getParams(group), strictCorrelation = true)
            ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        return CameraManagementProtocol.parse(data, group, capabilities.value?.management ?: ManagementAbilities()).also {
            if (group == ManagementGroup.MOTOR) {
                val patrol = it.values[CameraManagementProtocol.PATROL] as? CameraPatrol
                patrolActive = if (patrol != null) patrol.enabled || patrol.timerEnabled else if (capabilities.value?.management?.patrol == false) false else null
            }
        }
    }

    suspend fun readManagement(profileId: String, group: ManagementGroup): ManagementSnapshot = operations.withLock {
        requireManagement(profileId)
        val owner = iot
        val revision = managementRevision
        val settings = fetchManagement(group)
        requireManagement(profileId, revision)
        if (owner !== iot) throw CameraFailure(SessionError.AuthenticationFailed)
        ManagementSnapshot(profileId, revision, group, settings, profile!!.deviceId)
    }

    fun invalidateAreaReference() { mutableAreaReference.value = null }

    suspend fun awaitAreaFrame() {
        if (!areaCapturing) throw CameraFailure(SessionError.FeatureUnsupported)
        val epoch = generation.get()
        val afterLayout = System.nanoTime()
        withTimeout(1500) { renderedAt.first { it >= afterLayout } }
        if (!areaCapturing || generation.get() != epoch) throw CameraFailure(SessionError.AuthenticationFailed)
    }

    fun canCaptureArea(profileId: String): Boolean = BuildConfig.DEBUG && !maintenancePending && !areaCapturing &&
        isAuthenticated(profileId) && iot === authenticatedIoT && iot != null && state.value is SessionState.Ready &&
        capabilities.value?.management?.flatArea == true

    fun canUseAreaReference(reference: AreaReference?): Boolean = reference != null && mutableAreaReference.value === reference &&
        !maintenancePending && !areaCapturing && isAuthenticated(reference.context.profileId) && state.value is SessionState.Ready &&
        profile?.deviceId == reference.context.deviceId && managementRevision == reference.context.revision &&
        generation.get() == reference.context.generation && capabilities.value?.management?.flatArea == true

    private suspend fun readAreaOrientation(): Int {
        val ids = listOf(CameraSettingsProtocol.FLIP_MODE)
        val data = exchangeIoT(CameraSettingsProtocol.GET, CameraSettingsProtocol.GET_RESPONSE,
            CameraSettingsProtocol.getParams(ids), strictCorrelation = true) ?: throw CameraFailure(SessionError.MalformedPacket)
        return CameraSettingsProtocol.parseProperties(data, ids).int(CameraSettingsProtocol.FLIP_MODE)
            ?: throw CameraFailure(SessionError.FeatureUnsupported)
    }

    /** The sole capture owner. Media is stopped before issuing a reference; never auto-reconnect preview. */
    suspend fun captureAlarmArea(before: ManagementSnapshot, preview: Surface,
        copyFrame: suspend (VideoGeometry) -> Unit): AreaReference = operations.withLock {
        requireManagement(before.profileId, before.revision)
        if (!canCaptureArea(before.profileId) || before.group != ManagementGroup.ALARM || before.deviceId != profile?.deviceId)
            throw CameraFailure(SessionError.FeatureUnsupported)
        val baseline = before.settings.values[CameraManagementProtocol.ALARM_AREA] as? CameraAlarmArea
            ?: throw CameraFailure(SessionError.FeatureUnsupported)
        if (!CameraAreaProtocol.valid(baseline)) throw CameraFailure(SessionError.InvalidInput)
        val owner = iot
        invalidateAreaReference()
        areaCapturing = true
        var captureEpoch = -1L
        var geometry: VideoGeometry? = null
        val orientation: Int
        try {
            val fresh = fetchManagement(ManagementGroup.ALARM).values[CameraManagementProtocol.ALARM_AREA] as? CameraAlarmArea
                ?: throw CameraFailure(SessionError.FeatureUnsupported)
            if (!CameraAreaProtocol.sameBaseline(baseline, fresh)) throw CameraFailure(SessionError.DeviceRejected)
            requireStoppedPatrol(fetchManagement(ManagementGroup.MOTOR))
            orientation = readAreaOrientation()
            surface = preview
            startStream(Quality.HD)
            captureEpoch = generation.get()
            withTimeout(5000) { firstFrame.await() }
            val decoded = mutableVideoGeometry.value ?: throw CameraFailure(SessionError.FeatureUnsupported)
            val media = iotMediaInfo ?: throw CameraFailure(SessionError.FeatureUnsupported)
            val video = media.views.singleOrNull() ?: media.video
            if (!decoded.flat || media.views.size > 1 || video.width != decoded.width || video.height != decoded.height)
                throw CameraFailure(SessionError.FeatureUnsupported)
            geometry = decoded
            withTimeout(3000) { copyFrame(decoded) }
            requireManagement(before.profileId, before.revision)
            if (iot !== owner || generation.get() != captureEpoch || mutableVideoGeometry.value != decoded ||
                failedStreamGeneration.get() >= captureEpoch) throw CameraFailure(SessionError.NetworkUnavailable)
        } finally {
            withContext(NonCancellable) {
                stopStream(); surface = null
                if (isAuthenticated(before.profileId)) mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
            }
            areaCapturing = false
        }
        requireManagement(before.profileId, before.revision)
        if (owner !== iot || generation.get() != captureEpoch + 1 || failedStreamGeneration.get() >= captureEpoch ||
            readAreaOrientation() != orientation) throw CameraFailure(SessionError.DeviceRejected)
        val fresh = fetchManagement(ManagementGroup.ALARM).values[CameraManagementProtocol.ALARM_AREA] as? CameraAlarmArea
            ?: throw CameraFailure(SessionError.FeatureUnsupported)
        if (!CameraAreaProtocol.sameBaseline(baseline, fresh)) throw CameraFailure(SessionError.DeviceRejected)
        currentCoroutineContext().ensureActive()
        requireManagement(before.profileId, before.revision)
        if (owner !== iot || generation.get() != captureEpoch + 1 || maintenancePending || profile?.deviceId != before.deviceId)
            throw CameraFailure(SessionError.AuthenticationFailed)
        AreaReference(AreaContext(before.profileId, before.deviceId, before.revision, generation.get(), orientation),
            fresh, geometry!!).also { mutableAreaReference.value = it }
    }

    /** Reconcile every property, including partial acceptance, without replaying the write. */
    suspend fun saveManagement(before: ManagementSnapshot, changes: Map<Int, Any>): ManagementSnapshot = operations.withLock {
        if (maintenancePending) throw CameraFailure(SessionError.FeatureUnsupported)
        requireManagement(before.profileId, before.revision)
        if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val reference = if (CameraManagementProtocol.ALARM_AREA in changes) mutableAreaReference.value else null
        if (CameraManagementProtocol.ALARM_AREA in changes && !canUseAreaReference(reference))
            throw CameraFailure(SessionError.FeatureUnsupported)
        val owner = iot
        val fresh = fetchManagement(before.group)
        if (changes.keys.any { !CameraManagementProtocol.sameBaseline(it, before.settings.values[it], fresh.values[it]) })
            throw CameraFailure(SessionError.DeviceRejected)
        if (reference != null && (!reference.matches(AreaContext(before.profileId, before.deviceId, managementRevision,
                generation.get(), readAreaOrientation()), fresh.values[CameraManagementProtocol.ALARM_AREA] as CameraAlarmArea))) {
            invalidateAreaReference()
            throw CameraFailure(SessionError.DeviceRejected)
        }
        val params = CameraManagementProtocol.setParams(changes, fresh, capabilities.value!!.management)
        try { exchangeIoT(CameraSettingsProtocol.SET, CameraSettingsProtocol.SET_RESPONSE, params,
            beforeSend = { requireManagement(before.profileId, before.revision)
                if (owner !== iot || maintenancePending) throw CameraFailure(SessionError.AuthenticationFailed)
                if (reference != null && !canUseAreaReference(reference)) throw CameraFailure(SessionError.FeatureUnsupported)
            },
            strictCorrelation = true)?.let { CameraSettingsProtocol.validateSet(it, changes.keys) }
            ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        } finally { if (reference != null) invalidateAreaReference() }
        val after = fetchManagement(before.group)
        requireManagement(before.profileId, before.revision)
        if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        if (changes.any { (id, value) -> !CameraManagementProtocol.same(id, value, after.values[id]) }) throw CameraFailure(SessionError.DeviceRejected)
        before.copy(settings = after)
    }

    suspend fun presetAction(before: ManagementSnapshot, slot: Int, operation: Int, operationId: String): ManagementSnapshot = operations.withLock {
        if (maintenancePending || state.value !is SessionState.Streaming) throw CameraFailure(SessionError.FeatureUnsupported)
        requireManagement(before.profileId, before.revision)
        if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val fresh = fetchManagement(ManagementGroup.MOTOR)
        requireStoppedPatrol(fresh)
        val used = fresh.values[CameraManagementProtocol.PRESETS] as? CameraPresets ?: throw CameraFailure(SessionError.FeatureUnsupported)
        if (before.settings.values[CameraManagementProtocol.PRESETS] != used) throw CameraFailure(SessionError.DeviceRejected)
        if (operation in 2..3 && slot !in used.used) throw CameraFailure(SessionError.InvalidInput)
        val capacity = capabilities.value?.management?.presets ?: throw CameraFailure(SessionError.FeatureUnsupported)
        val params = CameraManagementProtocol.presetParams(slot, operation, capacity)
        stopPtz()
        val owner = iot
        exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE, params,
            beforeSend = {
                requireManagement(before.profileId, before.revision)
                if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
                if (owner !== iot || maintenancePending || !submittedManagementActions.add(operationId)) throw CameraFailure(SessionError.InvalidInput)
            }, strictCorrelation = true)
        val after = fetchManagement(ManagementGroup.MOTOR)
        requireManagement(before.profileId, before.revision)
        if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val slots = after.values[CameraManagementProtocol.PRESETS] as? CameraPresets
        if (operation == 1 && slot !in slots?.used.orEmpty() || operation == 3 && slot in slots?.used.orEmpty()) throw CameraFailure(SessionError.DeviceRejected)
        diagnostics.record(DiagnosticStage.PTZ_PRESET)
        before.copy(settings = after)
    }

    suspend fun calibrateCamera(before: ManagementSnapshot, operationId: String, beforeWrite: suspend () -> Unit) = operations.withLock {
        if (maintenancePending || capabilities.value?.management?.calibration != true) throw CameraFailure(SessionError.FeatureUnsupported)
        requireManagement(before.profileId, before.revision)
        if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val ability = capabilities.value!!.management
        if (ability.patrol != false && ability.motorControl != 0) requireStoppedPatrol(fetchManagement(ManagementGroup.MOTOR))
        stopStream()
        mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
        val owner = iot
        exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE, CameraManagementProtocol.calibrateParams(),
            beforeSend = {
                requireManagement(before.profileId, before.revision)
                if (profile?.deviceId != before.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
                if (owner !== iot || maintenancePending || !submittedManagementActions.add(operationId)) throw CameraFailure(SessionError.InvalidInput)
                beforeWrite()
                currentCoroutineContext().ensureActive()
                requireManagement(before.profileId, before.revision)
                maintenancePending = true
            }, strictCorrelation = true)
        // ACK proves acceptance only; keep further motor commands gated until a new session/status check.
        maintenancePending = true
        diagnostics.record(DiagnosticStage.PTZ_CALIBRATION)
    }

    fun restartPassword(profileId: String): CharArray {
        requireManagement(profileId)
        return secret?.copyOf() ?: throw CameraFailure(SessionError.AuthenticationFailed)
    }

    suspend fun restartCamera(profileId: String, deviceId: String, operationId: String, beforeWrite: suspend () -> Unit) = operations.withLock {
        requireManagement(profileId)
        if (profile?.deviceId != deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        if (maintenancePending || capabilities.value?.management?.restart != true) throw CameraFailure(SessionError.FeatureUnsupported)
        val ability = capabilities.value!!.management
        if (ability.patrol != false && ability.motorControl != 0) requireStoppedPatrol(fetchManagement(ManagementGroup.MOTOR))
        stopStream()
        mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
        val revision = managementRevision; val owner = iot
        try {
            exchangeIoT(IoTCommonProtocol.ACTION_REQUEST, IoTCommonProtocol.ACTION_RESPONSE, CameraManagementProtocol.restartParams(),
                beforeSend = {
                    requireManagement(profileId, revision)
                    if (owner !== iot || maintenancePending || !submittedManagementActions.add(operationId)) throw CameraFailure(SessionError.InvalidInput)
                    beforeWrite()
                    currentCoroutineContext().ensureActive()
                    requireManagement(profileId, revision)
                    maintenancePending = true
                }, strictCorrelation = true)
            diagnostics.record(DiagnosticStage.RESTART)
        } finally {
            // A disconnect after submission is expected; the ordinary stream recovery must not restart it.
            withContext(NonCancellable) { shutdown(); mutableState.value = SessionState.Idle }
        }
    }

    suspend fun listRecordings(startMs: Long, endMs: Long, eventsOnly: Boolean): List<RecordingSegment> = operations.withLock {
        requireIoT()
        val target = profile!!
        val session = iot!!
        val key = session.keyBytes()
        val channel = channelFactory(target).also { commandChannel = it }
        val deadline = deadlineAfterMillis(30000)
        val id = requestIds.incrementAndGet()
        try {
            channel.connect(target.host, target.port)
            sendRecordingControl(channel, session, key, RecordingProtocol.LIST, id,
                RecordingProtocol.listParams(startMs, endMs, eventsOnly))
            val result = linkedMapOf<String, RecordingSegment>()
            var slice = 0
            while (slice < 512) {
                val header = IoTCommonProtocol.parseHeader(channel.readExact(16, deadline))
                val bytes = channel.readExact(header.bodySize, deadline)
                val json = try { IoTCommonProtocol.decodeJson(header, bytes, key) } finally { bytes.fill(0) }
                if (header.command != IoTCommonProtocol.COMMAND_IOT || header.secureType != 2 || header.sessionId !in setOf(0, session.sessionId))
                    throw IoTProtocolException(IoTFailure.MalformedPacket)
                val data = IoTCommonProtocol.validateResponse(json, id, methodId = RecordingProtocol.LIST_RESPONSE).requireSuccess()
                    ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
                val page = RecordingProtocol.parsePage(data, startMs, endMs, slice)
                page.recordings.forEach { result[it.id] = it }
                if (result.size > RecordingProtocol.MAX_SEGMENTS) throw IoTProtocolException(IoTFailure.MalformedPacket)
                slice = page.slice
                if (!page.more) {
                    diagnostics.record(DiagnosticStage.RECORDINGS, bytes = result.size)
                    return@withLock result.values.sortedBy { it.startMs }
                }
            }
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        } finally { key.fill(0); channel.close(); if (commandChannel === channel) commandChannel = null }
    }

    suspend fun playRecording(renderSurface: Surface, recording: RecordingSegment, positionMs: Long = recording.startMs) {
        operations.withLock {
            requireIoT()
            stopStream()
            surface = renderSurface
            activeRecording = recording
            firstFrame = CompletableDeferred()
            val epoch = generation.get()
            val target = profile!!
            val session = iot!!
            val key = session.keyBytes()
            val channel = channelFactory(target).also { streamChannel = it }
            mutableState.value = SessionState.LoadingRecording(recording)
            streamJob = scope.launch {
                try {
                    channel.connect(target.host, target.port)
                    var firstTimestamp: Long? = null
                    consumeRecording(channel, session, key, recording, positionMs, false, epoch) { frame, info ->
                        if (firstTimestamp == null) firstTimestamp = frame.timestampMs
                        val delta = recordingTimestampDelta(frame.timestampMs, firstTimestamp!!, recording)
                        val codec = recordingCodec(info.codecId)
                        val encoded = EncodedFrame(codec, frame.channel, frame.keyframe, delta * 1000, frame.bytes)
                        offerFrame(encoded, info.width, info.height, epoch)
                        decoder?.offerPlayback(encoded)
                        if (generation.get() == epoch) mutableRecordingPosition.value = (positionMs + delta).coerceAtMost(recording.endMs)
                    }
                    if (generation.get() == epoch) mutableState.value = SessionState.RecordingEnded(recording)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    firstFrame.completeExceptionally(error)
                    scope.launch { operations.withLock {
                        if (generation.get() == epoch) { stopStream(); fail(error, DiagnosticStage.PLAYBACK) }
                    } }
                } finally { key.fill(0); channel.close() }
            }
        }
    }

    /** Uses the same replay wire in download mode, but never the dropping decoder queue. */
    suspend fun downloadRecording(recording: RecordingSegment, sample: suspend (EncodedFrame, Int, Int) -> Unit) {
        val target: CameraProfile
        val session: IoTSession
        val channel: TcpChannel
        val epoch: Long
        operations.withLock {
            requireIoT(); stopStream()
            target = profile!!; session = iot!!; epoch = generation.get()
            channel = channelFactory(target).also { streamChannel = it }
        }
        val key = session.keyBytes()
        var firstTimestamp: Long? = null
        var failure: Throwable? = null
        try {
            channel.connect(target.host, target.port)
            consumeRecording(channel, session, key, recording, recording.startMs, true, epoch) { frame, info ->
                if (firstTimestamp == null) firstTimestamp = frame.timestampMs
                val delta = recordingTimestampDelta(frame.timestampMs, firstTimestamp!!, recording)
                sample(EncodedFrame(recordingCodec(info.codecId), frame.channel, frame.keyframe, delta * 1000, frame.bytes), info.width, info.height)
            }
            diagnostics.record(DiagnosticStage.DOWNLOAD)
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            key.fill(0); channel.close()
            try {
                withContext(NonCancellable) { operations.withLock { if (epoch == generation.get()) {
                    stopStream(); mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
                } } }
            } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
        }
    }

    private fun recordingCodec(id: Int): VideoCodec = when (id) {
        IoTMediaProtocol.CODEC_AVC -> VideoCodec.AVC
        IoTMediaProtocol.CODEC_HEVC -> VideoCodec.HEVC
        else -> throw CameraFailure(SessionError.UnsupportedCodec)
    }

    private suspend fun sendRecordingControl(channel: TcpChannel, session: IoTSession, key: ByteArray,
        method: Int, id: Int, params: JSONObject) {
        val request = IoTCommonProtocol.encodeRequest(methodId = method, requestId = id, params = params,
            secureType = 2, sessionId = session.sessionId, key = key)
        try { channel.write(request) } finally { request.fill(0) }
    }

    private suspend fun consumeRecording(channel: TcpChannel, session: IoTSession, key: ByteArray,
        recording: RecordingSegment, positionMs: Long, download: Boolean, epoch: Long,
        sample: suspend (IoTMediaFrame, IoTVideoInfo) -> Unit) {
        RecordingReplay(diagnostics, requestIds::incrementAndGet).consume(channel, session, key,
            recording, positionMs, download, { generation.get() == epoch }, sample)
    }

    suspend fun detachSurface() = operations.withLock {
        stopStream(); surface = null
        mutableCapabilities.value?.let { mutableState.value = SessionState.Ready(it) }
    }
    suspend fun close() {
        accessRevoked = true; invalidateApAccess(); invalidateAreaReference()
        reconnectJob?.cancelAndJoin(); reconnectJob = null
        operations.withLock { shutdown(); mutableState.value = SessionState.Idle; diagnostics.record(DiagnosticStage.CLOSE) }
    }
    private suspend fun stopStream() {
        invalidateAreaReference()
        mutableVideoGeometry.value = null
        withTimeoutOrNull(600) { stopPtz() }
        generation.incrementAndGet()
        watchdog = null
        streamChannel?.close(); streamChannel = null
        streamJob?.cancelAndJoin(); streamJob = null
        pendingReply?.result?.cancel(); pendingReply = null
        decoder?.close(); decoder?.awaitClosed(); decoder = null
        decoderCodec = null; decoderDimensions = null
        activeRecording = null
        mediaCipher?.close(); mediaCipher = null
    }
    private suspend fun shutdown() {
        accessRevoked = true; authenticatedIoT = null; softApSupported = null; patrolActive = null; invalidateApAccess()
        stopStream(); commandChannel?.close(); commandChannel = null
        iot?.close(); iot = null; legacy = null
        secret?.fill('\u0000'); secret = null
        profile = null; mutableCapabilities.value = null
    }
    /** Immediate process/lifecycle fallback; normal callers use close() for best-effort motor stop first. */
    fun abort() {
        invalidateAreaReference(); mutableVideoGeometry.value = null
        accessRevoked = true; authenticatedIoT = null; softApSupported = null; patrolActive = null; invalidateApAccess()
        generation.incrementAndGet()
        reconnectJob?.cancel(); streamJob?.cancel()
        commandChannel?.close(); streamChannel?.close(); decoder?.close()
        secret?.fill('\u0000'); secret = null; iot?.close(); iot = null; mediaCipher?.close(); mediaCipher = null
        legacy = null; profile = null; mutableCapabilities.value = null; mutableState.value = SessionState.Idle
    }
    fun dispose() { abort(); scope.cancel() }
    private fun fail(error: Exception, stage: DiagnosticStage = DiagnosticStage.AUTH, detail: DiagnosticDetail? = null) {
        invalidateAreaReference(); mutableVideoGeometry.value = null
        accessRevoked = true; authenticatedIoT = null; invalidateApAccess()
        mutableState.value = SessionState.Error(reason(error))
        recordFailure(error, stage, detail)
    }
    private fun recordFailure(error: Exception, stage: DiagnosticStage, detail: DiagnosticDetail? = null,
        command: Int? = null, bytes: Int? = null) {
        diagnostics.record(stage, DiagnosticStatus.FAILED, code = when (error) {
            is LegacyAuthenticationException -> error.result
            is IoTProtocolException -> error.deviceCode
            else -> null
        }, reason = reason(error), detail = detail, command = command, bytes = bytes)
    }
    private fun retryable(error: Exception) = reason(error) in setOf(SessionError.NetworkUnavailable, SessionError.Timeout) ||
        error is IoTProtocolException && error.kind == IoTFailure.SessionExpired
    companion object {
        fun reason(error: Exception): SessionError = when (error) {
            is RecordingExportException -> error.reason
            is RecordingTimestampException -> SessionError.InvalidRecordingTimestamp
            is CameraFailure -> error.reason
            is LegacyAuthenticationException -> SessionError.AuthenticationFailed
            is LegacyMalformedPacketException -> SessionError.MalformedPacket
            is LegacyUnsupportedProtocolException -> SessionError.UnsupportedProtocol
            is IoTProtocolException -> when (error.kind) {
                IoTFailure.AuthenticationFailed -> SessionError.AuthenticationFailed
                IoTFailure.PermissionDenied -> SessionError.CameraPermissionDenied
                IoTFailure.MalformedPacket -> SessionError.MalformedPacket
                IoTFailure.UnsupportedCipher -> SessionError.UnsupportedProtocol
                IoTFailure.DeviceRejected -> SessionError.DeviceRejected
                else -> SessionError.NetworkUnavailable
            }
            is SecurityException -> SessionError.PermissionDenied
            is SocketTimeoutException, is TimeoutCancellationException -> SessionError.Timeout
            is java.io.IOException -> SessionError.NetworkUnavailable
            is IllegalArgumentException -> SessionError.InvalidInput
            else -> SessionError.NetworkUnavailable
        }
    }
}
