package dev.cctv.v380

import android.accessibilityservice.AccessibilityService
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.protocol.CameraSettingsProtocol as P
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Synthetic local state/picker checks only; no camera authentication or network transfers. */
class RecordingWorkflowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val profile = CameraProfile(name = "Contoh rekaman (sintetis)", host = "192.0.2.10", deviceId = "recording-workflow")
    private val start = LocalDate.of(2026, 10, 14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 8 * 3600000
    private val segment = RecordingSegment(start, start + 240000, 1)

    @Test fun exportSelectionSurvivesPickerHandoffRecreationAndReconnectWaitAndRejectsASecondPicker() {
        val store = ViewModelStore()
        val saved = SavedStateHandle()
        compose.activityRule.scenario.onActivity { activity ->
            val model = CameraViewModel(activity.application, saved)
            store.put("original", model)
            model.prepareFeatures(profile); model.selectTab(CameraTab.RECORDINGS); model.requestPlayback(segment); model.pausePlayback()
            runBlocking {
                assertTrue(model.beginRecordingExport(segment))
                assertFalse(model.beginRecordingExport(segment))
            }
            model.onBackground()
            assertEquals(DownloadPhase.CHOOSING_DESTINATION, model.features.value.download)
            val snapshot = Bundle(saved.get<Bundle>("recordingExport")!!)
            assertEquals(profile.id, snapshot.getString("profileId"))
            for (secret in listOf("password", "key", "sessionId")) assertFalse(snapshot.containsKey(secret))
            val restored = CameraViewModel(activity.application, SavedStateHandle(mapOf("recordingExport" to snapshot)))
            store.put("restored", restored)
            assertEquals(segment, restored.features.value.selected)
            assertEquals(profile.id, restored.features.value.profileId)
            restored.completeRecordingExport(Uri.parse("content://synthetic-recording/one"))
            assertEquals(DownloadPhase.AWAITING_ACCESS, restored.features.value.download)
            assertTrue(restored.features.value.notice!!.contains("Sambungkan kamera"))
            restored.onBackground()
            assertEquals(DownloadPhase.AWAITING_ACCESS, restored.features.value.download)
            // Do not leave a synthetic URI queued for deletion during teardown.
            restored.completeRecordingExport(null)
            model.completeRecordingExport(null)
            assertEquals(DownloadPhase.IDLE, restored.features.value.download)
            assertNull(saved.get<Bundle>("recordingExport"))
            store.clear()
        }
    }

    @Test fun backFromPlayerReturnsToListAndRepeatedDateOrFilterKeepsSelection() {
        lateinit var model: CameraViewModel
        var cameraExits = 0
        compose.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[CameraViewModel::class.java]
            model.prepareFeatures(profile); model.selectTab(CameraTab.RECORDINGS)
            model.requestPlayback(segment); model.pausePlayback()
            model.setRecordingOrder(RecordingOrder.OLDEST_FIRST)
            model.setRecordingDate(model.features.value.date)
            model.setEventsOnly(model.features.value.eventsOnly)
            assertEquals(segment, model.features.value.selected)
            activity.setContent { V380Theme {
                val feature by model.features.collectAsState()
                LivePlayerScreen(model, profile, SessionState.Idle, null, { cameraExits++ }, {}, {}, {})
            } }
        }
        compose.onNodeWithContentDescription("Kembali ke daftar rekaman").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5000) { model.features.value.selected == null }
        compose.runOnIdle {
            assertEquals(0, cameraExits)
            assertEquals(RecordingOrder.OLDEST_FIRST, model.features.value.recordingOrder)
        }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, cameraExits) }
    }

    @Test fun stalePickerFromAnotherProfileCannotExportItsRecording() {
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[CameraViewModel::class.java]
            model.prepareFeatures(profile); model.selectTab(CameraTab.RECORDINGS); model.requestPlayback(segment); model.pausePlayback()
            runBlocking { assertTrue(model.beginRecordingExport(segment)) }
            model.prepareFeatures(profile.copy(id = "another-profile"))
            model.completeRecordingExport(Uri.parse("content://synthetic-recording/stale"))
            assertEquals("another-profile", model.features.value.profileId)
            assertNull(model.features.value.selected)
            assertFalse(model.features.value.download == DownloadPhase.DOWNLOADING)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun nativePickerOpensWithoutCheckboxAndCancelPreservesSelectedRecording() {
        lateinit var model: CameraViewModel
        compose.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[CameraViewModel::class.java]
            model.prepareFeatures(profile); model.selectTab(CameraTab.RECORDINGS); model.requestPlayback(segment); model.pausePlayback()
            activity.setContent { V380Theme {
                val feature by model.features.collectAsState()
                val session by model.session.state.collectAsState()
                Scaffold(topBar = { TopAppBar(title = { Text("Contoh UI (sintetis)") }) }) { padding ->
                    RecordingsScreen(model, profile, session, feature, {}, Modifier.padding(padding))
                }
            } }
        }
        compose.onNodeWithText("Simpan video tanpa audio").assertDoesNotExist()
        compose.onNodeWithText(recordingTimes(segment)).performScrollTo().assertExists()
        compose.onNodeWithText("Simpan rekaman").performScrollTo().assertIsEnabled()
        compose.activityRule.scenario.onActivity { activity ->
            fun surface(view: android.view.View): android.view.SurfaceView? {
                if (view is android.view.SurfaceView) return view
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) surface(view.getChildAt(i))?.let { return it }
                return null
            }
            val video = surface(activity.window.decorView) ?: error("Native player Surface was disposed while scrolling")
            assertTrue(video.isAttachedToWindow); assertTrue(video.holder.surface.isValid)
        }
        compose.onNodeWithText("Simpan rekaman").performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(15000) { automation.rootInActiveWindow?.packageName?.toString()?.contains("documentsui") == true }
        compose.activityRule.scenario.onActivity { assertEquals(DownloadPhase.CHOOSING_DESTINATION, model.features.value.download) }
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        compose.waitUntil(15000) { model.features.value.download == DownloadPhase.IDLE }
        compose.onNodeWithText("Simpan rekaman").performScrollTo().assertIsEnabled()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            val retained = ViewModelProvider(activity)[CameraViewModel::class.java]
            assertEquals(segment, retained.features.value.selected)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun recordingsExposeSortCapacityWarningAndExplicitUnavailableDeletion() {
        compose.activityRule.scenario.onActivity { activity ->
            val model = ViewModelProvider(activity)[CameraViewModel::class.java]
            activity.setContent { V380Theme {
                val state by model.features.collectAsState()
                Scaffold(topBar = { TopAppBar(title = { Text("Contoh UI (sintetis)") }) }) { padding ->
                    RecordingsScreen(model, profile, SessionState.Idle, CameraFeatureState(profileId = profile.id,
                        tab = CameraTab.RECORDINGS, recordingOrder = state.recordingOrder,
                        recordingStorage = CameraSettings(mapOf(P.SD_STATUS to 1, P.SD_CAPACITY to "1000|100|0"), emptyMap()),
                        recordingsLoaded = true, recordings = listOf(segment, segment.copy(startMs = start + 3600000, endMs = start + 3840000))),
                        {}, Modifier.padding(padding))
                }
            } }
        }
        compose.waitUntil(10000) {
            runCatching { compose.onAllNodesWithText("Kartu SD hampir penuh — 90% terpakai").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Kartu SD hampir penuh — 90% terpakai").assertExists()
        compose.onNodeWithContentDescription("Urutan: Terbaru dahulu").performScrollTo().performClick()
        compose.onNodeWithText("Terlama dahulu").performClick()
        compose.onNodeWithContentDescription("Urutan: Terlama dahulu").assertExists()
        compose.onNodeWithContentDescription("Opsi rekaman ${java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))}")
            .performScrollTo().performClick()
        compose.onNodeWithText(RECORDING_DELETE_UNAVAILABLE).assertIsNotEnabled()
    }
}
