package dev.cctv.v380

import dev.cctv.v380.protocol.IoTCommonProtocol
import dev.cctv.v380.session.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun talkbackObservationExportsOnlyFormatAndEqualityFlags() {
        val diagnostics = Diagnostics()
        diagnostics.record(DiagnosticStage.TALKBACK, detail = DiagnosticDetail.TALKBACK_METADATA_ENVELOPE,
            talkbackMetadata = TalkbackMetadataObservation(939722786, 2, false, true, 100, 8000, 16, 1))
        val exported = JSONObject(diagnostics.exportJson()).getJSONArray("events").getJSONObject(0)
            .getJSONObject("talkbackMetadata")
        assertEquals(setOf("command", "security", "sessionMatches", "hasAudio", "codec", "sampleRate", "bits", "channels"),
            exported.keys().asSequence().toSet())
        assertFalse(exported.getBoolean("sessionMatches"))
        assertEquals(100, exported.getInt("codec"))
    }

    @Test fun streamFailureExportsTypedStageAndPacketMetadataWithoutInventingAnAuthFailure() {
        val diagnostics = Diagnostics()
        diagnostics.record(DiagnosticStage.AUTH)
        diagnostics.record(DiagnosticStage.FRAME)
        diagnostics.record(DiagnosticStage.STREAM, DiagnosticStatus.FAILED,
            reason = SessionError.MalformedPacket, detail = DiagnosticDetail.MEDIA_PACKET,
            command = IoTCommonProtocol.COMMAND_MEDIA, bytes = 39, framesReceived = 90, downloadBytes = 304128L, timeId = 1)
        val json = JSONObject(diagnostics.exportJson())
        assertEquals(4, json.getInt("schema"))
        val events = json.getJSONArray("events")
        assertEquals("OK", events.getJSONObject(0).getString("status"))
        val failure = events.getJSONObject(2)
        assertEquals("STREAM", failure.getString("stage"))
        assertEquals("MalformedPacket", failure.getString("reason"))
        assertEquals("MEDIA_PACKET", failure.getString("detail"))
        assertEquals(IoTCommonProtocol.COMMAND_MEDIA, failure.getInt("command"))
        assertEquals(39, failure.getInt("bytes"))
        assertEquals(90, failure.getInt("framesReceived")); assertEquals(304128L, failure.getLong("downloadBytes"))
        assertEquals(1, failure.getInt("timeId"))
        assertFalse(failure.has("payload")); assertFalse(failure.has("sessionId"))
        assertFalse(failure.has("password")); assertFalse(failure.has("key"))
        repeat(100) { diagnostics.record(DiagnosticStage.PTZ) }
        assertEquals(80, diagnostics.events.value.size)
    }
}
