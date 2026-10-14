package dev.cctv.v380.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*

@Composable
fun CameraSettingsScreen(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, onConnect: (CharArray?) -> Unit, modifier: Modifier = Modifier) {
    var confirmFormat by remember { mutableStateOf(false) }
    val settings = feature.settings
    val enabled = !model.managementBlocked() && !feature.busy && model.session.isAuthenticated(profile.id) && feature.pendingFormat == null && feature.pendingAp == null
    fun int(id: Int): Int? = feature.draft[id] as? Int ?: settings?.int(id)
    fun bool(id: Int): Boolean? = feature.draft[id] as? Boolean ?: settings?.bool(id)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CameraAccess(model, profile, session, onConnect)
        FeatureFeedback(feature, showProgress = !feature.apBusy)
        ApHotspotSettings(model, profile, session, feature)
        feature.managementNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        CameraManagementGroup(model, profile, feature, dev.cctv.v380.protocol.ManagementGroup.LIGHT)
        CameraManagementGroup(model, profile, feature, dev.cctv.v380.protocol.ManagementGroup.ALARM)
        CameraManagementGroup(model, profile, feature, dev.cctv.v380.protocol.ManagementGroup.MOTOR)
        CameraManagementGroup(model, profile, feature, dev.cctv.v380.protocol.ManagementGroup.DEVICE)
        Button(onClick = model::loadSettings, enabled = !feature.busy && model.session.isAuthenticated(profile.id) && feature.pendingAp == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Muat pengaturan kamera") }
        if (feature.busy && !feature.apBusy) TextButton(onClick = model::cancelFeature) {
            Text(if (feature.pendingFormat != null) "Hentikan pemeriksaan status" else "Batalkan")
        }
        if (settings == null) Text("Nilai pengaturan akan tampil setelah dibaca dari kamera. Kemampuan setiap kamera dapat berbeda.")
        else {
            HorizontalDivider()
            Text("Gambar", style = MaterialTheme.typography.titleLarge)
            if (settings.int(P.FLIP_MODE) != null) {
                Text("Balik orientasi gambar pada kamera. Perubahan berlaku untuk tayangan kamera, bukan hanya layar ini.")
                OutlinedButton(onClick = model::reverseImage, enabled = enabled && feature.draft.isEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Balik orientasi")
                }
            } else UnsupportedSetting(settings, P.FLIP_MODE, "Orientasi")
            HorizontalDivider()
            Text("Lampu", style = MaterialTheme.typography.titleLarge)
            val light = int(P.LIGHT_MODE)
            if (light in 0..2) SettingChoices("Lampu putih", light!!,
                listOf(0 to "Otomatis", 1 to "Hidup", 2 to "Mati"), enabled) { model.draftSetting(P.LIGHT_MODE, it) }
            else UnsupportedSetting(settings, P.LIGHT_MODE, "Lampu putih")
            bool(P.INDICATOR)?.let { SettingSwitch("Lampu indikator", it, enabled) { value -> model.draftSetting(P.INDICATOR, value) } }
            HorizontalDivider()
            Text("Perekaman", style = MaterialTheme.typography.titleLarge)
            val mode = int(P.RECORD_MODE)
            if (mode != null && mode >= 0) {
                SettingSwitch("Rekam terus-menerus", mode and 1 != 0, enabled) { on ->
                    model.draftSetting(P.RECORD_MODE, if (on) mode or 1 else mode and 1.inv())
                }
                SettingSwitch("Rekam peristiwa", mode and 2 != 0, enabled) { on ->
                    model.draftSetting(P.RECORD_MODE, if (on) mode or 2 else mode and 2.inv())
                }
            } else UnsupportedSetting(settings, P.RECORD_MODE, "Mode perekaman")
            val quality = int(P.RECORD_QUALITY)
            val caps by model.session.capabilities.collectAsState()
            val choices = listOf(1 to "SD", 2 to "HD", 3 to "Full HD").filter { it.first in caps?.recordingQualities.orEmpty() }
            if (quality in 1..3) {
                if (choices.isNotEmpty()) SettingChoices("Kualitas rekaman", quality!!, choices, enabled) { model.draftSetting(P.RECORD_QUALITY, it) }
                else Text("Kualitas saat ini: ${listOf("SD", "HD", "Full HD")[quality!! - 1]}. Pilihan lainnya belum dikonfirmasi kamera.")
            }
            bool(P.RECORD_AUDIO)?.let { SettingSwitch("Sertakan audio di rekaman SD", it, enabled) { value -> model.draftSetting(P.RECORD_AUDIO, value) } }
            bool(P.RECORD_LOOP)?.let { SettingSwitch("Timpa rekaman terlama saat kartu penuh", it, enabled) { value -> model.draftSetting(P.RECORD_LOOP, value) } }
            if (feature.draft.isNotEmpty()) {
                Button(onClick = model::saveSettings, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Simpan pengaturan")
                }
                Text("Perubahan diperiksa kembali pada kamera sebelum dinyatakan tersimpan.", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Kartu SD", style = MaterialTheme.typography.titleLarge)
            Text(sdStatus(settings.int(P.SD_STATUS)), style = MaterialTheme.typography.bodyLarge)
            P.remainingPercent(settings)?.let { Text("$it% ruang kartu tersedia", style = MaterialTheme.typography.bodyMedium) }
            if (feature.pendingFormat != null) {
                Text(when (feature.format) {
                    SdFormatPhase.SENDING -> "Mengirim perintah format…"
                    SdFormatPhase.CHECKING -> "Memeriksa status kartu. Proses format di kamera tetap berjalan jika Anda meninggalkan layar."
                    else -> "Hasil format sebelumnya belum dapat dipastikan. Perintah tidak akan dikirim ulang secara otomatis."
                })
                if (feature.pendingFormat.profileId == profile.id) {
                    OutlinedButton(onClick = model::loadSettings, enabled = !feature.busy && model.session.isAuthenticated(profile.id) && feature.pendingAp == null) { Text("Periksa status kartu") }
                    TextButton(onClick = model::acknowledgeFormat, enabled = !feature.busy) { Text("Tutup status setelah kartu diperiksa") }
                } else Text("Buka kamera yang memiliki pemeriksaan format tertunda terlebih dahulu.")
            }
            OutlinedButton(onClick = { confirmFormat = true }, enabled = P.FORMAT_STATUS_VERIFIED && enabled && feature.pendingLoaded && feature.draft.isEmpty() &&
                settings.int(P.SD_STATUS) in setOf(1, 2, 3, 8) && settings.int(P.SD_FORMAT_STATE) != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Format SD card") }
            Text("Format menghapus rekaman pada kartu. Fitur belum diaktifkan karena status akhir format belum terverifikasi.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (confirmFormat) SdFormatConfirmation(profile.name,
        onConfirm = { confirmFormat = false; model.formatSdCard(profile) }, onDismiss = { confirmFormat = false })
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
private fun SettingChoices(label: String, value: Int, choices: List<Pair<Int, String>>, enabled: Boolean, change: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        choices.forEach { (id, name) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(value == id, enabled = enabled,
                role = Role.RadioButton, onClick = { change(id) }), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = value == id, onClick = null, enabled = enabled)
                Text(name)
            }
        }
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
