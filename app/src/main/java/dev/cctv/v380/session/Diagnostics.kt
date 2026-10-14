package dev.cctv.v380.session

import dev.cctv.v380.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class DiagnosticStage { WIFI, DISCOVERY, AUTH, CAPABILITIES, STREAM, FRAME, PTZ, PROVISIONING, VERIFY_LAN, RECONNECT, CLOSE,
    RECORDINGS, PLAYBACK, DOWNLOAD, SETTINGS, SD_FORMAT, AP_EDIT, AP_VERIFY, ALARM, PTZ_PRESET, PTZ_CRUISE, PTZ_CALIBRATION, LIGHT_SCHEDULE, DEVICE_INFO, RESTART, RESTART_VERIFY }
enum class DiagnosticStatus { STARTED, OK, FAILED, CANCELLED }
enum class DiagnosticDetail { READ_HEADER, READ_BODY, MEDIA_PACKET, CONTROL_JSON, MEDIA_INFO, DECODER,
    PTZ_WRITE, IGNORED_PACKET, DEVICE_SHUTDOWN, DECODE_FEEDBACK }
data class DiagnosticEvent(val stage: DiagnosticStage, val status: DiagnosticStatus, val elapsedMs: Long,
    val code: Int? = null, val bytes: Int? = null, val protocolVersion: Int? = null,
    val reason: SessionError? = null, val detail: DiagnosticDetail? = null, val command: Int? = null)
data class DiagnosticNetwork(val localIpv4: String, val gateway: String?)

/** Structured allowlist only. No free-form strings, request bodies or secret-bearing objects. */
class Diagnostics {
    private val started = System.nanoTime()
    private val mutableEvents = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events = mutableEvents.asStateFlow()
    private val mutableNetwork = MutableStateFlow<DiagnosticNetwork?>(null)
    val network = mutableNetwork.asStateFlow()

    fun selectNetwork(localIpv4: String, gateway: String?) {
        require(isIpv4(localIpv4) && (gateway == null || isIpv4(gateway)))
        mutableNetwork.value = DiagnosticNetwork(localIpv4, gateway)
        record(DiagnosticStage.WIFI)
    }

    @Synchronized
    fun record(stage: DiagnosticStage, status: DiagnosticStatus = DiagnosticStatus.OK,
        code: Int? = null, bytes: Int? = null, protocolVersion: Int? = null,
        reason: SessionError? = null, detail: DiagnosticDetail? = null, command: Int? = null) {
        mutableEvents.value = (mutableEvents.value + DiagnosticEvent(stage, status,
            (System.nanoTime() - started) / 1_000_000, code, bytes, protocolVersion, reason, detail, command)).takeLast(80)
    }

    fun exportJson(): String {
        val array = JSONArray()
        events.value.forEach { event ->
            array.put(JSONObject().put("stage", event.stage.name).put("status", event.status.name)
                .put("elapsedMs", event.elapsedMs).apply {
                    event.code?.let { put("code", it) }
                    event.bytes?.let { put("bytes", it) }
                    event.protocolVersion?.let { put("protocolVersion", it) }
                    event.reason?.let { put("reason", it.name) }
                    event.detail?.let { put("detail", it.name) }
                    event.command?.let { put("command", it) }
                })
        }
        return JSONObject().put("schema", 3).put("experimental", true)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("cameraCompatibilityVerified", false).put("events", array).apply {
                network.value?.let { put("network", JSONObject().put("transport", "WIFI")
                    .put("localIpv4", it.localIpv4).put("gateway", it.gateway ?: JSONObject.NULL)) }
            }.toString(2)
    }
}

fun SessionError.userMessage(): String = when (this) {
    SessionError.PermissionDenied -> "Izin Wi-Fi atau jaringan lokal belum tersedia. Aktifkan izin lalu coba lagi."
    SessionError.NetworkUnavailable -> "Jaringan kamera tidak tersedia. Periksa koneksi Wi-Fi lalu coba lagi."
    SessionError.AuthenticationFailed -> "Autentikasi kamera ditolak. Periksa kredensial lokal kamera, lalu sambungkan kembali."
    SessionError.Timeout -> "Kamera tidak merespons dalam batas waktu. Periksa jaringan lalu coba lagi."
    SessionError.MalformedPacket -> "Respons kamera tidak sesuai profil protokol. Buka diagnostik untuk status pemeriksaan."
    SessionError.UnsupportedProtocol -> "Generasi protokol kamera belum dapat dipastikan atau belum didukung. Jalankan pencarian dan periksa diagnostik."
    SessionError.UnsupportedCodec -> "Codec video kamera tidak tersedia pada perangkat Android ini."
    SessionError.StorageUnavailable -> "Akses tersimpan tidak dapat dibuka. Masukkan kembali kredensial kamera."
    SessionError.PairingRequired -> "Kamera memerlukan pairing lokal. Gunakan Setup lewat AP untuk perangkat yang siap dipasangkan."
    SessionError.OwnerBindingRequired -> "Kamera memerlukan otorisasi pemilik. Akses yang sudah tersimpan di aplikasi vendor tidak dipindahkan otomatis."
    SessionError.WifiConfigurationRejected -> "Konfigurasi Wi-Fi ditolak kamera. Periksa SSID dan jenis keamanan jaringan."
    SessionError.WifiVerificationTimeout -> "Kamera belum ditemukan kembali. Konfigurasi mungkin sudah diterapkan; hubungkan ke Wi-Fi tujuan lalu periksa lagi."
    SessionError.CloudRequired -> "Jalur ini memerlukan binding atau signaling cloud yang belum didukung client LAN."
    SessionError.IdentityMismatch -> "Identitas kamera berbeda. Pilih kembali kamera sebelum melanjutkan."
    SessionError.InvalidInput -> "Isian tidak sesuai batas kamera. Periksa panjang SSID/password dan karakter yang didukung. Password Wi-Fi legacy dibatasi32 byte GBK."
    SessionError.DeviceRejected -> "Kamera menolak tindakan. Periksa kemampuan kamera dan status diagnostik sebelum mencoba lagi."
    SessionError.CameraPermissionDenied -> "Kamera tidak mengizinkan tindakan ini. Periksa hak akses lokal kamera."
    SessionError.FeatureUnsupported -> "Fitur ini belum didukung oleh profil kamera yang tersambung."
    SessionError.ExportFailed -> "Rekaman belum berhasil disimpan. Periksa ruang penyimpanan dan lokasi file, lalu coba lagi."
}
