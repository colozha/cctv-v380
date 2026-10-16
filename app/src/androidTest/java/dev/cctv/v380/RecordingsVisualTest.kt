package dev.cctv.v380

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Labelled fixtures; no network authentication or camera footage. */
class RecordingsVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val profile = CameraProfile(name = "Contoh UI (sintetis)", host = "192.0.2.10", deviceId = "visual-recordings")
    private val date = LocalDate.of(2026, 10, 14)
    private val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 21 * 3600000
    private val rows = List(6) { RecordingSegment(start - it * 240000, start - it * 240000 + 240000, 1) }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun captureListAndPlayerInLightDarkLargeFontAndLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            for ((name, dark, scale, landscape) in listOf(
                Variant("light-portrait", false, 1f, false), Variant("dark-large-portrait", true, 1.3f, false),
                Variant("dark-landscape", true, 1f, true), Variant("light-compact-portrait", false, 1f, false))) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(15000) { instrumentation.targetContext.resources.configuration.orientation ==
                    if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                for (player in listOf(false, true)) {
                    compose.activityRule.scenario.onActivity { activity ->
                        val model = ViewModelProvider(activity)[CameraViewModel::class.java]
                        model.prepareFeatures(profile)
                        androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                            isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
                        }
                        activity.setContent {
                            val configuration = Configuration(LocalConfiguration.current).apply {
                                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                            }
                            CompositionLocalProvider(LocalConfiguration provides configuration,
                                LocalDensity provides Density(LocalDensity.current.density, scale)) {
                                V380Theme {
                                    Scaffold(topBar = { TopAppBar(title = { Text("Contoh UI (sintetis)") }) }) { padding ->
                                        RecordingsScreen(model, profile, SessionState.Idle, CameraFeatureState(
                                            profileId = profile.id, tab = CameraTab.RECORDINGS, date = date,
                                            selected = if (player) rows.first() else null, paused = true,
                                            recordingsLoaded = true, recordings = rows,
                                            recordingStorage = CameraSettings(mapOf(P.SD_STATUS to 1, P.SD_CAPACITY to "1000|560|0"), emptyMap()),
                                            error = if (player) "Koneksi kamera terputus sebelum rekaman selesai diterima. Coba unduh kembali." else null),
                                            {}, Modifier.widthIn(max = if (name == "light-compact-portrait") 320.dp else androidx.compose.ui.unit.Dp.Infinity)
                                                .padding(padding))
                                    }
                                }
                            }
                        }
                    }
                    if (player) compose.onNodeWithText("Kartu SD terpasang").performScrollTo()
                    else compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Kartu SD terpasang"))
                    compose.onNodeWithText("Kartu SD terpasang").assertIsDisplayed()
                    capture("${if (player) "player" else "list"}-$name-top")
                    if (player) {
                        compose.onNodeWithContentDescription("Simpan rekaman").performScrollTo().assertIsDisplayed()
                        val actions = listOf("Rekaman sebelumnya", "Putar", "Hentikan", "Rekaman berikutnya", "Simpan rekaman")
                        val bounds = actions.map { label ->
                            compose.onNodeWithText(label).assertDoesNotExist()
                            compose.onNodeWithContentDescription(label).assertIsDisplayed()
                                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
                        }
                        bounds.zipWithNext().forEach { (left, right) ->
                            assertTrue("Controls must stay in order with 8dp gaps", right.left - left.right >= 7.9.dp)
                            assertTrue("Controls must share one row", left.top == right.top)
                        }
                        // These fixtures have no authenticated camera; playback and navigation stay gated.
                        for (label in listOf("Rekaman sebelumnya", "Putar", "Rekaman berikutnya"))
                            compose.onNodeWithContentDescription(label).assertIsNotEnabled()
                        capture("player-$name-controls")
                    } else {
                        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(recordingTimes(rows.first())))
                        compose.onNodeWithText(recordingTimes(rows.first())).assertIsDisplayed()
                        capture("list-$name-recordings")
                    }
                }
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "recordings-design").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private data class Variant(val name: String, val dark: Boolean, val scale: Float, val landscape: Boolean)
}
