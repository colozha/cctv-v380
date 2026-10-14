package dev.cctv.v380.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.nio.CharBuffer
import java.util.Base64

/** Direct authenticated SoftAP API; network-mode readback is an interface bitmap, not action.mode. */
object ApHotspotProtocol {
    const val NETWORK_MODE = 262401
    const val AP_SSID = 262403
    const val AP_PASSWORD = 262405
    const val CONFIG_ACTION = 262656
    private const val PASSWORD_SYMBOLS = "@#_^*%.+"

    fun inputError(ssid: String, password: CharArray, currentSsid: String? = null): String? = when {
        ssid.isBlank() || ssid.length > 30 || ssid.toByteArray(Charsets.UTF_8).size > 32 ||
            ssid != ssid.trim() || ssid.any { it.isISOControl() } -> "Nama hotspot harus berisi 1–30 karakter, maksimal 32 byte, tanpa spasi di awal/akhir."
        ssid != currentSsid && ssid.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' } ->
            "Nama hotspot baru hanya boleh berisi huruf A–Z dan angka. Nama lama boleh dipertahankan."
        password.size !in 8..30 || password.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it !in PASSWORD_SYMBOLS } ->
            "Password hotspot harus 8–30 karakter: huruf, angka, atau @#_^*%.+ tanpa spasi."
        else -> null
    }

    /** net[1], NETWORK_CFG ordinal1, SOFTAP_CFG = BMPC(3) = 4; absent is unknown. */
    fun supportsSoftAp(data: JSONObject): Boolean? {
        val net = data.optJSONArray("net") ?: return null
        if (net.length() <= 1) return null
        val raw = net.opt(1) as? Number ?: malformed()
        val bits = raw.toLong()
        if (bits !in 0..0xffffffffL || raw.toDouble() != bits.toDouble()) malformed()
        return bits and 4L != 0L
    }

    fun readParams(): JSONObject = JSONObject().put("dplist", JSONArray(listOf(NETWORK_MODE, AP_SSID)))
    fun verifyParams(): JSONObject = JSONObject().put("dplist", JSONArray(listOf(AP_SSID, AP_PASSWORD)))

    fun verifyReadback(data: JSONObject, ssid: String, password: CharArray): Boolean {
        val rows = data.optJSONArray("dplist_result") ?: malformed()
        if (rows.length() != 2) malformed()
        val seen = mutableSetOf<Int>()
        var matches = true
        repeat(rows.length()) { i ->
            val row = rows.optJSONObject(i) ?: malformed()
            val id = IoTCommonProtocol.exactInt(row, "dpid")
            if (id !in setOf(AP_SSID, AP_PASSWORD) || !seen.add(id) ||
                row.has("vs") && IoTCommonProtocol.exactInt(row, "vs") !in -1..0) malformed()
            IoTResponse(IoTCommonProtocol.exactInt(row, "code"), null).requireSuccess()
            val value = row.opt("value") as? String ?: malformed()
            if (value.toByteArray(Charsets.UTF_8).size > 128) malformed()
            if (id == AP_SSID) matches = matches && value == ssid
            else {
                val actual = value.toCharArray()
                try { matches = matches && actual.contentEquals(password) } finally { actual.fill('\u0000') }
            }
        }
        return matches
    }

    class Snapshot(val networkInterfaces: Long, val ssid: String) {
        override fun toString() = "ApSnapshot(redacted)"
    }

    fun parseSnapshot(data: JSONObject): Snapshot {
        val rows = data.optJSONArray("dplist_result") ?: malformed()
        if (rows.length() != 2) malformed()
        var mode: Long? = null
        var ssid: String? = null
        repeat(rows.length()) { i ->
            val row = rows.optJSONObject(i) ?: malformed()
            if (row.has("vs") && IoTCommonProtocol.exactInt(row, "vs") !in -1..0) malformed()
            IoTResponse(IoTCommonProtocol.exactInt(row, "code"), null).requireSuccess()
            when (IoTCommonProtocol.exactInt(row, "dpid")) {
                NETWORK_MODE -> {
                    if (mode != null) malformed()
                    mode = IoTCommonProtocol.exactLong(row, "value").takeIf { it in 1..0xffffffffL } ?: malformed()
                }
                AP_SSID -> {
                    if (ssid != null) malformed()
                    // Vendor getStringValue feeds setStrApName directly; readback is NOT Base64.
                    ssid = (row.opt("value") as? String)?.takeIf {
                        it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size <= 32 && it.none(Char::isISOControl)
                    } ?: malformed()
                }
                else -> malformed()
            }
        }
        return Snapshot(mode ?: malformed(), ssid ?: malformed())
    }

    fun actionParams(ssid: String, password: CharArray, currentSsid: String): JSONObject {
        require(inputError(ssid, password, currentSsid) == null)
        val chars = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(chars.remaining()).also { chars.get(it) }
        try {
            return JSONObject().put("daid", CONFIG_ACTION).put("in", JSONObject().put("mode", 1)
                .put("ssid", Base64.getEncoder().encodeToString(ssid.toByteArray(Charsets.UTF_8)))
                .put("pwd", Base64.getEncoder().encodeToString(bytes)))
        } finally { bytes.fill(0); if (chars.hasArray()) chars.array().fill(0) }
    }

    private fun malformed(): Nothing = throw IoTProtocolException(IoTFailure.MalformedPacket)
}
