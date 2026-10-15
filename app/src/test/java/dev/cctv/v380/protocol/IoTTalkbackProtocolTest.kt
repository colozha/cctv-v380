package dev.cctv.v380.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class IoTTalkbackProtocolTest {
    @Test fun distinguishesCaptureFromPlaybackAndKeepsUnknownCapabilityUnknown() {
        assertNull(IoTTalkbackProtocol.capability(JSONObject()))
        assertNull(IoTTalkbackProtocol.capability(JSONObject().put("stream", JSONArray().put(2))))
        for (mask in listOf(0, 1, 2, 3, 0xffffffffL)) {
            val data = JSONObject().put("stream", JSONArray().put(0).put(0).put(0).put(mask))
            assertEquals(mask.toLong() and 2 != 0L, IoTTalkbackProtocol.capability(data))
        }
        for (mask in listOf(-1, 0.5, "2", 4294967296L))
            assertThrows(IoTProtocolException::class.java) {
                IoTTalkbackProtocol.capability(JSONObject().put("stream", JSONArray().put(0).put(0).put(0).put(mask)))
            }
    }

    @Test fun onlyNegotiatedMonoPcmAtSupportedRatesCanBuildStart() {
        assertNull(IoTTalkbackProtocol.audioInfo(JSONObject()))
        for (rate in listOf(8000, 16000)) {
            val info = IoTAudioInfo(100, rate, 16, 1)
            val params = IoTTalkbackProtocol.startParams(info)
            assertEquals(1, params.length())
            assertFalse(params.has("up"))
            val attr = params.getJSONObject("down").getJSONObject("aattr")
            assertEquals(info, IoTTalkbackProtocol.audioInfo(JSONObject().put("params", JSONObject().put("aattr", attr))))
        }
        for (info in listOf(IoTAudioInfo(101, 8000, 16, 1), IoTAudioInfo(21, 8000, 16, 1),
            IoTAudioInfo(100, 44100, 16, 1), IoTAudioInfo(100, 8000, 8, 1), IoTAudioInfo(100, 8000, 16, 2))) {
            assertFalse(info.supported)
            assertThrows(IllegalArgumentException::class.java) { IoTTalkbackProtocol.startParams(info) }
        }
        assertThrows(IoTProtocolException::class.java) {
            IoTTalkbackProtocol.audioInfo(JSONObject("{params:{aattr:{codec:100,samplerate:8000.5,bits:16,chns:1}}}"))
        }
    }

    @Test fun outgoingPcmMatchesIndependentBlockChainingAndLeavesTwoByteTailPlain() {
        val key = ByteArray(32) { it.toByte() }
        val pcm = ByteArray(1010) { (it * 17).toByte() }
        val saved = pcm.copyOf()
        val wire = IoTTalkbackProtocol.audioPacket(pcm, 0xffffffffL, 1000, 17, key)
        val header = IoTCommonProtocol.parseHeader(wire.copyOfRange(0, 16))
        assertEquals(IoTHeader(IoTCommonProtocol.COMMAND_MEDIA, 1, 2, 17, 0, 1042), header)
        val plain = ByteBuffer.allocate(1042).order(ByteOrder.LITTLE_ENDIAN)
            .put(0).put(4).putInt(-1).putInt(1010).putLong(1000).putInt(0).putInt(1008).putInt(0)
            .put(0).put(0).put(pcm).array()
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"))
        var previous = key.copyOfRange(16, 32)
        for (offset in 0 until 1040 step 16) {
            val block = ByteArray(16) { plain[offset + it].toInt().xor(previous[it].toInt()).toByte() }
            previous = ecb.doFinal(block)
            assertArrayEquals(previous, wire.copyOfRange(16 + offset, 32 + offset))
        }
        assertArrayEquals(pcm.takeLast(2).toByteArray(), wire.takeLast(2).toByteArray())
        val frame = IoTMediaProtocol.parseFrame(header, wire.copyOfRange(16, wire.size), key)
        assertEquals(0, frame.format); assertTrue(frame.audio); assertEquals(0xffffffffL, frame.sequence)
        assertEquals(1000L, frame.timestampMs); assertEquals(0, frame.channel)
        assertArrayEquals(saved, frame.bytes); assertArrayEquals(saved, pcm)
        assertArrayEquals(ByteArray(32) { it.toByte() }, key)
    }

    @Test fun rejectsUnboundedFramesSequencesKeysAndTimestamps() {
        val key = ByteArray(32)
        for (size in listOf(0, 1, 1008, 1011, 100000)) assertThrows(IllegalArgumentException::class.java) {
            IoTTalkbackProtocol.audioPacket(ByteArray(size), 1, 1000, 1, key)
        }
        for (sequence in listOf(0L, -1L, 0x100000000L)) assertThrows(IllegalArgumentException::class.java) {
            IoTTalkbackProtocol.audioPacket(ByteArray(1010), sequence, 1000, 1, key)
        }
        assertThrows(IllegalArgumentException::class.java) { IoTTalkbackProtocol.audioPacket(ByteArray(1010), 1, -1, 1, key) }
        assertThrows(IllegalArgumentException::class.java) { IoTTalkbackProtocol.audioPacket(ByteArray(1010), 1, 1, 1, ByteArray(16)) }
    }
}
