package dev.cctv.v380.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.session.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, onConnect: (CharArray?) -> Unit, modifier: Modifier = Modifier) {
    var dateDialog by remember { mutableStateOf(false) }
    var videoOnly by rememberSaveable(profile.id) { mutableStateOf(false) }
    var renderSurface by remember { mutableStateOf<Surface?>(null) }
    var exportSelection by remember { mutableStateOf<RecordingSegment?>(null) }
    val position by model.session.recordingPosition.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        val selected = exportSelection
        if (uri != null && selected != null) model.exportRecording(uri, selected)
        exportSelection = null
    }
    LaunchedEffect(renderSurface, feature.playRequest) {
        if (renderSurface?.isValid == true && feature.selected != null && !feature.paused && feature.tab == CameraTab.RECORDINGS)
            model.startPlayback(renderSurface!!)
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val videoHeight = minOf(maxWidth * 9f / 16f, maxHeight * .5f)
        Column(Modifier.fillMaxSize()) {
            val selected = feature.selected
            if (selected != null) {
                CameraVideoSurface(videoHeight, onReady = { renderSurface = it }, onLost = {
                    renderSurface = null
                    if (model.features.value.tab == CameraTab.RECORDINGS) model.pausePlayback()
                })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { CameraAccess(model, profile, session, onConnect) }
                item { FeatureFeedback(feature) }
                if (feature.pendingAp != null) item { Text("Ada status hotspot tertunda. Buka Pengaturan untuk memeriksa hasilnya sebelum memakai rekaman.") }
                if (feature.busy) item { TextButton(onClick = model::cancelFeature) { Text("Batalkan") } }
                if (selected != null) item {
                    Text(recordingTime(selected.startMs) + " – " + recordingTime(selected.endMs), style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        session is SessionState.LoadingRecording -> "Menunggu video rekaman…"
                        session is SessionState.RecordingEnded -> "Rekaman selesai"
                        feature.paused -> "Dijeda"
                        session is SessionState.PlayingRecording -> "Memutar rekaman"
                        else -> "Rekaman dipilih"
                    }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            if (session is SessionState.PlayingRecording) model.pausePlayback()
                            else model.requestPlayback(selected, if (session is SessionState.RecordingEnded) selected.startMs
                                else position.takeIf { it in selected.startMs until selected.endMs } ?: selected.startMs)
                        }, enabled = !feature.busy && feature.pendingAp == null && model.session.isAuthenticated(profile.id)) {
                            Text(if (session is SessionState.PlayingRecording) "Jeda" else "Putar")
                        }
                        OutlinedButton(onClick = model::pausePlayback, enabled = !feature.busy && feature.pendingAp == null) { Text("Hentikan") }
                    }
                    var dragging by remember(selected.id) { mutableStateOf(false) }
                    var seek by remember(selected.id) { mutableFloatStateOf(
                        ((position - selected.startMs).toDouble() / selected.durationMs).toFloat().coerceIn(0f, 1f)) }
                    LaunchedEffect(position) { if (!dragging) seek = ((position - selected.startMs).toDouble() / selected.durationMs).toFloat().coerceIn(0f, 1f) }
                    Slider(value = seek, onValueChange = { dragging = true; seek = it },
                        onValueChangeFinished = { dragging = false; model.requestPlayback(selected, selected.startMs + (seek * (selected.durationMs - 1)).toLong()) },
                        enabled = !feature.busy && feature.pendingAp == null && model.session.isAuthenticated(profile.id),
                        modifier = Modifier.semantics { contentDescription = "Posisi rekaman ${recordingTime((selected.startMs + seek * selected.durationMs).toLong())}" })
                    Text(recordingTime((selected.startMs + seek * selected.durationMs).toLong()), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(videoOnly, enabled = !feature.busy && feature.pendingAp == null,
                        role = Role.Checkbox, onValueChange = { videoOnly = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = videoOnly, onCheckedChange = null, enabled = !feature.busy && feature.pendingAp == null)
                        Text("Simpan video tanpa audio", modifier = Modifier.weight(1f))
                    }
                    Text("Penyalinan saat ini menyimpan satu rekaman penuh. Audio tidak ikut disimpan.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        model.pausePlayback(); exportSelection = selected
                        save.launch("Rekaman-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(Instant.ofEpochMilli(selected.startMs).atZone(ZoneId.systemDefault()))}.mp4")
                    }, enabled = videoOnly && !feature.busy && feature.pendingAp == null && feature.pendingFormat == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Simpan rekaman") }
                    HorizontalDivider()
                }
                item {
                    Text("Rekaman di kartu SD", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { dateDialog = true }, enabled = !feature.busy && feature.pendingAp == null) {
                        Text(feature.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"))))
                    }
                    Text("Waktu ${ZoneId.systemDefault().id}. Pastikan zona waktu kamera sesuai.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = !feature.eventsOnly, onClick = { model.setEventsOnly(false) }, label = { Text("Semua") }, enabled = !feature.busy && feature.pendingAp == null)
                        FilterChip(selected = feature.eventsOnly, onClick = { model.setEventsOnly(true) }, label = { Text("Peristiwa") }, enabled = !feature.busy && feature.pendingAp == null)
                    }
                    Button(onClick = model::loadRecordings, enabled = !feature.busy && feature.pendingAp == null && model.session.isAuthenticated(profile.id),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Muat rekaman") }
                }
                if (feature.recordingsLoaded && feature.recordings.isEmpty()) item {
                    Text("Tidak ada rekaman pada tanggal ini. Pilih tanggal lain atau periksa pengaturan perekaman kamera.")
                }
                items(feature.recordings, key = { it.id }) { recording ->
                    TextButton(onClick = { model.requestPlayback(recording) }, enabled = !feature.busy && feature.pendingAp == null && model.session.isAuthenticated(profile.id),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(recordingTime(recording.startMs) + " – " + recordingTime(recording.endMs), style = MaterialTheme.typography.titleMedium)
                            Text("${recording.durationMs / 1000} detik · ${if (recording.kind == 2) "Peristiwa" else "Rekaman"}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Putar")
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = feature.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { model.setRecordingDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                dateDialog = false
            }) { Text("Pilih tanggal") }
        }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Batal") } }) { DatePicker(state = picker) }
    }
}

@Composable
fun CameraVideoSurface(height: androidx.compose.ui.unit.Dp, onReady: (Surface) -> Unit, onLost: () -> Unit,
    aspectRatio: Float = 16f / 9f, onViewReady: (SurfaceView) -> Unit = {}) {
    val ready by rememberUpdatedState(onReady)
    val lost by rememberUpdatedState(onLost)
    val viewReady by rememberUpdatedState(onViewReady)
    Box(Modifier.fillMaxWidth().height(height).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { context -> SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) { ready(holder.surface); viewReady(this@apply) }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) { lost() }
            })
        } }, modifier = Modifier.width(height * aspectRatio).fillMaxHeight())
    }
}

