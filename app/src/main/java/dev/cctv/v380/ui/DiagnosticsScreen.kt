package dev.cctv.v380.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.session.*
import dev.cctv.v380.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(model: CameraViewModel, onBack: () -> Unit) {
    val events by model.diagnostics.events.collectAsStateWithLifecycle()
    val network by model.diagnostics.network.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(model::exportDiagnostics)
    }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Diagnostik LAN") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
        actions = { TextButton(onClick = { export.launch("v380-lan-diagnostics.json") }, enabled = events.isNotEmpty()) { Text("Ekspor") } }) }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Status pemeriksaan", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text("Catatan hanya berisi tahap, kode status, panjang pesan, dan waktu. Password, kunci sesi, serta video tidak disertakan.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Kompatibilitas firmware masih dalam pengujian.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(network?.let { stringResource(R.string.diagnostic_wifi_info, it.localIpv4,
                    it.gateway ?: stringResource(R.string.not_available)) }
                    ?: "Jaringan belum dipilih untuk pemeriksaan.", style = MaterialTheme.typography.bodyMedium)
            }
            if (events.isEmpty()) item { Text("Belum ada pemeriksaan. Cari atau sambungkan kamera untuk memulai.") }
            itemsIndexed(events.asReversed()) { _, event ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.event_stage_status, localizedCopy(stageLabel(event.stage)), stringResource(when (event.status) {
                        DiagnosticStatus.STARTED -> R.string.event_status_started
                        DiagnosticStatus.OK -> R.string.event_status_ok
                        DiagnosticStatus.FAILED -> R.string.event_status_failed
                        DiagnosticStatus.CANCELLED -> R.string.event_status_cancelled
                    })), style = MaterialTheme.typography.titleMedium)
                    val details = listOfNotNull(
                        stringResource(R.string.diagnostic_elapsed, event.elapsedMs),
                        event.code?.let { stringResource(R.string.diagnostic_code, it) },
                        event.bytes?.let { stringResource(R.string.diagnostic_length, it) },
                        event.protocolVersion?.let { stringResource(R.string.diagnostic_version, it) },
                        when (event.detail) {
                            DiagnosticDetail.TALKBACK_METADATA_WAITING -> R.string.talkback_metadata_missing
                            DiagnosticDetail.TALKBACK_METADATA_ABSENT -> R.string.talkback_metadata_absent
                            DiagnosticDetail.TALKBACK_METADATA_INVALID -> R.string.talkback_metadata_invalid
                            DiagnosticDetail.TALKBACK_METADATA_UNTRUSTED -> R.string.talkback_metadata_untrusted
                            DiagnosticDetail.TALKBACK_METADATA_COMMAND -> R.string.talkback_metadata_command
                            DiagnosticDetail.TALKBACK_METADATA_CIPHER -> R.string.talkback_metadata_cipher
                            DiagnosticDetail.TALKBACK_METADATA_READY -> R.string.talkback_metadata_ready
                            DiagnosticDetail.TALKBACK_METADATA_UNSUPPORTED -> R.string.talkback_metadata_unsupported
                            else -> null
                        }?.let { stringResource(it) },
                    )
                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp)); HorizontalDivider()
                }
            }
        }
    }
}
@Composable
private fun stageLabel(stage: DiagnosticStage) = when (stage) {
    DiagnosticStage.TALKBACK -> stringResource(dev.cctv.v380.R.string.talkback_title)
    DiagnosticStage.WIFI -> "Wi-Fi"; DiagnosticStage.DISCOVERY -> "Pencarian kamera"
    DiagnosticStage.AUTH -> "Autentikasi"; DiagnosticStage.CAPABILITIES -> "Kemampuan kamera"
    DiagnosticStage.STREAM -> "Negosiasi video"; DiagnosticStage.FRAME -> "Frame video"
    DiagnosticStage.PTZ -> "Kontrol PTZ"; DiagnosticStage.PROVISIONING -> "Konfigurasi Wi-Fi"
    DiagnosticStage.VERIFY_LAN -> "Verifikasi jaringan"; DiagnosticStage.RECONNECT -> "Pemulihan koneksi"
    DiagnosticStage.CLOSE -> "Penutupan sesi"
    DiagnosticStage.RECORDINGS -> "Daftar rekaman"; DiagnosticStage.PLAYBACK -> "Playback rekaman"
    DiagnosticStage.DOWNLOAD -> "Penyalinan rekaman"; DiagnosticStage.SETTINGS -> "Pengaturan kamera"
    DiagnosticStage.SD_FORMAT -> "Format kartu SD"
    DiagnosticStage.ALARM -> "Alarm dan gerakan"; DiagnosticStage.PTZ_PRESET -> "Preset PTZ"
    DiagnosticStage.PTZ_CRUISE -> "Patroli PTZ"; DiagnosticStage.PTZ_CALIBRATION -> "Kalibrasi PTZ"
    DiagnosticStage.LIGHT_SCHEDULE -> "Jadwal lampu"; DiagnosticStage.DEVICE_INFO -> "Informasi perangkat"
    DiagnosticStage.RESTART -> "Restart kamera"; DiagnosticStage.RESTART_VERIFY -> "Pemeriksaan setelah restart"
    DiagnosticStage.AP_EDIT -> "Perubahan hotspot"; DiagnosticStage.AP_VERIFY -> "Verifikasi hotspot"
}
