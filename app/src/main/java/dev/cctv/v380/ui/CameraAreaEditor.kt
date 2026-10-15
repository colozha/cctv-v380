package dev.cctv.v380.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.R
import kotlin.math.roundToInt

@Composable
fun CameraAreaControls(model: CameraViewModel, profile: CameraProfile, feature: CameraFeatureState, area: CameraAlarmArea, enabled: Boolean) {
    val geometry by model.session.videoGeometry.collectAsState()
    val reference by model.session.areaReference.collectAsState()
    val capabilities by model.session.capabilities.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Area deteksi", style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.selected_detection_cells, area.rows, area.columns, area.cells.count { it == 1 }))
        feature.areaError?.let { ErrorNotice(it) }
        when (feature.areaPhase) {
            AreaCapturePhase.CAPTURING -> {
                val requestId = feature.areaRequestId
                BackHandler { model.cancelAreaCapture(requestId) }
                Text("Menyiapkan gambar kamera. Preview berhenti setelah gambar diambil.")
                AreaCaptureFeedback(true, null)
                key(requestId) {
                    val ratio = geometry?.takeIf { it.flat }?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val height = minOf(maxWidth / ratio, 240.dp)
                        CameraVideoSurface(height, onReady = {}, onLost = { model.cancelAreaCapture(requestId) }, aspectRatio = ratio,
                            onViewReady = { model.areaSurfaceReady(requestId, it) })
                    }
                }
                TextButton(onClick = { model.cancelAreaCapture(requestId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Batalkan pengambilan gambar") }
            }
            AreaCapturePhase.EDITING -> {
                BackHandler { model.cancelAreaEdit() }
                feature.areaImage?.let { image ->
                    CameraAreaEditor(image.bitmap, area, enabled && reference === image.reference && model.areaDraftReady(),
                        onUse = { model.useArea(image.reference, it) }, onCancel = model::cancelAreaEdit)
                }
            }
            else -> {
                OutlinedButton(onClick = model::requestAreaReference, enabled = enabled && model.session.canCaptureArea(profile.id),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ambil gambar referensi") }
                if (feature.areaPhase == AreaCapturePhase.STAGED && model.areaDraftReady())
                    Text("Area masih berupa draf. Simpan alarm untuk menerapkannya ke kamera.", style = MaterialTheme.typography.bodySmall)
                if (capabilities?.management?.flatArea != true)
                    Text("Editor memerlukan metadata satu tampilan datar. Fisheye, gambar gabungan, dan pemetaan yang belum pasti hanya dapat dibaca.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AreaCaptureFeedback(loading: Boolean, error: String?) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("area-loading"))
    error?.let { ErrorNotice(it) }
}

/** All changes stay local until Use; image coordinates always run left-to-right from the top. */
@Composable
fun CameraAreaEditor(bitmap: Bitmap, area: CameraAlarmArea, enabled: Boolean, onUse: (CameraAlarmArea) -> Unit, onCancel: () -> Unit) {
    require(CameraAreaProtocol.valid(area))
    val imageDescription = stringResource(R.string.area_reference_accessibility, area.cells.count { it == 1 })
    var cells by remember(bitmap, area.source) { mutableStateOf(area.cells) }
    var row by remember(bitmap) { mutableIntStateOf(0) }
    var column by remember(bitmap) { mutableIntStateOf(0) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val selected = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ketuk sel pada gambar atau pilih baris dan kolom. Gambar ini adalah referensi, bukan tayangan langsung.", style = MaterialTheme.typography.bodyMedium)
        Text("Jika arah kamera berubah, ambil gambar referensi ulang.", style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).testTag("area-grid")
            .semantics { contentDescription = imageDescription }
            .pointerInput(enabled, bitmap, area.rows, area.columns) {
                if (enabled) detectTapGestures { point ->
                    CameraAreaProtocol.cellAt(point.x, point.y, size.width.toFloat(), size.height.toFloat(), area.rows, area.columns)?.let { index ->
                        row = index / area.columns; column = index % area.columns
                        cells = cells.toMutableList().also { it[index] = 1 - it[index] }
                    }
                }
            }) {
            drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val w = size.width / area.columns; val h = size.height / area.rows
            cells.forEachIndexed { index, value ->
                if (value == 1) {
                    val origin = Offset(index % area.columns * w, index / area.columns * h)
                    drawRect(selected, origin, Size(w, h))
                    val radius = minOf(4.dp.toPx(), w / 8, h / 8)
                    val center = origin + Offset(w - radius * 2, radius * 2)
                    drawCircle(Color.Black, radius + 1.dp.toPx(), center)
                    drawCircle(Color.White, radius, center)
                }
            }
            for (stroke in listOf(3.dp.toPx() to Color.Black, 1.dp.toPx() to Color.White)) {
                for (c in 0..area.columns) drawLine(stroke.second, Offset(c * w, 0f), Offset(c * w, size.height), stroke.first)
                for (r in 0..area.rows) drawLine(stroke.second, Offset(0f, r * h), Offset(size.width, r * h), stroke.first)
            }
            drawRect(Color.Black, Offset(column * w, row * h), Size(w, h), style = Stroke(5.dp.toPx()))
            drawRect(Color.White, Offset(column * w, row * h), Size(w, h), style = Stroke(2.dp.toPx()))
        }
        Text(stringResource(R.string.selected_cells_count, cells.count { it == 1 }, cells.size), modifier = Modifier.testTag("area-count"))
        AreaCoordinateChoice("Baris", row, area.rows, enabled) { row = it }
        AreaCoordinateChoice("Kolom", column, area.columns, enabled) { column = it }
        val index = row * area.columns + column
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(cells[index] == 1, enabled = enabled, role = Role.Switch,
            onValueChange = { checked -> cells = cells.toMutableList().also { it[index] = if (checked) 1 else 0 } }), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.choose_cell, row + 1, column + 1), Modifier.weight(1f))
            Switch(cells[index] == 1, onCheckedChange = null, enabled = enabled)
        }
        OutlinedButton(onClick = { cells = List(cells.size) { 1 } }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Pilih semua") }
        OutlinedButton(onClick = { cells = List(cells.size) { 0 } }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kosongkan pilihan") }
        Button(onClick = { onUse(area.copy(cells = cells)) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Gunakan area") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Batal") }
    }
}

@Composable
private fun AreaCoordinateChoice(label: String, value: Int, count: Int, enabled: Boolean, change: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.label_number, localizedCopy(label), value + 1)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            repeat(count) { index -> DropdownMenuItem(text = { Text(stringResource(R.string.label_number, localizedCopy(label), index + 1)) },
                onClick = { change(index); open = false }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
    }
}
