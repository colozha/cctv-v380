package dev.cctv.v380.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.ManagementGroup
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*

@Composable
fun CameraSettingsScreen(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, onConnect: (CharArray?) -> Unit, modifier: Modifier = Modifier) {
    var confirmFormat by remember(profile.id) { mutableStateOf(false) }
    val settings = feature.settings
    val authenticated = model.session.isAuthenticated(profile.id)
    val enabled = !model.managementBlocked() && !feature.busy && authenticated && feature.pendingFormat == null && feature.pendingAp == null
    val caps by model.session.capabilities.collectAsState()
    fun int(id: Int): Int? = feature.draft[id] as? Int ?: settings?.int(id)
    fun bool(id: Int): Boolean? = feature.draft[id] as? Boolean ?: settings?.bool(id)
    val unfilled = "Muat pengaturan kamera untuk melihat nilai yang tersedia."
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme.copy(
        errorContainer = if (dark) Color(0xFF3B1F24) else Color(0xFFFFE8E9),
        onErrorContainer = if (dark) Color(0xFFFFB4B8) else Color(0xFF8C202B))
    MaterialTheme(colorScheme = scheme) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Pengaturan kamera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Kelola fitur dan preferensi kamera Anda.", style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant)
            }
            if (authenticated) Text("Akses kamera tersambung", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            else CameraPanel { CameraAccess(model, profile, session, onConnect) }
            feature.error?.let { SettingsNotice(it, error = true) }
            feature.notice?.let { SettingsNotice(it) }
            if (feature.busy && !feature.apBusy && feature.managementLoading == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            feature.managementNotice?.let { SettingsNotice(it) }
            ApHotspotSettings(model, profile, session, feature)
            CameraManagementGroup(model, profile, feature, ManagementGroup.LIGHT)
            CameraManagementGroup(model, profile, feature, ManagementGroup.ALARM)
            CameraManagementGroup(model, profile, feature, ManagementGroup.MOTOR)
            CameraManagementGroup(model, profile, feature, ManagementGroup.DEVICE)

            SettingsCard(profile.id, "Gambar", PictureIcon, if (settings == null) "Belum diperiksa" else "Orientasi tayangan kamera") {
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else if (settings.int(P.FLIP_MODE) != null) {
                    Text("Balik orientasi gambar pada kamera. Perubahan berlaku untuk tayangan kamera, bukan hanya layar ini.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    OutlinedButton(onClick = model::reverseImage, enabled = enabled && feature.draft.isEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Balik orientasi")
                    }
                } else UnsupportedSetting(settings, P.FLIP_MODE, "Orientasi")
            }
            SettingsCard(profile.id, "Lampu", LightIcon,
                summary = when (int(P.LIGHT_MODE)) { 0 -> "Lampu putih · Otomatis"; 1 -> "Lampu putih · Hidup"; 2 -> "Lampu putih · Mati"; else -> "Belum diperiksa" },
                status = { if (listOf(P.LIGHT_MODE, P.INDICATOR).any { it in feature.draft }) SettingsNotice("Perubahan belum disimpan") }) {
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else {
                    val light = int(P.LIGHT_MODE)
                    if (light in 0..2) SettingChoices("Lampu putih", light!!,
                        listOf(0 to "Otomatis", 1 to "Hidup", 2 to "Mati"), enabled) { model.draftSetting(P.LIGHT_MODE, it) }
                    else UnsupportedSetting(settings, P.LIGHT_MODE, "Lampu putih")
                    bool(P.INDICATOR)?.let { SettingSwitch("Lampu indikator", it, enabled) { value -> model.draftSetting(P.INDICATOR, value) } }
                }
            }
            SettingsCard(profile.id, "Perekaman", CameraIcon,
                summary = if (settings == null) "Belum diperiksa" else "Mode dan kualitas rekaman SD",
                status = { if (listOf(P.RECORD_MODE, P.RECORD_QUALITY, P.RECORD_AUDIO, P.RECORD_LOOP).any { it in feature.draft })
                    SettingsNotice("Perubahan belum disimpan") }) {
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else {
                    val mode = int(P.RECORD_MODE)
                    if (mode != null && mode >= 0) {
                        SettingSwitch("Rekam terus-menerus", mode and 1 != 0, enabled) { on ->
                            model.draftSetting(P.RECORD_MODE, if (on) mode or 1 else mode and 1.inv())
                        }
                        SettingSwitch("Rekam peristiwa", mode and 2 != 0, enabled) { on ->
                            model.draftSetting(P.RECORD_MODE, if (on) mode or 2 else mode and 2.inv())
                        }
                    } else UnsupportedSetting(settings, P.RECORD_MODE, "Mode perekaman")
                    HorizontalDivider(color = scheme.outlineVariant)
                    val quality = int(P.RECORD_QUALITY)
                    val choices = listOf(1 to "SD", 2 to "HD", 3 to "Full HD").filter { it.first in caps?.recordingQualities.orEmpty() }
                    if (quality in 1..3) {
                        if (choices.isNotEmpty()) SettingChoices("Kualitas rekaman", quality!!, choices, enabled) { model.draftSetting(P.RECORD_QUALITY, it) }
                        else Text("Kualitas saat ini: ${listOf("SD", "HD", "Full HD")[quality!! - 1]}. Pilihan lainnya belum dikonfirmasi kamera.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    bool(P.RECORD_AUDIO)?.let { SettingSwitch("Sertakan audio di rekaman SD", it, enabled) { value -> model.draftSetting(P.RECORD_AUDIO, value) } }
                    bool(P.RECORD_LOOP)?.let { SettingSwitch("Timpa rekaman terlama saat kartu penuh", it, enabled) { value -> model.draftSetting(P.RECORD_LOOP, value) } }
                }
            }
            SettingsCard(profile.id, "Kartu SD", RecordingSdIcon,
                summary = if (settings == null) "Belum diperiksa" else sdStatus(settings.int(P.SD_STATUS)),
                status = {
                    if (feature.pendingFormat != null) SettingsNotice(when (feature.format) {
                        SdFormatPhase.SENDING -> "Mengirim perintah format…"
                        SdFormatPhase.CHECKING -> "Memeriksa status kartu. Proses format di kamera tetap berjalan jika Anda meninggalkan layar."
                        else -> "Hasil format sebelumnya belum dapat dipastikan. Perintah tidak akan dikirim ulang secara otomatis."
                    }, error = !feature.busy)
                }) {
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(sdStatus(settings.int(P.SD_STATUS)), style = MaterialTheme.typography.bodyMedium)
                    P.remainingPercent(settings)?.let { remaining ->
                        Text("$remaining% ruang kartu tersedia", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { remaining / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = scheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
                    }
                    if (feature.pendingFormat != null) {
                        if (feature.pendingFormat.profileId == profile.id) {
                            OutlinedButton(onClick = model::loadSettings, enabled = !feature.busy && authenticated && feature.pendingAp == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Periksa status kartu") }
                            TextButton(onClick = model::acknowledgeFormat, enabled = !feature.busy,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup status setelah kartu diperiksa") }
                        } else Text("Buka kamera yang memiliki pemeriksaan format tertunda terlebih dahulu.")
                    }
                    OutlinedButton(onClick = { confirmFormat = true }, enabled = P.FORMAT_STATUS_VERIFIED && enabled && feature.pendingLoaded && feature.draft.isEmpty() &&
                        settings.int(P.SD_STATUS) in setOf(1, 2, 3, 8) && settings.int(P.SD_FORMAT_STATE) != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)) { Text("Format SD card") }
                    Text("Format menghapus rekaman pada kartu. Fitur belum diaktifkan karena status akhir format belum terverifikasi.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Button(onClick = model::loadSettings, enabled = !feature.busy && authenticated && feature.pendingAp == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Muat pengaturan kamera")
            }
            if (feature.draft.isNotEmpty()) {
                Button(onClick = model::saveSettings, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(SaveIcon, null); Spacer(Modifier.width(8.dp)); Text("Simpan pengaturan")
                }
                Text("Perubahan diperiksa kembali pada kamera sebelum dinyatakan tersimpan.", style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
            if (feature.busy && !feature.apBusy) TextButton(onClick = model::cancelFeature, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (feature.pendingFormat != null) "Hentikan pemeriksaan status" else "Batalkan")
            }
        }
        if (confirmFormat) SdFormatConfirmation(profile.name,
            onConfirm = { confirmFormat = false; model.formatSdCard(profile) }, onDismiss = { confirmFormat = false })
    }
}

@Composable
fun SdFormatConfirmation(cameraName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Format SD card?") },
        text = { Text("Semua rekaman pada kartu SD kamera “$cameraName” akan dihapus. Simpan rekaman yang diperlukan terlebih dahulu. Setelah dikirim, proses format tidak dapat dibatalkan dari aplikasi.") },
        confirmButton = { TextButton(onClick = onConfirm,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Hapus rekaman dan format") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value, enabled = enabled, role = Role.Switch, onValueChange = change),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun UnsupportedSetting(settings: CameraSettings, id: Int, label: String) {
    Text(when (settings.rejected[id]) {
        1109 -> "$label tidak diizinkan untuk akses kamera ini."
        null -> "$label belum dapat dikonfirmasi kamera."
        else -> "$label tidak tersedia pada kamera ini."
    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun sdStatus(code: Int?): String = when (code) {
    1 -> "Kartu SD siap digunakan"
    2 -> "Kartu SD mengalami gangguan baca/tulis"
    3 -> "Kartu SD penuh"
    4 -> "Kartu SD sedang diformat"
    5 -> "Tidak ada kartu SD"
    6 -> "Kartu SD sedang dilepas"
    7 -> "Kartu SD sedang disambungkan kembali"
    8 -> "Kartu SD perlu diformat"
    else -> "Status kartu SD belum diketahui"
}
