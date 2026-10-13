package dev.cctv.v380.protocol

import org.json.JSONArray
import org.json.JSONObject

enum class IoTPtzOperation(val wireValue: Int) { Stop(0), Up(1), Down(2), Left(3), Right(4) }
class IoTAbilities(val horizontalPtz: Boolean, val verticalPtz: Boolean, val ptzKnown: Boolean,
                   val viewPtzMasks: List<Int?>) {
    val ptz: Boolean get() = horizontalPtz || verticalPtz
}

object IoTControlProtocol {
    const val PTZ_STEP_ACTION = 393728 // Protocol.DAID_PT_STEP_CTRL, group6/action0.

    fun abilityParams(): JSONObject = JSONObject()

    /** Called only for an authenticated incoming NOTIFY197120, never its ordinary IoT reply. */
    fun decodeFeedbackParams(notification: JSONObject): JSONObject? {
        val time = notification.optJSONObject("params")?.opt("tms1") as? Number ?: return null
        if (!time.toDouble().isFinite()) return null
        // Native HSBaseTransmitterHandle returns0 by default. Our bounded MediaCodec queue does
        // not implement HSLivePlayer's signed decode-deficit accumulator; do not substitute PTS.
        return JSONObject().put("flag", 2).put("tms1", time).put("decode_tms", 0)
    }

    /** data.ctrl[0] bits 0/1 are horizontal/vertical; data.msc[].ctrl overrides for multiple views. */
    fun parseAbilities(data: JSONObject): IoTAbilities {
        val views = data.optJSONArray("msc")
        val masks = ArrayList<Int?>()
        if (views != null && views.length() > 0) {
            if (views.length() > 4) throw IoTProtocolException(IoTFailure.MalformedPacket)
            repeat(views.length()) { index ->
                val item = views.optJSONObject(index) ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
                masks.add(mask(item.optJSONArray("ctrl")))
            }
        }
        // This client renders view0 only. Another view's motor capability cannot authorize its controls.
        val combined = if (masks.isNotEmpty()) masks[0] else mask(data.optJSONArray("ctrl"))
        return IoTAbilities(combined != null && combined and 1 != 0, combined != null && combined and 2 != 0,
            combined != null, masks)
    }

    /** A finite PTZ step. The session watchdog may send Stop; this function never moves hardware itself. */
    fun ptzParams(operation: IoTPtzOperation, count: Int = 1, view: Int? = null): JSONObject {
        require(count in 1..255 && (view == null || view in 0..3))
        return actionParams(PTZ_STEP_ACTION, JSONObject().put("op", operation.wireValue).put("cnt", count), view, noResponse = true)
    }

    fun actionParams(actionId: Int, input: JSONObject, view: Int? = null, noResponse: Boolean = false): JSONObject {
        require(actionId >= 0 && (view == null || view in 0..3))
        val params = JSONObject().put("daid", actionId)
        if (view != null) params.put("vsbmp", 1 shl view)
        if (noResponse) params.put("no_rsp", true)
        return params.put("in", input)
    }

    private fun mask(array: JSONArray?): Int? {
        if (array == null) return null
        if (array.length() == 0) return 0
        val raw = array.opt(0) as? Number ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val value = raw.toLong()
        if (value !in 0..0xffffffffL || raw.toDouble() != value.toDouble()) throw IoTProtocolException(IoTFailure.MalformedPacket)
        return value.toInt() and 3
    }
}
