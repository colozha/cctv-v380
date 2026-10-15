package dev.cctv.v380.ui

import dev.cctv.v380.session.RecordingSegment
import dev.cctv.v380.session.CameraSettings
import dev.cctv.v380.protocol.CameraSettingsProtocol
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.cctv.v380.session.CameraSession
import dev.cctv.v380.session.SessionError
import dev.cctv.v380.session.userMessage
import java.io.EOFException

enum class RecordingOrder(val label: String) {
    NEWEST_FIRST("Terbaru dahulu"), OLDEST_FIRST("Terlama dahulu");

    fun sort(recordings: List<RecordingSegment>): List<RecordingSegment> {
        val order = compareBy<RecordingSegment>({ it.startMs }, { it.endMs }, { it.kind })
        return recordings.sortedWith(if (this == NEWEST_FIRST) order.reversed() else order)
    }
}

fun recordingRange(recording: RecordingSegment, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = Instant.ofEpochMilli(recording.startMs).atZone(zone)
    val end = Instant.ofEpochMilli(recording.endMs).atZone(zone)
    val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    val time = DateTimeFormatter.ofPattern("HH:mm:ss")
    return if (start.toLocalDate() == end.toLocalDate())
        "${date.format(start)}\n${time.format(start)} – ${time.format(end)}"
    else "${date.format(start)}, ${time.format(start)}\n– ${date.format(end)}, ${time.format(end)}"
}

fun recordingDownloadMessage(error: Exception): String = when (val reason = CameraSession.reason(error)) {
    SessionError.Timeout -> "Unduhan melewati batas waktu respons kamera. Coba lagi dan buka diagnostik jika berulang."
    SessionError.NetworkUnavailable -> if (error is EOFException)
        "Koneksi kamera terputus sebelum rekaman selesai diterima. Coba unduh kembali."
        else "Koneksi kamera gagal saat menerima rekaman. Periksa Wi-Fi lalu coba lagi."
    SessionError.MalformedPacket -> "Paket replay kamera tidak sesuai protokol. Unduhan dihentikan; buka diagnostik untuk rinciannya."
    else -> reason.userMessage()
}

// No per-file TF deletion contract has been recovered from the reference APK.
const val RECORDING_DELETE_UNAVAILABLE = "Hapus rekaman SD satu per satu belum didukung aplikasi"

fun recordingStorageWarning(storage: CameraSettings): String? {
    val status = storage.int(CameraSettingsProtocol.SD_STATUS)
    if (status == 3) return "Kartu SD penuh"
    if (status == 5) return null
    val capacity = CameraSettingsProtocol.capacity(storage) ?: return null
    return when {
        capacity.remaining == 0L -> "Kartu SD penuh"
        capacity.almostFull -> "Kartu SD hampir penuh — ${capacity.usedPercent}% terpakai"
        else -> null
    }
}

fun recordingTimes(recording: RecordingSegment): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").let { f ->
        f.format(Instant.ofEpochMilli(recording.startMs).atZone(ZoneId.systemDefault())) + " – " +
            f.format(Instant.ofEpochMilli(recording.endMs).atZone(ZoneId.systemDefault()))
    }

fun recordingDates(recording: RecordingSegment, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = Instant.ofEpochMilli(recording.startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(recording.endMs).atZone(zone).toLocalDate()
    val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
    return if (start == end) date.format(start) else "${date.format(start)} – ${date.format(end)}"
}
