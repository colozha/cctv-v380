package dev.cctv.v380.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import dev.cctv.v380.control.Direction
import dev.cctv.v380.session.*
import dev.cctv.v380.protocol.*
import dev.cctv.v380.R
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePlayerScreen(model: CameraViewModel, profile: CameraProfile, state: SessionState, error: String?,
    onBack: () -> Unit, onEdit: () -> Unit, onConnect: (CharArray?) -> Unit, onDiagnostics: () -> Unit) {
    var password by remember(profile.id) { mutableStateOf("") }
    var overrideSaved by remember(profile.id) { mutableStateOf(false) }
    var surface by remember { mutableStateOf<Surface?>(null) }
    val feature by model.features.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var presetsOpen by rememberSaveable(profile.id) { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(profile.id) {
        model.prepareFeatures(profile)
        if (profile.hasSavedPassword && !model.session.isAuthenticated(profile.id)) onConnect(null)
    }
    fun leave(action: () -> Unit) {
        if (feature.busy || feature.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS) || feature.areaPhase == AreaCapturePhase.EDITING || feature.draft.isNotEmpty() || feature.managementDraft.isNotEmpty()) pendingLeave = action else action()
    }
    val capabilities by model.session.capabilities.collectAsStateWithLifecycle()
    val talkback by model.session.talkback.collectAsStateWithLifecycle()
    val talkbackAudio by model.session.talkbackAudio.collectAsStateWithLifecycle()
    val talkbackMetadataIssue by model.session.talkbackMetadataIssue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var microphoneGranted by remember { mutableStateOf(model.microphoneGranted()) }
    var microphoneDenied by remember { mutableStateOf(false) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphoneGranted = granted
        microphoneDenied = !granted
        // A permission result never starts capture; require a fresh explicit tap.
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { microphoneGranted = model.microphoneGranted() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { model.stopTalkback() }
    val busy = state is SessionState.Connecting || state is SessionState.Authenticating ||
        state is SessionState.Negotiating || state is SessionState.Reconnecting
    val streaming = state is SessionState.Streaming
    val backAction: () -> Unit = if (feature.tab == CameraTab.RECORDINGS && feature.selected != null)
        model::returnToRecordingList else onBack
    BackHandler { leave(backAction) }
    DisposableEffect(profile.id) { onDispose { password = ""; model.stopTalkback(); model.surfaceLost(surface) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { password = "" }
    LaunchedEffect(surface, state) {
        if (surface?.isValid == true && state is SessionState.Ready) model.surfaceReady(surface!!, profile.id)
    }
    Scaffold(topBar = { Column {
        TopAppBar(title = { Text(profile.name, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = { leave(backAction) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (feature.tab == CameraTab.RECORDINGS && feature.selected != null) "Kembali ke daftar rekaman" else "Kembali") } },
        actions = {
            IconButton(onClick = { leave(onDiagnostics) }) { Icon(Icons.Default.Info, "Diagnostik LAN") }
            IconButton(onClick = { leave(onEdit) }) { Icon(Icons.Default.Edit, "Edit kamera") }
        })
        PrimaryTabRow(selectedTabIndex = feature.tab.ordinal) {
            CameraTab.entries.forEach { tab -> Tab(selected = feature.tab == tab,
                onClick = { leave {
                    val transition = model.selectTab(tab)
                    if (profile.hasSavedPassword && transition != null)
                        scope.launch {
                            transition.join()
                            if (!transition.isCancelled && model.features.value.profileId == profile.id &&
                                model.features.value.tab == tab && !model.session.isAuthenticated(profile.id)) onConnect(null)
                        }
                } }, text = { Text(tab.label) }) }
        }
    } }) { insets ->
        if (feature.tab == CameraTab.RECORDINGS) {
            RecordingsScreen(model, profile, state, feature, onConnect, Modifier.padding(insets), ::leave)
            return@Scaffold
        }
        if (feature.tab == CameraTab.SETTINGS) {
            CameraSettingsScreen(model, profile, state, feature, onConnect, Modifier.padding(insets))
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val videoHeight = minOf((maxWidth - 16.dp) * 9f / 16f, maxHeight * .45f)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                // Keep the native Surface attached outside the scrolling controls and modal sheet.
                Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(videoHeight)
                    .clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                    CameraVideoSurface(videoHeight, cornerRadius = 16.dp, onReady = { value -> surface = value; model.surfaceReady(value, profile.id) },
                        onLost = { val old = surface; surface = null; model.surfaceLost(old) })
                    if (!streaming) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        if (busy) CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(28.dp))
                        else Icon(CameraIcon, null, Modifier.size(32.dp), tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .7f))
                        Spacer(Modifier.height(8.dp))
                        Text(when (state) {
                            SessionState.Connecting -> "Menghubungkan kamera…"
                            SessionState.Authenticating -> "Memverifikasi akses lokal…"
                            is SessionState.Ready -> "Kamera terautentikasi"
                            is SessionState.Negotiating -> "Menunggu video…"
                            is SessionState.Reconnecting -> stringResource(R.string.reconnecting_attempt, state.attempt)
                            is SessionState.Error -> "Video belum tersedia"
                            else -> "Belum terhubung"
                        }, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f).widthIn(max = 680.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CameraPanel {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionIcon(WifiIcon)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Koneksi lokal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatusLabel(if (streaming) "Terhubung" else if (busy) "Menghubungkan…" else "Belum terhubung", streaming)
                            }
                        }
                        (state as? SessionState.Error)?.let { ErrorNotice(it.reason.userMessage()) }
                        error?.let { ErrorNotice(it, model::dismissError) }
                        if (!streaming && !busy && (!profile.hasSavedPassword || state is SessionState.Error || error != null)) {
                            if (!profile.hasSavedPassword || overrideSaved) OutlinedTextField(password, { password = it.take(4096) },
                                label = { Text("Password lokal kamera") }, supportingText = { Text("Gunakan akses perangkat kamera, terpisah dari akun V380 dan Wi-Fi rumah.") },
                                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (profile.hasSavedPassword) TextButton(onClick = { overrideSaved = !overrideSaved; password = "" }) {
                                Text(if (overrideSaved) "Gunakan akses tersimpan" else "Masukkan password lain")
                            }
                            Button(onClick = {
                                val entered = if (profile.hasSavedPassword && !overrideSaved) null else password.toCharArray()
                                password = ""; onConnect(entered)
                            }, enabled = surface?.isValid == true, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Sambungkan")
                            }
                        }
                        if (state !is SessionState.Idle) OutlinedButton(onClick = model::disconnect,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                            Text(if (busy) "Batalkan koneksi" else "Putuskan koneksi")
                        }
                    }
                    CameraPanel {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionIcon(Icons.Default.Settings)
                            Text("Kualitas video", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            SingleChoiceSegmentedButtonRow(Modifier.width(128.dp)) {
                                Quality.entries.forEachIndexed { index, quality -> SegmentedButton(
                                    selected = (state as? SessionState.Streaming)?.quality == quality || (state as? SessionState.Negotiating)?.quality == quality,
                                    onClick = { model.changeQuality(quality) }, enabled = streaming && quality in capabilities?.qualities.orEmpty(),
                                    shape = SegmentedButtonDefaults.itemShape(index, Quality.entries.size), icon = {},
                                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimary), label = { Text(quality.name) }) }
                            }
                        }
                    }
                    TalkbackControls(talkback, when {
                        !streaming -> dev.cctv.v380.media.TalkbackError.NOT_READY
                        feature.busy || model.managementBlocked() -> dev.cctv.v380.media.TalkbackError.BUSY
                        profile.protocolProfileId == "legacy-v2" || profile.platformType == 1 -> dev.cctv.v380.media.TalkbackError.UNSUPPORTED
                        capabilities?.talkback == null -> dev.cctv.v380.media.TalkbackError.CAPABILITY_UNKNOWN
                        capabilities?.talkback != true -> dev.cctv.v380.media.TalkbackError.UNSUPPORTED
                        talkbackAudio == null -> talkbackMetadataIssue ?: dev.cctv.v380.media.TalkbackError.METADATA_MISSING
                        talkbackAudio?.supported != true -> dev.cctv.v380.media.TalkbackError.UNSUPPORTED_AUDIO
                        else -> model.session.talkbackBlock(profile.id)
                    }, microphoneGranted, microphoneDenied,
                        onStart = { model.startTalkback(profile.id) }, onStop = model::stopTalkback,
                        onPermission = { microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO) },
                        onPermissionSettings = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri())) },
                        probeEnabled = dev.cctv.v380.BuildConfig.TALKBACK_PROBE_ENABLED,
                        onProbe = { model.startTalkbackProbe(profile.id) },
                        onVoiceTest = { model.startTalkbackVoiceTest(profile.id) })
                    CameraPanel {
                        PanelHeading(Icons.Default.Settings, "Kontrol kamera")
                        val ptz = streaming && model.session.manualPtzAllowed && !model.managementBlocked() && !feature.busy && capabilities?.ptz == true
                        if (!ptz) {
                            Text(when {
                                !streaming -> "Kontrol tersedia setelah video berjalan dan kemampuan PTZ dikonfirmasi kamera."
                                capabilities?.ptz != true -> "Kamera belum mengonfirmasi dukungan PTZ."
                                feature.managementLoading == ManagementGroup.MOTOR -> "Memeriksa status motor kamera…"
                                model.managementBlocked() -> "Kontrol menunggu pemeriksaan operasi kamera yang belum selesai."
                                feature.busy -> "Tunggu operasi kamera selesai."
                                else -> {
                                    val patrol = feature.management[ManagementGroup.MOTOR]?.settings?.values?.get(CameraManagementProtocol.PATROL) as? CameraPatrol
                                    if (patrol?.enabled == true || patrol?.timerEnabled == true)
                                        "Patroli kamera aktif. Kontrol arah menunggu konfigurasi patroli tidak aktif."
                                    else "Status motor belum dapat diperiksa. Coba periksa ulang."
                                }
                            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (streaming && capabilities?.management?.patrol == true && !feature.busy)
                                OutlinedButton(onClick = { model.loadManagement(ManagementGroup.MOTOR) },
                                    enabled = !model.managementBlocked(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Periksa ulang status motor")
                                }
                        }
                        Column(Modifier.align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PtzButton(Icons.Default.KeyboardArrowUp, "Atas", Direction.UP, ptz && capabilities?.verticalPtz == true, model)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                PtzButton(Icons.Default.KeyboardArrowLeft, "Kiri", Direction.LEFT, ptz && capabilities?.horizontalPtz == true, model)
                                val scope = rememberCoroutineScope()
                                OutlinedButton(onClick = { scope.launch { model.stopPtz() } }, enabled = streaming && capabilities?.ptz == true,
                                    shape = CircleShape, contentPadding = PaddingValues(8.dp), modifier = Modifier.size(72.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(StopIcon, null, Modifier.size(20.dp)); Text("Stop", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                PtzButton(Icons.Default.KeyboardArrowRight, "Kanan", Direction.RIGHT, ptz && capabilities?.horizontalPtz == true, model)
                            }
                            PtzButton(Icons.Default.KeyboardArrowDown, "Bawah", Direction.DOWN, ptz && capabilities?.verticalPtz == true, model)
                        }
                        feature.managementErrors[ManagementGroup.MOTOR]?.let { ErrorNotice(it) }
                    }
                    Surface(onClick = { presetsOpen = true }, shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionIcon(BookmarkIcon)
                            Column(Modifier.weight(1f)) {
                                Text("Preset & patroli", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val presets = feature.management[ManagementGroup.MOTOR]?.settings?.values?.get(CameraManagementProtocol.PRESETS) as? CameraPresets
                                Text(if (presets != null && capabilities?.management?.presets != null)
                                    stringResource(R.string.saved_presets, presets.used.size, capabilities?.management?.presets ?: 0)
                                    else stringResource(R.string.preset_position_route),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, null)
                        }
                    }
                    feature.managementNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    if (presetsOpen) {
        val protected by rememberUpdatedState(feature.busy || feature.managementDraft.isNotEmpty() || feature.draft.isNotEmpty())
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { value ->
            if (value == SheetValue.Hidden && protected) {
                pendingLeave = { presetsOpen = false }
                false
            } else true
        })
        ModalBottomSheet(onDismissRequest = { leave { presetsOpen = false } }, sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .7f).dp)
                .verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(BookmarkIcon, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Preset & patroli", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { leave { presetsOpen = false } }) { Icon(Icons.Default.Close, stringResource(R.string.preset_patrol_close)) }
                }
                CameraManagementGroup(model, profile, feature, ManagementGroup.MOTOR, live = true)
                feature.managementNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    if (pendingLeave != null) AlertDialog(onDismissRequest = { pendingLeave = null },
        title = { Text(if ((feature.draft.isNotEmpty() || feature.managementDraft.isNotEmpty())) "Buang perubahan?" else "Tinggalkan pekerjaan ini?") },
        text = { Text(when {
            feature.pendingAp != null -> "Pemeriksaan hotspot akan berhenti. Perubahan yang mungkin sudah diterapkan tidak dibatalkan; perintah tidak akan dikirim ulang."
            feature.pendingRestart != null -> "Pemeriksaan restart akan berhenti. Kamera mungkin sudah menerima perintah; tidak ada pengiriman ulang."
            feature.pendingCalibration != null -> "Pemeriksaan kalibrasi akan berhenti. Kamera mungkin masih bergerak; perintah tidak dikirim ulang."
            feature.pendingFormat != null -> "Pemeriksaan status akan berhenti. Format yang sudah dikirim tetap berjalan di kamera dan tidak akan dikirim ulang."
            feature.areaPhase == AreaCapturePhase.EDITING -> "Perubahan area yang belum digunakan akan dibuang. Gambar referensi akan dilepas."
            (feature.draft.isNotEmpty() || feature.managementDraft.isNotEmpty()) -> "Perubahan pengaturan yang belum disimpan akan dibuang."
            else -> "Pekerjaan yang sedang berjalan akan dibatalkan. File yang belum lengkap tidak dinyatakan tersimpan."
        }) },
        confirmButton = { TextButton(onClick = { val action = pendingLeave; pendingLeave = null; model.cancelFeature(); action?.invoke() }) { Text("Lanjutkan keluar") } },
        dismissButton = { TextButton(onClick = { pendingLeave = null }) { Text("Tetap di sini") } })
}

@Composable
private fun PtzButton(icon: ImageVector, name: String, direction: Direction, enabled: Boolean, model: CameraViewModel) {
    val scope = rememberCoroutineScope()
    OutlinedIconButton(onClick = {
        scope.launch { try { model.move(direction); delay(200) } finally { model.stopPtz() } }
    }, enabled = enabled, modifier = Modifier.size(56.dp).pointerInput(enabled, direction) {
        if (enabled) detectTapGestures(onPress = {
            coroutineScope {
                var repeat: Job? = null
                try {
                    model.move(direction)
                    repeat = launch {
                        while (isActive) {
                            delay(200)
                            withContext(NonCancellable) { withTimeoutOrNull(500) { model.renewPtz() } }
                        }
                    }
                    tryAwaitRelease()
                } finally {
                    withContext(NonCancellable) { repeat?.cancelAndJoin(); model.stopPtz() }
                }
            }
        })
    }) { Icon(icon, name) }
}
