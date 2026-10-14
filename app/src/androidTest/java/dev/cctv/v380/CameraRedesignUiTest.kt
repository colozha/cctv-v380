package dev.cctv.v380

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Labelled native fixtures; no real camera login, movement, or footage. Does not save profiles. */
class CameraRedesignUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val profile = CameraProfile(id = "redesign-fixture", name = "Kamera UI (sintetis)", host = "192.0.2.10",
        port = 8089, deviceId = "424242", protocolProfileId = "iot-lan")

    @Test fun captureHomeEditorApLiveAndSheetAcrossThemesFontsAndOrientation() {
        val store = ViewModelStore()
        lateinit var model: CameraViewModel
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = CameraViewModel(activity.application as Application,
                sessionOverride = CameraSession(channelFactory = { error("Visual fixtures never open transport") }))
            store.put("visual", model)
        }
        try {
            compose.waitUntil(5000) { !model.ui.value.loading && model.features.value.pendingLoaded }
            for ((variant, dark, scale, landscape) in listOf(
                Variant("light", false, 1f, false), Variant("dark", true, 1f, false),
                Variant("dark-large", true, 1.3f, false), Variant("dark-landscape", true, 1f, true))) {
                compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(15000) { instrumentation.targetContext.resources.configuration.orientation ==
                    if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                for (screen in listOf("home", "editor", "ap", "live")) {
                    compose.activityRule.scenario.onActivity { activity ->
                        setLiveFixture(model)
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
                                    when (screen) {
                                        "home" -> CameraList(CameraUiState(profiles = listOf(profile), loading = false, scanCompleted = true,
                                            discovered = listOf(discovered("424242", 8089), discovered("515151", 9090))),
                                            {}, {}, {}, {}, {}, {}, {}, {}, {})
                                        "editor" -> ProfileEditor(null, "", 8800, null, null, null, false, null, {}, { _, _, _ -> }, {})
                                        "ap" -> ProvisioningScreen(model, {}, {}, { _, action -> action() }, { action -> action() }, { _, action -> action() })
                                        else -> LivePlayerScreen(model, profile, SessionState.Streaming(Quality.HD), null, {}, {}, {}, {})
                                    }
                                }
                            }
                        }
                    }
                    val expected = when (screen) { "home" -> "Kamera Anda"; "editor" -> "Detail kamera"; "ap" -> "Hubungkan ke hotspot kamera"; else -> "Koneksi lokal" }
                    compose.waitUntil(10000) { runCatching {
                        compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
                    }.getOrDefault(false) }
                    capture("$screen-$variant-top")
                    when (screen) {
                        "home" -> {
                            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Kamera 515151"))
                            compose.onNodeWithText("Kamera 515151").assertIsDisplayed()
                            capture("home-$variant-discovery")
                        }
                        "editor" -> {
                            compose.onNodeWithText("Simpan kamera").performScrollTo().assertIsDisplayed()
                            capture("editor-$variant-save")
                        }
                        "ap" -> {
                            compose.onNodeWithText("Saya sudah di hotspot kamera").performScrollTo().assertIsDisplayed()
                            capture("ap-$variant-actions")
                        }
                        "live" -> {
                            compose.onNodeWithContentDescription("Atas").performScrollTo().assertIsEnabled()
                            compose.onNodeWithContentDescription("Kiri").assertIsEnabled()
                            capture("live-$variant-controls")
                            compose.onNodeWithText("Preset & patroli").performScrollTo().performClick()
                            compose.onNodeWithText("Preset tersimpan: 1/6").assertExists()
                            capture("sheet-$variant-top")
                            compose.onNodeWithText("Tambah rute patroli").performScrollTo().assertIsEnabled()
                            capture("sheet-$variant-routes")
                            compose.onNodeWithContentDescription("Tutup preset dan patroli").performScrollTo().performClick()
                            compose.onNodeWithText("Muat preset dan patroli").assertDoesNotExist()
                            // Opening/closing the sheet must not remove the native video surface.
                            assertTrue(findSurface(compose.activity.window.decorView))
                        }
                    }
                }
            }
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; store.clear() }
        }
    }

    @Test fun sheetProtectsPatrolDraftOnClose() {
        lateinit var model: CameraViewModel
        val store = ViewModelStore()
        compose.activityRule.scenario.onActivity { activity ->
            model = CameraViewModel(activity.application, sessionOverride = CameraSession(channelFactory = { error("No transport") }))
            store.put("draft", model)
        }
        try {
            compose.waitUntil(5000) { model.features.value.pendingLoaded }
            compose.activityRule.scenario.onActivity { activity ->
                setLiveFixture(model)
                activity.setContent { V380Theme { LivePlayerScreen(model, profile, SessionState.Streaming(Quality.HD), null, {}, {}, {}, {}) } }
            }
            compose.onNodeWithText("Preset & patroli").performScrollTo().performClick()
            compose.onNodeWithText("Tambah rute patroli").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Tutup preset dan patroli").performScrollTo().performClick()
            compose.onNodeWithText("Buang perubahan?").assertIsDisplayed()
            compose.onNodeWithText("Tetap di sini").performClick()
            assertTrue(model.features.value.managementDraft.isNotEmpty())
            compose.onNodeWithContentDescription("Tutup preset dan patroli").performClick()
            compose.onNodeWithText("Lanjutkan keluar").performClick()
            compose.onNodeWithText("Muat preset dan patroli").assertDoesNotExist()
            assertTrue(model.features.value.managementDraft.isEmpty())
        } finally { compose.activityRule.scenario.onActivity { store.clear() } }
    }

    private fun setLiveFixture(model: CameraViewModel) {
        val session = model.session
        val iot = IoTSession(ByteArray(32) { (it + 1).toByte() }, 17, 3, 0)
        set(session, "profile", profile); set(session, "iot", iot); set(session, "authenticatedIoT", iot)
        set(session, "accessRevoked", false); set(session, "managementRevision", 1L); set(session, "patrolActive", false)
        val caps = CameraCapabilities(emptySet(), Quality.entries.toSet(), true, management = ManagementAbilities(presets = 6, patrol = true))
        flow<CameraCapabilities?>(session, "mutableCapabilities").value = caps
        val settings = CameraSettings(mapOf(CameraManagementProtocol.PRESETS to CameraPresets(listOf(0)),
            CameraManagementProtocol.PATROL to CameraPatrol(false, false, 2, 3, emptyList(), "{}")), emptyMap())
        flow<CameraFeatureState>(model, "mutableFeatures").value = CameraFeatureState(profileId = profile.id,
            pendingLoaded = true, restartLoaded = true, apPendingLoaded = true,
            management = mapOf(ManagementGroup.MOTOR to ManagementSnapshot(profile.id, 1, ManagementGroup.MOTOR, settings, profile.deviceId)))
        flow<Long>(session, "mutableAuthenticationRevision").value = 1
        flow<SessionState>(session, "mutableState").value = SessionState.Streaming(Quality.HD)
        session.blockMaintenanceWrites(false)
    }
    private fun discovered(id: String, port: Int) = DiscoveredCamera(id, "192.0.2.10", port, "", 300, null, null, null, 2)
    private fun findSurface(view: android.view.View): Boolean = view is android.view.SurfaceView ||
        view is android.view.ViewGroup && (0 until view.childCount).any { findSurface(view.getChildAt(it)) }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "camera-redesign").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableStateFlow<T>
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private data class Variant(val name: String, val dark: Boolean, val scale: Float, val landscape: Boolean)
}
