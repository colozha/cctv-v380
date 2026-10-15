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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.cctv.v380.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.provisioning.ProvisioningPhase
import dev.cctv.v380.provisioning.ProvisioningState
import dev.cctv.v380.provisioning.RouterCheck
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.session.SessionError
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
    val defaultName = stringResource(R.string.copy_kamera_saya_81d35bd9)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var username by rememberSaveable { mutableStateOf("") }
    var saveAccess by rememberSaveable { mutableStateOf(false) }
    var noLocalPassword by rememberSaveable { mutableStateOf(false) }
    var openHomeWifi by rememberSaveable { mutableStateOf(false) }
    var apPassword by remember { mutableStateOf("") }
    var cameraPassword by remember { mutableStateOf("") }
    var homePassword by remember { mutableStateOf("") }
    var newLanPassword by remember { mutableStateOf("") }
    var localOptions by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<Int?>(null) }
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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_offline_title), fontWeight = FontWeight.SemiBold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.copy_kembali_31fadaaf)) } }) }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (initial) Image(painterResource(R.drawable.ap_camera_illustration), contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().height(152.dp))
            Text(when (state.phase) {
                ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP -> stringResource(R.string.copy_hubungkan_ke_hotspot_kamera_cb00fb73)
                ProvisioningPhase.DISCOVERING, ProvisioningPhase.CHOOSE_CAMERA -> stringResource(R.string.copy_pilih_kamera_e4c06a2b)
                ProvisioningPhase.CREDENTIALS, ProvisioningPhase.VERIFICATION_CREDENTIALS, ProvisioningPhase.AUTHENTICATING -> stringResource(R.string.copy_verifikasi_akses_lokal_c2d06e9f)
                ProvisioningPhase.READY_AP, ProvisioningPhase.SENDING_WIFI -> stringResource(R.string.setup_router_title)
                ProvisioningPhase.WAITING_LAN, ProvisioningPhase.VERIFYING -> stringResource(R.string.copy_temukan_kamera_kembali_2a3344cd)
                ProvisioningPhase.COMPLETE -> stringResource(R.string.copy_kamera_siap_81d37708)
                ProvisioningPhase.ERROR -> stringResource(R.string.copy_setup_perlu_diperiksa_d1fe2d6b)
            }, modifier = Modifier.fillMaxWidth().semantics { heading(); liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, textAlign = if (initial) TextAlign.Center else TextAlign.Start)
            val step = when (state.phase) {
                ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP, ProvisioningPhase.DISCOVERING, ProvisioningPhase.CHOOSE_CAMERA -> 1
                ProvisioningPhase.CREDENTIALS -> 2
                ProvisioningPhase.AUTHENTICATING -> if (state.mayHaveConfigured) 4 else 2
                ProvisioningPhase.READY_AP, ProvisioningPhase.SENDING_WIFI -> 3
                ProvisioningPhase.ERROR -> if (state.mayHaveConfigured) 4 else if (state.camera != null) 2 else 1
                else -> 4
            }
            Text(stringResource(R.string.setup_stage, step, stringResource(when (step) {
                1 -> R.string.setup_stage_ap; 2 -> R.string.setup_stage_access
                3 -> R.string.setup_stage_router; else -> R.string.setup_stage_verify
            })), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            validation?.let { ErrorNotice(stringResource(it)) }
            state.error?.let { error ->
                ErrorNotice(when {
                    error == SessionError.WifiVerificationTimeout -> error.userMessage()
                    error == SessionError.AuthenticationFailed -> stringResource(R.string.setup_access_rejected)
                    state.mayHaveConfigured -> stringResource(R.string.setup_unknown_result)
                    else -> when (error) {
                    SessionError.CloudRequired, SessionError.OwnerBindingRequired -> stringResource(R.string.setup_cloud_limit)
                    SessionError.PairingRequired -> stringResource(R.string.setup_pairing_limit)
                    SessionError.WifiConfigurationRejected -> stringResource(R.string.setup_wifi_rejected)
                    SessionError.InvalidInput -> if (state.phase == ProvisioningPhase.READY_AP)
                        stringResource(R.string.setup_invalid_wifi) else error.userMessage()
                    SessionError.AuthenticationFailed -> stringResource(R.string.setup_access_rejected)
                    SessionError.NetworkUnavailable -> if (state.phase == ProvisioningPhase.READY_AP)
                        stringResource(R.string.setup_network_lost) else error.userMessage()
                    else -> error.userMessage()
                    }
                })
            }
            ui.error?.let { ErrorNotice(it, model::dismissError) }
            SetupContent(initial) {
            when (state.phase) {
                ProvisioningPhase.IDLE, ProvisioningPhase.CONNECTING_AP -> {
                    Text(stringResource(R.string.copy_aktifkan_mode_hotspot_ap_pada_kamera_sesuai_pand_22148c86), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.setup_offline_help), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CameraPanel {
                    OutlinedTextField(apSsid, { apSsid = it; apPassword = "" }, label = { Text(stringResource(R.string.copy_ssid_hotspot_kamera_b10961d3)) }, leadingIcon = { SectionIcon(WifiIcon) }, singleLine = true,
                        enabled = !busy, modifier = Modifier.fillMaxWidth())
                    SecretField(apPassword, { apPassword = it }, stringResource(R.string.copy_password_hotspot_kamera_18f95693), !busy,
                        supporting = stringResource(R.string.copy_isi_bila_hotspot_kamera_meminta_password_7069494b))
                    OutlinedButton(onClick = { requestScanPermissions { model.scanWifi() } }, enabled = !busy && !ui.wifiScanning,
                        modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Menu, null); Spacer(Modifier.width(8.dp)); Text(if (ui.wifiScanning) stringResource(R.string.copy_mencari_wi_fi_54f06a3d) else stringResource(R.string.copy_lihat_daftar_wi_fi_600054fc)) }
                    ui.nearbyWifi.take(12).forEach { network -> TextButton(onClick = { apSsid = network.ssid; apPassword = "" }, enabled = !busy) {
                        Text(stringResource(R.string.setup_wifi_item, network.ssid, network.security))
                    } }
                    }
                    if (Build.VERSION.SDK_INT >= 29) Button(onClick = {
                        if (apSsid.toByteArray(Charsets.UTF_8).size !in 1..32 || apPassword.isNotEmpty() && apPassword.length !in 8..63) {
                            validation = R.string.copy_periksa_ssid_dan_password_hotspot_password_hotsp_87332421
                        } else {
                            val password = apPassword.toCharArray(); apPassword = ""
                            requestApPermissions({ password.fill('\u0000') }) { model.connectAp(apSsid, password) }
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(WifiIcon, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.copy_hubungkan_ke_hotspot_kamera_cb00fb73)) }
                    OutlinedButton(onClick = ::openWifiSettings, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.copy_pilih_hotspot_di_pengaturan_android_bb163076)) }
                    TextButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_saya_sudah_di_hotspot_kamera_441f28e8)) }
                }
                ProvisioningPhase.CHOOSE_CAMERA -> {
                    Text(stringResource(R.string.copy_pilih_perangkat_yang_akan_anda_konfigurasi_487de42d))
                    state.cameras.forEach { camera -> OutlinedButton(onClick = { model.provisioning.choose(camera) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Column(Modifier.fillMaxWidth()) { Text(stringResource(R.string.camera_number, camera.deviceId)); Text(stringResource(R.string.setup_endpoint, camera.host, camera.port), style = MaterialTheme.typography.bodySmall) }
                    } }
                    TextButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_cari_lagi_6de3cb22)) }
                }
                ProvisioningPhase.CREDENTIALS, ProvisioningPhase.VERIFICATION_CREDENTIALS -> {
                    Text(stringResource(R.string.copy_masukkan_akses_lokal_yang_anda_tetapkan_untuk_ka_31c31e96))
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.copy_nama_kamera_c3df87b6)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (state.camera?.platformType == 1) OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.copy_username_kamera_d343a287)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (!noLocalPassword) SecretField(cameraPassword, { cameraPassword = it.take(4096) }, stringResource(R.string.copy_password_lokal_kamera_03c234af))
                    ToggleRow(stringResource(R.string.copy_kamera_belum_memakai_password_lokal_b675e6e9), noLocalPassword) { noLocalPassword = it; cameraPassword = "" }
                    ToggleRow(stringResource(R.string.copy_ingat_password_terenkripsi_050f9674), saveAccess) { saveAccess = it }
                    Button(onClick = {
                        if (name.isBlank() || state.camera?.platformType == 1 && username.isBlank() || !noLocalPassword && cameraPassword.isEmpty())
                            validation = R.string.copy_lengkapi_nama_dan_akses_lokal_kamera_jangan_guna_302c0d6e
                        else {
                            val password = if (noLocalPassword) charArrayOf() else cameraPassword.toCharArray()
                            cameraPassword = ""
                            lan(onDenied = { password.fill('\u0000') }) { model.provisioning.authenticate(name.trim(), username, password, saveAccess) }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.copy_verifikasi_akses_kamera_94c43c71)) }
                    if (state.phase == ProvisioningPhase.CREDENTIALS) TextButton(onClick = { model.provisioning.requestFreshPairing() }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.copy_saya_perlu_pairing_kamera_baru_595e4229))
                    }
                }
                ProvisioningPhase.READY_AP -> {
                    Text(stringResource(R.string.copy_akses_lokal_kamera_berhasil_diverifikasi_pilih_w_a0c9caca))
                    Text(stringResource(R.string.setup_offline_help), style = MaterialTheme.typography.bodyMedium)
                    if (state.error in setOf(SessionError.NetworkUnavailable, SessionError.AuthenticationFailed)) {
                        OutlinedButton(onClick = ::openWifiSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.copy_pilih_hotspot_di_pengaturan_android_bb163076))
                        }
                        TextButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.copy_saya_sudah_di_hotspot_kamera_441f28e8))
                        }
                    }
                    OutlinedTextField(homeSsid, { homeSsid = it }, label = { Text(stringResource(R.string.setup_router_ssid)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (!openHomeWifi) SecretField(homePassword, { homePassword = it.take(63) }, stringResource(R.string.setup_router_password))
                    ToggleRow(stringResource(R.string.setup_router_open), openHomeWifi) { openHomeWifi = it; homePassword = "" }
                    OutlinedButton(onClick = { requestScanPermissions { model.scanWifi() } }, enabled = !ui.wifiScanning,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_lihat_wi_fi_tujuan_78c42a7d)) }
                    ui.nearbyWifi.take(12).forEach { network -> TextButton(onClick = { homeSsid = network.ssid; openHomeWifi = network.security == "OPEN"; homePassword = "" }) {
                        Text(stringResource(R.string.setup_wifi_item, network.ssid, network.security))
                    } }
                    Button(onClick = {
                        if (homeSsid.toByteArray(Charsets.UTF_8).size !in 1..32 || !openHomeWifi && homePassword.length !in 8..63)
                            validation = R.string.copy_periksa_ssid_dan_password_wi_fi_tujuan_06a057cf
                        else {
                            val password = if (openHomeWifi) charArrayOf() else homePassword.toCharArray()
                            homePassword = ""
                            lan(onDenied = { password.fill('\u0000') }) { model.provisioning.configureWifi(homeSsid, password) }
                        }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.copy_kirim_konfigurasi_wi_fi_5216d8f6)) }
                    OutlinedButton(onClick = { model.provisioning.useApDirectly() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_gunakan_koneksi_ap_langsung_5631a397)) }
                    if (state.camera?.platformType == 2) {
                        HorizontalDivider()
                        TextButton(onClick = { localOptions = !localOptions; newLanPassword = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.setup_local_options))
                        }
                        if (localOptions) {
                        Text(stringResource(R.string.setup_local_password_help), style = MaterialTheme.typography.bodyMedium)
                        SecretField(newLanPassword, { newLanPassword = it.take(1024) }, stringResource(R.string.copy_password_lan_baru_c5ea78ce))
                        OutlinedButton(onClick = {
                            if (newLanPassword.isEmpty()) validation = R.string.copy_isi_password_lan_baru_45268a61
                            else { val password = newLanPassword.toCharArray(); newLanPassword = ""; model.provisioning.pairAndSaveAccess(password) }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.setup_set_password)) }
                        }
                    }
                }
                ProvisioningPhase.WAITING_LAN -> {
                    RouterSetupRecovery(state, ::openWifiSettings,
                        { confirmed -> lan { model.provisioning.verifyLan(confirmed) } }, model.provisioning::discardPending)
                }
                ProvisioningPhase.VERIFYING -> Text(stringResource(R.string.copy_mencari_kamera_dan_memverifikasi_login_lokal_pem_9f195fbe))
                ProvisioningPhase.AUTHENTICATING -> Text(stringResource(R.string.copy_memverifikasi_respons_kamera_1f475597))
                ProvisioningPhase.SENDING_WIFI -> Text(stringResource(R.string.copy_mengirim_konfigurasi_satu_kali_koneksi_hotspot_d_7d218ae0))
                ProvisioningPhase.DISCOVERING -> Text(stringResource(R.string.copy_mencari_kamera_pada_jaringan_yang_dipilih_6ec87610))
                ProvisioningPhase.COMPLETE -> Text(stringResource(R.string.copy_identitas_dan_autentikasi_kamera_sudah_diverifik_12a424b2))
                ProvisioningPhase.ERROR -> {
                    if (state.mayHaveConfigured) {
                        RouterSetupRecovery(state, ::openWifiSettings,
                            { confirmed -> lan { model.provisioning.verifyLan(confirmed) } }, model.provisioning::discardPending)
                    } else if (state.camera != null) {
                        Button(onClick = { model.provisioning.choose(state.camera!!) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.copy_masukkan_akses_kembali_b9409be9)) }
                    } else OutlinedButton(onClick = { lan { model.provisioning.discoverCurrentAp() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.copy_cari_kamera_kembali_88856217)) }
                }
            }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Recovery actions never receive the router password and cannot replay a configuration write. */
