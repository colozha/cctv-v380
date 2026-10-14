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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
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
        primary = Color(0xFF82DEB8), onPrimary = Color(0xFF06291D),
        primaryContainer = Color(0xFF12372D), onPrimaryContainer = Color(0xFFA0F0D3),
        secondary = Color(0xFFAAF2DF), onSecondary = Color(0xFF06382D),
        secondaryContainer = Color(0xFF233A35), onSecondaryContainer = Color(0xFFC0F2DD),
        tertiary = Color(0xFF6531CF), onTertiary = Color.White,
        tertiaryContainer = Color(0xFF37205F), onTertiaryContainer = Color(0xFFE8DAFF),
        surface = Color(0xFF080E10), background = Color(0xFF080E10),
        onSurface = Color(0xFFF3F4F8), onBackground = Color(0xFFF3F4F8),
        surfaceContainer = Color(0xFF131B20), surfaceContainerHigh = Color(0xFF1C252C),
        surfaceContainerLow = Color(0xFF10171C), surfaceContainerHighest = Color(0xFF252E35),
        surfaceVariant = Color(0xFF222B32), onSurfaceVariant = Color(0xFFB9BBCB),
        outline = Color(0xFF7D8495), outlineVariant = Color(0xFF2E3842),
    ) else lightColorScheme(
        primary = Color(0xFF185C43), onPrimary = Color.White,
        primaryContainer = Color(0xFFD0EFDF), onPrimaryContainer = Color(0xFF123526),
        secondary = Color(0xFF296852), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDEEEE7), onSecondaryContainer = Color(0xFF123526),
        tertiary = Color(0xFF6531CF), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE2FF), onTertiaryContainer = Color(0xFF43247A),
        surface = Color(0xFFF5F8F6), background = Color(0xFFF5F8F6),
        onSurface = Color(0xFF17231E), onBackground = Color(0xFF17231E),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE9F0ED),
        surfaceContainerLow = Color(0xFFF0F5F2), surfaceContainerHighest = Color(0xFFE1E9E5),
        surfaceVariant = Color(0xFFE7EEE9), onSurfaceVariant = Color(0xFF4C5E55),
        outline = Color(0xFF6C7D74), outlineVariant = Color(0xFFD9E2DC),
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
internal fun CameraList(
    state: CameraUiState, onScan: () -> Unit, onCancel: () -> Unit, onAdd: () -> Unit,
    onOpen: (CameraProfile) -> Unit, onEdit: (CameraProfile) -> Unit,
    onFound: (DiscoveredCamera) -> Unit, onDismissError: () -> Unit,
    onSetup: () -> Unit, onDiagnostics: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(buildAnnotatedString {
            append("V380 ")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("LAN") }
        }, fontWeight = FontWeight.Bold) }, actions = {
            IconButton(onClick = onDiagnostics) { Icon(Icons.Default.Info, "Diagnostik LAN") }
            TextButton(onClick = if (state.scanning) onCancel else onScan) {
                Icon(if (state.scanning) Icons.Default.Close else Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp)); Text(if (state.scanning) "Batal" else "Cari")
            }
        }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Add, "Tambah kamera manual", Modifier.size(32.dp))
                }
                Text("Tambah kamera", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        },
    ) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Kamera Anda", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pantau kamera melalui Wi-Fi yang sama.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
                Surface(onClick = onSetup, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionIcon(WifiIcon)
                        Text("Setup lewat AP kamera", Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            state.error?.let { message -> item { ErrorNotice(message, onDismissError) } }
            if (state.loading || state.scanning) item {
                LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Mencari kamera" })
                Text(if (state.loading) "Membaca profil…" else "Mencari kamera di Wi-Fi…", Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (!state.loading && state.profiles.isEmpty()) item {
                CameraPanel {
                    CameraBadge()
                    Text("Belum ada kamera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Cari kamera di jaringan lokal atau tambahkan alamat IP secara manual.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onScan, enabled = !state.scanning, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cari di Wi-Fi") }
                    TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tambah manual") }
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                Surface(onClick = { onOpen(profile) }, shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CameraBadge()
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val found = state.discovered.any { it.deviceId == profile.deviceId }
                            StatusLabel(if (found) "Ditemukan di Wi-Fi" else "Belum diperiksa", found)
                        }
                        IconButton(onClick = { onEdit(profile) }) { Icon(Icons.Default.Edit, "Edit ${profile.name}") }
                    }
                }
            }
            if (state.discovered.isNotEmpty() || state.scanCompleted) item {
                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Ditemukan di Wi-Fi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Kamera yang ditemukan pada jaringan Wi-Fi saat ini.", Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.scanCompleted && state.discovered.isEmpty()) item {
                Text("Tidak ada kamera ditemukan. Pastikan kamera menyala dan tersambung ke Wi-Fi yang sama, atau tambahkan IP manual.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.discovered, key = { "discovered-${it.deviceId}" }) { camera ->
                val saved = state.profiles.firstOrNull { it.deviceId == camera.deviceId }
                var menu by remember(camera.deviceId) { mutableStateOf(false) }
                CameraPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CameraBadge(Modifier.size(48.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(saved?.name ?: "Kamera ${camera.deviceId}", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("${camera.host}:${camera.port}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            StatusLabel("Ditemukan", true)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opsi kamera ${camera.deviceId}") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (saved == null) "Lihat detail" else "Edit kamera") },
                                    onClick = { menu = false; if (saved == null) onFound(camera) else onEdit(saved) })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (saved != null) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                            Text("Tersimpan", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
                        } else FilledTonalButton(onClick = { onFound(camera) }, modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Tambah")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditor(
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
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Detail kamera", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Gunakan alamat dan akses lokal kamera. Profil protokol dipilih dari hasil discovery.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CameraPanel {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nama kamera") }, leadingIcon = { Icon(CameraIcon, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(host, { host = it }, label = { Text("Alamat IP") }, placeholder = { Text("192.168.1.10") }, leadingIcon = { Icon(WifiIcon, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(port, { port = it }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            }
            if (protocol != "iot-lan" && (existing?.platformType ?: initialPlatform) != 2)
                OutlinedTextField(username, { username = it }, label = { Text("Username kamera") }, leadingIcon = { Icon(Icons.Default.Person, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Protokol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Otomatis", "legacy-v2" to "Legacy V2", "iot-lan" to "IoT LAN").forEach { (id, label) ->
                    val selected = protocol == id
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.selectable(selected, role = Role.RadioButton, onClick = {
                            protocol = id; if (id == "iot-lan" && port == "8800") port = "8089"
                        }).heightIn(min = 48.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onTertiaryContainer))
                            Text(label, color = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (protocol != "auto") Text("Pilih manual hanya jika generasi protokol sudah diketahui. Tidak ada percobaan format login alternatif.", style = MaterialTheme.typography.bodySmall)
            }
            CameraPanel {
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
            }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(SaveIcon, null); Spacer(Modifier.width(12.dp)); Text(if (saving) "Menyimpan…" else "Simpan kamera")
            }
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
