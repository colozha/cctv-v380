package dev.cctv.v380

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Offline recovery fixtures; the session factory fails if any transport is opened. */
class SdMaintenanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun pendingFormatWithoutSettingsCanBeClosedWithConfirmationAndNeverEnablesFormat() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.cacheDir, "sd-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val profile = CameraProfile(name = "Contoh SD (sintetis)", host = "192.0.2.10", deviceId = "sd-ui")
        val store = ProfileStore(instrumentation.targetContext, File(dir, "profiles.json"))
        val owners = ViewModelStore()
        lateinit var model: CameraViewModel
        var opened = 0
        try {
            compose.activityRule.scenario.onActivity { activity ->
                model = CameraViewModel(activity.application, sessionOverride = CameraSession(channelFactory = {
                    opened++; error("Offline UI must not open transport")
                }))
                owners.put("sd-fixture", model)
                model.javaClass.getDeclaredField("store").apply { isAccessible = true }.set(model, store)
            }
            compose.waitUntil(15000) { model.features.value.pendingLoaded }
            @Suppress("UNCHECKED_CAST")
            val features = model.javaClass.getDeclaredField("mutableFeatures").apply { isAccessible = true }
                .get(model) as MutableStateFlow<CameraFeatureState>
            compose.runOnIdle {
                features.value = CameraFeatureState(profileId = profile.id, tab = CameraTab.SETTINGS,
                    pendingLoaded = true, restartLoaded = true, apPendingLoaded = true,
                    pendingFormat = store.savePendingFormat(profile, "sd-ui-op"), format = SdFormatPhase.UNKNOWN)
            }
            for ((name, dark, scale, landscape, phase) in listOf(
                Variant("light", false, 1f, false, SdFormatPhase.UNKNOWN),
                Variant("dark-large", true, 1.3f, false, SdFormatPhase.FORMATTING),
                Variant("dark-landscape", true, 1f, true, SdFormatPhase.CHECKING))) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(15000) { instrumentation.targetContext.resources.configuration.orientation ==
                    if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                compose.activityRule.scenario.onActivity { activity ->
                    features.value = features.value.copy(format = phase, busy = phase == SdFormatPhase.CHECKING)
                    activity.setContent {
                        val configuration = Configuration(LocalConfiguration.current).apply {
                            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        }
                        CompositionLocalProvider(LocalConfiguration provides configuration,
                            LocalDensity provides Density(LocalDensity.current.density, scale)) {
                            V380Theme {
                                val feature by model.features.collectAsState()
                                CameraSettingsScreen(model, profile, SessionState.Idle, feature, {})
                            }
                        }
                    }
                }
                if (compose.onAllNodesWithText("Format kartu SD").fetchSemanticsNodes().isEmpty())
                    compose.onNodeWithText("Rekaman & kartu SD").performScrollTo().performClick()
                compose.onNodeWithText("Format kartu SD").performScrollTo().assertIsNotEnabled()
                compose.onNodeWithText("Periksa status kartu").assertIsNotEnabled()
                capture("recovery-$name")
                if (phase == SdFormatPhase.CHECKING) compose.onNodeWithText("Tutup catatan format").assertIsNotEnabled()
            }
            compose.runOnIdle { features.value = features.value.copy(busy = false, format = SdFormatPhase.UNKNOWN) }
            compose.runOnIdle { model.acknowledgeFormat("stale-dialog-operation") }
            assertNotNull(store.loadPendingFormat())
            compose.onNodeWithText("Tutup catatan format").performScrollTo().performClick()
            compose.onNodeWithText("Tutup catatan format?").assertIsDisplayed()
            capture("close-confirmation")
            compose.onNodeWithText("Kembali").performClick()
            assertNotNull(store.loadPendingFormat())
            compose.onNodeWithText("Tutup catatan format").performClick()
            compose.onNodeWithText("Tutup catatan").performClick()
            compose.waitUntil(15000) { model.features.value.pendingFormat == null }
            assertNull(store.loadPendingFormat())
            assertEquals(SdFormatPhase.UNKNOWN, model.features.value.format)
            assertTrue(model.features.value.notice!!.contains("belum diketahui"))
            assertEquals(0, opened)
        } finally {
            compose.activityRule.scenario.onActivity {
                owners.clear()
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            dir.deleteRecursively()
        }
    }

    @Test fun formatConfirmationCanBeCancelledBeforeAnyAction() {
        var confirmed = 0
        var dismissed = 0
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { V380Theme { SdFormatConfirmation("Contoh SD (sintetis)", { confirmed++ }, { dismissed++ }) } }
        }
        compose.onNodeWithText("Batal").performClick()
        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "sd-maintenance").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private data class Variant(val name: String, val dark: Boolean, val scale: Float, val landscape: Boolean, val phase: SdFormatPhase)
}
