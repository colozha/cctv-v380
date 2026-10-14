package dev.cctv.v380.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import dev.cctv.v380.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.provisioning.ProvisioningPhase
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.session.userMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningScreen(model: CameraViewModel, onBack: () -> Unit, onComplete: (CameraProfile) -> Unit,
    requestApPermissions: (() -> Unit, () -> Unit) -> Unit, requestScanPermissions: (() -> Unit) -> Unit,
    requestLanPermission: (() -> Unit, () -> Unit) -> Unit) {
    val state by model.provisioning.state.collectAsStateWithLifecycle()
    val ui by model.ui.collectAsStateWithLifecycle()
    var apSsid by rememberSaveable { mutableStateOf("") }
    var homeSsid by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Kamera saya") }
    var username by rememberSaveable { mutableStateOf("") }
    var saveAccess by rememberSaveable { mutableStateOf(false) }
    var noLocalPassword by rememberSaveable { mutableStateOf(false) }
    var openHomeWifi by rememberSaveable { mutableStateOf(false) }
    var apPassword by remember { mutableStateOf("") }
    var cameraPassword by remember { mutableStateOf("") }
    var homePassword by remember { mutableStateOf("") }
    var newLanPassword by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    val wifiSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.onForeground() }
    fun openWifiSettings() { model.expectWifiHandoff(); wifiSettings.launch(Intent(Settings.ACTION_WIFI_SETTINGS)) }
    fun clearPasswords() { apPassword = ""; cameraPassword = ""; homePassword = ""; newLanPassword = "" }
    fun lan(onDenied: () -> Unit = {}, action: () -> Unit) = requestLanPermission(onDenied, action)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { clearPasswords() }
    DisposableEffect(Unit) { onDispose { clearPasswords() } }
    LaunchedEffect(state.phase) {
        validation = null
        if (state.phase == ProvisioningPhase.COMPLETE) state.profile?.let(onComplete)
    }
    BackHandler(onBack = onBack)
    val busy = state.phase in setOf(ProvisioningPhase.CONNECTING_AP, ProvisioningPhase.DISCOVERING,
        ProvisioningPhase.AUTHENTICATING, ProvisioningPhase.SENDING_WIFI, ProvisioningPhase.VERIFYING)
    val initial = state.phase in setOf(ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP)
    Scaffold(topBar = { TopAppBar(title = { Text("Setup Wi-Fi kamera", fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (initial) Image(painterResource(R.drawable.ap_camera_illustration), contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().height(152.dp))
            Text(when (state.phase) {
                ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP -> "Hubungkan ke hotspot kamera"
                ProvisioningPhase.DISCOVERING, ProvisioningPhase.CHOOSE_CAMERA -> "Pilih kamera"
                ProvisioningPhase.CREDENTIALS, ProvisioningPhase.VERIFICATION_CREDENTIALS, ProvisioningPhase.AUTHENTICATING -> "Verifikasi akses lokal"
                ProvisioningPhase.READY_AP, ProvisioningPhase.SENDING_WIFI -> "Sambungkan ke Wi-Fi rumah"
                ProvisioningPhase.WAITING_LAN, ProvisioningPhase.VERIFYING -> "Temukan kamera kembali"
                ProvisioningPhase.COMPLETE -> "Kamera siap"
                ProvisioningPhase.ERROR -> "Setup perlu diperiksa"
            }, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = if (initial) TextAlign.Center else TextAlign.Start)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            validation?.let { ErrorNotice(it) }
            state.error?.let { ErrorNotice(it.userMessage()) }
            ui.error?.let { ErrorNotice(it, model::dismissError) }
            SetupContent(initial) {
            when (state.phase) {
                ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP -> {
                    Text("Aktifkan mode hotspot/AP pada kamera sesuai panduannya. Pilih Wi-Fi yang dipancarkan kamera; koneksi ini dapat tetap dipakai tanpa internet.", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    CameraPanel {
                    OutlinedTextField(apSsid, { apSsid = it; apPassword = "" }, label = { Text("SSID hotspot kamera") }, leadingIcon = { SectionIcon(WifiIcon) }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    SecretField(apPassword, { apPassword = it }, "Password hotspot kamera", !busy,
                        supporting = "Isi bila hotspot kamera meminta password.")
                    OutlinedButton(onClick = { requestScanPermissions { model.scanWifi() } }, enabled = !busy && !ui.wifiScanning,
                        modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Menu, null); Spacer(Modifier.width(8.dp)); Text(if (ui.wifiScanning) "Mencari Wi-Fi…" else "Lihat daftar Wi-Fi") }
                    ui.nearbyWifi.take(12).forEach { network -> TextButton(onClick = { apSsid = network.ssid; apPassword = "" }, enabled = !busy) {
                        Text("${network.ssid} · ${network.security}")
                    } }
                    }
                    if (Build.VERSION.SDK_INT >= 29) Button(onClick = {
                        if (apSsid.toByteArray(Charsets.UTF_8).size !in 1..32 || apPassword.isNotEmpty() && apPassword.length !in 8..63) {
                            validation = "Periksa SSID dan password hotspot. Password hotspot WPA2 memerlukan 8–63 karakter."
                        } else {
                            val password = apPassword.toCharArray(); apPassword = ""
                            requestApPermissions({ password.fill('\u0000') }) { model.connectAp(apSsid, password) }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(WifiIcon, null); Spacer(Modifier.width(12.dp)); Text("Hubungkan ke hotspot kamera") }
                    OutlinedButton(onClick = ::openWifiSettings, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(12.dp)); Text("Pilih hotspot di pengaturan Android") }
                    TextButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Saya sudah di hotspot kamera") }
                }
                ProvisioningPhase.CHOOSE_CAMERA -> {
                    Text("Pilih perangkat yang akan Anda konfigurasi.")
                    state.cameras.forEach { camera -> OutlinedButton(onClick = { model.provisioning.choose(camera) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Column(Modifier.fillMaxWidth()) { Text("Kamera ${camera.deviceId}"); Text("${camera.host}:${camera.port}", style = MaterialTheme.typography.bodySmall) }
                    } }
                    TextButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cari lagi") }
                }
                ProvisioningPhase.CREDENTIALS, ProvisioningPhase.VERIFICATION_CREDENTIALS -> {
                    Text("Masukkan akses lokal yang Anda tetapkan untuk kamera. Password akun V380 dan Wi-Fi rumah berbeda dengan akses ini.")
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama kamera") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (state.camera?.platformType == 1) OutlinedTextField(username, { username = it }, label = { Text("Username kamera") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (!noLocalPassword) SecretField(cameraPassword, { cameraPassword = it.take(4096) }, "Password lokal kamera")
                    ToggleRow("Kamera belum memakai password lokal", noLocalPassword) { noLocalPassword = it; cameraPassword = "" }
                    ToggleRow("Ingat password terenkripsi", saveAccess) { saveAccess = it }
                    Button(onClick = {
                        if (name.isBlank() || state.camera?.platformType == 1 && username.isBlank() || !noLocalPassword && cameraPassword.isEmpty())
                            validation = "Lengkapi nama dan akses lokal kamera. Jangan gunakan kredensial akun V380."
                        else {
                            val password = if (noLocalPassword) charArrayOf() else cameraPassword.toCharArray()
                            cameraPassword = ""
                            lan(onDenied = { password.fill('\u0000') }) { model.provisioning.authenticate(name.trim(), username, password, saveAccess) }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Verifikasi akses kamera") }
                    if (state.phase == ProvisioningPhase.CREDENTIALS) TextButton(onClick = { model.provisioning.requestFreshPairing() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Saya perlu pairing kamera baru")
                    }
                }
                ProvisioningPhase.READY_AP -> {
                    Text("Akses lokal kamera berhasil diverifikasi. Pilih Wi-Fi tujuan, atau gunakan koneksi langsung melalui hotspot kamera.")
                    OutlinedTextField(homeSsid, { homeSsid = it }, label = { Text("SSID Wi-Fi rumah") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (!openHomeWifi) SecretField(homePassword, { homePassword = it.take(63) }, "Password Wi-Fi rumah")
                    ToggleRow("Wi-Fi rumah tanpa password", openHomeWifi) { openHomeWifi = it; homePassword = "" }
                    OutlinedButton(onClick = { requestScanPermissions { model.scanWifi() } }, enabled = !ui.wifiScanning,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Lihat Wi-Fi tujuan") }
                    ui.nearbyWifi.take(12).forEach { network -> TextButton(onClick = { homeSsid = network.ssid; openHomeWifi = network.security == "OPEN"; homePassword = "" }) {
                        Text("${network.ssid} · ${network.security}")
                    } }
                    Button(onClick = {
                        if (homeSsid.toByteArray(Charsets.UTF_8).size !in 1..32 || !openHomeWifi && homePassword.length !in 8..63)
                            validation = "Periksa SSID dan password Wi-Fi tujuan."
                        else {
                            val password = if (openHomeWifi) charArrayOf() else homePassword.toCharArray()
                            homePassword = ""
                            lan(onDenied = { password.fill('\u0000') }) { model.provisioning.configureWifi(homeSsid, password) }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Kirim konfigurasi Wi-Fi") }
                    OutlinedButton(onClick = { model.provisioning.useApDirectly() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Gunakan koneksi AP langsung") }
                    if (state.camera?.platformType == 2) {
                        HorizontalDivider()
                        Text("Akses lokal tersimpan", style = MaterialTheme.typography.titleMedium)
                        Text("Opsional: tetapkan password LAN baru. Akses terenkripsi disimpan setelah setup berhasil.", style = MaterialTheme.typography.bodyMedium)
                        SecretField(newLanPassword, { newLanPassword = it.take(1024) }, "Password LAN baru")
                        OutlinedButton(onClick = {
                            if (newLanPassword.isEmpty()) validation = "Isi password LAN baru."
                            else { val password = newLanPassword.toCharArray(); newLanPassword = ""; model.provisioning.pairAndSaveAccess(password) }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tetapkan dan simpan akses lokal") }
                    }
                }
                ProvisioningPhase.WAITING_LAN -> {
                    Text("Hubungkan HP ke Wi-Fi ${state.targetSsid}. Kamera dapat memperoleh IP baru; aplikasi akan mencari identitas perangkat yang sama.")
                    if (state.mayHaveConfigured) Text("Konfigurasi mungkin sudah diterapkan. Pemeriksaan berikut tidak mengirim ulang password Wi-Fi.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = ::openWifiSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Buka Wi-Fi rumah") }
                    Button(onClick = { lan { model.provisioning.verifyLan() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Periksa kamera di Wi-Fi rumah") }
                    TextButton(onClick = { model.provisioning.discardPending() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Setup ulang lewat hotspot") }
                }
                ProvisioningPhase.VERIFYING -> Text("Mencari kamera dan memverifikasi login lokal. Pemeriksaan berlangsung maksimal 120 detik.")
                ProvisioningPhase.AUTHENTICATING -> Text("Memverifikasi respons kamera…")
                ProvisioningPhase.SENDING_WIFI -> Text("Mengirim konfigurasi satu kali. Koneksi hotspot dapat terputus saat kamera berpindah jaringan.")
                ProvisioningPhase.DISCOVERING -> Text("Mencari kamera pada jaringan yang dipilih…")
                ProvisioningPhase.COMPLETE -> Text("Identitas dan autentikasi kamera sudah diverifikasi.")
                ProvisioningPhase.ERROR -> {
                    if (state.mayHaveConfigured) {
                        Text("Konfigurasi mungkin sudah diterapkan. Periksa di Wi-Fi tujuan sebelum mencoba setup lagi.")
                        OutlinedButton(onClick = ::openWifiSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Buka Wi-Fi rumah") }
                        Button(onClick = { lan { model.provisioning.verifyLan() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Periksa kamera di Wi-Fi rumah") }
                        TextButton(onClick = { model.provisioning.discardPending() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Setup ulang lewat hotspot") }
                    } else if (state.camera != null) {
                        Button(onClick = { model.provisioning.choose(state.camera!!) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Masukkan akses kembali") }
                    } else OutlinedButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cari kamera kembali") }
                }
            }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupContent(initial: Boolean, content: @Composable ColumnScope.() -> Unit) {
    if (initial) Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    else CameraPanel(content = content)
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean = true, supporting: String? = null) {
    var visible by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { visible = false }
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = enabled, modifier = Modifier.fillMaxWidth(), singleLine = true,
        leadingIcon = { SectionIcon(Icons.Default.Lock) },
        trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = enabled) {
            Icon(if (visible) EyeOffIcon else EyeIcon, if (visible) "Sembunyikan $label" else "Tampilkan $label")
        } }, supportingText = supporting?.let { text -> { Text(text) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null); Spacer(Modifier.width(8.dp)); Text(text, Modifier.weight(1f))
    }
}
