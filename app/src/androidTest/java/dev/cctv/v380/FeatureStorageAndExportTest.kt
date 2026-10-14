package dev.cctv.v380

import android.media.MediaExtractor
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.media.RecordingExporter
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.session.EncodedFrame
import dev.cctv.v380.session.VideoCodec
import dev.cctv.v380.ui.ProfileStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Local files and platform encoder only. No sockets, camera, cloud, or vendor execution. */
class FeatureStorageAndExportTest {
    @Test fun pendingFormatSurvivesRecreationAndRejectsASecondSubmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "format-check-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "profiles.json")
        val profile = CameraProfile(name = "Synthetic SD", host = "192.0.2.10", deviceId = "synthetic-sd")
        try {
            val store = ProfileStore(context, file)
            store.savePendingFormat(profile)
            val restored = ProfileStore(context, file).loadPendingFormat()!!
            assertEquals(profile.id, restored.profileId); assertEquals(profile.deviceId, restored.deviceId)
            assertThrows(IllegalArgumentException::class.java) { store.savePendingFormat(profile) }
            val text = File(dir, "pending-sd-format.json").readText()
            for (forbidden in listOf("password", "secret", "sessionId", "handle", "key")) assertFalse(text.contains(forbidden))
            store.clearPendingFormat(); assertNull(ProfileStore(context, file).loadPendingFormat())
        } finally { dir.deleteRecursively() }
    }

    @Test fun grayEncodedSamplesProduceAReadableMp4AndTemporaryFilesAreRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val frames = syntheticAvcFrames()
        val exporter = RecordingExporter(context.cacheDir)
        var result: File? = null
        try {
            frames.forEachIndexed { index, frame ->
                exporter.write(EncodedFrame(VideoCodec.AVC, 0, frame.keyframe, index * 100000L, frame.bytes), 64, 64)
            }
            result = exporter.finish()
            assertTrue(result.length() > 0)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(result.path); assertEquals(1, extractor.trackCount)
                assertEquals("video/avc", extractor.getTrackFormat(0).getString("mime"))
                extractor.selectTrack(0)
                var count = 0; var last = -1L
                while (extractor.sampleTime >= 0) {
                    assertTrue(extractor.sampleTime >= last)
                    last = extractor.sampleTime; count++
                    if (!extractor.advance()) break
                }
                assertEquals(frames.size, count); assertEquals((frames.size - 1) * 100000L, last)
            } finally { extractor.release() }
        } finally { exporter.close() }
        assertFalse(result!!.exists())
    }
}
