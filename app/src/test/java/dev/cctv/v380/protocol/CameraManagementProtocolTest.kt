package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CameraManagementProtocolTest {
    private val p = CameraManagementProtocol
    private fun row(id: Int, value: Any, code: Int = 1000) = JSONObject().put("dpid", id).put("value", value).put("code", code)
    private fun data(vararg rows: JSONObject) = JSONObject().put("dplist_result", JSONArray(rows.toList()))
    private fun window(enable: Any = true) = JSONObject().put("enable", enable).put("s_time", "1:0:0").put("e_time", "2:0:0").put("mode", 9).put("vendor", 42)
    private fun schedule() = JSONObject().also { obj -> p.dayNames.forEach { obj.put(it, JSONObject().put("cnt", 1).put("max", 3).put("sche", JSONArray().put(window()))) } }

    @Test fun capabilitiesArePerViewAndCalibrationDoesNotFollowDirectionalBits() {
        val data = JSONObject().put("ctrl", JSONArray(listOf(19, 8, 1))).put("misc", JSONArray(List(6) { 0 } + 1))
            .put("light", JSONArray(listOf(0, 0, 1, 1))).put("alert", JSONArray(listOf(0, 0, 1)))
        val ability = p.abilities(data)
        assertEquals(8, ability.presets); assertEquals(true, ability.calibration); assertEquals(true, ability.restart)
        assertEquals(true, ability.patrol); assertEquals(true, ability.lightSchedule); assertEquals(true, ability.motion)
        val directional = p.abilities(JSONObject().put("ctrl", JSONArray(listOf(3, 8, 0))))
        assertEquals(false, directional.calibration)
        val views = data.put("msc", JSONArray().put(JSONObject().put("ctrl", JSONArray(listOf(3, 2, 0)))))
        assertEquals(2, p.abilities(views).presets); assertEquals(false, p.abilities(views).calibration)
        assertNull(p.abilities(data.put("msc", JSONArray().put(JSONObject()))).presets)
        assertNull(p.abilities(JSONObject()).restart)
    }

    @Test fun readsKeepMissingAndDeniedPropertiesUnknownAndCheckCorrelation() {
        val settings = p.parse(data(row(p.ALARM_MAIN, true), row(p.MOTION, false, 1109)), ManagementGroup.ALARM, ManagementAbilities())
        assertEquals(true, settings.bool(p.ALARM_MAIN)); assertNull(settings.bool(p.MOTION)); assertEquals(1109, settings.rejected[p.MOTION])
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.ALARM_MAIN, true), row(p.ALARM_MAIN, false)), ManagementGroup.ALARM, ManagementAbilities()) }
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.SOFTWARE, "x")), ManagementGroup.ALARM, ManagementAbilities()) }
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.ALARM_MAIN, 1.5)), ManagementGroup.ALARM, ManagementAbilities()) }
        val otherView = row(p.ALARM_MAIN, true).put("vs", 1)
        assertTrue(p.parse(data(otherView), ManagementGroup.ALARM, ManagementAbilities()).values.isEmpty())
    }

    @Test fun alarmWritePreservesUnownedEventBitsAndVendorFields() {
        val before = p.parse(data(row(p.ALARM_SCHEDULE, schedule())), ManagementGroup.ALARM, ManagementAbilities())
        val original = before.values[p.ALARM_SCHEDULE] as CameraSchedule
        val mon = original.days.getValue("mon")[0].copy(start = 5400, source = "{\"vendor\":999}")
        val draft = original.copy(days = original.days + ("mon" to listOf(mon)))
        val wire = p.setParams(mapOf(p.ALARM_SCHEDULE to draft), before, ManagementAbilities()).getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        val value = wire.getJSONObject("mon").getJSONArray("sche").getJSONObject(0)
        assertEquals(9, value.getInt("mode")); assertEquals(42, value.getInt("vendor")); assertEquals("01:30:00", value.getString("s_time"))
        val changedMode = draft.copy(days = original.days + ("mon" to listOf(mon.copy(mode = 1))))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to changedMode), before, ManagementAbilities()) }
    }

    @Test fun invalidTimesAndOverlapsNeverSerialize() {
        assertThrows(Exception::class.java) { p.parseTime("24:00:00") }
        assertThrows(Exception::class.java) { p.parseTime("00:60:00") }
        assertThrows(IllegalArgumentException::class.java) { p.validateWindows(listOf(CameraTimeWindow(300, 100, true))) }
        assertThrows(IllegalArgumentException::class.java) { p.validateWindows(listOf(CameraTimeWindow(100, 300, true), CameraTimeWindow(200, 400, true))) }
        assertEquals(3661, p.parseTime("1:1:1"))
    }

    @Test fun alarmMiddleDeletionKeepsEachSurvivingIntervalsOwnMetadata() {
        val raw = schedule().put("vendor_week", 13)
        val mon = raw.getJSONObject("mon")
        val windows = JSONArray()
        repeat(3) { i -> windows.put(window().put("s_time", "${i * 2}:0:0").put("e_time", "${i * 2 + 1}:0:0")
            .put("mode", 1 shl i).put("vendor", 40 + i)) }
        mon.put("cnt", 3).put("sche", windows)
        val before = p.parse(data(row(p.ALARM_SCHEDULE, raw)), ManagementGroup.ALARM, ManagementAbilities())
        val old = before.values[p.ALARM_SCHEDULE] as CameraSchedule
        val draft = old.copy(days = old.days + ("mon" to old.days.getValue("mon").filterIndexed { i, _ -> i != 1 }))
        val wire = p.setParams(mapOf(p.ALARM_SCHEDULE to draft), before, ManagementAbilities()).getJSONArray("dplist")
            .getJSONObject(0).getJSONObject("value")
        val kept = wire.getJSONObject("mon").getJSONArray("sche")
        assertEquals(2, wire.getJSONObject("mon").getInt("cnt"))
        assertEquals(40, kept.getJSONObject(0).getInt("vendor")); assertEquals(42, kept.getJSONObject(1).getInt("vendor"))
        assertEquals(4, kept.getJSONObject(1).getInt("mode")); assertEquals(13, wire.getInt("vendor_week"))
        assertEquals(3, wire.getJSONObject("mon").getInt("max"))
        val reordered = JSONObject(wire.toString()).also { it.getJSONObject("mon").put("sche", JSONArray()
            .put(kept.getJSONObject(1)).put(kept.getJSONObject(0))) }
        val returned = p.parse(data(row(p.ALARM_SCHEDULE, reordered)), ManagementGroup.ALARM, ManagementAbilities())
        assertTrue(p.same(p.ALARM_SCHEDULE, draft, returned.values[p.ALARM_SCHEDULE]))
        assertFalse(p.sameBaseline(p.ALARM_SCHEDULE, draft, returned.values[p.ALARM_SCHEDULE]))
        val duplicate = old.copy(days = old.days + ("mon" to listOf(old.days.getValue("mon")[0], old.days.getValue("mon")[0])))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to duplicate), before, ManagementAbilities()) }
        val inventedOrigin = old.copy(days = old.days + ("mon" to listOf(old.days.getValue("mon")[0].copy(origin = 99))))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to inventedOrigin), before, ManagementAbilities()) }
    }

    @Test fun alarmNewIntervalsUseProvenDefaultsAndRespectConservativeLimits() {
        val before = p.parse(data(row(p.ALARM_MAIN, true), row(p.MOTION, true), row(p.ALARM_SCHEDULE, schedule())), ManagementGroup.ALARM, ManagementAbilities())
        val old = before.values[p.ALARM_SCHEDULE] as CameraSchedule
        val new = p.newAlarmInterval()
        assertEquals(0, new.start); assertEquals(86399, new.end); assertTrue(new.enabled); assertEquals(1, new.mode)
        val added = new.copy(start = 10800, end = 14400, source = "{\"vendor\":999}")
        val draft = old.copy(days = old.days + ("mon" to (old.days.getValue("mon") + added)))
        val ability = ManagementAbilities(motion = true)
        val wire = p.setParams(mapOf(p.ALARM_SCHEDULE to draft), before, ability).getJSONArray("dplist").getJSONObject(0)
            .getJSONObject("value").getJSONObject("mon")
        assertEquals(2, wire.getInt("cnt")); assertEquals(1, wire.getJSONArray("sche").getJSONObject(1).getInt("mode"))
        assertFalse(wire.getJSONArray("sche").getJSONObject(1).has("vendor"))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to draft), before, ManagementAbilities()) }
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to draft, p.MOTION to false), before, ability) }
        assertEquals(3, p.alarmIntervalLimit(old, "mon"))
        assertEquals(5, p.alarmIntervalLimit(old.copy(maximums = old.maximums + ("mon" to 20)), "mon"))
        assertEquals(1, p.alarmIntervalLimit(old.copy(maximums = old.maximums + ("mon" to null)), "mon"))
        val tooMany = old.copy(days = old.days + ("mon" to List(4) { added.copy(start = it * 3600, end = it * 3600 + 1800) }))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to tooMany), before, ability) }
    }

    @Test fun emptyAlarmDaysRoundTripAndEmptyWeekRequiresMainSwitchOff() {
        val before = p.parse(data(row(p.ALARM_MAIN, true), row(p.ALARM_SCHEDULE, schedule())), ManagementGroup.ALARM, ManagementAbilities())
        val old = before.values[p.ALARM_SCHEDULE] as CameraSchedule
        val dayEmpty = old.copy(days = old.days + ("mon" to emptyList()))
        val wire = p.setParams(mapOf(p.ALARM_SCHEDULE to dayEmpty), before, ManagementAbilities()).getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        assertEquals(0, wire.getJSONObject("mon").getInt("cnt")); assertEquals(0, wire.getJSONObject("mon").getJSONArray("sche").length())
        val after = p.parse(data(row(p.ALARM_SCHEDULE, wire)), ManagementGroup.ALARM, ManagementAbilities())
        assertTrue(p.same(p.ALARM_SCHEDULE, dayEmpty, after.values[p.ALARM_SCHEDULE]))
        val weekEmpty = old.copy(days = p.dayNames.associateWith { emptyList() })
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.ALARM_SCHEDULE to weekEmpty), before, ManagementAbilities()) }
        p.setParams(mapOf(p.ALARM_SCHEDULE to weekEmpty, p.ALARM_MAIN to false), before, ManagementAbilities())
        assertNotNull(p.alarmScheduleError(weekEmpty, old, null, false, false))
    }

    @Test fun alarmPreflightRejectsChangedLimitsAndMetadataButReadbackIgnoresOriginIndices() {
        val old = p.parse(data(row(p.ALARM_SCHEDULE, schedule())), ManagementGroup.ALARM, ManagementAbilities()).values[p.ALARM_SCHEDULE] as CameraSchedule
        val changed = schedule().also { it.getJSONObject("mon").put("max", 2) }
        val fresh = p.parse(data(row(p.ALARM_SCHEDULE, changed)), ManagementGroup.ALARM, ManagementAbilities()).values[p.ALARM_SCHEDULE] as CameraSchedule
        assertTrue(p.same(p.ALARM_SCHEDULE, old, fresh)); assertFalse(p.sameBaseline(p.ALARM_SCHEDULE, old, fresh))
        val otherMetadata = schedule().put("vendor", 3)
        val other = p.parse(data(row(p.ALARM_SCHEDULE, otherMetadata)), ManagementGroup.ALARM, ManagementAbilities()).values[p.ALARM_SCHEDULE]
        assertFalse(p.sameBaseline(p.ALARM_SCHEDULE, old, other))
        val renumbered = old.copy(days = old.days.mapValues { (_, w) -> w.map { it.copy(origin = null) } })
        assertTrue(p.same(p.ALARM_SCHEDULE, old, renumbered))
        assertNotNull(p.alarmScheduleError(old.copy(maximums = fresh.maximums), old, true, true, true))
    }

    @Test fun alarmRejectsMalformedCountsTimesAndUnprovenSensitivity() {
        val malformed = schedule().also { it.getJSONObject("mon").put("cnt", 0) }
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.ALARM_SCHEDULE, malformed)), ManagementGroup.ALARM, ManagementAbilities()) }
        val invalidMax = schedule().also { it.getJSONObject("mon").put("max", 1.5) }
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.ALARM_SCHEDULE, invalidMax)), ManagementGroup.ALARM, ManagementAbilities()) }
        assertNotNull(p.alarmWindowsError(listOf(CameraTimeWindow(300, 100, false))))
        assertNotNull(p.alarmWindowsError(listOf(CameraTimeWindow(0, 600, true), CameraTimeWindow(300, 900, true))))
        assertFalse(p.MOTION_SENSITIVITY_VERIFIED); assertFalse(p.ids(ManagementGroup.ALARM).contains(p.MOTION_SENSITIVITY))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.MOTION_SENSITIVITY to 2), CameraSettings(mapOf(p.MOTION_SENSITIVITY to 1), emptyMap()), ManagementAbilities()) }
    }

    @Test fun lightScheduleRequiresItsOwnAbilityAndRetainsCameraMetadata() {
        val raw = JSONObject().put("defaultAction", 0).put("capability", 3).put("total", 1)
            .put("vendor", 7).put("time_schedule", JSONArray().put(window(1)))
        val before = p.parse(data(row(p.LIGHT_SCHEDULE, raw)), ManagementGroup.LIGHT, ManagementAbilities())
        val old = before.values[p.LIGHT_SCHEDULE] as CameraLightSchedule
        val draft = old.copy(windows = listOf(old.windows[0].copy(enabled = false)))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.LIGHT_SCHEDULE to draft), before, ManagementAbilities()) }
        val wire = p.setParams(mapOf(p.LIGHT_SCHEDULE to draft), before, ManagementAbilities(lightSchedule = true)).getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        assertEquals(7, wire.getInt("vendor")); assertEquals(1, wire.getInt("total")); assertEquals(0, wire.getJSONArray("time_schedule").getJSONObject(0).getInt("enable"))
        assertFalse(p.same(p.LIGHT_SCHEDULE, old, draft))
        val removed = old.copy(total = 0, windows = emptyList())
        val cleared = p.setParams(mapOf(p.LIGHT_SCHEDULE to removed), before, ManagementAbilities(lightSchedule = true))
            .getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        assertEquals(0, cleared.getInt("total")); assertEquals(0, cleared.getJSONArray("time_schedule").length())
        val empty = p.parse(data(row(p.LIGHT_SCHEDULE, cleared)), ManagementGroup.LIGHT, ManagementAbilities())
        p.setParams(mapOf(p.LIGHT_SCHEDULE to old), empty, ManagementAbilities(lightSchedule = true))
    }

    @Test fun presetListUsesZeroBasedCameraSlotsAndRejectsDuplicates() {
        val raw = JSONObject().put("pos_mark_cnt", 2).put("pos_mark_details", JSONArray(listOf(0, 3)))
        val parsed = p.parse(data(row(p.PRESETS, raw)), ManagementGroup.MOTOR, ManagementAbilities(presets = 4))
        assertEquals(listOf(0, 3), (parsed.values[p.PRESETS] as CameraPresets).used)
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.PRESETS, raw)), ManagementGroup.MOTOR, ManagementAbilities(presets = 3)) }
        raw.put("pos_mark_details", JSONArray(listOf(0, 0)))
        assertThrows(IoTProtocolException::class.java) { p.parse(data(row(p.PRESETS, raw)), ManagementGroup.MOTOR, ManagementAbilities(presets = 4)) }
        val action = p.presetParams(3, 2, 4)
        assertEquals(393729, action.getInt("daid")); assertEquals(1, action.getInt("vsbmp")); assertEquals(2, action.getJSONObject("in").getInt("ctrl_flag"))
        assertFalse(action.has("no_rsp"))
        assertThrows(IllegalArgumentException::class.java) { p.presetParams(4, 1, 4) }
    }

    @Test fun routeEditingPreservesDisabledEngineAndCannotActivatePatrol() {
        val raw = JSONObject().put("ptz_auto_enable", false).put("ptz_timer_enable", false)
            .put("ptz_cruise_route_total_cnt", 2).put("ptz_cruise_route_position_max", 3)
            .put("ptz_auto_route_cnt", 0).put("ptz_auto_route_info", JSONArray()).put("vendor", 17)
        val ability = ManagementAbilities(presets = 4, patrol = true)
        val before = p.parse(data(row(p.PATROL, raw), row(p.PRESETS, JSONObject().put("pos_mark_cnt", 1)
            .put("pos_mark_details", JSONArray(listOf(0))))), ManagementGroup.MOTOR, ability)
        val original = before.values[p.PATROL] as CameraPatrol
        val draft = original.copy(routes = listOf(CameraPatrolRoute(0, 86399, listOf(CameraPatrolPoint(0, 60)))))
        val wire = p.setParams(mapOf(p.PATROL to draft), before, ability).getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        assertFalse(wire.getBoolean("ptz_auto_enable")); assertFalse(wire.getBoolean("ptz_timer_enable"))
        assertEquals(17, wire.getInt("vendor")); assertEquals(1, wire.getInt("ptz_auto_route_cnt"))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.PATROL to draft.copy(enabled = true)), before, ability) }
        val invalid = draft.copy(routes = listOf(CameraPatrolRoute(0, 86399, listOf(CameraPatrolPoint(1, 60)))))
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.PATROL to invalid), before, ability) }
    }

    @Test fun restartAndCalibrationAreSeparateReplyRequiredActionsAndFormatTerminalRemainsGated() {
        val reboot = p.restartParams(); assertEquals(66050, reboot.getInt("daid"))
        assertFalse(reboot.has("in")); assertFalse(reboot.has("no_rsp"))
        assertEquals(393732, p.calibrateParams().getInt("daid")); assertEquals(1, p.calibrateParams().getInt("vsbmp"))
        assertTrue(CameraSettingsProtocol.FORMAT_ACTION_VERIFIED)
        assertFalse(p.FORMAT_TERMINAL_VERIFIED); assertFalse(p.PATROL_MOTOR_STATUS_VERIFIED)
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.SOFTWARE to "fake"), CameraSettings(mapOf(p.SOFTWARE to "real"), emptyMap()), ManagementAbilities()) }
    }
}
