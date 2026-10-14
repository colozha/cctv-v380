package dev.cctv.v380.session

import java.util.UUID

data class CameraProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String = "",
    val port: Int = 8800,
    val deviceId: String,
    val username: String = "",
    val protocolProfileId: String = "auto",
    val hasSavedPassword: Boolean = false,
    val authenticationMode: AuthenticationMode = AuthenticationMode.AUTO,
    val platformType: Int? = null,
    val discoveryGeneration: Int? = null,
)

enum class AuthenticationMode { AUTO, USER_PASSWORD, LOCAL_PAIRING }
enum class VideoCodec(val mime: String) { AVC("video/avc"), HEVC("video/hevc") }
enum class Quality { SD, HD }
data class CameraCapabilities(val codecs: Set<VideoCodec>, val qualities: Set<Quality>, val ptz: Boolean,
    val wifiConfiguration: Boolean = false, val horizontalPtz: Boolean = ptz, val verticalPtz: Boolean = ptz,
    val recordingQualities: Set<Int> = emptySet())
class EncodedFrame(val codec: VideoCodec, val channel: Int, val keyframe: Boolean, val ptsUs: Long, val bytes: ByteArray)

enum class SessionError {
    PermissionDenied, NetworkUnavailable, AuthenticationFailed, Timeout, MalformedPacket,
    UnsupportedProtocol, UnsupportedCodec, StorageUnavailable, PairingRequired, OwnerBindingRequired,
    WifiConfigurationRejected, WifiVerificationTimeout, CloudRequired, IdentityMismatch, InvalidInput, DeviceRejected,
    CameraPermissionDenied, FeatureUnsupported, ExportFailed,
}
sealed interface SessionState {
    data object Idle : SessionState
    data object Connecting : SessionState
    data object Authenticating : SessionState
    data class Ready(val capabilities: CameraCapabilities) : SessionState
    data class Negotiating(val quality: Quality) : SessionState
    data class Streaming(val quality: Quality) : SessionState
    data class LoadingRecording(val recording: RecordingSegment) : SessionState
    data class PlayingRecording(val recording: RecordingSegment) : SessionState
    data class RecordingEnded(val recording: RecordingSegment) : SessionState
    data class Reconnecting(val attempt: Int) : SessionState
    data class Error(val reason: SessionError) : SessionState
}

enum class CameraTab(val label: String) { LIVE("Live"), RECORDINGS("Rekaman"), SETTINGS("Pengaturan") }
class ApHotspotAccess(val ssid: String, val deviceId: String) {
    override fun toString() = "ApHotspotAccess(redacted)"
}
enum class ApWriteResult { ACCEPTED, REJECTED, UNKNOWN }
data class RecordingSegment(val startMs: Long, val endMs: Long, val kind: Int, val sourceStartMs: Long = startMs) {
    val id: String get() = "$startMs:$endMs:$kind"
    val durationMs: Long get() = endMs - startMs
}
data class CameraSettings(val values: Map<Int, Any>, val rejected: Map<Int, Int>) {
    fun int(id: Int): Int? = values[id] as? Int
    fun bool(id: Int): Boolean? = values[id] as? Boolean
}

fun isIpv4(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
            (part.toIntOrNull() ?: -1) in 0..255
    } && parts.first().toInt() in 1..223
}

fun CameraProfile.validationError(allowUnresolvedHost: Boolean = false): String? = when {
    name.isBlank() || name.length > 80 -> "Nama kamera harus berisi 1–80 karakter."
    !(allowUnresolvedHost && host.isEmpty()) && !isIpv4(host) -> "Masukkan alamat IPv4 kamera yang valid."
    port !in 1..65535 -> "Port harus berada di antara 1 dan 65535."
    deviceId.isBlank() || deviceId.length > 128 || deviceId.any { it.isISOControl() } -> "Device ID harus berisi 1–128 karakter."
    username.toByteArray(Charsets.UTF_8).size > 32 || (authenticationMode == AuthenticationMode.USER_PASSWORD && username.isBlank()) -> "Username kamera harus berisi maksimal32 byte UTF-8."
    else -> null
}
