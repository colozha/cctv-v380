package dev.cctv.v380.protocol

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class IoTAudioInfo(val codec: Int, val sampleRate: Int, val bits: Int, val channels: Int) {
    val supported: Boolean get() = (codec == 100 && sampleRate in setOf(8000, 16000) || codec == 102 && sampleRate == 16000) && bits == 16 && channels == 1
}

/** V380 V3 PCM21 microphone branch. Reported codec is negotiation metadata, not the payload codec. */
object IoTTalkbackProtocol {
    const val PCM_FRAME_BYTES = 1010 // Vendor MicrophoneRecorder's PCM aggregation buffer.

    fun capability(data: JSONObject): Boolean? {
        val stream = data.optJSONArray("stream") ?: return null
        if (stream.length() <= 3) return null
        val raw = stream.opt(3) as? Number ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
        val value = raw.toLong()
        if (value !in 0..0xffffffffL || raw.toDouble() != value.toDouble())
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        return value and 2L != 0L
    }

    fun audioInfo(notification: JSONObject): IoTAudioInfo? {
        val attr = notification.optJSONObject("params")?.optJSONObject("aattr") ?: return null
        return IoTAudioInfo(IoTCommonProtocol.exactInt(attr, "codec"), IoTCommonProtocol.exactInt(attr, "samplerate"),
            IoTCommonProtocol.exactInt(attr, "bits"), IoTCommonProtocol.exactInt(attr, "chns"))
    }

    fun startParams(info: IoTAudioInfo): JSONObject {
        require(info.supported)
        return JSONObject().put("down", JSONObject().put("aattr", JSONObject().put("codec", info.codec)
            .put("samplerate", info.sampleRate).put("bits", info.bits).put("chns", info.channels)))
    }

    /** Native PCM21/non100 branch emits an IMA block even when aattr advertises AAC102. */
    fun validAdpcm(frame: ByteArray): Boolean = frame.size == 256 &&
        frame[2].toInt() and 255 in 0..88 && frame[3] == 0.toByte()

    /** Native audio encryption: CBC header followed by full payload blocks; final partial block stays plain. */
    fun audioPacket(pcm: ByteArray, sequence: Long, timestampMs: Long, sessionId: Int, key: ByteArray,
        codec: Int = 100): ByteArray {
        require((codec == 100 && pcm.size == PCM_FRAME_BYTES || codec == 102 && validAdpcm(pcm)) && sequence in 1..0xffffffffL && timestampMs >= 0 &&
            sessionId in 0..255 && key.size == 32)
        val chunk = pcm.size / 16 * 16
        val body = ByteBuffer.allocate(32 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(0).put(4).putInt(sequence.toInt()).putInt(pcm.size).putLong(timestampMs)
            .putInt(0).putInt(chunk).putInt(0).put(0).put(0).put(pcm).array()
        val aes = key.copyOfRange(0, 16)
        val iv = key.copyOfRange(16, 32)
        var encrypted: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aes, "AES"), IvParameterSpec(iv))
            val blocks = cipher.doFinal(body, 0, 32 + chunk).also { encrypted = it }
            blocks.copyInto(body)
            return ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(IoTCommonProtocol.COMMAND_MEDIA).put(1).put(2).put(sessionId.toByte()).put(0)
                .putInt(0).putInt(body.size).put(body).array()
        } finally { aes.fill(0); iv.fill(0); body.fill(0); encrypted?.fill(0) }
    }
}
