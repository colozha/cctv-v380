package dev.cctv.v380

import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.cctv.v380.media.VideoDecoder
import dev.cctv.v380.session.EncodedFrame
import dev.cctv.v380.session.SessionError
import dev.cctv.v380.session.VideoCodec
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VideoDecoderTest {
    @Test fun platformEncodedSyntheticFrameRendersToSurface() {
        val packets = syntheticAvcFrames()
        val handlerThread = HandlerThread("v380-test-images").apply { start() }
        val reader = ImageReader.newInstance(64, 64, ImageFormat.YUV_420_888, 3)
        val rendered = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<SessionError>())
        reader.setOnImageAvailableListener({ source -> source.acquireLatestImage()?.use { rendered.countDown() } }, Handler(handlerThread.looper))
        val decoder = VideoDecoder(reader.surface, 64, 64, VideoCodec.AVC) { errors += it }
        try {
            packets.forEachIndexed { i, frame ->
                assertTrue(decoder.offer(EncodedFrame(VideoCodec.AVC, 0,
                    frame.keyframe, i * 100_000L, frame.bytes)))
                Thread.sleep(50)
            }
            assertTrue("Decoded frame reached a real Android Surface", rendered.await(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty())
        } finally {
            decoder.close()
            reader.setOnImageAvailableListener(null, null)
            handlerThread.quitSafely()
            handlerThread.join(1000)
            reader.close()
        }
    }
}
