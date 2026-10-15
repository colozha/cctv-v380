package dev.cctv.v380

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.provisioning.*
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Synthetic presentation/permission checks. No scan, login, Wi-Fi request or camera write is permitted. */
class OfflineProvisioningUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val fixture = mutableStateOf(ProvisioningState(phase = ProvisioningPhase.WAITING_LAN,
        targetSsid = "SyntheticRouter", mayHaveConfigured = true, routerCheck = RouterCheck.CONFIRMATION_REQUIRED))
    private val dark = mutableStateOf(false)
    private val language = mutableStateOf("id")
    private var verified = 0
    private var reset = 0

    private fun recovery() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val context = activity.createConfigurationContext(Configuration(activity.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language.value))
                })
                CompositionLocalProvider(LocalContext provides context,
                    LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    V380Theme(dark.value) {
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                androidx.compose.material3.Text(context.getString(R.string.setup_synthetic))
                                RouterSetupRecovery(fixture.value, {}, { confirmed -> assertTrue(confirmed); verified++ }, { reset++ })
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun unknownSsidNeedsExplicitConfirmationAndResetOnlyClearsProgress() {
        recovery()
        compose.onNodeWithText("Periksa kamera di router").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Saya sudah memilih Wi-Fi router SyntheticRouter").performScrollTo().performClick()
        compose.onNodeWithText("Periksa kamera di router").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, verified) }
        compose.onNodeWithText("Periksa kamera di router").assertIsNotEnabled()
        compose.onNodeWithText("Hapus progres setup lokal").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, reset); assertEquals(1, verified) }
    }

    @Test fun recoveryThemesLanguagesLargeTypeAndLandscape() {
        recovery()
        for (lang in listOf("id", "en")) for (isDark in listOf(false, true)) {
            compose.runOnIdle {
                language.value = lang; dark.value = isDark
                fixture.value = fixture.value.copy(routerCheck = RouterCheck.WRONG_NETWORK)
            }
            compose.onNodeWithText(if (lang == "id") "Periksa kamera di router" else "Check camera on router")
                .performScrollTo().assertIsEnabled()
            capture("$lang-${if (isDark) "dark" else "light"}-wrong-network-large")
            compose.runOnIdle { fixture.value = fixture.value.copy(routerCheck = RouterCheck.CONFIRMATION_REQUIRED) }
            compose.onNodeWithText(if (lang == "id") "Periksa kamera di router" else "Check camera on router")
                .performScrollTo().assertIsNotEnabled()
            capture("$lang-${if (isDark) "dark" else "light"}-confirmation-large")
        }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        recovery()
        compose.onNodeWithText("Check camera on router").performScrollTo().assertIsNotEnabled()
        capture("en-dark-confirmation-large-landscape")
    }

    @Test fun deniedApPermissionClearsSecretAndFreshPairingExplainsCloudBoundary() {
        val models = ViewModelStore()
        lateinit var model: CameraViewModel
        var requests = 0
        compose.activityRule.scenario.onActivity { activity ->
            model = CameraViewModel(activity.application as Application,
                sessionOverride = CameraSession(channelFactory = { error("Synthetic UI cannot connect") }))
            models.put("offline-setup", model)
            model.provisioning.cancel()
            activity.setContent { V380Theme {
                ProvisioningScreen(model, {}, {}, { cancel, _ -> requests++; cancel() }, {}, { cancel, _ -> cancel() })
            } }
        }
        try {
            compose.onNodeWithText("SSID hotspot kamera").performTextInput("SyntheticAP")
            compose.onNodeWithText("Password hotspot kamera").performTextInput("synthetic-secret")
            compose.onAllNodesWithText("Hubungkan ke hotspot kamera").filter(hasClickAction()).onFirst().performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, requests); assertEquals(ProvisioningPhase.IDLE, model.provisioning.state.value.phase) }
            assertTrue(compose.onNodeWithText("Password hotspot kamera").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty())
            compose.runOnIdle {
                val camera = DiscoveredCamera("424242", "192.0.2.10", 8089, "", 300, null, null, null, 2)
                val field = ProvisioningManager::class.java.getDeclaredField("mutable").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                (field.get(model.provisioning) as MutableStateFlow<ProvisioningState>).value =
                    ProvisioningState(ProvisioningPhase.CREDENTIALS, camera = camera)
            }
            compose.onNodeWithText("Saya perlu pairing kamera baru").performScrollTo().performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.setup_cloud_limit)).performScrollTo().assertIsDisplayed()
            capture("id-cloud-boundary")
        } finally { compose.runOnIdle { models.clear() } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "offline-provisioning-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
