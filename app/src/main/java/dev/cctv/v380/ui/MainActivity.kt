package dev.cctv.v380.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.control.Direction
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.session.*
import dev.cctv.v380.provisioning.ProvisioningPhase

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[CameraViewModel::class.java] }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { V380Theme { V380App(model) } }
    }
    override fun onResume() { super.onResume(); model.onForeground() }
    override fun onStop() { if (!isChangingConfigurations) model.onBackground(); super.onStop() }
}

@Composable
fun V380Theme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF95D6B1), secondary = Color(0xFFB4CCBD),
        surface = Color(0xFF101511), background = Color(0xFF101511),
    ) else lightColorScheme(
        primary = Color(0xFF185C43), secondary = Color(0xFF496453),
        surface = Color(0xFFF8FAF6), background = Color(0xFFF8FAF6),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun V380App(model: CameraViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    val session by model.session.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var draftHost by rememberSaveable { mutableStateOf("") }
    var draftPort by rememberSaveable { mutableStateOf(8800) }
    var draftDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    var draftPlatform by rememberSaveable { mutableStateOf<Int?>(null) }
    var draftGeneration by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingPermission by remember { mutableStateOf<PendingNetworkAction?>(null) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val action = pendingPermission
        pendingPermission = null
        if (grants.values.all { it }) action?.execute?.invoke() else {
            action?.cancel?.invoke()
            model.message("Izin Wi-Fi, lokasi untuk daftar SSID, atau jaringan lokal belum diberikan. Aktifkan izin yang diminta, atau masukkan SSID manual.")
        }
    }
    fun withPermissions(permissions: List<String>, onDenied: () -> Unit = {}, action: () -> Unit) {
        val missing = permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action()
        else {
            pendingPermission?.cancel?.invoke()
            pendingPermission = PendingNetworkAction(action, onDenied)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
    val lanPermissions = if (Build.VERSION.SDK_INT >= 37) listOf(Manifest.permission.ACCESS_LOCAL_NETWORK) else emptyList()
    val apPermissions = lanPermissions + if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else if (Build.VERSION.SDK_INT >= 29) listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) else emptyList()
    val scanPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    DisposableEffect(Unit) { onDispose { pendingPermission?.cancel?.invoke(); pendingPermission = null } }
    val selected = state.profiles.firstOrNull { it.id == selectedId }
    val editing = state.profiles.firstOrNull { it.id == editingId }
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        diagnosticsOpen -> DiagnosticsScreen(model, onBack = { diagnosticsOpen = false })
        setupOpen -> ProvisioningScreen(model, onBack = { model.cancelSetup(); setupOpen = false },
            onComplete = { camera -> model.reloadAfterSetup(); selectedId = camera.id; setupOpen = false },
            requestApPermissions = { cancel, action -> withPermissions(apPermissions, onDenied = cancel, action = action) },
            requestScanPermissions = { action -> withPermissions(scanPermissions, action = action) },
            requestLanPermission = { cancel, action -> withPermissions(lanPermissions, onDenied = cancel, action = action) })
        editorOpen -> ProfileEditor(editing, draftHost, draftPort, draftDeviceId, draftPlatform, draftGeneration, state.saving, state.error,
            onBack = { editorOpen = false; model.dismissError() },
            onSave = { profile, password, remember -> model.save(profile, password, remember) { editorOpen = false } },
            onDelete = { profile -> model.delete(profile) { editorOpen = false; if (selectedId == profile.id) selectedId = null } })
        selected != null -> LivePlayerScreen(model, selected, session, state.error,
            onBack = { model.disconnect(); selectedId = null; model.dismissError() },
            onEdit = { editingId = selected.id; editorOpen = true },
            onConnect = { password -> withPermissions(lanPermissions, onDenied = { password?.fill('\u0000') }) { model.connect(selected, password) } },
            onDiagnostics = { diagnosticsOpen = true })
        else -> CameraList(state,
            onScan = { withPermissions(lanPermissions) { model.scan() } }, onCancel = model::cancelScan,
            onAdd = { editingId = null; draftHost = ""; draftPort = 8800; draftDeviceId = null; draftPlatform = null; draftGeneration = null; editorOpen = true; model.dismissError() },
            onOpen = { selectedId = it.id; model.dismissError() },
            onEdit = { editingId = it.id; editorOpen = true; model.dismissError() },
            onFound = { editingId = null; draftHost = it.host; draftPort = it.port; draftDeviceId = it.deviceId; draftPlatform = it.platformType; draftGeneration = it.generation; editorOpen = true },
            onSetup = { model.beginSetup(); setupOpen = true }, onDiagnostics = { diagnosticsOpen = true }, onDismissError = model::dismissError)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraList(
    state: CameraUiState, onScan: () -> Unit, onCancel: () -> Unit, onAdd: () -> Unit,
    onOpen: (CameraProfile) -> Unit, onEdit: (CameraProfile) -> Unit,
    onFound: (DiscoveredCamera) -> Unit, onDismissError: () -> Unit,
    onSetup: () -> Unit, onDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("V380 LAN", fontWeight = FontWeight.SemiBold) }, actions = {
            IconButton(onClick = onDiagnostics) { Icon(Icons.Default.Info, "Diagnostik LAN") }
            TextButton(onClick = if (state.scanning) onCancel else onScan) {
                Icon(if (state.scanning) Icons.Default.Close else Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp)); Text(if (state.scanning) "Batal" else "Cari")
            }
        }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Tambah kamera manual") } },
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 104.dp)) {
            item {
                Spacer(Modifier.height(20.dp))
                Text("Kamera Anda", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Pantau kamera melalui Wi-Fi yang sama.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onSetup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Setup lewat AP kamera") }
                Spacer(Modifier.height(28.dp))
                state.error?.let { ErrorNotice(it, onDismissError); Spacer(Modifier.height(16.dp)) }
                if (state.loading || state.scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Mencari kamera" })
                    Spacer(Modifier.height(12.dp))
                    Text(if (state.loading) "Membaca profil…" else "Mencari kamera di Wi-Fi…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (!state.loading && state.profiles.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 40.dp), horizontalAlignment = Alignment.Start) {
                    Icon(CameraIcon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text("Belum ada kamera", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Cari kamera di jaringan lokal atau tambahkan alamat IP secara manual.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onScan, enabled = !state.scanning, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cari di Wi-Fi") }
                    TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tambah manual") }
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(profile) }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CameraIcon, null, Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onEdit(profile) }) { Icon(Icons.Default.Edit, "Edit ${profile.name}") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (state.discovered.isNotEmpty()) item {
                Spacer(Modifier.height(32.dp))
                Text("Ditemukan di Wi-Fi", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }
            if (state.scanCompleted && state.discovered.isEmpty()) item {
                Text("Tidak ada kamera ditemukan. Pastikan kamera menyala dan tersambung ke Wi-Fi yang sama, atau tambahkan IP manual.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.discovered, key = { "discovered-${it.deviceId}" }) { camera ->
                val saved = state.profiles.any { it.deviceId == camera.deviceId }
                ListItem(headlineContent = { Text("Kamera ${camera.deviceId}") },
                    supportingContent = { Text("${camera.host}:${camera.port}") },
                    trailingContent = {
                        TextButton(onClick = { onFound(camera) }, enabled = !saved) { Text(if (saved) "Tersimpan" else "Tambah") }
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    existing: CameraProfile?, initialHost: String, initialPort: Int, initialDeviceId: String?, initialPlatform: Int?, initialGeneration: Int?, saving: Boolean, error: String?, onBack: () -> Unit,
    onSave: (CameraProfile, CharArray?, Boolean) -> Unit, onDelete: (CameraProfile) -> Unit,
) {
    val id = rememberSaveable { existing?.id ?: java.util.UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: initialDeviceId?.let { "Kamera $it" }.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(existing?.host ?: initialHost) }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: initialPort).toString()) }
    var protocol by rememberSaveable { mutableStateOf(existing?.protocolProfileId ?: "auto") }
    var deviceId by rememberSaveable { mutableStateOf((existing?.deviceId ?: initialDeviceId)?.toString().orEmpty()) }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var rememberPassword by rememberSaveable { mutableStateOf(existing?.hasSavedPassword ?: false) }
    var replacePassword by rememberSaveable { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { onBack() }
    DisposableEffect(Unit) { onDispose { password = "" } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { password = "" }
    fun setRemember(value: Boolean) {
        rememberPassword = value
        if (!value) { password = ""; replacePassword = false }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(if (existing == null) "Tambah kamera" else "Edit kamera") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Kembali") } },
            actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }, enabled = !saving) { Icon(Icons.Default.Delete, "Hapus kamera") } })
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Detail kamera", style = MaterialTheme.typography.headlineSmall)
            Text("Gunakan alamat dan akses lokal kamera. Profil protokol dipilih dari hasil discovery.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama kamera") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(host, { host = it }, label = { Text("Alamat IP") }, placeholder = { Text("192.168.1.10") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(2f), singleLine = true)
            }
            if (protocol != "iot-lan" && (existing?.platformType ?: initialPlatform) != 2)
                OutlinedTextField(username, { username = it }, label = { Text("Username kamera") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Protokol", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Otomatis", "legacy-v2" to "Legacy V2", "iot-lan" to "IoT LAN").forEach { (id, label) ->
                    FilterChip(selected = protocol == id, onClick = { protocol = id; if (id == "iot-lan" && port == "8800") port = "8089" }, label = { Text(label) })
                }
            }
            if (protocol != "auto") Text("Pilih manual hanya jika generasi protokol sudah diketahui. Tidak ada percobaan format login alternatif.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(rememberPassword, role = Role.Checkbox, onValueChange = ::setRemember).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberPassword, onCheckedChange = null)
                Spacer(Modifier.width(8.dp)); Column {
                    Text("Ingat password", style = MaterialTheme.typography.titleSmall)
                    Text("Terenkripsi di perangkat ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (rememberPassword) {
                if (existing?.hasSavedPassword == true) TextButton(onClick = { replacePassword = !replacePassword; password = "" }) {
                    Text(if (replacePassword) "Pertahankan password tersimpan" else "Ganti password tersimpan")
                }
                if (existing?.hasSavedPassword != true || replacePassword) OutlinedTextField(password, { password = it },
                    label = { Text("Password kamera") }, supportingText = { Text("Biarkan kosong jika kamera tidak memakai password.") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            (validation ?: error)?.let { ErrorNotice(it) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val profile = CameraProfile(id, name.trim(), host.trim(), port.toIntOrNull() ?: 0, deviceId.trim(),
                    username, protocol, authenticationMode = existing?.authenticationMode ?: if (protocol == "legacy-v2" || initialPlatform == 1) AuthenticationMode.USER_PASSWORD else AuthenticationMode.AUTO,
                    platformType = existing?.platformType ?: initialPlatform, discoveryGeneration = existing?.discoveryGeneration ?: initialGeneration)
                validation = profile.validationError()
                if (validation == null) {
                    val secret = if (rememberPassword && (existing?.hasSavedPassword != true || replacePassword)) password.toCharArray() else null
                    password = ""
                    onSave(profile, secret, rememberPassword)
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (saving) "Menyimpan…" else "Simpan kamera") }
        }
    }
    if (confirmDelete && existing != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Hapus kamera?") }, text = { Text("Profil ${existing.name} dan password tersimpannya akan dihapus dari perangkat ini.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } })
}

@Composable
internal fun ErrorNotice(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}

internal val CameraIcon = ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 5f); lineTo(16f, 5f); lineTo(16f, 9f); lineTo(21f, 6f); lineTo(21f, 18f)
        lineTo(16f, 15f); lineTo(16f, 19f); lineTo(3f, 19f); close()
    }
}.build()

private class PendingNetworkAction(val execute: () -> Unit, val cancel: () -> Unit)
