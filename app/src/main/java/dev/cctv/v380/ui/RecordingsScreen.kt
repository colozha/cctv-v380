package dev.cctv.v380.ui

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLocale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import dev.cctv.v380.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, onConnect: (CharArray?) -> Unit, modifier: Modifier = Modifier,
    onLeave: (() -> Unit) -> Unit = { it() }) {
    var dateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val position by model.session.recordingPosition.collectAsStateWithLifecycle()
    val recordingFilenamePrefix = stringResource(R.string.recording_filename_prefix)
    val selected = feature.selected
    val authenticated = model.session.isAuthenticated(profile.id)
    val rows = remember(feature.recordings, feature.recordingOrder) { feature.recordingOrder.sort(feature.recordings) }
    val exportPending = feature.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4"), model::completeRecordingExport)
    fun export(recording: RecordingSegment) {
        scope.launch {
            try {
                if (model.beginRecordingExport(recording)) {
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .format(Instant.ofEpochMilli(recording.startMs).atZone(ZoneId.systemDefault()))
                    save.launch("$recordingFilenamePrefix$stamp.mp4")
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { model.completeRecordingExport(null); throw cancelled }
            catch (_: Exception) { model.completeRecordingExport(null); model.message("Pemilih lokasi penyimpanan tidak dapat dibuka. Coba lagi.") }
        }
    }
    LaunchedEffect(profile.id, authenticated, feature.date, feature.eventsOnly, feature.tab, selected == null, exportPending) {
        if (authenticated && feature.tab == CameraTab.RECORDINGS && selected == null &&
            !feature.recordingsLoaded && !feature.busy && !exportPending) model.loadRecordings()
    }

    val dark = LocalAppDarkTheme.current
    val scheme = MaterialTheme.colorScheme.copy(
        primary = if (dark) Color(0xFF82DEB8) else Color(0xFF185C43),
        onPrimary = if (dark) Color(0xFF08291D) else Color.White,
        surface = if (dark) Color(0xFF080E10) else Color(0xFFF5F8F6),
        surfaceContainer = if (dark) Color(0xFF151D20) else Color.White,
        onSurface = if (dark) Color(0xFFF0F3F5) else Color(0xFF17231E),
        onSurfaceVariant = if (dark) Color(0xFFB2BEC7) else Color(0xFF4C5E55),
        outline = if (dark) Color(0xFF445259) else Color(0xFF6C7D74),
        outlineVariant = if (dark) Color(0xFF344249) else Color(0xFFD9E2DC),
        primaryContainer = if (dark) Color(0xFF213F34) else Color(0xFFD0EFDF),
        onPrimaryContainer = if (dark) Color(0xFFC0F2D8) else Color(0xFF123526))
    MaterialTheme(colorScheme = scheme) {
        BoxWithConstraints(modifier.fillMaxSize().background(scheme.surface)) {
            val videoHeight = minOf((maxWidth - 32.dp) * 9f / 16f, maxHeight * .5f)
            if (selected != null) {
                RecordingPlayer(model, profile, session, feature, position, videoHeight, onConnect,
                    ::export, { onLeave { model.setRecordingDate(it) } }, { dateDialog = true })
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!authenticated) item("access") { RecordingPanel { CameraAccess(model, profile, session, onConnect) } }
                    item("storage") { RecordingStorageStatus(feature.recordingStorage, feature.storageLoading, feature.storageError) }
                    if (feature.error != null || feature.notice != null || feature.busy || exportPending || feature.download == DownloadPhase.CANCELLED)
                        item("feedback") { RecordingFeedback(feature, model::cancelFeature) }
                    if (feature.pendingAp != null) item { Text("Ada status hotspot tertunda. Buka Pengaturan untuk memeriksanya sebelum memakai rekaman.") }
                    item("filters") {
                    RecordingPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rekaman di kartu SD", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = model::loadRecordings, enabled = authenticated && !feature.busy && !exportPending) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.recording_refresh))
                            }
                            RecordingOrderMenu(feature.recordingOrder, model::setRecordingOrder)
                        }
                        Text("Pilih tanggal untuk melihat rekaman kamera.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        RecordingDateControl(feature.date, { onLeave { model.setRecordingDate(it) } }, { dateDialog = true }, feature.pendingAp == null)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(!feature.eventsOnly, { if (feature.eventsOnly) onLeave { model.setEventsOnly(false) } }, label = { Text("Semua") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50), colors = recordingChipColors(), enabled = feature.pendingAp == null)
                            FilterChip(feature.eventsOnly, { if (!feature.eventsOnly) onLeave { model.setEventsOnly(true) } }, label = { Text("Peristiwa") },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(50), colors = recordingChipColors(), enabled = feature.pendingAp == null)
                        }
                        Text(stringResource(R.string.recording_timezone, ZoneId.systemDefault().id), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                                    }
                    if (feature.recordingsLoaded && rows.isEmpty()) item("empty") {
                        RecordingPanel { Text("Tidak ada rekaman pada tanggal ini.", style = MaterialTheme.typography.titleMedium)
                            Text("Pilih tanggal lain atau periksa pengaturan perekaman kamera.", color = scheme.onSurfaceVariant) }
                    }
                    items(rows, key = { it.id }) { recording ->
                        RecordingCard(recording, authenticated && !feature.busy && !exportPending && feature.pendingAp == null,
                            { model.requestPlayback(recording) }, { export(recording) })
                    }
                }
            }
        }
        if (dateDialog) {
            val picker = rememberDatePickerState(initialSelectedDateMillis = feature.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { day -> onLeave { model.setRecordingDate(Instant.ofEpochMilli(day).atZone(ZoneOffset.UTC).toLocalDate()) } }
                    dateDialog = false
                }) { Text("Pilih tanggal") }
            }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Batal") } }) { DatePicker(picker) }
        }
    }
}

