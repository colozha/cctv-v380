package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import dev.cctv.v380.session.RecordingSegment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CameraFeatureProtocolTest {
    private fun property(id: Int, value: Any, code: Int = 1000) = JSONObject().put("dpid", id).put("code", code).put("value", value)
    private fun data(vararg rows: JSONObject) = JSONObject().put("dplist_result", JSONArray(rows.toList()))

    @Test fun propertyReadsPreserveUnknownsAndRejectUnrelatedOrDuplicateValues() {
        val p = CameraSettingsProtocol
        val ids = listOf(p.RECORD_MODE, p.RECORD_AUDIO, p.LIGHT_MODE)
        val snapshot = p.parseProperties(data(property(p.RECORD_MODE, 9), property(p.RECORD_AUDIO, true),
            property(p.LIGHT_MODE, 0, 1109)), ids)
        assertEquals(9, snapshot.int(p.RECORD_MODE)); assertEquals(true, snapshot.bool(p.RECORD_AUDIO))
        assertNull(snapshot.int(p.LIGHT_MODE)); assertEquals(1109, snapshot.rejected[p.LIGHT_MODE])
        val missing = p.parseProperties(data(), ids)
        assertTrue(missing.values.isEmpty()); assertTrue(missing.rejected.isEmpty())
        assertThrows(IoTProtocolException::class.java) { p.parseProperties(data(property(p.FLIP_MODE, 1)), ids) }
        assertThrows(IoTProtocolException::class.java) { p.parseProperties(data(property(p.RECORD_MODE, 1), property(p.RECORD_MODE, 2)), ids) }
        assertThrows(IoTProtocolException::class.java) { p.parseProperties(data(property(p.RECORD_MODE, 1.5)), ids) }
    }

    @Test fun changesPreserveUnownedRecordingBitsAndRequireEveryAcknowledgement() {
        val p = CameraSettingsProtocol
        val before = CameraSettings(mapOf(p.RECORD_MODE to 9, p.RECORD_QUALITY to 1, p.RECORD_AUDIO to false), emptyMap())
        val request = p.setParams(mapOf(p.RECORD_MODE to 11, p.RECORD_AUDIO to true), before, setOf(1, 2))
        assertEquals(2, request.getJSONArray("dplist").length())
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.RECORD_MODE to 3), before, setOf(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.RECORD_QUALITY to 3), before, setOf(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { p.setParams(mapOf(p.SD_STATUS to 1), before, setOf(1, 2)) }
        p.validateSet(data(property(p.RECORD_MODE, 11), property(p.RECORD_AUDIO, true)), setOf(p.RECORD_MODE, p.RECORD_AUDIO))
        assertThrows(IoTProtocolException::class.java) { p.validateSet(data(property(p.RECORD_MODE, 11)), setOf(p.RECORD_MODE, p.RECORD_AUDIO)) }
        assertThrows(IoTProtocolException::class.java) { p.validateSet(data(property(p.RECORD_MODE, 11, 1109)), setOf(p.RECORD_MODE)) }
        assertEquals(setOf(1, 3), IoTControlProtocol.recordingQualities(JSONObject().put("misc", JSONArray(List(8) { 0 } + 5))))
    }

    @Test fun destructiveActionOmitsInputAndRequestsAReplyWhileFlipUsesTheRecoveredAction() {
        val format = CameraSettingsProtocol.formatParams()
        assertEquals(131584, format.getInt("daid")); assertFalse(format.has("in")); assertFalse(format.has("no_rsp"))
        val flip = CameraSettingsProtocol.flipParams()
        assertEquals(524800, flip.getInt("daid")); assertEquals(1, flip.getJSONObject("in").getInt("action"))
        assertFalse(flip.has("no_rsp"))
        assertFalse(CameraSettingsProtocol.FORMAT_STATUS_VERIFIED)
    }

    @Test fun capacityUsesVerifiedPipeFieldsWithoutInventingUnits() {
        fun remaining(raw: String) = CameraSettingsProtocol.remainingPercent(CameraSettings(
            mapOf(CameraSettingsProtocol.SD_CAPACITY to raw), emptyMap()))
        assertEquals(50, remaining("32768|16384|0"))
        assertNull(remaining("32768,16384,0")); assertNull(remaining("0|0|0"))
        assertNull(remaining("10|11|0")); assertNull(remaining("10|-1|0"))
    }

    @Test fun recordPagesHaveCorrelatedSlicesBoundedOffsetsAndExplicitCompletion() {
        val start = 1700000000000L
        val row = JSONObject().put("s", 5000L).put("d", 10000).put("t", 2).put("r", 3000L)
        val page = JSONObject().put("slice", 1).put("flag", 0).put("event", JSONArray().put(row))
        val parsed = RecordingProtocol.parsePage(page, start, start + 3600000, 0)
        assertFalse(parsed.more)
        assertEquals(RecordingSegment(start + 5000, start + 15000, 2, start + 3000), parsed.recordings.single())
        assertThrows(IoTProtocolException::class.java) { RecordingProtocol.parsePage(page, start, start + 3600000, 1) }
        assertThrows(IoTProtocolException::class.java) {
            RecordingProtocol.parsePage(JSONObject().put("slice", 1).put("flag", 0).put("list", "invalid"), start, start + 3600000, 0)
        }
        assertThrows(IoTProtocolException::class.java) { RecordingProtocol.parsePage(page.put("event", JSONArray().put(row.put("d", -1))), start, start + 3600000, 0) }
        val request = RecordingProtocol.listParams(start, start + 3600000)
        assertEquals(2, request.getInt("mode")); assertEquals(15, request.getInt("filter"))
    }

    @Test fun replayAndDownloadUseDistinctModesAndRawPrerollWithoutChangingSegmentIdentity() {
        val segment = RecordingSegment(10000, 20000, 2, 8000)
        val play = RecordingProtocol.startParams(segment, 12000)
        assertEquals(10000, play.getLong("sts")); assertEquals(12001, play.getLong("pts")); assertEquals(0, play.getInt("mode"))
        val download = RecordingProtocol.startParams(segment, download = true)
        assertEquals(10000, download.getLong("sts")); assertEquals(8001, download.getLong("pts")); assertEquals(2, download.getInt("mode"))
        assertEquals(100, download.getInt("frames")); assertEquals(0, download.getInt("audio"))
        assertThrows(IllegalArgumentException::class.java) { RecordingProtocol.startParams(segment, 20000) }
        val end = JSONObject().put("params", JSONObject().put("time_id", 1).put("info_id", 3))
        assertEquals(3, RecordingProtocol.information(end).infoId)
        assertThrows(IoTProtocolException::class.java) { RecordingProtocol.information(JSONObject().put("params", JSONObject().put("time_id", 0).put("info_id", 3))) }
    }
}
