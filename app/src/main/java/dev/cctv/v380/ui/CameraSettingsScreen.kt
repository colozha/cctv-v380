package dev.cctv.v380.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.ManagementGroup
import dev.cctv.v380.R
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*

@Composable
fun CameraSettingsScreen(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, onConnect: (CharArray?) -> Unit, modifier: Modifier = Modifier) {
    var connectionExpanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var confirmFormat by remember(profile.id) { mutableStateOf(false) }
    var confirmCloseFormat by remember(profile.id, feature.pendingFormat?.operationId) { mutableStateOf(false) }
    val settings = feature.settings
    val authenticated = model.session.isAuthenticated(profile.id)
    val enabled = !model.managementBlocked() && !feature.busy && authenticated && feature.pendingFormat == null && feature.pendingAp == null
    val caps by model.session.capabilities.collectAsState()
    fun int(id: Int): Int? = feature.draft[id] as? Int ?: settings?.int(id)
    fun bool(id: Int): Boolean? = feature.draft[id] as? Boolean ?: settings?.bool(id)
    val unfilled = "Muat pengaturan kamera untuk melihat nilai yang tersedia."
    val dark = LocalAppDarkTheme.current
    val scheme = MaterialTheme.colorScheme.copy(
        errorContainer = if (dark) Color(0xFF3B1F24) else Color(0xFFFFE8E9),
        onErrorContainer = if (dark) Color(0xFFFFB4B8) else Color(0xFF8C202B))
    MaterialTheme(colorScheme = scheme) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pengaturan kamera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (authenticated) Text("Akses kamera tersambung", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            else CameraPanel { CameraAccess(model, profile, session, onConnect) }
            OutlinedButton(onClick = model::loadSettings, enabled = !feature.busy && authenticated && feature.pendingAp == null,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Muat ulang")
            }
            feature.error?.let { SettingsNotice(it, error = true) }
            feature.notice?.let { SettingsNotice(it) }
            if (feature.busy && !feature.apBusy && feature.managementLoading == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            feature.managementNotice?.let { SettingsNotice(it) }
            SettingsCard(profile.id, stringResource(R.string.settings_image_lights), PictureIcon,
                summary = if (settings == null) localizedCopy("Belum diperiksa") else localizedCopy("Orientasi tayangan kamera"),
                status = { if (listOf(P.LIGHT_MODE, P.INDICATOR).any { it in feature.draft }) SettingsNotice("Perubahan belum disimpan") }) {
                Text(stringResource(R.string.settings_image), style = MaterialTheme.typography.titleSmall)
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else if (settings.int(P.FLIP_MODE) != null) {
                    Text("Balik orientasi gambar pada kamera. Perubahan berlaku untuk tayangan kamera, bukan hanya layar ini.",
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    OutlinedButton(onClick = model::reverseImage, enabled = enabled && feature.draft.isEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Balik orientasi")
                    }
                } else UnsupportedSetting(settings, P.FLIP_MODE, "Orientasi")
                HorizontalDivider(color = scheme.outlineVariant)
                Text(stringResource(R.string.settings_lights), style = MaterialTheme.typography.titleSmall)
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else {
                    val light = int(P.LIGHT_MODE)
                    if (light in 0..2) SettingChoices("Lampu putih", light!!,
                        listOf(0 to "Otomatis", 1 to "Hidup", 2 to "Mati"), enabled) { model.draftSetting(P.LIGHT_MODE, it) }
                    else UnsupportedSetting(settings, P.LIGHT_MODE, "Lampu putih")
                    bool(P.INDICATOR)?.let { SettingSwitch("Lampu indikator", it, enabled) { value -> model.draftSetting(P.INDICATOR, value) } }
                }
            }
            SettingsCard(profile.id, stringResource(R.string.settings_recording_storage), RecordingSdIcon,
                summary = if (settings == null) localizedCopy("Belum diperiksa") else localizedCopy(sdStatus(settings.int(P.SD_STATUS))),
                status = { if (listOf(P.RECORD_MODE, P.RECORD_QUALITY, P.RECORD_AUDIO, P.RECORD_LOOP).any { it in feature.draft })
                    SettingsNotice("Perubahan belum disimpan")
                    if (feature.pendingFormat != null ||
                        feature.format in setOf(SdFormatPhase.COMPLETE, SdFormatPhase.REJECTED, SdFormatPhase.NOT_SENT))
                        SettingsNotice(when (feature.format) {
                            SdFormatPhase.SENDING -> "Mengirim perintah format…"
                            SdFormatPhase.ACCEPTED -> "Kamera menerima perintah format. Memantau status kartu…"
                            SdFormatPhase.CHECKING -> "Memeriksa status kartu. Proses format di kamera tetap berjalan jika Anda meninggalkan layar."
                            SdFormatPhase.FORMATTING -> "Kamera melaporkan kartu sedang diformat. Hasil akhir belum terverifikasi."
                            SdFormatPhase.COMPLETE -> "Kamera melaporkan format selesai dan kartu kembali siap."
                            SdFormatPhase.REJECTED -> "Kamera menolak perintah format. Periksa kartu dan status kamera sebelum mencoba lagi."
                            SdFormatPhase.NOT_SENT -> "Perintah format tidak dikirim."
                            else -> "Hasil format sebelumnya belum dapat dipastikan. Perintah tidak akan dikirim ulang secara otomatis."
                        }, error = feature.format in setOf(SdFormatPhase.NOT_SENT, SdFormatPhase.REJECTED, SdFormatPhase.UNKNOWN))
                }) {
                Text(stringResource(R.string.settings_recording), style = MaterialTheme.typography.titleSmall)
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
                        else Text(stringResource(R.string.current_quality_unverified, listOf("SD", "HD", "Full HD")[quality!! - 1]),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    bool(P.RECORD_AUDIO)?.let { SettingSwitch("Sertakan audio di rekaman SD", it, enabled) { value -> model.draftSetting(P.RECORD_AUDIO, value) } }
                    bool(P.RECORD_LOOP)?.let { SettingSwitch("Timpa rekaman terlama saat kartu penuh", it, enabled) { value -> model.draftSetting(P.RECORD_LOOP, value) } }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Text(stringResource(R.string.settings_sd_card), style = MaterialTheme.typography.titleSmall)
                if (settings == null) Text(unfilled, style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(sdStatus(settings.int(P.SD_STATUS)), style = MaterialTheme.typography.bodyMedium)
                    P.remainingPercent(settings)?.let { remaining ->
                        Text(stringResource(R.string.remaining_card_space, remaining), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        LinearProgressIndicator(progress = { remaining / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
                            trackColor = scheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
                    }
                }
                if (feature.pendingFormat != null) {
                    if (feature.pendingFormat.profileId == profile.id) {
                        OutlinedButton(onClick = model::checkPendingFormat, enabled = !feature.busy && authenticated && feature.pendingAp == null &&
                            model.session.connectedProfile?.deviceId == feature.pendingFormat.deviceId,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Periksa status kartu") }
                        TextButton(onClick = { confirmCloseFormat = true }, enabled = !feature.busy && feature.pendingAp == null,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup catatan format") }
                    } else Text("Buka kamera yang memiliki pemeriksaan format tertunda terlebih dahulu.")
                }
                OutlinedButton(onClick = { confirmFormat = true }, enabled = P.FORMAT_ACTION_VERIFIED && enabled && feature.pendingLoaded && feature.draft.isEmpty() &&
                    P.formatEligible(settings) &&
                    feature.download !in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS, DownloadPhase.DOWNLOADING, DownloadPhase.SAVING),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error)) { Text("Format SD card") }
                Text("Format menghapus semua rekaman pada kartu. Kamera dapat menolak perintah; hasil akhir dipantau dari status kartu.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            CameraPanel {
                SettingsHeader(stringResource(R.string.settings_connection_device), Icons.Default.Settings,
                    connectionExpanded, { connectionExpanded = it })
                ApHotspotSettings(model, profile, session, feature, expanded = connectionExpanded)
                if (connectionExpanded) HorizontalDivider(color = scheme.outlineVariant)
                CameraManagementGroup(model, profile, feature, ManagementGroup.DEVICE,
                    embedded = true, expanded = connectionExpanded)
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
            Text(stringResource(R.string.settings_not_working), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            CameraPanel {
                CameraManagementGroup(model, profile, feature, ManagementGroup.LIGHT, unfinished = true)
                HorizontalDivider(color = scheme.outlineVariant)
                CameraManagementGroup(model, profile, feature, ManagementGroup.ALARM, unfinished = true)
                HorizontalDivider(color = scheme.outlineVariant)
                CameraManagementGroup(model, profile, feature, ManagementGroup.MOTOR, unfinished = true)
            }
        }
        if (confirmFormat) SdFormatConfirmation(profile.name,
            onConfirm = { confirmFormat = false; model.formatSdCard(profile) }, onDismiss = { confirmFormat = false })
        if (confirmCloseFormat && feature.pendingFormat != null) AlertDialog(onDismissRequest = { confirmCloseFormat = false },
            title = { Text("Tutup catatan format?") },
            text = { Text("Catatan pemeriksaan pada aplikasi akan ditutup. Hasil format tetap belum diketahui; proses di kamera tidak dibatalkan. Periksa kartu sebelum mengirim tindakan baru.") },
            confirmButton = { TextButton(onClick = { confirmCloseFormat = false; model.acknowledgeFormat(feature.pendingFormat.operationId) }) { Text("Tutup catatan") } },
            dismissButton = { TextButton(onClick = { confirmCloseFormat = false }) { Text("Kembali") } })
    }
}

@Composable
fun SdFormatConfirmation(cameraName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Format SD card?") },
        text = { Text(stringResource(R.string.format_camera_confirmation, cameraName)) },
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
        1109 -> stringResource(R.string.setting_not_allowed, localizedCopy(label).lowercase())
        null -> stringResource(R.string.unsupported_setting_unconfirmed, localizedCopy(label).lowercase())
        else -> stringResource(R.string.setting_not_available, localizedCopy(label).lowercase())
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
