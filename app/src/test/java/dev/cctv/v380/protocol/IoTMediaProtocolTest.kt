package dev.cctv.v380.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class IoTMediaProtocolTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test fun shortVideoTailsAndLargePlaintextGapsAreValidSparsePatterns() {
        val payload = byteArrayOf(0, 0, 0, 1, 0x41, 3, 4)
        for (secure in listOf(1, 2)) {
            val body = mediaBody(payload, 0, 128, 4096)
            val cipher = Cipher.getInstance(if (secure == 1) "AES/ECB/NoPadding" else "AES/CBC/NoPadding")
            val secret = SecretKeySpec(key.copyOfRange(0, 16), "AES")
            if (secure == 1) cipher.init(Cipher.ENCRYPT_MODE, secret)
            else cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(key.copyOfRange(16, 32)))
            val wire = cipher.doFinal(body.copyOfRange(0, 32)) + payload
            val header = IoTHeader(IoTCommonProtocol.COMMAND_MEDIA, 1, secure, 17, 0, wire.size)
            assertArrayEquals("A tail shorter than128 bytes stays plaintext", payload,
                IoTMediaProtocol.parseFrame(header, wire, key).bytes)

            val larger = ByteArray(57) { (it * 3).toByte() }
            val plain = mediaBody(larger, 0, 16, 4096)
            if (secure == 1) cipher.init(Cipher.ENCRYPT_MODE, secret)
            else cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(key.copyOfRange(16, 32)))
            val sparseWire = cipher.doFinal(plain.copyOfRange(0, 48)) + larger.copyOfRange(16, larger.size)
            assertArrayEquals("Only the first chunk is encrypted before a gap larger than the remaining frame", larger,
                IoTMediaProtocol.parseFrame(header.copy(bodySize = sparseWire.size), sparseWire, key).bytes)
            ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN).putInt(22, -16)
            assertThrows(IoTProtocolException::class.java) {
                IoTMediaProtocol.parseFrame(header.copy(secureType = 0), body, null)
            }
        }
    }

    @Test fun decryptsSparseCbcAndEcbWithoutChainingAcrossPlaintextGaps() {
        val payload = ByteArray(133) { (it * 13).toByte() }
        payload[0] = 0; payload[1] = 0; payload[2] = 0; payload[3] = 1; payload[4] = 0x65
        val plain = mediaBody(payload, prefix = 5, chunk = 32, gap = 7)
        for (secure in listOf(1, 2)) {
            val cipher = Cipher.getInstance(if (secure == 1) "AES/ECB/NoPadding" else "AES/CBC/NoPadding")
            val secret = SecretKeySpec(key.copyOfRange(0, 16), "AES")
            if (secure == 1) cipher.init(Cipher.ENCRYPT_MODE, secret)
            else cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(key.copyOfRange(16, 32)))
            // Encrypt one gathered stream independently, then scatter blocks around untouched gaps.
            val offsets = listOf(0, 37, 76, 115)
            val gathered = offsets.flatMap { plain.copyOfRange(it, it + 32).toList() }.toByteArray()
            val encrypted = cipher.doFinal(gathered)
            val wire = plain.copyOf()
            offsets.forEachIndexed { index, offset -> encrypted.copyInto(wire, offset, index * 32, (index + 1) * 32) }
            val frame = IoTMediaProtocol.parseFrame(IoTHeader(IoTCommonProtocol.COMMAND_MEDIA, 1, secure, 17, 0, wire.size), wire, key)
            assertArrayEquals(payload, frame.bytes)
            assertEquals(1500L, frame.timestampMs)
            assertEquals(12L, frame.sequence)
            assertTrue(frame.keyframe)
            assertFalse(frame.audio)
        }
    }

    @Test fun parsesPlainFramesAndRejectsUnsafeChunkMetadata() {
        val wire = mediaBody(ByteArray(24), 0, 0, 0)
        val header = IoTHeader(IoTCommonProtocol.COMMAND_MEDIA, 1, 0, 17, 0, wire.size)
        assertEquals(24, IoTMediaProtocol.parseFrame(header, wire, null).bytes.size)
        ByteBuffer.wrap(wire).order(ByteOrder.LITTLE_ENDIAN).putInt(6, 99999)
        assertThrows(IoTProtocolException::class.java) { IoTMediaProtocol.parseFrame(header, wire, null) }
        val large = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(IoTCommonProtocol.COMMAND_MEDIA)
            .put(1).put(2).put(17).put(0).putInt(0).putInt(2 * 1024 * 1024).array()
        assertEquals(2 * 1024 * 1024, IoTCommonProtocol.parseHeader(large).bodySize)
        large[0] = 0
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.parseHeader(large) }
    }

    @Test fun readsNegotiatedCodecAndPtzCapabilitiesAndBuildsFiniteSteps() {
        val info = IoTMediaProtocol.parseMediaInfo(JSONObject().put("params", JSONObject().put("vattr",
            JSONObject().put("codec", 5).put("width", 1920).put("height", 1080))))
        assertEquals(IoTMediaProtocol.CODEC_HEVC, info.video.codecId)
        assertEquals(1920, info.video.width)
        val abilities = IoTControlProtocol.parseAbilities(JSONObject().put("ctrl", JSONArray().put(3)))
        assertTrue(abilities.ptzKnown && abilities.horizontalPtz && abilities.verticalPtz)
        assertFalse(IoTControlProtocol.parseAbilities(JSONObject()).ptzKnown)
        val views = IoTControlProtocol.parseAbilities(JSONObject().put("msc", JSONArray()
            .put(JSONObject().put("ctrl", JSONArray().put(0))).put(JSONObject().put("ctrl", JSONArray().put(2)))))
        assertEquals(listOf(0, 2), views.viewPtzMasks)
        assertFalse(views.ptz)
        val horizontal = IoTControlProtocol.parseAbilities(JSONObject().put("ctrl", JSONArray().put(1)))
        assertTrue(horizontal.horizontalPtz)
        assertFalse(horizontal.verticalPtz)
        val step = IoTControlProtocol.ptzParams(IoTPtzOperation.Up)
        assertEquals(393728, step.getInt("daid"))
        assertEquals(1, step.getJSONObject("in").getInt("op"))
        assertEquals(1, step.getJSONObject("in").getInt("cnt"))
        assertTrue(step.getBoolean("no_rsp"))
        assertFalse(step.has("vsbmp"))
        assertEquals(0, IoTControlProtocol.ptzParams(IoTPtzOperation.Stop).getJSONObject("in").getInt("op"))
        assertEquals(1, IoTMediaProtocol.liveParams(0).getJSONObject("up").getInt("quality"))
        assertEquals(2, IoTMediaProtocol.liveParams(1).getJSONObject("up").getInt("quality"))
    }

    private fun mediaBody(payload: ByteArray, prefix: Int, chunk: Int, gap: Int): ByteArray =
        ByteBuffer.allocate(32 + payload.size).order(ByteOrder.LITTLE_ENDIAN).put(1).put(1).putInt(12)
            .putInt(payload.size).putLong(1500).putInt(prefix).putInt(chunk).putInt(gap).put(0).put(0).put(payload).array()
}
