package dev.cctv.v380.protocol

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Wire profile recovered from V380 6.4.90; no camera generation is inferred from firmware names. */
enum class IoTFailure { MalformedPacket, AuthenticationFailed, SessionExpired, PermissionDenied, DeviceRejected, UnsupportedCipher }
class IoTProtocolException(val kind: IoTFailure, val deviceCode: Int? = null) :
    java.io.IOException(if (deviceCode == null) kind.name else "${kind.name} ($deviceCode)")

data class IoTHeader(val command: Int, val version: Int, val secureType: Int, val sessionId: Int, val magic: Int, val bodySize: Int) {
    override fun toString() = "IoTHeader(command=$command, version=$version, secureType=$secureType, bodySize=$bodySize)"
}
class IoTResponse(val code: Int, val data: JSONObject?) {
    fun requireSuccess(): JSONObject? {
        if (code != 1000) throw IoTProtocolException(when (code) {
            1103, 1119 -> IoTFailure.AuthenticationFailed
            1104 -> IoTFailure.SessionExpired
            1109 -> IoTFailure.PermissionDenied
            else -> IoTFailure.DeviceRejected
        }, code)
        return data
    }
    override fun toString() = "IoTResponse(code=$code)"
}

object IoTCommonProtocol {
    const val COMMAND_IOT = 939722776
    const val COMMAND_MEDIA = 939722784
    const val COMMAND_NOTIFY = 939722786
    const val COMMAND_SHUTDOWN = 199056
    const val LAN_PASSWORD_REQUEST = 196870
    const val LAN_PASSWORD_RESPONSE = 196871
    const val BIND_ACK_REQUEST = 196864
    const val BIND_ACK_RESPONSE = 196865
    const val LIVE_CONTROL_REQUEST = 197122
    const val DECODE_FEEDBACK = 197120
    const val LIVE_CONTROL_RESPONSE = 197123
    const val LIVE_INFORMATION = 197124
    const val ABILITY_REQUEST = 197386
    const val ABILITY_RESPONSE = 197387
    const val ACTION_REQUEST = 197384
    const val ACTION_RESPONSE = 197385
    const val HEADER_SIZE = 16
    const val MAX_JSON_SIZE = 1024 * 1024

    fun encodeRequest(
        command: Int = COMMAND_IOT, methodId: Int, requestId: Int, channel: Int = 1,
        params: JSONObject, version: Int = 1, secureType: Int = 0, sessionId: Int = 0,
        magic: Int = 0, key: ByteArray? = null, timestampSeconds: Long = System.currentTimeMillis() / 1000,
    ): ByteArray {
        require(channel >= 0 && version in 0..255 && sessionId in 0..255 && magic in 0..255)
        val json = JSONObject().put("id", requestId).put("method_id", methodId).put("chn", channel)
            .put("ts", timestampSeconds).put("params", params)
        val plain = json.toString().toByteArray(Charsets.UTF_8)
        if (plain.size > MAX_JSON_SIZE - 16) throw IoTProtocolException(IoTFailure.MalformedPacket)
        var body: ByteArray? = null
        return try {
            val encoded = cipher(plain, secureType, key, Cipher.ENCRYPT_MODE).also { body = it }
            ByteBuffer.allocate(HEADER_SIZE + encoded.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).put(version.toByte()).put(secureType.toByte()).put(sessionId.toByte())
                .put(magic.toByte()).putInt(0).putInt(encoded.size).put(encoded).array()
        } finally { plain.fill(0); if (body !== plain) body?.fill(0) }
    }

    fun parseHeader(bytes: ByteArray, allowUnknownCommand: Boolean = false): IoTHeader {
        if (bytes.size != HEADER_SIZE) throw IoTProtocolException(IoTFailure.MalformedPacket)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val version = buffer.get().toInt() and 255
        val secure = buffer.get().toInt() and 255
        val sid = buffer.get().toInt() and 255
        val magic = buffer.get().toInt() and 255
        buffer.int
        val size = buffer.int
        val maxSize = if (command == COMMAND_MEDIA) IoTMediaProtocol.MAX_FRAME_SIZE else MAX_JSON_SIZE
        val known = command in setOf(COMMAND_IOT, COMMAND_MEDIA, COMMAND_NOTIFY, COMMAND_SHUTDOWN)
        val minSize = when {
            command == COMMAND_MEDIA -> 32
            command == COMMAND_SHUTDOWN || !known && allowUnknownCommand -> 0
            else -> 1
        }
        if ((!known && !allowUnknownCommand) || version != 1 || size !in minSize..maxSize)
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        if (secure !in 0..2) throw IoTProtocolException(IoTFailure.UnsupportedCipher)
        return IoTHeader(command, version, secure, sid, magic, size)
    }

    fun decodeJson(header: IoTHeader, payload: ByteArray, key: ByteArray? = null): JSONObject {
        if (payload.size != header.bodySize || payload.size !in 1..MAX_JSON_SIZE)
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        val plain = cipher(payload, header.secureType, key, Cipher.DECRYPT_MODE)
        try {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return JSONObject(decoder.decode(ByteBuffer.wrap(plain)).toString())
        } catch (_: Exception) {
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        } finally {
            if (plain !== payload) plain.fill(0)
        }
    }

    fun validateResponse(json: JSONObject, requestId: Int, channel: Int = 1, methodId: Int): IoTResponse {
        val result = json.optJSONObject("result") ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val code = exactInt(result, "code")
        // The camera may omit transaction fields when the session/key is invalid. These cannot
        // authorize an operation; propagate the typed failure so the caller can renew once.
        if (code == 1103 || code == 1104) return IoTResponse(code, null)
        // Successful replies and every other error must match this request, even in plaintext.
        if (exactInt(json, "id") != requestId || exactInt(json, "chn") !in setOf(0, channel) ||
            exactInt(json, "method_id") != methodId) throw IoTProtocolException(IoTFailure.MalformedPacket)
        return IoTResponse(code, json.optJSONObject("data"))
    }

    internal fun exactInt(json: JSONObject, name: String): Int {
        val number = json.opt(name) as? Number ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val value = number.toLong()
        if (value !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || number.toDouble() != value.toDouble())
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        return value.toInt()
    }

    internal fun exactLong(json: JSONObject, name: String): Long {
        val number = json.opt(name) as? Number ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        if (number is Long || number is Int || number is Short || number is Byte) return number.toLong()
        val value = number.toDouble()
        if (!value.isFinite() || value < -9_007_199_254_740_991.0 || value > 9_007_199_254_740_991.0 || value != value.toLong().toDouble())
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        return value.toLong()
    }

    private fun cipher(bytes: ByteArray, secureType: Int, key: ByteArray?, mode: Int): ByteArray {
        if (secureType == 0) return bytes
        if (secureType !in 1..2) throw IoTProtocolException(IoTFailure.UnsupportedCipher)
        if (key == null || key.size != 32 || (mode == Cipher.DECRYPT_MODE && bytes.size % 16 != 0))
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        val aesKey = key.copyOfRange(0, 16)
        val iv = key.copyOfRange(16, 32)
        try {
            val cipher = Cipher.getInstance(if (secureType == 1) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
            val secret = SecretKeySpec(aesKey, "AES")
            if (secureType == 1) cipher.init(mode, secret) else cipher.init(mode, secret, IvParameterSpec(iv))
            return cipher.doFinal(bytes)
        } catch (_: Exception) {
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        } finally {
            aesKey.fill(0)
            iv.fill(0)
        }
    }
}
