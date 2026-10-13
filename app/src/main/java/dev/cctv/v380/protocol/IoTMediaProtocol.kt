package dev.cctv.v380.protocol

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class IoTMediaFrame(val format: Int, val frameType: Int, val sequence: Long, val timestampMs: Long,
                    val channel: Int, val bytes: ByteArray) {
    val keyframe: Boolean get() = frameType == 1
    val audio: Boolean get() = frameType == 4
    override fun toString() = "IoTMediaFrame(type=$frameType, channel=$channel, bytes=${bytes.size})"
}
data class IoTVideoInfo(val codecId: Int, val width: Int, val height: Int)
class IoTMediaInfo(val video: IoTVideoInfo, val views: List<IoTVideoInfo>)

object IoTMediaProtocol {
    const val MAX_FRAME_SIZE = 4 * 1024 * 1024
    const val CODEC_AVC = 4
    const val CODEC_HEVC = 5

    /** APK maps legacy stream selector 0 to quality 1 and selector 1 to quality 2. */
    fun liveParams(streamType: Int, audio: Boolean = false, view: Int? = null): JSONObject {
        require(streamType in 0..1 && (view == null || view in 0..3))
        val up = JSONObject().put("quality", if (streamType == 0) 1 else 2)
            .put("video", 1).put("audio", if (audio) 1 else 0).put("IDRreq", 1)
        if (view != null) up.put("vsspec", view)
        return JSONObject().put("up", up)
    }

    /** Method 197124 notification carries params.vattr; view entries can override codec and dimensions. */
    fun parseMediaInfo(json: JSONObject): IoTMediaInfo {
        val params = json.optJSONObject("params") ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val attr = params.optJSONObject("vattr") ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val video = videoInfo(attr)
        val views = ArrayList<IoTVideoInfo>()
        val array = attr.optJSONArray("msvi")
        if (array != null) {
            if (array.length() !in 1..4) throw IoTProtocolException(IoTFailure.MalformedPacket)
            repeat(array.length()) { index ->
                val item = array.optJSONObject(index) ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
                val res = item.optJSONArray("res") ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
                if (res.length() < 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
                val values = JSONObject().put("codec", item.opt("codec")).put("width", res.opt(0)).put("height", res.opt(1))
                views.add(videoInfo(values))
            }
        }
        return IoTMediaInfo(video, views)
    }

    /** Per-packet cipher resets, then chains across the 32-byte header and only encrypted payload ranges. */
    fun parseFrame(header: IoTHeader, body: ByteArray, key: ByteArray?): IoTMediaFrame {
        if (header.command != IoTCommonProtocol.COMMAND_MEDIA || header.bodySize != body.size ||
            body.size !in 32..MAX_FRAME_SIZE || header.secureType !in 0..2)
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        val bytes = body.copyOf()
        try {
            val cipher = if (header.secureType == 0) null else mediaCipher(header.secureType, key)
            if (cipher != null) decryptRange(cipher, bytes, 0, 32)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val format = buffer.get(0).toInt() and 255
            val type = buffer.get(1).toInt() and 255
            val sequence = buffer.getInt(2).toLong() and 0xffffffffL
            val size = buffer.getInt(6)
            val timestamp = buffer.getLong(10)
            val prefix = buffer.getInt(18)
            val chunk = buffer.getInt(22)
            val gap = buffer.getInt(26)
            val channel = buffer.get(30).toInt() and 255
            if (type !in 1..4 || size != body.size - 32 || size <= 0 || timestamp < 0 || channel !in 0..3 ||
                prefix !in 0..size || chunk !in 0..MAX_FRAME_SIZE || gap !in 0..MAX_FRAME_SIZE || chunk % 16 != 0 ||
                (cipher != null && type == 4 && chunk > size))
                throw IoTProtocolException(IoTFailure.MalformedPacket)
            // Chunk/gap describe a sparse pattern, independently of the compressed frame size.
            // Native 0x93a28 leaves a tail shorter than one chunk untouched; 0x93b40 clamps the gap.
            if (cipher != null && chunk > 0) {
                if (type == 4) {
                    decryptRange(cipher, bytes, 32, chunk)
                } else {
                    var offset = 32 + prefix
                    while (bytes.size - offset >= chunk) {
                        decryptRange(cipher, bytes, offset, chunk)
                        offset += chunk
                        offset += minOf(gap, bytes.size - offset)
                    }
                }
                if (cipher.doFinal().isNotEmpty()) throw IoTProtocolException(IoTFailure.MalformedPacket)
            }
            return IoTMediaFrame(format, type, sequence, timestamp, channel, bytes.copyOfRange(32, bytes.size))
        } catch (failure: IoTProtocolException) {
            throw failure
        } catch (_: Exception) {
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        } finally {
            bytes.fill(0)
        }
    }

    private fun videoInfo(value: JSONObject): IoTVideoInfo {
        val codec = IoTCommonProtocol.exactInt(value, "codec")
        val width = IoTCommonProtocol.exactInt(value, "width")
        val height = IoTCommonProtocol.exactInt(value, "height")
        if (codec !in 1..5 || width !in 1..16384 || height !in 1..16384)
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        return IoTVideoInfo(codec, width, height)
    }

    private fun mediaCipher(secure: Int, key: ByteArray?): Cipher {
        if (key == null || key.size != 32) throw IoTProtocolException(IoTFailure.MalformedPacket)
        val aes = key.copyOfRange(0, 16)
        val iv = key.copyOfRange(16, 32)
        try {
            val cipher = Cipher.getInstance(if (secure == 1) "AES/ECB/NoPadding" else "AES/CBC/NoPadding")
            if (secure == 1) cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"))
            else cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(iv))
            return cipher
        } finally { aes.fill(0); iv.fill(0) }
    }

    private fun decryptRange(cipher: Cipher, bytes: ByteArray, offset: Int, size: Int) {
        val plain = cipher.update(bytes, offset, size)
        if (plain == null || plain.size != size) throw IoTProtocolException(IoTFailure.MalformedPacket)
        plain.copyInto(bytes, offset)
        plain.fill(0)
    }
}