@Composable
private fun RecordingPlayer(model: CameraViewModel, profile: CameraProfile, session: SessionState,
    feature: CameraFeatureState, position: Long, videoHeight: androidx.compose.ui.unit.Dp,
    onConnect: (CharArray?) -> Unit, onExport: (RecordingSegment) -> Unit,
    onDateChange: (LocalDate) -> Unit, onPicker: () -> Unit) {
    val selected = feature.selected ?: return
    val positionDescription = stringResource(R.string.recording_position)
    val authenticated = model.session.isAuthenticated(profile.id)
    val exportPending = feature.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS)
    val scheme = MaterialTheme.colorScheme
    var renderSurface by remember { mutableStateOf<Surface?>(null) }
    LaunchedEffect(renderSurface, feature.playRequest) {
        if (renderSurface?.isValid == true && !feature.paused && feature.tab == CameraTab.RECORDINGS)
            model.startPlayback(renderSurface!!)
    }
    // Keep the Surface attached while the small player form scrolls; only the recording list is lazy.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!authenticated) RecordingPanel { CameraAccess(model, profile, session, onConnect) }
        RecordingStorageStatus(feature.recordingStorage, feature.storageLoading, feature.storageError)

                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                        CameraVideoSurface(videoHeight, onReady = { renderSurface = it }, onLost = {
                            renderSurface = null
                            if (model.features.value.tab == CameraTab.RECORDINGS) model.pausePlayback()
                        }, cornerRadius = 20.dp)
                        if (session !is SessionState.PlayingRecording) Column(Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session is SessionState.LoadingRecording) CircularProgressIndicator(Modifier.size(32.dp), color = scheme.primary)
                            else Icon(if (feature.error != null || session is SessionState.Error) CameraIcon else Icons.Default.PlayArrow, null,
                                Modifier.size(40.dp), tint = Color(0xFFBBC6CB))
                            Text(when {
                                session is SessionState.LoadingRecording -> "Menunggu video rekaman…"
                                feature.error != null || session is SessionState.Error -> "Video belum tersedia"
                                session is SessionState.RecordingEnded -> "Rekaman selesai"
                                else -> "Rekaman dijeda"
                            }, color = Color.White,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }

        if (feature.error != null || feature.notice != null || feature.busy || exportPending || feature.download == DownloadPhase.CANCELLED)
            RecordingFeedback(feature, model::cancelFeature)
        RecordingDateControl(feature.date, onDateChange, onPicker, feature.pendingAp == null)

                    RecordingPanel {
                        Text(recordingTimes(selected), style = MaterialTheme.typography.titleLarge)
                        Text(recordingDates(selected), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Text(when {
                            session is SessionState.LoadingRecording -> "Menunggu video rekaman…"
                            session is SessionState.RecordingEnded -> "Rekaman selesai"
                            feature.paused -> "Dijeda"
                            session is SessionState.PlayingRecording -> "Memutar rekaman"
                            else -> "Rekaman dipilih"
                        }, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = scheme.primary, style = MaterialTheme.typography.bodyMedium)
                        var dragging by remember(selected.id) { mutableStateOf(false) }
                        var seek by remember(selected.id) { mutableFloatStateOf(0f) }
                        LaunchedEffect(position, selected.id) {
                            if (!dragging) seek = ((position - selected.startMs).toDouble() / selected.durationMs).toFloat().coerceIn(0f, 1f)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(recordingTime(selected.startMs + (seek * selected.durationMs).toLong()), style = MaterialTheme.typography.bodySmall)
                            Text(recordingTime(selected.endMs), style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(seek, { dragging = true; seek = it }, onValueChangeFinished = {
                            dragging = false; model.requestPlayback(selected, selected.startMs + (seek * (selected.durationMs - 1)).toLong())
                        }, enabled = authenticated && !feature.busy && !exportPending && feature.pendingAp == null,
                            modifier = Modifier.semantics { contentDescription = positionDescription })
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                if (session is SessionState.PlayingRecording) model.pausePlayback()
                                else model.requestPlayback(selected, if (session is SessionState.RecordingEnded) selected.startMs
                                    else position.takeIf { it in selected.startMs until selected.endMs } ?: selected.startMs)
                            }, enabled = authenticated && !feature.busy && !exportPending && feature.pendingAp == null,
                                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) {
                                Icon(if (session is SessionState.PlayingRecording) RecordingPauseIcon else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp)); Text(if (session is SessionState.PlayingRecording) "Jeda" else "Putar")
                            }
                            OutlinedButton(onClick = model::pausePlayback, enabled = !feature.busy && !exportPending && feature.pendingAp == null,
                                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) {
                                Icon(RecordingStopIcon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Hentikan")
                            }
                        }
                        OutlinedButton(onClick = { onExport(selected) }, enabled = !feature.busy && !exportPending && feature.pendingAp == null && feature.pendingFormat == null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Icon(RecordingDownloadIcon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Simpan rekaman")
                        }
                        Text("Menyimpan satu rekaman penuh tanpa audio.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }

    }
}

@Composable
private fun RecordingFeedback(feature: CameraFeatureState, onCancel: () -> Unit) {
    val pending = feature.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        feature.error?.let { error ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(error, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        FeatureFeedback(feature.copy(error = null))
        if (feature.busy || pending) TextButton(onClick = onCancel) { Text("Batalkan") }
    }
}

@Composable
private fun RecordingPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun recordingChipColors() = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary)

@Composable
private fun RecordingDateControl(date: LocalDate, onChange: (LocalDate) -> Unit, onPicker: () -> Unit, enabled: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onChange(date.minusDays(1)) }, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.date_previous)) }
            TextButton(onPicker, enabled = enabled, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)) {
                Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(8.dp))
                Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", LocalLocale.current.platformLocale)), color = MaterialTheme.colorScheme.onSurface)
            }
            IconButton({ onChange(date.plusDays(1)) }, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.date_next)) }
        }
    }
}

