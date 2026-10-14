package dev.cctv.v380.protocol

import dev.cctv.v380.session.CameraSettings
import org.json.JSONArray
import org.json.JSONObject

data class SdCapacity(val total: Long, val remaining: Long) {
    val remainingPercent: Int get() = (remaining.toDouble() / total * 100).toInt()
    val usedPercent: Int get() = ((total - remaining).toDouble() / total * 100).toInt()
    // Integer comparison keeps 89.9% below the threshold, without overflow or rounding.
    val almostFull: Boolean get() = remaining <= total / 10
}

/** V380 6.4.90 Java property API. The native live controller uses a different PID dialect. */
object CameraSettingsProtocol {
    const val GET = 197376
    const val GET_RESPONSE = 197377
    const val SET = 197378
    const val SET_RESPONSE = 197379
    const val SD_STATUS = 131328
    const val SD_CAPACITY = 131329
    const val SD_FORMAT_STATE = 131330
    const val RECORD_MODE = 131331
    const val RECORD_QUALITY = 131332
    const val RECORD_AUDIO = 131333
    const val RECORD_LOOP = 131334
    const val LIGHT_MODE = 327936
    const val LIGHT_SENSITIVITY = 327939
    const val INDICATOR = 327941
    const val FLIP_MODE = 524544
    const val FORMAT = 131584
    const val FLIP = 524800
    // REA: Java explicitly skips this property; no terminal format enum is verified yet.
    const val FORMAT_STATUS_VERIFIED = false
    val properties = listOf(SD_STATUS, SD_CAPACITY, SD_FORMAT_STATE, RECORD_MODE, RECORD_QUALITY,
        RECORD_AUDIO, RECORD_LOOP, LIGHT_MODE, LIGHT_SENSITIVITY, INDICATOR, FLIP_MODE)
    private val booleans = setOf(RECORD_AUDIO, RECORD_LOOP, INDICATOR)

    fun getParams(ids: List<Int>): JSONObject {
        require(ids.isNotEmpty() && ids.size <= 10 && ids.distinct().size == ids.size && ids.all { it in properties })
        return JSONObject().put("dplist", JSONArray(ids))
    }

    fun parseProperties(data: JSONObject, requested: List<Int>): CameraSettings {
        val rows = data.optJSONArray("dplist_result") ?: malformed()
        if (rows.length() > requested.size) malformed()
        val values = linkedMapOf<Int, Any>()
        val rejected = linkedMapOf<Int, Int>()
        val seen = mutableSetOf<Int>()
        repeat(rows.length()) { index ->
            val row = rows.optJSONObject(index) ?: malformed()
            val id = IoTCommonProtocol.exactInt(row, "dpid")
            if (id !in requested || !seen.add(id) || row.has("vs") && IoTCommonProtocol.exactInt(row, "vs") !in -1..0) malformed()
            val code = IoTCommonProtocol.exactInt(row, "code")
            if (code != 1000) rejected[id] = code else {
                val value = when {
                    id in booleans -> when (val raw = row.opt("value")) {
                        is Boolean -> raw
                        is Number -> IoTCommonProtocol.exactInt(row, "value").let { if (it !in 0..1) malformed(); it == 1 }
                        else -> malformed()
                    }
                    id == SD_CAPACITY -> (row.opt("value") as? String)?.takeIf { it.length <= 96 } ?: malformed()
                    else -> IoTCommonProtocol.exactInt(row, "value")
                }
                values[id] = value
            }
        }
        // A missing property remains unknown; never fabricate a default or enable its writer.
        return CameraSettings(values.toMap(), rejected.toMap())
    }

    fun setParams(changes: Map<Int, Any>, before: CameraSettings, qualities: Set<Int>): JSONObject {
        require(changes.isNotEmpty() && changes.size <= 10)
        val rows = JSONArray()
        changes.forEach { (id, value) ->
            require(id in before.values)
            when (id) {
                RECORD_AUDIO, RECORD_LOOP, INDICATOR -> require(value is Boolean)
                RECORD_MODE -> {
                    require(value is Int && value >= 0)
                    // Preserve retrench and every other bit not owned by these two toggles.
                    require(value and 3.inv() == before.int(id)!! and 3.inv())
                }
                RECORD_QUALITY -> require(value is Int && value in qualities && value in 1..3)
                LIGHT_MODE -> require(value is Int && value in 0..2)
                LIGHT_SENSITIVITY -> require(value is Int && value in 1..3)
                else -> throw IllegalArgumentException("Property is read only")
            }
            rows.put(JSONObject().put("dpid", id).put("value", value))
        }
        return JSONObject().put("dplist", rows)
    }

    fun validateSet(data: JSONObject, expected: Set<Int>) {
        val rows = data.optJSONArray("dplist_result") ?: malformed()
        if (rows.length() != expected.size) malformed()
        val seen = mutableSetOf<Int>()
        repeat(rows.length()) {
            val row = rows.optJSONObject(it) ?: malformed()
            val id = IoTCommonProtocol.exactInt(row, "dpid")
            if (id !in expected || !seen.add(id)) malformed()
            IoTResponse(IoTCommonProtocol.exactInt(row, "code"), null).requireSuccess()
        }
    }

    fun formatParams(): JSONObject = IoTControlProtocol.actionParams(FORMAT)
    fun capacity(settings: CameraSettings): SdCapacity? {
        val parts = (settings.values[SD_CAPACITY] as? String)?.split('|') ?: return null
        if (parts.size != 3) return null
        val total = parts[0].toLongOrNull() ?: return null
        val remaining = parts[1].toLongOrNull() ?: return null
        if (total <= 0 || remaining !in 0..total) return null
        return SdCapacity(total, remaining)
    }
    fun remainingPercent(settings: CameraSettings): Int? = capacity(settings)?.remainingPercent
    /** Native DAID8/action0 uses input action=1; no guessed flip enum or local Surface rotation. */
    fun flipParams(): JSONObject = IoTControlProtocol.actionParams(FLIP, JSONObject().put("action", 1))
    private fun malformed(): Nothing = throw IoTProtocolException(IoTFailure.MalformedPacket)
}
