package dev.cctv.v380

import dev.cctv.v380.media.TalkbackAdpcmEncoder
import dev.cctv.v380.protocol.IoTTalkbackProtocol
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import kotlin.math.sin

/** Synthetic PCM only. No native vendor code, microphone, network or speaker. */
class TalkbackAdpcmEncoderTest {
    private fun pcm(sample: (Int) -> Int) = ByteBuffer.allocate(1010).order(ByteOrder.LITTLE_ENDIAN).apply {
        repeat(505) { putShort(sample(it).toShort()) }
    }.array()

    @Test fun silenceConstantAndSmallRampsHaveExactNativeLayout() {
        assertArrayEquals(ByteArray(256), TalkbackAdpcmEncoder().encode(ByteArray(1010)))
        val constant = ByteArray(256).apply { this[0] = 0xe8.toByte(); this[1] = 3 }
        assertArrayEquals(constant, TalkbackAdpcmEncoder().encode(pcm { 1000 }))
        assertArrayEquals(ByteArray(256) { if (it < 4) 0 else 0x10 }, TalkbackAdpcmEncoder().encode(pcm { it }))
        assertArrayEquals(ByteArray(256) { if (it < 4) 0 else 0x98.toByte() }, TalkbackAdpcmEncoder().encode(pcm { -it }))
    }

    @Test fun extremeTransitionsRemainBoundedAndEachBlockCarriesItsOwnState() {
        val encoder = TalkbackAdpcmEncoder()
        repeat(100) { frame ->
            val input = pcm { if ((it + frame) % 2 == 0) -32768 else 32767 }
            val block = encoder.encode(input)
            assertTrue(IoTTalkbackProtocol.validAdpcm(block))
            assertEquals(input[0], block[0]); assertEquals(input[1], block[1]); assertEquals(0, block[3].toInt())
        }
        assertThrows(IllegalArgumentException::class.java) { encoder.encode(ByteArray(1008)) }
        assertThrows(IllegalArgumentException::class.java) { encoder.encode(ByteArray(1012)) }
    }

    @Test fun syntheticToneFixtureHasWholeBlocksForIndependentDecoding() {
        val encoder = TalkbackAdpcmEncoder()
        val source = ByteBuffer.allocate(20 * 1010)
        val blocks = ByteBuffer.allocate(20 * 256)
        repeat(20) { frame ->
            val input = pcm { (sin((frame * 505 + it) * 2 * Math.PI * 440 / 16000) * 4000).toInt() }
            val encoded = encoder.encode(input)
            assertTrue(IoTTalkbackProtocol.validAdpcm(encoded))
            source.put(input); blocks.put(encoded)
        }
        val wav = ByteBuffer.allocate(60 + blocks.capacity()).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(capacity() - 8); put("WAVEfmt ".toByteArray()); putInt(20)
            putShort(17); putShort(1); putInt(16000); putInt(16000 * 256 / 505)
            putShort(256); putShort(4); putShort(2); putShort(505)
            put("fact".toByteArray()); putInt(4); putInt(20 * 505)
            put("data".toByteArray()); putInt(blocks.capacity()); put(blocks.array())
        }
        assertEquals(wav.capacity(), wav.position())
        // Labeled synthetic build artifacts only, consumed by the independent decoder check.
        val dir = File("build/talkback-noise").apply { mkdirs() }
        File(dir, "synthetic-input.pcm").writeBytes(source.array())
        File(dir, "synthetic-ima.wav").writeBytes(wav.array())
    }
}
