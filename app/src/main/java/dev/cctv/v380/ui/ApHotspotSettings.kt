package dev.cctv.v380.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.protocol.ApHotspotProtocol
import dev.cctv.v380.session.*

@Composable
fun ApHotspotSettings(model: CameraViewModel, profile: CameraProfile, session: SessionState, feature: CameraFeatureState) {
    val access by model.session.apAccess.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var ssid by rememberSaveable(profile.id) { mutableStateOf("") }
    var password by remember(profile.id) { mutableStateOf("") }
    var localPassword by remember(profile.id) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }
    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
    fun permitted() = permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        permissionNotice = if (grants.values.all { it }) "Akses Wi-Fi tersedia. Periksa hotspot atau lanjutkan verifikasi." else
            "Izinkan akses Wi-Fi dan lokasi untuk memastikan jaringan hotspot yang sedang dipakai."
    }
    val wifiSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.resumeManualApVerification() }
    fun clearSecrets() { password = ""; localPassword = ""; visible = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { clearSecrets() }
    DisposableEffect(profile.id) { onDispose { clearSecrets() } }
    LaunchedEffect(access) { if (access != null) ssid = access!!.ssid else clearSecrets() }
    val pending = feature.pendingAp
    val ownPending = pending?.profileId == profile.id && pending.deviceId == profile.deviceId
    val authenticated = model.session.isAuthenticated(profile.id)
    val editable = session is SessionState.Ready && model.session.canEditAp(profile.id) && !feature.busy &&
        feature.apPendingLoaded && !feature.apStorageFailed && pending == null && feature.pendingFormat == null && feature.draft.isEmpty()
    val validation = remember(ssid, password, access) {
        val chars = password.toCharArray()
        try { if (password.isNotEmpty() || ssid.isNotEmpty()) ApHotspotProtocol.inputError(ssid, chars, access?.ssid) else null }
        finally { chars.fill('\u0000') }
    }
    val status = when {
        feature.apBusy -> apPhaseLabel(feature.apPhase)
        feature.apStorageFailed -> "Catatan perubahan hotspot tidak dapat dibaca. Periksa penyimpanan lalu buka kembali aplikasi."
        pending != null -> if (ownPending) apPhaseLabel(feature.apPhase) else "Ada perubahan hotspot tertunda pada kamera lain. Buka kamera tersebut untuk memeriksa hasilnya."
        feature.apPhase in setOf(ApEditPhase.NOT_SENT, ApEditPhase.REJECTED) -> apPhaseLabel(feature.apPhase)
        !authenticated -> "Sambungkan kamera terlebih dahulu untuk mengubah hotspot."
        access == null -> "Periksa hotspot saat tersambung langsung ke AP kamera. Dukungan harus dikonfirmasi sebelum mengubah isian."
        else -> "Hotspot aktif telah diperiksa. Masukkan password yang ingin digunakan, termasuk jika hanya mengganti nama."
    }
    SettingsCard(profile.id, "Hotspot kamera", WifiIcon, access?.ssid ?: "Koneksi langsung ke AP kamera", status = {
        val failed = feature.apStorageFailed || feature.apPhase in setOf(ApEditPhase.NOT_SENT, ApEditPhase.REJECTED, ApEditPhase.UNKNOWN)
        if (failed || pending != null) SettingsNotice(status, error = failed)
        else Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (feature.apBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (pending == null) {
            if (!permitted()) OutlinedButton(onClick = { permissionRequest.launch(permissions.toTypedArray()) },
                enabled = !feature.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Izinkan akses Wi-Fi") }
            OutlinedButton(onClick = model::loadApHotspot,
                enabled = authenticated && session is SessionState.Ready && !feature.busy && permitted() && !feature.apStorageFailed,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Periksa hotspot kamera") }
        }
        if (feature.apPhase == ApEditPhase.VERIFIED || feature.apNotice != null || permissionNotice != null) {
            Text(feature.apNotice ?: permissionNotice ?: "Hotspot terverifikasi.", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (feature.apBusy) TextButton(onClick = model::cancelFeature, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (pending != null) "Hentikan pemeriksaan" else "Batalkan")
        }
    }) {
        DisposableEffect(profile.id) { onDispose { clearSecrets() } }
        ApHotspotForm(ssid, { ssid = it }, password, { password = it }, visible, { visible = !visible },
            editable = editable, busy = false, status = "",
            validation = if (editable && password.isNotEmpty()) validation else null,
            onSave = { val value = password.toCharArray(); clearSecrets(); model.saveApHotspot(profile, ssid, value) },
            canSave = editable && validation == null && password.isNotEmpty(),
            showInputs = pending == null,
            showHeader = false)
        if (pending != null && !permitted()) OutlinedButton(onClick = { permissionRequest.launch(permissions.toTypedArray()) },
            enabled = !feature.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Izinkan akses Wi-Fi") }
        if (pending != null && ownPending) {
            Text("Hotspot tujuan: ${pending.targetSsid}", style = MaterialTheme.typography.bodyLarge)
            Text("Perintah tidak dikirim ulang. Verifikasi hanya menyambungkan dan membaca status kamera.",
                style = MaterialTheme.typography.bodyMedium)
            if (feature.apPhase == ApEditPhase.WAITING_WIFI) {
                Text("Pilih hotspot tujuan pada pengaturan Wi-Fi Android, lalu kembali ke aplikasi.")
                Button(onClick = { wifiSettings.launch(Intent(Settings.ACTION_WIFI_SETTINGS)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Buka pengaturan Wi-Fi")
                }
                TextButton(onClick = model::resumeManualApVerification, modifier = Modifier.heightIn(min = 48.dp)) { Text("Saya sudah tersambung, periksa") }
            } else if (!feature.busy) {
                HotspotPasswordField(password, { password = it }, visible, { visible = !visible }, true, "Password hotspot tujuan")
                if (!profile.hasSavedPassword) OutlinedTextField(localPassword, { localPassword = it },
                    label = { Text("Password lokal kamera") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(14.dp),
                    supportingText = { Text("Password login kamera, terpisah dari password hotspot. Isi kosong hanya jika kamera belum memiliki password lokal.") },
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val ap = password.toCharArray()
                    val local = if (profile.hasSavedPassword) null else localPassword.toCharArray()
                    clearSecrets(); model.verifyPendingAp(ap, local)
                }, enabled = permitted() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Sambungkan dan periksa hotspot")
                }
                Text("Menutup catatan hanya menghapus status lokal; hotspot kamera tetap seperti sekarang.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = model::closeApStatus, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tutup catatan lokal") }
            }
        }
    }
}

/** Pure native form, also rendered with explicitly synthetic state in offline UI verification. */
@Composable
fun ApHotspotForm(ssid: String, onSsid: (String) -> Unit, password: String, onPassword: (String) -> Unit,
    visible: Boolean, onVisibility: () -> Unit, editable: Boolean, busy: Boolean, status: String,
    validation: String?, onSave: () -> Unit, canSave: Boolean, showInputs: Boolean = true,
    readAction: @Composable () -> Unit = {}, showHeader: Boolean = true) {
    if (showHeader) {
        Text("Hotspot kamera", style = MaterialTheme.typography.titleLarge)
        Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    readAction()
    if (showInputs) {
        val nameError = validation?.takeIf { it.startsWith("Nama") }
        val passwordError = validation?.takeUnless { it.startsWith("Nama") }
        OutlinedTextField(ssid, onSsid, label = { Text("Nama hotspot kamera") }, enabled = editable, singleLine = true, shape = RoundedCornerShape(14.dp),
            isError = nameError != null,
            supportingText = { Text(nameError ?: "Nama baru: huruf A–Z dan angka, maksimal 30 karakter.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
            modifier = Modifier.fillMaxWidth().semantics { if (nameError != null) error(nameError) })
        HotspotPasswordField(password, onPassword, visible, onVisibility, editable, "Password hotspot kamera", passwordError)
        Text("Perubahan dapat memutus koneksi. Aplikasi akan mencoba menyambung ke hotspot dengan nama dan password ini. Password login kamera tetap sama.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Simpan hotspot") }
    }
}

@Composable
private fun HotspotPasswordField(password: String, onPassword: (String) -> Unit, visible: Boolean,
    onVisibility: () -> Unit, enabled: Boolean, label: String, fieldError: String? = null) {
    OutlinedTextField(password, onPassword, label = { Text(label) }, enabled = enabled, singleLine = true, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = fieldError != null,
        supportingText = { Text(fieldError ?: "8–30 karakter: huruf, angka, atau @#_^*%.+ tanpa spasi.",
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
        trailingIcon = { TextButton(onClick = onVisibility, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (visible) "Sembunyikan" else "Tampilkan")
        } }, modifier = Modifier.fillMaxWidth().semantics { if (fieldError != null) error(fieldError) })
}

private fun apPhaseLabel(phase: ApEditPhase): String = when (phase) {
    ApEditPhase.CHECKING -> "Memeriksa koneksi kamera…"
    ApEditPhase.SENDING -> "Mengirim perubahan hotspot satu kali…"
    ApEditPhase.RECONNECTING -> "Menyambungkan kembali ke hotspot tujuan…"
    ApEditPhase.WAITING_WIFI -> "Sambungkan Android ke hotspot tujuan."
    ApEditPhase.VERIFYING -> "Memverifikasi identitas dan pengaturan hotspot kamera…"
    ApEditPhase.VERIFIED -> "Hotspot terverifikasi."
    ApEditPhase.NOT_SENT -> "Perubahan belum dikirim. Periksa koneksi kamera dan isian sebelum menyimpan."
    ApEditPhase.REJECTED -> "Perubahan ditolak kamera. Sambungkan kembali dan periksa dukungan hotspot."
    else -> "Hasil perubahan belum diketahui. Sambungkan dan periksa hotspot."
}
