package dev.cctv.v380

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Labelled fixtures, no camera/session/socket activity. */
class CameraManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun alarmDraftAddsDeletesEditsTimeAndHonorsCapacityWithoutOpeningASession() {
        val p = CameraManagementProtocol
        val before = alarmSettings(0, 2)
        val draft = mutableStateOf<Map<Int, Any>>(emptyMap())
        val enabled = mutableStateOf(true)
        var saves = 0
        showAlarm(before, draft, enabled) { p.setParams(draft.value, before, ManagementAbilities(motion = true)); saves++ }
        compose.onNodeWithText("Belum ada interval pada Senin.").assertExists()
        compose.onNodeWithText("Tambah interval").performScrollTo().performClick()
        compose.onNodeWithText("Mulai 00:00:00").assertExists()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithTag("alarm-discard").performScrollTo().performClick()
        compose.onNodeWithTag("alarm-save").assertDoesNotExist()
        compose.onNodeWithText("Tambah interval").performScrollTo().performClick()
        compose.onNodeWithText("Tambah interval").performScrollTo().performClick()
        compose.onNodeWithText("Tambah interval").assertIsNotEnabled()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Hapus interval 2").performScrollTo().performClick()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Mulai 00:00:00").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("01")
        compose.onNodeWithText("Pilih waktu").performClick()
        compose.onNodeWithText("Mulai 01:00:00").assertExists()
        compose.runOnIdle {
            val schedule = draft.value[p.ALARM_SCHEDULE] as CameraSchedule
            assertEquals(3600, schedule.days.getValue("mon").single().start)
        }
        compose.onNodeWithText("Hari: Senin").performScrollTo().performClick()
        compose.onNodeWithText("Selasa · 0 interval").performClick()
        compose.onNodeWithText("Belum ada interval pada Selasa.").assertExists()
        compose.onNodeWithText("Tambah interval").performScrollTo().performClick()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithText("Tambah interval").assertIsNotEnabled()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("alarm-discard").assertIsNotEnabled()
    }

    @Test fun alarmMiddleDeletionPreservesEventBitsAndEmptyWeekNeedsMainSwitchOff() {
        val p = CameraManagementProtocol
        val before = alarmSettings(3, 5)
        val draft = mutableStateOf<Map<Int, Any>>(emptyMap())
        var saved: JSONObject? = null
        showAlarm(before, draft, mutableStateOf(true)) { saved = p.setParams(draft.value, before, ManagementAbilities(motion = true)) }
        compose.onNodeWithText("Hapus interval 2").performScrollTo().performClick()
        compose.onNodeWithTag("alarm-save").performScrollTo().performClick()
        compose.runOnIdle {
            val windows = saved!!.getJSONArray("dplist").getJSONObject(0).getJSONObject("value").getJSONObject("mon").getJSONArray("sche")
            assertEquals(2, windows.length()); assertEquals(42, windows.getJSONObject(1).getInt("vendor"))
            assertEquals(4, windows.getJSONObject(1).getInt("mode"))
        }
        compose.onNodeWithText("Hapus interval 2").performScrollTo().performClick()
        compose.onNodeWithText("Hapus interval 1").performScrollTo().performClick()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Alarm utama").performScrollTo().performClick()
        compose.onNodeWithTag("alarm-save").performScrollTo().assertIsEnabled()
        capture("alarm-empty")
    }

    private fun alarmSettings(count: Int, maximum: Int): CameraSettings {
        val p = CameraManagementProtocol
        val schedule = JSONObject()
        p.dayNames.forEach { day ->
            val windows = JSONArray()
            if (day == "mon") repeat(count) { i -> windows.put(JSONObject().put("enable", true).put("mode", 1 shl i)
                .put("s_time", "${i * 2}:0:0").put("e_time", "${i * 2 + 1}:0:0").put("vendor", 40 + i)) }
            schedule.put(day, JSONObject().put("max", maximum).put("cnt", windows.length()).put("sche", windows))
        }
        val rows = JSONArray()
        mapOf(p.ALARM_MAIN to (count > 0), p.MOTION to true, p.ALARM_SCHEDULE to schedule).forEach { (id, value) ->
            rows.put(JSONObject().put("dpid", id).put("code", 1000).put("value", value))
        }
        return p.parse(JSONObject().put("dplist_result", rows), ManagementGroup.ALARM, ManagementAbilities(motion = true))
    }

    private fun showAlarm(before: CameraSettings, draft: MutableState<Map<Int, Any>>, enabled: MutableState<Boolean>, onSave: () -> Unit) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { V380Theme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Contoh alarm (sintetis, tanpa koneksi)")
                CameraAlarmControls(before, draft.value, ManagementAbilities(motion = true), enabled.value,
                    onChange = { id, value -> draft.value = draft.value + (id to value) }, onSave = onSave,
                    onDiscard = { draft.value = emptyMap() })
            }
        } } }
    }

    @Test fun managementGroupsShowActualFixtureValuesAndLockUnauthenticatedWrites() {
        val profile = CameraProfile(name = "Contoh manajemen (sintetis)", host = "192.0.2.10", deviceId = "management-ui-synthetic")
        val store = ViewModelStore()
        compose.activityRule.scenario.onActivity { activity ->
            val model = CameraViewModel(activity.application)
            store.put("management-review", model)
            val snapshots = mapOf(
                ManagementGroup.ALARM to ManagementSnapshot(profile.id, 1, ManagementGroup.ALARM,
                    CameraSettings(mapOf(CameraManagementProtocol.ALARM_MAIN to true, CameraManagementProtocol.MOTION to false), emptyMap())),
                ManagementGroup.DEVICE to ManagementSnapshot(profile.id, 1, ManagementGroup.DEVICE,
                    CameraSettings(mapOf(CameraManagementProtocol.MODEL to "MODEL-SYNTHETIC", CameraManagementProtocol.SOFTWARE to "VERSION-SYNTHETIC"), emptyMap())))
            val state = CameraFeatureState(profileId = profile.id, management = snapshots, restartLoaded = true, pendingLoaded = true)
            activity.setContent { V380Theme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Contoh manajemen (sintetis)")
                    CameraManagementGroup(model, profile, state, ManagementGroup.ALARM)
                    CameraManagementGroup(model, profile, state, ManagementGroup.LIGHT)
                    CameraManagementGroup(model, profile, state, ManagementGroup.DEVICE)
                }
            } }
        }
        try {
            compose.onNodeWithText("Alarm dan deteksi gerakan").performClick()
            compose.onNodeWithText("Alarm utama").assertExists()
            compose.onNodeWithText("Muat alarm dan deteksi gerakan").assertIsNotEnabled()
            capture("alarm")
            compose.onNodeWithText("Alarm dan deteksi gerakan").performScrollTo().performClick()
            compose.onNodeWithText("Perangkat").performScrollTo().performClick()
            compose.onNodeWithText("MODEL-SYNTHETIC").assertExists()
            compose.onNodeWithText("Restart kamera").performScrollTo().assertIsNotEnabled()
            capture("device")
        } finally { compose.runOnIdle { store.clear() } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "management-review").apply { mkdirs() }
        val mode = context.resources.configuration
        val suffix = if (mode.uiMode and 0x30 == 0x20) "dark" else "light"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(root, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
