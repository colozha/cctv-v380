package dev.cctv.v380

import android.content.res.Configuration
import android.view.WindowManager
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.media.TalkbackError
import dev.cctv.v380.session.TalkbackState
import dev.cctv.v380.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fake permission and capture state only: never asks permission, opens a microphone or sends packets. */
class TalkbackUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val phase = mutableStateOf<TalkbackState>(TalkbackState.Idle)
    private val blocked = mutableStateOf<TalkbackError?>(null)
    private val granted = mutableStateOf(false)
    private val denied = mutableStateOf(false)
    private val dark = mutableStateOf(false)
    private val language = mutableStateOf("id")
    private val large = mutableStateOf(false)
    private var starts = 0
    private var stops = 0
    private var permissionRequests = 0

    private fun show() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setContent {
            val localized = activity.createConfigurationContext(Configuration(activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
            })
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized,
                LocalDensity provides Density(density.density, if (large.value) 1.3f else 1f)) {
                V380Theme(dark.value) {
                    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            Text(if (language.value == "id") "Kamera sintetis · tanpa mikrofon/jaringan" else "Synthetic camera · no microphone/network")
                            Spacer(Modifier.height(16.dp))
                            TalkbackControls(phase.value, blocked.value, granted.value, denied.value,
                                onStart = { starts++ }, onStop = { stops++ },
                                onPermission = { permissionRequests++; granted.value = true }, onPermissionSettings = {})
                        }
                    }
                }
            }
        } }
    }

    @Test fun permissionNeverStartsCaptureAndStopRemainsAvailableWhileBlocked() {
        show()
        compose.onNodeWithText("Mulai bicara").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, permissionRequests); assertEquals(0, starts) }
        compose.onNodeWithText("Mulai bicara").performClick()
        compose.runOnIdle { assertEquals(1, starts); phase.value = TalkbackState.Starting; blocked.value = TalkbackError.BUSY }
        compose.onNodeWithText("Batalkan mulai bicara").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, stops); phase.value = TalkbackState.Sending }
        compose.onNodeWithText("Berhenti bicara").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, stops); phase.value = TalkbackState.Stopping }
        compose.onAllNodesWithText("Menghentikan mikrofon…", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodes(hasText("Menghentikan mikrofon…") and hasClickAction()).onFirst().assertIsNotEnabled()
    }

    @Test fun localizedStatesThemesLargeTypeAndLandscapeAreReadable() {
        show()
        for (lang in listOf("id", "en")) for (isDark in listOf(false, true)) {
            compose.runOnIdle {
                language.value = lang; dark.value = isDark; large.value = true
                phase.value = TalkbackState.Idle; blocked.value = TalkbackError.UNSUPPORTED_AUDIO
            }
            compose.onNodeWithText(if (lang == "id") "Mulai bicara" else "Start talking").performScrollTo().assertIsNotEnabled()
            capture("$lang-${if (isDark) "dark" else "light"}-gated-large")
            compose.runOnIdle { blocked.value = null; granted.value = true; phase.value = TalkbackState.Sending }
            compose.onNodeWithText(if (lang == "id") "Berhenti bicara" else "Stop talking").performScrollTo().assertIsEnabled()
            capture("$lang-${if (isDark) "dark" else "light"}-active-large")
        }
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        // Activity recreation installs the normal app; reattach the synthetic surface explicitly.
        show()
        compose.onNodeWithText("Stop talking").performScrollTo().assertIsEnabled()
        capture("en-dark-active-large-landscape")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "talkback-review").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
