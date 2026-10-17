package dev.cctv.v380

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.protocol.*
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Native synthetic fixtures: never contact, persist credentials for, or move an owner camera. */
class SettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store = ViewModelStore()
    private lateinit var model: CameraViewModel
    private val profile = CameraProfile(id = "settings-fixture", name = "Pengaturan (sintetis)", host = "192.0.2.10",
        deviceId = "settings-synthetic", protocolProfileId = "iot-lan")
    private val capabilities = CameraCapabilities(emptySet(), Quality.entries.toSet(), true, recordingQualities = setOf(1, 2),
        management = ManagementAbilities(presets = 6, patrol = true, calibration = true, restart = true, motion = true, lightSchedule = true))

    @Before fun fixture() {
        compose.activityRule.scenario.onActivity { activity ->
            model = CameraViewModel(activity.application,
                sessionOverride = CameraSession(channelFactory = { error("Synthetic UI must not open transport") }))
            store.put("settings", model)
        }
        compose.waitUntil(5000) { !model.ui.value.loading && model.features.value.pendingLoaded && model.features.value.restartLoaded }
        compose.runOnIdle { resetFixture() }
    }

    @After fun closeFixture() { compose.activityRule.scenario.onActivity { store.clear() } }

    @Test fun cardsKeepDraftsAndErrorsVisibleWhileClosedAndUnauthenticatedControlsStayLocked() {
        showSettings()
        compose.onNodeWithText("Balik orientasi").assertDoesNotExist()
        compose.onNodeWithText("Respons kamera tidak sesuai profil protokol.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Muat jadwal lampu").assertDoesNotExist()
        compose.onNodeWithText("Gambar & lampu").performScrollTo().performClick()
        compose.onNodeWithText("Hidup").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Gambar & lampu").performScrollTo().performClick()
        compose.onNodeWithText("Hidup").assertDoesNotExist()
        compose.onNodeWithText("Perubahan belum disimpan").performScrollTo().assertIsDisplayed()
        capture("closed-draft")
        compose.onNodeWithText("Balik orientasi").assertDoesNotExist()
        compose.onNodeWithText("Gambar & lampu").performScrollTo().performClick()
        compose.onNodeWithText("Hidup").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(1, model.features.value.draft[P.LIGHT_MODE]) }
        compose.onNodeWithText("Rekaman & kartu SD").performScrollTo().performClick()
        compose.onNodeWithText("Rekam peristiwa").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(3, model.features.value.draft[P.RECORD_MODE]) }
        compose.onNodeWithText("Rekaman & kartu SD").performScrollTo().performClick()
        compose.onNodeWithText("Rekaman & kartu SD").performClick()
        compose.onNodeWithText("Rekam peristiwa").assertIsOn()
        compose.runOnIdle { flow<SessionState>(model.session, "mutableState").value = SessionState.Idle }
        compose.onNodeWithText("Rekam peristiwa").assertDoesNotExist()
        compose.onNodeWithText("Muat ulang").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Simpan pengaturan").assertDoesNotExist()
        compose.runOnIdle {
            val state = flow<CameraFeatureState>(model, "mutableFeatures")
            state.value = state.value.copy(busy = true, managementLoading = ManagementGroup.ALARM,
                pendingRestart = PendingCameraRestart("synthetic-restart", profile.id, profile.deviceId, 1L))
        }
        compose.onNodeWithText("Sedang memeriksa kamera…").performScrollTo().assertIsDisplayed()
        capture("closed-management-progress")
        compose.onNodeWithText("Hasil restart belum diketahui. Buka kartu untuk memeriksa kamera; perintah tidak dikirim ulang.")
            .performScrollTo().assertIsDisplayed()
        capture("closed-restart-recovery")
    }

    @Test fun threeMainGroupsPrecedeMarkedFeaturesAndOpeningNeverRequestsCamera() {
        compose.runOnIdle {
            val state = flow<CameraFeatureState>(model, "mutableFeatures")
            state.value = state.value.copy(settings = null, management = emptyMap(), managementErrors = emptyMap())
        }
        showSettings()
        val titles = listOf("Gambar & lampu", "Rekaman & kartu SD", "Koneksi & perangkat",
            "Jadwal lampu", "Alarm dan deteksi gerakan", "Preset dan patroli")
        val positions = titles.map { compose.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }
        assertTrue(positions.zipWithNext().all { (a, b) -> a < b })
        compose.onAllNodesWithText("Belum berfungsi", useUnmergedTree = true).assertCountEquals(4)
        compose.onNodeWithText("Periksa hotspot kamera").assertDoesNotExist()
        for (title in titles) compose.onNodeWithText(title).performScrollTo().performClick()
        compose.onNodeWithText("Balik orientasi").assertDoesNotExist()
        compose.onNodeWithText("Muat jadwal lampu").assertExists()
        compose.onNodeWithText("Muat alarm dan deteksi gerakan").assertExists()
        compose.onNodeWithText("Muat preset dan patroli").assertExists()
        compose.runOnIdle {
            assertNull(model.features.value.settings)
            assertTrue(model.features.value.management.isEmpty())
            assertFalse(model.features.value.busy)
        }
        // Each main group remains expanded while another is opened.
        compose.onNodeWithText("Lampu").assertExists()
        compose.onNodeWithText("Kartu SD").assertExists()
        compose.onNodeWithText("Hotspot kamera").assertExists()
    }

    @Test fun independentCardsRestoreExpansionAndDraftAfterSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val canCollapse = mutableStateOf(true)
        compose.activityRule.scenario.onActivity { activity ->
            activity.findViewById<android.view.ViewGroup>(android.R.id.content).removeAllViews()
        }
        restoration.setContent {
            V380Theme {
                var value by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    SettingsCard(profile.id, "Pertama", LightIcon, canCollapse = canCollapse.value) {
                        SettingChoices("Mode", value, listOf(0 to "Otomatis", 1 to "Hidup"), true) { value = it }
                    }
                    SettingsCard(profile.id, "Kedua", PictureIcon) { Text("Isi kedua") }
                }
            }
        }
        compose.onNodeWithText("Pertama").performClick()
        compose.onNodeWithText("Hidup").performClick()
        compose.onNodeWithText("Kedua").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Hidup").assertIsSelected()
        compose.onNodeWithText("Isi kedua").assertExists()
        compose.runOnIdle { canCollapse.value = false }
        compose.onNodeWithText("Pertama").assertIsNotEnabled()
        compose.onNodeWithText("Hidup").assertIsSelected()
    }

    @Test fun hotspotKeepsNameButClearsPasswordOnCollapseAndRejectsInvalidInput() {
        compose.runOnIdle {
            set(model.session, "softApSupported", true)
            set(model.session, "apNetworkValid", { true })
            flow<ApHotspotAccess?>(model.session, "mutableApAccess").value = ApHotspotAccess("CameraOld", profile.deviceId)
        }
        showSettings()
        compose.onNodeWithText("Koneksi & perangkat").performScrollTo().performClick()
        compose.onNodeWithText("Nama hotspot kamera").performScrollTo().performTextReplacement("CameraNew")
        compose.onNodeWithText("Password hotspot kamera").performScrollTo().performTextReplacement("bad password")
        compose.onNodeWithText("Simpan hotspot").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Password hotspot kamera").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        compose.onNodeWithText("Password hotspot kamera").performScrollTo().performTextReplacement("Secret123")
        compose.onNodeWithText("Simpan hotspot").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Koneksi & perangkat").performScrollTo().performClick()
        compose.onNodeWithText("Password hotspot kamera").assertDoesNotExist()
        compose.onNodeWithText("Koneksi & perangkat").performClick()
        compose.onNodeWithText("Nama hotspot kamera").assertTextContains("CameraNew")
        compose.onNodeWithText("Password hotspot kamera").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithText("Simpan hotspot").performScrollTo().assertIsNotEnabled()
    }

    @Test fun diagnosticsShowNewestFirstAfterAppendingAndExportStaysChronological() {
        compose.runOnIdle {
            model.diagnostics.record(DiagnosticStage.AUTH, code = 1)
            model.diagnostics.record(DiagnosticStage.DISCOVERY, code = 2)
        }
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { V380Theme { DiagnosticsScreen(model) {} } } }
        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        assertTrue(top("Pencarian kamera · OK") < top("Autentikasi · OK"))
        compose.runOnIdle { model.diagnostics.record(DiagnosticStage.PTZ, code = 3) }
        compose.waitForIdle()
        assertTrue(top("Kontrol PTZ · OK") < top("Pencarian kamera · OK"))
        capture("diagnostics-newest-first")
        val events = JSONObject(model.diagnostics.exportJson()).getJSONArray("events")
        assertEquals("AUTH", events.getJSONObject(0).getString("stage"))
        assertEquals("PTZ", events.getJSONObject(2).getString("stage"))
    }

    @Test fun captureSettingsAcrossThemesLargeTypeAndLandscape() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            for (variant in listOf(
                Variant("id-light", false, 1f, false), Variant("id-dark", true, 1f, false),
                Variant("id-dark-large", true, 1.3f, false), Variant("en-light", false, 1f, false, "en"),
                Variant("en-dark", true, 1f, false, "en"), Variant("en-dark-landscape", true, 1.3f, true, "en"))) {
                val name = variant.name
                val dark = variant.dark
                val scale = variant.scale
                val landscape = variant.landscape
                compose.activityRule.scenario.onActivity { it.requestedOrientation = if (landscape)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                compose.waitUntil(15000) { instrumentation.targetContext.resources.configuration.orientation ==
                    if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                compose.runOnIdle {
                    resetFixture()
                    val state = flow<CameraFeatureState>(model, "mutableFeatures")
                    state.value = state.value.copy(apPhase = ApEditPhase.IDLE, managementErrors = emptyMap())
                }
                showSettings(dark, scale, variant.locale)
                val en = variant.locale == "en"
                compose.onNodeWithText(if (en) "Camera settings" else "Pengaturan kamera").performScrollTo()
                capture("$name-top")
                compose.onNodeWithText(if (en) "Presets and patrol" else "Preset dan patroli").performScrollTo()
                capture("$name-unfinished")
                val sections = if (en) listOf(
                    "Image & lights" to "Indicator light", "Recording & SD card" to "Format SD card",
                    "Connection & device" to "Restart camera", "Light schedule" to "Add schedule interval",
                    "Alarm and motion detection" to "Main alarm", "Presets and patrol" to "Calibrate PTZ")
                else listOf("Gambar & lampu" to "Lampu indikator", "Rekaman & kartu SD" to "Format kartu SD",
                    "Koneksi & perangkat" to "Restart kamera", "Jadwal lampu" to "Tambah interval jadwal",
                    "Alarm dan deteksi gerakan" to "Alarm utama", "Preset dan patroli" to "Kalibrasi PTZ")
                for ((title, control) in sections) {
                    // Capture settled layout without transient pointer/ripple feedback; behavior tests use real taps.
                    compose.onNodeWithText(title).performScrollTo()
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                    compose.onNodeWithText(control).performScrollTo().assertIsDisplayed()
                    if (en && title == "Image & lights") {
                        compose.onNodeWithText("On").assertExists()
                        compose.onNodeWithText("Off").assertExists()
                    }
                    if (en && title == "Light schedule") {
                        compose.onNodeWithText("Low").assertExists()
                        compose.onNodeWithText("High").assertExists()
                    }
                    if (en && title == "Connection & device") compose.onNodeWithText("Show").assertExists()
                    if (title == "Koneksi & perangkat" || title == "Connection & device") compose.onNodeWithText("MODEL-SYNTHETIC").assertExists()
                    if (title == "Rekaman & kartu SD" || title == "Recording & SD card") {
                        compose.onNodeWithText(if (en) "91% card space available" else "91% ruang kartu tersedia").assertExists()
                        compose.onNodeWithText(control).assertIsEnabled()
                    }
                    compose.onNodeWithText(title).performScrollTo()
                    val scroll = compose.onNode(hasScrollAction())
                    val offset = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top - scroll.fetchSemanticsNode().boundsInRoot.top
                    scroll.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, offset) }
                    capture("$name-${title.replace(' ', '-')}")
                    if (!compose.onNodeWithText(control).isDisplayed()) {
                        compose.onNodeWithText(control).performScrollTo()
                        capture("$name-${title.replace(' ', '-')}-controls")
                    }
                    compose.onNodeWithText(title).performScrollTo()
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
                }
            }
        } finally { compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    }

    @Test fun captureDarkTopWithSettledSystemChrome() {
        showSettings(dark = true)
        compose.onNodeWithText("Pengaturan kamera").performScrollTo().assertIsDisplayed()
        // MIUI can reapply bar appearance during Activity resume after the fixture's initial theme change.
        compose.activityRule.scenario.onActivity { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
            }
        }
        Thread.sleep(500)
        capture("dark-top")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun showSettings(dark: Boolean = false, scale: Float = 1f, locale: String = "id") {
        compose.activityRule.scenario.onActivity { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
            activity.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    setLocale(Locale.forLanguageTag(locale))
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val localizedContext = ContextThemeWrapper(activity, 0).apply { applyOverrideConfiguration(configuration) }
                CompositionLocalProvider(LocalContext provides localizedContext,
                    LocalConfiguration provides configuration,
                    LocalDensity provides Density(LocalDensity.current.density, scale)) {
                    V380Theme(darkTheme = dark) {
                        val feature by model.features.collectAsState()
                        val state by model.session.state.collectAsState()
                        Scaffold(topBar = { TopAppBar(title = { Text(profile.name) }) }) { padding ->
                            CameraSettingsScreen(model, profile, state, feature, {}, Modifier.padding(padding))
                        }
                    }
                }
            }
        }
    }

    private fun resetFixture() {
        val iot = IoTSession(ByteArray(32) { (it + 1).toByte() }, 17, 3, 0)
        set(model.session, "profile", profile); set(model.session, "iot", iot); set(model.session, "authenticatedIoT", iot)
        set(model.session, "accessRevoked", false); set(model.session, "managementRevision", 1L); set(model.session, "patrolActive", false)
        flow<CameraCapabilities?>(model.session, "mutableCapabilities").value = capabilities
        flow<Long>(model.session, "mutableAuthenticationRevision").value = 1L
        flow<SessionState>(model.session, "mutableState").value = SessionState.Ready(capabilities)
        val management = mapOf(
            ManagementGroup.DEVICE to mapOf(CameraManagementProtocol.MODEL to "MODEL-SYNTHETIC",
                CameraManagementProtocol.SOFTWARE to "V2.1.6.0(SYNTHETIC_FIRMWARE_20250804)",
                CameraManagementProtocol.HARDWARE to "SYNTHETIC_HARDWARE", CameraManagementProtocol.MODULE to "V()"),
            ManagementGroup.MOTOR to mapOf(CameraManagementProtocol.PRESETS to CameraPresets(listOf(0)),
                CameraManagementProtocol.PATROL to CameraPatrol(false, false, 2, 3, emptyList(), "{}")),
            ManagementGroup.ALARM to mapOf(CameraManagementProtocol.ALARM_MAIN to true, CameraManagementProtocol.MOTION to true),
            ManagementGroup.LIGHT to mapOf(P.LIGHT_SENSITIVITY to 2,
                CameraManagementProtocol.LIGHT_SCHEDULE to CameraLightSchedule(0, 1, 0, emptyList(), "{}")))
        flow<CameraFeatureState>(model, "mutableFeatures").value = CameraFeatureState(profileId = profile.id, tab = CameraTab.SETTINGS,
            pendingLoaded = true, restartLoaded = true, apPendingLoaded = true, apPhase = ApEditPhase.NOT_SENT,
            settings = CameraSettings(mapOf(P.FLIP_MODE to 0, P.LIGHT_MODE to 0, P.INDICATOR to true,
                P.RECORD_MODE to 1, P.RECORD_QUALITY to 2, P.RECORD_AUDIO to false, P.RECORD_LOOP to true,
                P.SD_STATUS to 1, P.SD_CAPACITY to "1000|910|0", P.SD_FORMAT_STATE to 0), emptyMap()),
            management = management.mapValues { (group, values) -> ManagementSnapshot(profile.id, 1L, group, CameraSettings(values, emptyMap())) },
            managementErrors = mapOf(ManagementGroup.LIGHT to "Respons kamera tidak sesuai profil protokol."))
        model.session.blockMaintenanceWrites(false)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(150)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "settings-simplified").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableStateFlow<T>
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private data class Variant(val name: String, val dark: Boolean, val scale: Float, val landscape: Boolean, val locale: String = "id")
}