@Composable
fun RecordingOrderMenu(order: RecordingOrder, onChange: (RecordingOrder) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = true }, enabled = enabled) { Icon(Icons.Default.MoreVert, stringResource(R.string.recording_order, localizedCopy(order.label))) }
        DropdownMenu(expanded, { expanded = false }) { RecordingOrder.entries.forEach { option ->
            DropdownMenuItem(text = { Text(option.label) }, onClick = { onChange(option); expanded = false },
                trailingIcon = { if (order == option) Icon(Icons.Default.Check, null) })
        } }
    }
}

@Composable
private fun RecordingCard(recording: RecordingSegment, enabled: Boolean, onPlay: () -> Unit, onSave: () -> Unit) {
    var menu by remember(recording.id) { mutableStateOf(false) }
    Card(onClick = onPlay, enabled = enabled, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recordingTimes(recording), style = MaterialTheme.typography.titleMedium)
                Text(recordingDates(recording), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.recording_duration_type, recording.durationMs / 1000,
                    stringResource(if (recording.kind == 2) R.string.recording_event_kind else R.string.recording_video_kind)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.recording_options, recordingTime(recording.startMs)))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Putar") }, onClick = { menu = false; onPlay() }, enabled = enabled)
                    DropdownMenuItem(text = { Text("Simpan rekaman") }, onClick = { menu = false; onSave() }, enabled = enabled)
                    DropdownMenuItem(text = { Text(stringResource(R.string.recording_delete_unavailable)) }, onClick = {}, enabled = false)
                }
            }
        }
    }
}

