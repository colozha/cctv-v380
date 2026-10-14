package dev.cctv.v380

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic screen fixtures are labelled. These checks never authenticate or contact a camera. */
class FeatureUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val profile = CameraProfile(name = "Contoh fitur (sintetis)", host = "192.0.2.10", deviceId = "feature-ui-synthetic")

    @After fun removeSyntheticProfile() {
        ProfileStore(InstrumentationRegistry.getInstrumentation().targetContext).delete(profile.id)
    }

    @Test fun cameraTabsExposeRecordingsAndSettingsWithoutStartingLiveVideo() {
        compose.activityRule.scenario.onActivity { activity ->
            ProfileStore(activity).save(profile, null, false)
            ViewModelProvider(activity)[CameraViewModel::class.java].reloadAfterSetup()
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(profile.name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(profile.name).performClick()
        compose.onNodeWithText("Rekaman").performClick()
        compose.onNodeWithText("Sambungkan kamera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Muat ulang rekaman").assertIsNotEnabled()
        capture("recordings-disconnected")
        compose.onNodeWithText("Pengaturan").performClick()
        compose.onNodeWithText("Muat pengaturan kamera").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        capture("settings-disconnected")
        compose.onNodeWithText("Live").performClick()
        compose.onAllNodesWithText("Belum terhubung")[0].assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun syntheticPanelsAndDestructiveConfirmationHaveNativeReadableStates() {
        val settings = CameraSettings(mapOf(P.FLIP_MODE to 0, P.LIGHT_MODE to 0, P.INDICATOR to true,
            P.RECORD_MODE to 3, P.RECORD_QUALITY to 2, P.RECORD_AUDIO to true, P.RECORD_LOOP to true,
            P.SD_STATUS to 1, P.SD_CAPACITY to "32768|16384|0", P.SD_FORMAT_STATE to 0), emptyMap())
        var panel by mutableIntStateOf(0)
        var confirmations = 0
        var dismissals = 0
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[CameraViewModel::class.java]
            activity.setContent {
                V380Theme {
                    Scaffold(topBar = { TopAppBar(title = { Text("Contoh UI (sintetis)") }) }) { padding ->
                        when (panel) {
                            0 -> CameraSettingsScreen(model, profile, SessionState.Idle,
                                CameraFeatureState(profileId = profile.id, tab = CameraTab.SETTINGS, settings = settings, pendingLoaded = true), {}, Modifier.padding(padding))
                            1 -> {
                                val start = LocalDate.of(2026, 10, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                RecordingsScreen(model, profile, SessionState.Idle, CameraFeatureState(profileId = profile.id, tab = CameraTab.RECORDINGS,
                                    date = LocalDate.of(2026, 10, 14), recordingsLoaded = true,
                                    recordings = listOf(RecordingSegment(start + 8 * 3600000, start + 8 * 3600000 + 60000, 1))), {}, Modifier.padding(padding))
                            }
                            else -> SdFormatConfirmation("Contoh SD (sintetis)", { confirmations++ }, { dismissals++; panel = 0 })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Gambar").performScrollTo().performClick()
        capture("settings-synthetic")
        compose.onNodeWithText("Kartu SD").performScrollTo().performClick()
        compose.onNodeWithText("50% ruang kartu tersedia").assertExists()
        compose.onNodeWithText("Format SD card").assertIsNotEnabled()
        capture("storage-synthetic")
        compose.runOnIdle { panel = 1 }
        compose.onNodeWithText("Rekaman di kartu SD").assertExists()
        capture("recordings-synthetic")
        compose.runOnIdle { panel = 2 }
        compose.onNodeWithText("Format SD card?").assertIsDisplayed()
        capture("format-confirmation-synthetic")
        compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle { assertEquals(0, confirmations); assertEquals(1, dismissals) }
    }

    @Test fun hotspotFormIsLockedUntilAuthenticatedAndUsesNativePasswordAndSaveControls() {
        var editable by mutableStateOf(false)
        var submissions = 0
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { V380Theme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Contoh hotspot (sintetis)")
                    ApHotspotForm("CameraNew", {}, "Secret123", {}, false, {}, editable, false,
                        if (editable) "Sesi autentikasi sintetis, tanpa koneksi kamera." else "Sambungkan kamera terlebih dahulu untuk mengubah hotspot.",
                        null, { submissions++ }, editable)
                }
            } }
        }
        compose.onNodeWithText("Nama hotspot kamera").assertIsNotEnabled()
        compose.onNodeWithText("Password hotspot kamera").assertIsNotEnabled()
        compose.onNodeWithText("Simpan hotspot").performScrollTo().assertIsNotEnabled()
        capture("hotspot-locked-synthetic")
        compose.runOnIdle { editable = true }
        compose.onNodeWithText("Simpan hotspot").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
        capture("hotspot-editable-synthetic")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.getExternalFilesDir(null), "feature-review").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(root, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
