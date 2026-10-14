package dev.cctv.v380

import dev.cctv.v380.protocol.IoTCommonProtocol
import dev.cctv.v380.session.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
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
