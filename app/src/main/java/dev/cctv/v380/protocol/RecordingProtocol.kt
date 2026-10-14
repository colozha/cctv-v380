package dev.cctv.v380.protocol

import dev.cctv.v380.session.RecordingSegment
import org.json.JSONObject

data class RecordingPage(val slice: Int, val more: Boolean, val recordings: List<RecordingSegment>)
data class ReplayInformation(val timeId: Int, val infoId: Int, val media: IoTMediaInfo?)

/** Direct IoT TF replay, recovered from RecordFileHelper and HSReplayDataTFV3Transmitter. */
object RecordingProtocol {
    const val LIST = 197152
    const val LIST_RESPONSE = 197153
    const val CONTROL = 197154
    const val CONTROL_RESPONSE = 197155
    const val INFORMATION = 197156
    const val FRAME_WINDOW = 100
    const val MAX_SEGMENTS = 10000

    fun listParams(startMs: Long, endMs: Long, eventsOnly: Boolean = false): JSONObject {
        require(startMs >= 0 && endMs > startMs && endMs - startMs <= 25 * 60 * 60 * 1000L)
        return JSONObject().put("filter", if (eventsOnly) 2 else 15).put("mode", 2)
            .put("start", startMs).put("end", endMs)
    }

    fun parsePage(data: JSONObject, startMs: Long, endMs: Long, previousSlice: Int): RecordingPage {
        val slice = IoTCommonProtocol.exactInt(data, "slice")
        val flag = IoTCommonProtocol.exactInt(data, "flag")
        if (slice != previousSlice + 1 || flag < 0) malformed()
        val result = linkedMapOf<String, RecordingSegment>()
        for (name in listOf("list", "event")) {
            if (data.has(name) && data.optJSONArray(name) == null) malformed()
            val rows = data.optJSONArray(name) ?: continue
            if (rows.length() > MAX_SEGMENTS) malformed()
            repeat(rows.length()) {
                val item = rows.optJSONObject(it) ?: malformed()
                val offset = IoTCommonProtocol.exactLong(item, "s")
                val duration = IoTCommonProtocol.exactInt(item, "d")
                val type = IoTCommonProtocol.exactInt(item, "t") and 15
                val raw = if (item.has("r")) IoTCommonProtocol.exactLong(item, "r") else -1L
                if (offset < 0 || offset >= endMs - startMs || duration !in 1..3600000 || type !in setOf(1, 2, 8) || raw < -1) malformed()
                val start = Math.addExact(startMs, offset)
                val end = Math.addExact(start, duration.toLong())
                if (end > endMs + 3600000 || raw > offset) malformed()
                val segment = RecordingSegment(start, end, type, if (raw >= 0) Math.addExact(startMs, raw) else start)
                result[segment.id] = segment
            }
        }
        // "brief" is a different listing mode, not an empty list of playable segments.
        if (data.optJSONArray("brief")?.length()?.let { it > 0 } == true) malformed()
        return RecordingPage(slice, flag != 0, result.values.toList())
    }

    fun startParams(segment: RecordingSegment, positionMs: Long = segment.startMs, download: Boolean = false,
                    timeId: Int = 1): JSONObject {
        require(segment.kind in setOf(1, 2, 8) && segment.durationMs in 1..3600000 &&
            segment.sourceStartMs in 0..segment.startMs && positionMs in segment.startMs until segment.endMs && timeId in 1..255)
        return JSONObject().put("sts", segment.startMs).put("pts", Math.addExact(if (download) segment.sourceStartMs else positionMs, 1))
            .put("time_id", timeId).put("frames", FRAME_WINDOW).put("speed", 10)
            .put("mode", if (download) 2 else 0).put("video", 1).put("audio", 0).put("rtype", segment.kind)
    }

    fun nextFramesParams(): JSONObject = JSONObject().put("frames", FRAME_WINDOW)
    fun information(json: JSONObject): ReplayInformation {
        val params = json.optJSONObject("params") ?: malformed()
        val timeId = IoTCommonProtocol.exactInt(params, "time_id")
        val infoId = IoTCommonProtocol.exactInt(params, "info_id")
        if (timeId !in 1..255 || infoId !in 1..4) malformed()
        val media = if (infoId == 1) {
            val info = params.optJSONObject("info") ?: malformed()
            IoTMediaProtocol.parseMediaInfo(JSONObject().put("params", info))
        } else null
        return ReplayInformation(timeId, infoId, media)
    }
    private fun malformed(): Nothing = throw IoTProtocolException(IoTFailure.MalformedPacket)
}
