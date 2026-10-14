package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CameraAreaProtocolTest {
    private val p = CameraAreaProtocol
    private val m = CameraManagementProtocol
    private fun area(): CameraAlarmArea {
        val raw = JSONObject().put("alarm_area_row_cnt", 2).put("alarm_area_line_cnt", 3)
            .put("alarm_area", JSONArray(listOf(1, 0, 0, 0, 0, 1))).put("vendor", 42)
        return m.parse(JSONObject().put("dplist_result", JSONArray().put(JSONObject().put("dpid", m.ALARM_AREA)
            .put("code", 1000).put("value", raw))), ManagementGroup.ALARM, ManagementAbilities()).values[m.ALARM_AREA] as CameraAlarmArea
    }

    @Test fun rectangularGridUsesImageLocalTopLeftCoordinatesAndRejectsMarginsAndNonfiniteValues() {
        assertEquals(0, p.cellAt(0f, 0f, 300f, 200f, 2, 3))
        assertEquals(1, p.cellAt(100f, 10f, 300f, 200f, 2, 3))
        assertEquals(3, p.cellAt(1f, 100f, 300f, 200f, 2, 3))
        assertEquals(5, p.cellAt(299f, 199f, 300f, 200f, 2, 3))
        for ((x, y) in listOf(-1f to 0f, 300f to 0f, 0f to 200f, Float.NaN to 0f, 0f to Float.POSITIVE_INFINITY))
            assertNull(p.cellAt(x, y, 300f, 200f, 2, 3))
        assertNull(p.cellAt(0f, 0f, 0f, 200f, 2, 3)); assertNull(p.cellAt(0f, 0f, 300f, 200f, 17, 3))
    }

    @Test fun writerChangesOnlyMaskAndKeepsDimensionsAndUnknownCameraFields() {
        val before = area()
        val desired = before.copy(cells = listOf(0, 1, 0, 1, 0, 0), source = "{\"vendor\":999}")
        val settings = CameraSettings(mapOf(m.ALARM_AREA to before), emptyMap())
        val wire = m.setParams(mapOf(m.ALARM_AREA to desired), settings, ManagementAbilities(lensType = 0, viewCount = 1))
            .getJSONArray("dplist").getJSONObject(0).getJSONObject("value")
        assertEquals(42, wire.getInt("vendor")); assertEquals(2, wire.getInt("alarm_area_row_cnt")); assertEquals(3, wire.getInt("alarm_area_line_cnt"))
        assertEquals(1, wire.getJSONArray("alarm_area").getInt(1)); assertEquals(0, wire.getJSONArray("alarm_area").getInt(0))
        p.write(before.copy(cells = List(6) { 0 }), before)
        assertThrows(IllegalArgumentException::class.java) { p.write(before.copy(rows = 3, columns = 2), before) }
        assertThrows(IllegalArgumentException::class.java) { p.write(before.copy(cells = listOf(0)), before) }
        assertThrows(IllegalArgumentException::class.java) { p.write(before.copy(cells = List(6) { 2 }), before) }
        assertThrows(IllegalArgumentException::class.java) { m.setParams(mapOf(m.ALARM_AREA to desired), settings, ManagementAbilities()) }
    }

    @Test fun capabilityRequiresExplicitFlatLensAndOneViewWithoutVendorUnknownFallback() {
        fun ability(lens: Any, count: Int) = JSONObject().put("misc", JSONArray(listOf(0, 0, lens)))
            .put("msc", JSONArray(List(count) { JSONObject() }))
        assertTrue(m.abilities(ability(0, 1)).flatArea)
        assertFalse(m.abilities(ability(1, 1)).flatArea); assertFalse(m.abilities(ability(0, 2)).flatArea)
        assertFalse(m.abilities(JSONObject().put("misc", JSONArray(listOf(0, 0, 0)))).flatArea)
        assertFalse(m.abilities(JSONObject().put("msc", JSONArray().put(JSONObject()))).flatArea)
        assertThrows(IoTProtocolException::class.java) { m.abilities(ability(0.5, 1)) }
    }

    @Test fun referencesRejectEveryChangedOwnerGenerationOrientationAndGridBaseline() {
        val baseline = area(); val context = AreaContext("p", "d", 2, 8, 19)
        val reference = AreaReference(context, baseline, VideoGeometry(640, 480))
        assertTrue(reference.matches(context, baseline))
        for (changed in listOf(context.copy(profileId = "other"), context.copy(deviceId = "other"), context.copy(revision = 3),
            context.copy(generation = 9), context.copy(orientation = 20))) assertFalse(reference.matches(changed, baseline))
        assertFalse(reference.matches(context, baseline.copy(cells = List(6) { 0 })))
        assertFalse(reference.matches(context, baseline.copy(rows = 3, columns = 2)))
        assertFalse(reference.matches(context, baseline.copy(source = "{}")))
        assertFalse(AreaReference(context, baseline, VideoGeometry(640, 480, rotation = 90)).matches(context, baseline))
    }

    @Test fun referenceGeometryNeverGuessesCroppingOrRotationAndBitmapBudgetIsBounded() {
        assertEquals(1024 to 576, VideoGeometry(1920, 1080).copySize())
        assertEquals(640 to 480, VideoGeometry(640, 480).copySize())
        assertEquals(576 to 1024, VideoGeometry(1080, 1920).copySize())
        for (geometry in listOf(VideoGeometry(0, 100), VideoGeometry(8193, 1), VideoGeometry(100, 100, left = 1),
            VideoGeometry(1920, 1088, bottom = 1079), VideoGeometry(640, 480, rotation = 180))) {
            assertFalse(geometry.flat); assertThrows(IllegalArgumentException::class.java) { geometry.copySize() }
        }
        assertFalse(p.valid(area().copy(rows = 17)))
    }
}
