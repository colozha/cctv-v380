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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.session.*

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
                Text(network?.let { "Wi-Fi terpilih · IPv4 ${it.localIpv4}\nGateway ${it.gateway ?: "tidak tersedia"}" }
                    ?: "Jaringan belum dipilih untuk pemeriksaan.", style = MaterialTheme.typography.bodyMedium)
            }
            if (events.isEmpty()) item { Text("Belum ada pemeriksaan. Cari atau sambungkan kamera untuk memulai.") }
            itemsIndexed(events) { _, event ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${stageLabel(event.stage)} · ${when (event.status) { DiagnosticStatus.STARTED -> "Dimulai"; DiagnosticStatus.OK -> "OK"; DiagnosticStatus.FAILED -> "Gagal"; DiagnosticStatus.CANCELLED -> "Dibatalkan" }}", style = MaterialTheme.typography.titleMedium)
                    Text(buildString {
                        append("${event.elapsedMs} ms")
                        event.code?.let { append(" · kode $it") }
                        event.bytes?.let { append(" · panjang $it") }
                        event.protocolVersion?.let { append(" · versi $it") }
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp)); HorizontalDivider()
                }
            }
        }
    }
}
private fun stageLabel(stage: DiagnosticStage) = when (stage) {
    DiagnosticStage.WIFI -> "Wi-Fi"; DiagnosticStage.DISCOVERY -> "Pencarian kamera"
    DiagnosticStage.AUTH -> "Autentikasi"; DiagnosticStage.CAPABILITIES -> "Kemampuan kamera"
    DiagnosticStage.STREAM -> "Negosiasi video"; DiagnosticStage.FRAME -> "Frame video"
    DiagnosticStage.PTZ -> "Kontrol PTZ"; DiagnosticStage.PROVISIONING -> "Konfigurasi Wi-Fi"
    DiagnosticStage.VERIFY_LAN -> "Verifikasi jaringan"; DiagnosticStage.RECONNECT -> "Pemulihan koneksi"
    DiagnosticStage.CLOSE -> "Penutupan sesi"
}