@Composable
fun RecordingStorageStatus(storage: CameraSettings?, loading: Boolean, error: String?) {
    val status = storage?.int(P.SD_STATUS)
    val capacity = storage?.takeUnless { status == 5 }?.let(P::capacity)
    val warning = storage?.let(::recordingStorageWarning)
    RecordingPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(RecordingSdIcon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when (status) {
                    1, 3 -> "Kartu SD terpasang"
                    2 -> "Kartu SD bermasalah"
                    4 -> "Kartu SD sedang diformat"
                    5 -> "Tidak ada kartu SD"
                    6 -> "Kartu SD sedang dilepas"
                    7 -> "Kartu SD sedang dipasang"
                    8 -> "Kartu SD perlu diformat"
                    else -> "Kartu SD"
                }, style = MaterialTheme.typography.titleMedium)
                Text(when {
                    loading -> "Memeriksa kapasitas…"
                    error != null -> error
                    capacity != null -> stringResource(R.string.used_space_percent, capacity.usedPercent)
                    else -> "Kapasitas kartu SD belum tersedia."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (capacity != null) Text("${capacity.usedPercent}%", style = MaterialTheme.typography.titleMedium)
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (capacity != null) LinearProgressIndicator(progress = { (1.0 - capacity.remaining.toDouble() / capacity.total).toFloat() },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (warning != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        if (warning != null) Text(warning, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

private val RecordingStopIcon = ImageVector.Builder("Hentikan", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(5f, 5f); horizontalLineTo(19f); verticalLineTo(19f); horizontalLineTo(5f); close() }
}.build()
private val RecordingPauseIcon = ImageVector.Builder("Jeda", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(6f, 4f); horizontalLineTo(10f); verticalLineTo(20f); horizontalLineTo(6f); close()
        moveTo(14f, 4f); horizontalLineTo(18f); verticalLineTo(20f); horizontalLineTo(14f); close() }
}.build()
private val RecordingDownloadIcon = ImageVector.Builder("Simpan", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(10f, 3f); horizontalLineTo(14f); verticalLineTo(12f); horizontalLineTo(18f)
        lineTo(12f, 18f); lineTo(6f, 12f); horizontalLineTo(10f); close()
        moveTo(4f, 18f); horizontalLineTo(6f); verticalLineTo(20f); horizontalLineTo(18f); verticalLineTo(18f)
        horizontalLineTo(20f); verticalLineTo(22f); horizontalLineTo(4f); close() }
}.build()
internal val RecordingSdIcon = ImageVector.Builder("Kartu SD", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
        moveTo(7f, 2f); lineTo(3f, 6f); verticalLineTo(22f); horizontalLineTo(21f); verticalLineTo(2f); close()
        moveTo(8f, 4f); horizontalLineTo(10f); verticalLineTo(9f); horizontalLineTo(8f); close()
        moveTo(12f, 4f); horizontalLineTo(14f); verticalLineTo(9f); horizontalLineTo(12f); close()
        moveTo(16f, 4f); horizontalLineTo(18f); verticalLineTo(9f); horizontalLineTo(16f); close()
    }
}.build()

@Composable
fun CameraVideoSurface(height: androidx.compose.ui.unit.Dp, onReady: (Surface) -> Unit, onLost: () -> Unit,
    aspectRatio: Float = 16f / 9f, onViewReady: (SurfaceView) -> Unit = {}, cornerRadius: androidx.compose.ui.unit.Dp = 0.dp) {
    val ready by rememberUpdatedState(onReady)
    val lost by rememberUpdatedState(onLost)
    val viewReady by rememberUpdatedState(onViewReady)
    Box(Modifier.fillMaxWidth().height(height).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { context -> SurfaceView(context).apply {
            if (cornerRadius.value > 0) {
                val radius = cornerRadius.value * resources.displayMetrics.density
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
            }
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
        Text(if (feature.download == DownloadPhase.SAVING) stringResource(R.string.saving_file)
            else stringResource(R.string.download_progress, feature.downloadBytes / 1024))
    }
    if (feature.download == DownloadPhase.CANCELLED) Text("Penyalinan dibatalkan.")
}

private fun recordingTime(ms: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
