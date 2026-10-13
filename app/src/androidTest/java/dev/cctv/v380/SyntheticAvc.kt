package dev.cctv.v380

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import java.io.ByteArrayOutputStream

internal class SyntheticAvcFrame(val bytes: ByteArray, val keyframe: Boolean)

/** Generated gray pixels, encoded by the Android platform; never camera footage. */
internal fun syntheticAvcFrames(): List<SyntheticAvcFrame> {
    val format = MediaFormat.createVideoFormat("video/avc", 64, 64).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, 10)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
    assumeNotNull(name)
    val encoder = MediaCodec.createByCodecName(name!!)
    val config = ByteArrayOutputStream()
    val packets = mutableListOf<Pair<ByteArray, Int>>()
    try {
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val pixels = ByteArray(64 * 64 * 3 / 2) { if (it < 64 * 64) 80.toByte() else 128.toByte() }
        val info = MediaCodec.BufferInfo()
        fun drain(untilEos: Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (System.nanoTime() < deadline) {
                val output = encoder.dequeueOutputBuffer(info, 100_000)
                if (output >= 0) {
                    if (info.size > 0) {
                        val bytes = ByteArray(info.size)
                        encoder.getOutputBuffer(output)!!.apply { position(info.offset); limit(info.offset + info.size); get(bytes) }
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) config.write(bytes)
                        else packets += bytes to info.flags
                    }
                    encoder.releaseOutputBuffer(output, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else if (!untilEos && output == MediaCodec.INFO_TRY_AGAIN_LATER) break
            }
        }
        repeat(8) { frame ->
            val index = encoder.dequeueInputBuffer(1_000_000)
            assertTrue(index >= 0)
            encoder.getInputBuffer(index)!!.apply { clear(); put(pixels) }
            encoder.queueInputBuffer(index, 0, pixels.size, frame * 100_000L, 0)
            drain(false)
        }
        val index = encoder.dequeueInputBuffer(1_000_000)
        assertTrue(index >= 0)
        encoder.queueInputBuffer(index, 0, 0, 800_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
    } finally { runCatching { encoder.stop() }; encoder.release() }
    assertTrue(config.size() > 0 && packets.isNotEmpty())
    return packets.map { (bytes, flags) -> SyntheticAvcFrame(config.toByteArray() + bytes, flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) }
}
