package dev.cctv.v380

import dev.cctv.v380.media.TalkbackAdpcmEncoder
import dev.cctv.v380.protocol.IoTTalkbackProtocol
import dev.cctv.v380.protocol.TalkbackPayloadEncoding
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Recorded outputs of the hash-pinned vendor functions, with entirely synthetic inputs. */
class TalkbackVendorVectorTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun nativeSequentialEncoderAndWholeMediaPacketsMatchByteForByte() {
        val resource = requireNotNull(javaClass.getResourceAsStream("/talkback-vendor-vectors.json"))
        val reference = resource.bufferedReader().use { JSONObject(it.readText()) }
        assertTrue(reference.getBoolean("syntheticOnly"))
        val vectors = reference.getJSONArray("vectors")
        assertEquals(7, vectors.length())
        // The native factory retains the process-global step index across release/init.
        // Keep equivalent continuous state here; production deliberately owns state per attempt.
        val encoder = TalkbackAdpcmEncoder()
        val key = ByteArray(32) { it.toByte() }
        repeat(vectors.length()) { index ->
            val vector = vectors.getJSONObject(index)
            val name = vector.getString("name")
            val pcm = hex(vector.getString("pcm"))
            val encoded = encoder.encode(pcm)
            assertArrayEquals("$name: native encoder", hex(vector.getString("encoded")), encoded)
            val packets = vector.getJSONArray("packets")
            for ((mode, payload) in listOf(pcm, encoded).withIndex()) {
                val encoding = if (mode == 0) TalkbackPayloadEncoding.PCM16 else TalkbackPayloadEncoding.IMA_ADPCM
                assertArrayEquals("$name/$encoding: full native packet", hex(packets.getString(mode)),
                    IoTTalkbackProtocol.audioPacket(payload, index + 1L, 1000L + index * 32, 17, key, encoding))
            }
        }
    }

    @Test fun newAttemptUsesFreshSelfContainedStateInsteadOfVendorProcessGlobalState() {
        val reference = requireNotNull(javaClass.getResourceAsStream("/talkback-vendor-vectors.json"))
            .bufferedReader().use { JSONObject(it.readText()) }.getJSONArray("vectors")
        val firstTone = reference.getJSONObject(2)
        val restart = reference.getJSONObject(5)
        assertArrayEquals(hex(firstTone.getString("pcm")), hex(restart.getString("pcm")))
        assertEquals(36, hex(restart.getString("encoded"))[2].toInt())
        val fresh = TalkbackAdpcmEncoder().encode(hex(restart.getString("pcm")))
        assertEquals(0, fresh[2].toInt())
        assertArrayEquals("Fresh state matches the native tone with initial index0", hex(firstTone.getString("encoded")), fresh)
    }
}
