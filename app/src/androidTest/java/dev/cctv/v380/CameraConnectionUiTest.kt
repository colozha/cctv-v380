package dev.cctv.v380

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Connection callbacks and session states only; transport must never open. */
class CameraConnectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val store = ViewModelStore()
    private lateinit var model: CameraViewModel
    private val profile = CameraProfile(id = "connection-ui-synthetic", name = "Koneksi (sintetis)",
        host = "192.0.2.10", deviceId = "connection-ui-synthetic", hasSavedPassword = true)
    private var connections = 0

    @Before fun fixture() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            model = CameraViewModel(activity.application,
                sessionOverride = CameraSession(channelFactory = { error("Synthetic UI must not open transport") }))
            store.put("connection", model)
            model.prepareFeatures(profile)
        }
        compose.waitUntil(5000) { !model.ui.value.loading && model.features.value.pendingLoaded }
    }

    @After fun cleanup() { compose.activityRule.scenario.onActivity { store.clear() } }

    @Test fun settingsConnectsOnceAfterLeavingLiveAndRecordings() {
        showDetail()
        compose.runOnIdle { assertEquals(1, connections) }
        tap(CameraTab.SETTINGS)
        compose.waitUntil(5000) { connections == 2 }
        tap(CameraTab.RECORDINGS)
        compose.waitUntil(5000) { connections == 3 }
        tap(CameraTab.SETTINGS)
        compose.waitUntil(5000) { connections == 4 }
        tap(CameraTab.SETTINGS)
        compose.runOnIdle { assertEquals("Selecting the current tab must not retry", 4, connections) }
    }

    @Test fun tabsWithoutSavedPasswordWaitForManualCredentials() {
        showDetail(profile.copy(hasSavedPassword = false))
        tap(CameraTab.SETTINGS)
        compose.onNodeWithText(text(R.string.copy_password_lokal_kamera_03c234af)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.copy_sambungkan_kamera_d52ffb30)).assertIsEnabled()
        tap(CameraTab.RECORDINGS)
        compose.runOnIdle { assertEquals(0, connections) }
    }

    @Test fun retainedSettingsConnectsWhenDetailIsReopened() {
        compose.runOnIdle { model.selectTab(CameraTab.SETTINGS) }
        compose.waitForIdle()
        showDetail()
        compose.runOnIdle { assertEquals(1, connections) }
    }

    @Test fun cancelledTransitionsCannotConnectWhenTheSameTabIsSelectedAgain() {
        compose.runOnIdle { model.selectTab(CameraTab.RECORDINGS) }
        compose.waitForIdle()
        showDetail()
        compose.runOnIdle { assertEquals(1, connections) }
        val operations = field(model.session, "operations") as Mutex
        runBlocking { operations.lock() }
        try {
            tap(CameraTab.SETTINGS)
            tap(CameraTab.RECORDINGS)
            tap(CameraTab.SETTINGS)
            compose.runOnIdle { assertEquals("Wait for session cleanup", 1, connections) }
        } finally { operations.unlock() }
        compose.waitUntil(5000) { connections == 2 }
        compose.runOnIdle { assertEquals(2, connections) }
    }

    @Test fun staleProfileTransitionCannotRequestCredentialsForTheOldCamera() {
        showDetail()
        val operations = field(model.session, "operations") as Mutex
        runBlocking { operations.lock() }
        try {
            tap(CameraTab.SETTINGS)
            compose.runOnIdle { model.prepareFeatures(profile.copy(id = "other-synthetic-profile")) }
        } finally { operations.unlock() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, connections) }
    }

    @Test fun idleShowsConnectAndOnlyActiveConnectionStatesShowSpinner() {
        var state by mutableStateOf<SessionState>(SessionState.Idle)
        var saved by mutableStateOf(true)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { V380Theme { Column {
                CameraAccess(model, profile.copy(hasSavedPassword = saved), state, { connections++ })
            } } }
        }
        val connect = text(R.string.copy_sambungkan_kamera_d52ffb30)
        val connecting = text(R.string.copy_menghubungkan_kamera_f97aa297)
        compose.onNodeWithText(connect).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, connections) }
        for (active in listOf(SessionState.Connecting, SessionState.Authenticating, SessionState.Reconnecting(1))) {
            compose.runOnIdle { state = active }
            compose.onNodeWithText(connecting).assertIsDisplayed()
            compose.onNodeWithText(connect).assertDoesNotExist()
        }
        for (error in listOf(SessionError.AuthenticationFailed, SessionError.Timeout)) {
            compose.runOnIdle { state = SessionState.Error(error) }
            compose.onNodeWithText(connecting).assertDoesNotExist()
            compose.onNodeWithText(connect).assertIsEnabled()
        }
        compose.runOnIdle { state = SessionState.Idle; saved = false }
        compose.onNodeWithText(text(R.string.copy_password_lokal_kamera_03c234af)).assertIsDisplayed()
        compose.onNodeWithText(connect).assertIsEnabled()
        compose.runOnIdle { assertEquals("State changes must not automatically retry", 1, connections) }
    }

    private fun showDetail(camera: CameraProfile = profile) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { V380Theme {
                val state by model.session.state.collectAsState()
                LivePlayerScreen(model, camera, state, null, {}, {}, {
                    assertNull("Automatic connection uses the saved local password", it)
                    assertEquals(SessionState.Idle, model.session.state.value)
                    connections++
                }, {})
            } }
        }
        compose.waitForIdle()
    }

    private fun tap(tab: CameraTab) {
        val label = when (tab) {
            CameraTab.LIVE -> "Live"
            CameraTab.RECORDINGS -> text(R.string.copy_rekaman_a42a01d1)
            CameraTab.SETTINGS -> text(R.string.copy_pengaturan_6d75e64b)
        }
        compose.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun text(id: Int) = compose.activity.getString(id)
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
}