@Composable
internal fun RouterSetupRecovery(state: ProvisioningState, onSettings: () -> Unit,
    onVerify: (Boolean) -> Unit, onReset: () -> Unit,
) {
    var confirmed by remember(state.routerCheck, state.targetSsid) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { confirmed = false }
    Text(stringResource(R.string.setup_verify_help, state.targetSsid))
    Text(stringResource(R.string.copy_konfigurasi_mungkin_sudah_diterapkan_pemeriksaan_e104fab2),
        style = MaterialTheme.typography.bodyMedium)
    when (state.routerCheck) {
        RouterCheck.WRONG_NETWORK -> ErrorNotice(stringResource(R.string.setup_wrong_network, state.targetSsid))
        RouterCheck.CONFIRMATION_REQUIRED -> {
            Text(stringResource(R.string.setup_ssid_hidden), style = MaterialTheme.typography.bodyMedium)
            ToggleRow(stringResource(R.string.setup_confirm_router, state.targetSsid), confirmed) { confirmed = it }
        }
        else -> Unit
    }
    OutlinedButton(onClick = { confirmed = false; onSettings() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.setup_open_router))
    }
    Button(onClick = { onVerify(confirmed); confirmed = false },
        enabled = state.routerCheck != RouterCheck.CONFIRMATION_REQUIRED || confirmed,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.setup_verify_router)) }
    Text(stringResource(R.string.setup_reset_help), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.setup_reset)) }
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
            Icon(if (visible) EyeOffIcon else EyeIcon,
                stringResource(if (visible) R.string.hide_secret else R.string.show_secret, label))
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
