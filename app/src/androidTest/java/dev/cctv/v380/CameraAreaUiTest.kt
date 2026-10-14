package dev.cctv.v380

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.media.VideoFrameCapture
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.CameraSettings
import dev.cctv.v380.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic pixels/forms only. No camera, socket, session, vendor APK or image export. */
class CameraAreaUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun area() = CameraAlarmArea(2, 3, List(6) { 0 }, "{}")
    private fun bitmap(): Bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); val paint = Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA).forEachIndexed { i, color ->
            paint.color = color; canvas.drawRect(i % 3 * 100f, i / 3 * 100f, (i % 3 + 1) * 100f, (i / 3 + 1) * 100f, paint)
        }
    }

    @Test fun gridTapsAndAccessibleCoordinatesStayDraftUntilUseAndCancelKeepsOriginal() {
        val source = area(); var used: CameraAlarmArea? = null; var cancelled = 0
        val image = bitmap()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { V380Theme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                androidx.compose.material3.Text("Area sintetis, tanpa koneksi")
                CameraAreaEditor(image, source, true, { used = it }, { cancelled++ })
            }
        } } }
        compose.onNodeWithTag("area-grid").performTouchInput { click(percentOffset(.85f, .75f)) }
        compose.onNodeWithText("1 dari 6 sel dipilih").assertExists()
        compose.onNodeWithText("Baris 2").performScrollTo().assertExists()
        compose.onNodeWithText("Kolom 3").assertExists()
        capture("editor")
        compose.runOnIdle { assertNull(used); assertEquals(List(6) { 0 }, source.cells) }
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertNull(used) }
        compose.onNodeWithText("Pilih semua").performScrollTo().performClick()
        compose.onNodeWithText("6 dari 6 sel dipilih").assertExists()
        compose.onNodeWithText("Kosongkan pilihan").performScrollTo().performClick()
        compose.onNodeWithText("Baris 2").performScrollTo().performClick()
        compose.onNodeWithText("Baris 1").performClick()
        compose.onNodeWithText("Pilih sel baris 1, kolom 3").performScrollTo().performClick()
        compose.onNodeWithText("Gunakan area").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(0, 0, 1, 0, 0, 0), used!!.cells); assertEquals(List(6) { 0 }, source.cells) }
    }

    @Test fun loadingFailureDisabledEditorAndMissingReferencePreventWrites() {
        val loading = mutableStateOf(true); val error = mutableStateOf<String?>(null)
        val image = bitmap(); val source = area()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { V380Theme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                AreaCaptureFeedback(loading.value, error.value)
                CameraAreaEditor(image, source, false, { fail("Disabled editor must not apply changes") }, {})
                CameraAlarmControls(CameraSettings(mapOf(CameraManagementProtocol.ALARM_AREA to source), emptyMap()),
                    mapOf(CameraManagementProtocol.ALARM_AREA to source.copy(cells = List(6) { 1 })), ManagementAbilities(), true,
                    onChange = { _, _ -> fail("No write") }, onSave = { fail("Reference required") }, onDiscard = {})
            }
        } } }
        compose.onNodeWithTag("area-loading").assertExists()
        compose.runOnIdle { loading.value = false; error.value = "Gambar referensi tidak tersedia" }
        compose.onNodeWithTag("area-loading").assertDoesNotExist()
        compose.onNodeWithText("Gambar referensi tidak tersedia").assertExists()
        capture("failure")
        compose.onNodeWithText("Gunakan area").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih semua").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Pilih sel baris 1, kolom 1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsNotEnabled()
    }

    @Test fun pixelCopyCopiesOnlySyntheticSurfaceAndKeepsGeometryAndColors() = runBlocking {
        val ready = CompletableDeferred<SurfaceView>()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { V380Theme {
            CameraVideoSurface(160.dp, onReady = {}, onLost = {}, aspectRatio = 1.5f, onViewReady = { ready.complete(it) })
        } } }
        val view = withTimeout(5000) { ready.await() }
        val rendered = CompletableDeferred<Unit>()
        compose.runOnIdle {
            val canvas = view.holder.lockCanvas()
            try {
                canvas.drawColor(Color.RED)
                val paint = Paint().apply { color = Color.BLUE }
                canvas.drawRect(canvas.width / 2f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
            } finally { view.holder.unlockCanvasAndPost(canvas) }
            view.postOnAnimation { rendered.complete(Unit) }
        }
        withTimeout(3000) { rendered.await() }
        val copied = VideoFrameCapture.copy(view, VideoGeometry(120, 80))
        try {
            assertEquals(120, copied.width); assertEquals(80, copied.height)
            assertEquals(Color.RED, copied.getPixel(20, 40)); assertEquals(Color.BLUE, copied.getPixel(100, 40))
        } finally { copied.recycle() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = context.resources.configuration
        val theme = if (config.uiMode and 0x30 == 0x20) "dark" else "light"
        val orientation = if (config.orientation == 2) "landscape" else "portrait"
        val root = File(context.getExternalFilesDir(null), "area-review").apply { mkdirs() }
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try { File(root, "$name-$theme-$orientation-font${config.fontScale}.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { screenshot.recycle() }
    }
}