@Composable
fun CameraAccess(model: CameraViewModel, profile: CameraProfile, state: SessionState, onConnect: (CharArray?) -> Unit) {
    val ui by model.ui.collectAsStateWithLifecycle()
    var password by remember(profile.id) { mutableStateOf("") }
    var overrideSaved by remember(profile.id) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { password = "" }
    DisposableEffect(profile.id) { onDispose { password = "" } }
    val connecting = state is SessionState.Connecting || state is SessionState.Authenticating || state is SessionState.Reconnecting
    if (model.session.isAuthenticated(profile.id) && !connecting) {
        Text("Akses kamera tersambung", style = MaterialTheme.typography.titleMedium)
    } else {
        Text("Akses lokal kamera", style = MaterialTheme.typography.titleMedium)
        Text("Sambungkan kamera untuk membuka rekaman dan pengaturan.", style = MaterialTheme.typography.bodyMedium)
        if (!profile.hasSavedPassword || overrideSaved) OutlinedTextField(value = password,
            onValueChange = { password = it.take(4096) }, label = { Text("Password lokal kamera") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, enabled = !connecting,
            modifier = Modifier.fillMaxWidth())
        if (profile.hasSavedPassword) TextButton(onClick = { overrideSaved = !overrideSaved; password = "" }, enabled = !connecting) {
            Text(if (overrideSaved) "Gunakan akses tersimpan" else "Masukkan password lain")
        }
        Button(onClick = {
            val entered = if (profile.hasSavedPassword && !overrideSaved) null else password.toCharArray()
            password = ""; onConnect(entered)
        }, enabled = !connecting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (connecting) "Menyambungkan…" else "Sambungkan kamera")
        }
    }
    if (state is SessionState.Error) ErrorNotice(state.reason.userMessage())
    else ui.error?.let { ErrorNotice(it) }
}

@Composable
fun FeatureFeedback(feature: CameraFeatureState, showProgress: Boolean = true) {
    feature.error?.let { ErrorNotice(it) }
    feature.notice?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium) }
    if (feature.busy && showProgress) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (feature.download in setOf(DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) {
        Text(if (feature.download == DownloadPhase.SAVING) "Menyimpan file…" else "Mengunduh rekaman · ${feature.downloadBytes / 1024} KB diterima")
    }
    if (feature.download == DownloadPhase.CANCELLED) Text("Penyalinan dibatalkan.")
}

private fun recordingTime(ms: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
