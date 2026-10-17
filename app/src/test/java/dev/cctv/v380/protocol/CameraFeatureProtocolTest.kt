package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import dev.cctv.v380.session.RecordingSegment
import dev.cctv.v380.ui.RecordingOrder
import dev.cctv.v380.ui.adjacentRecording
import dev.cctv.v380.ui.recordingRange
import dev.cctv.v380.ui.recordingStorageWarning
import java.time.LocalDateTime
import java.time.ZoneId
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
        assertEquals(131584, format.getInt("daid")); assertFalse(format.has("in")); assertFalse(format.has("vsbmp"))
        assertFalse(format.has("no_rsp")); assertEquals(1, format.length())
        val flip = CameraSettingsProtocol.flipParams()
        assertEquals(524800, flip.getInt("daid")); assertEquals(1, flip.getJSONObject("in").getInt("action"))
        assertFalse(flip.has("no_rsp"))
        assertTrue(CameraSettingsProtocol.FORMAT_ACTION_VERIFIED)
        assertFalse(CameraSettingsProtocol.FORMAT_TERMINAL_VERIFIED)
    }

    @Test fun formatEligibilityRequiresAUsableCardAndCompletionRequiresReadyStatus() {
        fun settings(status: Int?) = CameraSettings(
            if (status == null) emptyMap() else mapOf(CameraSettingsProtocol.SD_STATUS to status), emptyMap())
        for (status in listOf(1, 2, 3, 8)) assertTrue("status=$status", CameraSettingsProtocol.formatEligible(settings(status)))
        for (status in listOf(0, 4, 5, 6, 7, 9)) assertFalse("status=$status", CameraSettingsProtocol.formatEligible(settings(status)))
        assertFalse(CameraSettingsProtocol.formatEligible(settings(null)))
        assertFalse(CameraSettingsProtocol.formatEligible(null))
        for (status in listOf(1, 2, 3, 4, 5, 6, 7, 8)) {
            assertEquals("status=$status", status == 1, CameraSettingsProtocol.sdCardReady(settings(status)))
        }
    }

    @Test fun capacityUsesVerifiedPipeFieldsWithoutInventingUnits() {
        fun remaining(raw: String) = CameraSettingsProtocol.remainingPercent(CameraSettings(
            mapOf(CameraSettingsProtocol.SD_CAPACITY to raw), emptyMap()))
        assertEquals(50, remaining("32768|16384|0"))
        assertNull(remaining("32768,16384,0")); assertNull(remaining("0|0|0"))
        assertNull(remaining("10|11|0")); assertNull(remaining("10|-1|0"))
    }

    @Test fun storageWarningUsesExactNinetyPercentThresholdAndFullStatusOverridesMissingCapacity() {
        fun settings(raw: String, status: Int = 1) = CameraSettings(mapOf(
            CameraSettingsProtocol.SD_CAPACITY to raw, CameraSettingsProtocol.SD_STATUS to status), emptyMap())
        assertNull(recordingStorageWarning(settings("1000|101|0"))) // 89.9%, not 90%.
        assertEquals("Kartu SD hampir penuh — 90% terpakai", recordingStorageWarning(settings("1000|100|0")))
        assertEquals("Kartu SD penuh", recordingStorageWarning(settings("1000|0|0")))
        assertEquals("Kartu SD penuh", recordingStorageWarning(settings("invalid", 3)))
        assertNull(recordingStorageWarning(settings("1000|0|0", 5)))
        for (raw in listOf("invalid", "0|0|0", "100|101|0", "100|-1|0", "99999999999999999999|0|0"))
            assertNull(recordingStorageWarning(settings(raw)))
        assertFalse(CameraSettingsProtocol.capacity(settings("1001|101|0"))!!.almostFull)
        assertTrue(CameraSettingsProtocol.capacity(settings("1001|100|0"))!!.almostFull)
        assertFalse(CameraSettingsProtocol.capacity(settings("${Long.MAX_VALUE}|${Long.MAX_VALUE}|0"))!!.almostFull)
        assertTrue(CameraSettingsProtocol.capacity(settings("${Long.MAX_VALUE}|0|0"))!!.almostFull)
    }

    @Test fun recordingOrderUsesNumericTimestampsAndStableTieBreaksWithoutChangingSelectionOrInput() {
        val rows = listOf(RecordingSegment(9000, 12000, 1), RecordingSegment(1000, 3000, 2),
            RecordingSegment(9000, 10000, 2), RecordingSegment(9000, 10000, 1))
        val selected = rows[2]
        val ascending = listOf(rows[1], rows[3], rows[2], rows[0])
        assertEquals(ascending, RecordingOrder.OLDEST_FIRST.sort(rows))
        assertEquals(ascending.reversed(), RecordingOrder.NEWEST_FIRST.sort(rows))
        assertSame(selected, rows[2])
        assertEquals(9000, rows[0].startMs)
        assertTrue(RecordingOrder.NEWEST_FIRST.sort(emptyList()).isEmpty())
    }

    @Test fun recordingNavigationFollowsDisplayedOrderAndStopsAtListBoundaries() {
        val rows = listOf(RecordingSegment(9000, 12000, 1), RecordingSegment(1000, 3000, 2),
            RecordingSegment(5000, 7000, 1))
        for (order in RecordingOrder.entries) {
            val sorted = order.sort(rows)
            assertNull(adjacentRecording(sorted, sorted.first(), -1))
            assertEquals(sorted[1], adjacentRecording(sorted, sorted.first(), 1))
            assertEquals(sorted[0], adjacentRecording(sorted, sorted[1], -1))
            assertEquals(sorted[2], adjacentRecording(sorted, sorted[1], 1))
            assertEquals(sorted[1], adjacentRecording(sorted, sorted.last(), -1))
            assertNull(adjacentRecording(sorted, sorted.last(), 1))
            // Identity remains the segment ID, even if source pre-roll differs.
            assertEquals(sorted[2], adjacentRecording(sorted, sorted[1].copy(sourceStartMs = 0), 1))
        }
        for (offset in listOf(-1, 1)) {
            assertNull(adjacentRecording(emptyList(), rows[0], offset))
            assertNull(adjacentRecording(listOf(rows[0]), rows[0], offset))
            assertNull(adjacentRecording(rows, RecordingSegment(20000, 21000, 1), offset))
            assertNull(adjacentRecording(rows.filter { it.kind == 2 }, rows[1], offset))
        }
    }

    @Test fun recordingLabelsIncludeDatesAndBothDatesAcrossMidnightInTheQueryTimeZone() {
        val zone = ZoneId.of("Asia/Jakarta")
        fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
        assertEquals("14 Oktober 2026\n00:01:04 – 00:05:04", recordingRange(
            RecordingSegment(at("2026-10-14T00:01:04"), at("2026-10-14T00:05:04"), 1), zone))
        assertEquals("14 Oktober 2026, 23:59:00\n– 15 Oktober 2026, 00:03:00", recordingRange(
            RecordingSegment(at("2026-10-14T23:59:00"), at("2026-10-15T00:03:00"), 1), zone))
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
