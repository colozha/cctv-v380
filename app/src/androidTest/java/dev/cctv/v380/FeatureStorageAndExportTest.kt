package dev.cctv.v380

import android.media.MediaExtractor
import android.net.Uri
import dev.cctv.v380.media.copyRecording
import dev.cctv.v380.media.verifyRecordingCopy
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.media.RecordingExporter
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.session.EncodedFrame
import dev.cctv.v380.session.VideoCodec
import dev.cctv.v380.ui.ProfileStore
import dev.cctv.v380.ui.PendingApEdit
import dev.cctv.v380.ui.PendingCameraRestart
import dev.cctv.v380.ui.PendingCameraCalibration
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Local files and platform encoder only. No sockets, camera, cloud, or vendor execution. */
class FeatureStorageAndExportTest {
    @Test fun pendingHotspotIsDurableContainsNoCredentialsAndDoesNotOverwriteOtherRecovery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "ap-check-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "profiles.json")
        val profile = CameraProfile(name = "Synthetic AP", host = "192.0.2.10", deviceId = "synthetic-ap")
        try {
            val store = ProfileStore(context, file)
            store.save(profile, "Local123".toCharArray(), true)
            store.savePendingFormat(profile)
            store.savePendingSetup(profile, "SyntheticRouter")
            val marker = PendingApEdit("synthetic-op", profile.id, profile.deviceId, "HSC_OLD", "CameraNew", 1)
            store.savePendingApEdit(marker)
            val restored = ProfileStore(context, file).loadPendingApEdit()!!
            assertEquals(marker.operationId, restored.operationId)
            assertEquals("CameraNew", restored.targetSsid)
            assertThrows(IllegalArgumentException::class.java) { store.savePendingApEdit(marker) }
            assertThrows(IllegalArgumentException::class.java) { store.clearPendingApEdit("stale-op") }
            val text = File(dir, "pending-ap-edit.json").readText()
            for (forbidden in listOf("password", "secret", "sessionId", "handle", "key", "Local123")) assertFalse(text.contains(forbidden))
            assertThrows(IllegalArgumentException::class.java) { store.delete(profile.id) }
            store.updateVerifiedAddress(profile.id, profile.deviceId, "192.0.2.20", 8089)
            assertEquals("192.0.2.20", store.load().single().host)
            assertArrayEquals("Local123".toCharArray(), store.password(profile.id))
            store.clearPendingApEdit(marker.operationId)
            assertNull(store.loadPendingApEdit()); assertNotNull(store.loadPendingSetup()); assertNotNull(store.loadPendingFormat())
            File(dir, "pending-ap-edit.json").writeText("broken")
            assertThrows(Exception::class.java) { store.loadPendingApEdit() }
            assertThrows(Exception::class.java) { store.savePendingApEdit(marker) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun pendingFormatSurvivesRecreationAndRejectsASecondSubmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "format-check-${UUID.randomUUID()}").apply { mkdirs() }
        val file = File(dir, "profiles.json")
        val profile = CameraProfile(name = "Synthetic SD", host = "192.0.2.10", deviceId = "synthetic-sd")
        try {
            val store = ProfileStore(context, file)
            val pending = store.savePendingFormat(profile, "format-storage-op")
            val restored = ProfileStore(context, file).loadPendingFormat()!!
            assertEquals(pending, restored)
            assertEquals(profile.id, restored.profileId); assertEquals(profile.deviceId, restored.deviceId)
            assertThrows(IllegalArgumentException::class.java) { store.savePendingFormat(profile) }
            val text = File(dir, "pending-sd-format.json").readText()
            for (forbidden in listOf("password", "secret", "sessionId", "handle", "key")) assertFalse(text.contains(forbidden))
            assertThrows(IllegalArgumentException::class.java) { store.clearPendingFormat("stale-op") }
            assertEquals(restored, store.loadPendingFormat())
            store.clearPendingFormat(restored.operationId); assertNull(ProfileStore(context, file).loadPendingFormat())
        } finally { dir.deleteRecursively() }
    }

    @Test fun restartAndLegacyFormatRecoveryAreIndependentAndStaleCleanupIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "management-storage-${UUID.randomUUID()}").apply { mkdirs() }
        val profile = CameraProfile(name = "Synthetic management", host = "192.0.2.10", deviceId = "synthetic-management")
        val file = File(dir, "profiles.json")
        try {
            val store = ProfileStore(context, file)
            store.save(profile, null, false)
            File(dir, "pending-sd-format.json").writeText(org.json.JSONObject().put("version", 1)
                .put("profileId", profile.id).put("deviceId", profile.deviceId).put("createdAtMs", 1).toString())
            val legacy = store.loadPendingFormat()!!
            assertTrue(legacy.operationId.startsWith("legacy:"))
            assertThrows(IllegalArgumentException::class.java) { store.clearPendingFormat("stale") }
            val pending = PendingCameraRestart("op-1", profile.id, profile.deviceId, 2)
            store.savePendingRestart(pending)
            val calibration = PendingCameraCalibration("calibration-1", profile.id, profile.deviceId, 3)
            store.savePendingCalibration(calibration)
            assertEquals(calibration, ProfileStore(context, file).loadPendingCalibration())
            assertThrows(IllegalArgumentException::class.java) { store.clearPendingCalibration("stale") }
            store.clearPendingCalibration(calibration.operationId)
            assertNotNull(store.loadPendingRestart())
            assertEquals(pending, ProfileStore(context, file).loadPendingRestart())
            assertThrows(IllegalArgumentException::class.java) { store.savePendingRestart(pending) }
            assertThrows(IllegalArgumentException::class.java) { store.clearPendingRestart("stale") }
            assertThrows(IllegalArgumentException::class.java) { store.delete(profile.id) }
            val text = File(dir, "pending-camera-restart.json").readText()
            for (forbidden in listOf("password", "secret", "sessionId", "handle", "key")) assertFalse(text.contains(forbidden))
            store.clearPendingRestart(pending.operationId)
            assertNotNull(store.loadPendingFormat())
            store.clearPendingFormat(legacy.operationId)
            store.savePendingFormat(profile)
            assertFalse(store.loadPendingFormat()!!.operationId.startsWith("legacy:"))
            File(dir, "pending-camera-restart.json").writeText("broken")
            assertThrows(Exception::class.java) { store.savePendingRestart(pending) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun formatMarkerRejectsInvalidIdsAndStorageFailureBeforeSubmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "format-write-failure-${UUID.randomUUID()}").apply { mkdirs() }
        val profile = CameraProfile(name = "Synthetic SD", host = "192.0.2.10", deviceId = "synthetic-sd")
        try {
            val store = ProfileStore(context, File(dir, "profiles.json"))
            for (id in listOf("", " ", "x".repeat(257))) {
                assertThrows(IllegalArgumentException::class.java) { store.savePendingFormat(profile, id) }
                assertNull(store.loadPendingFormat())
            }
            val blockedParent = File(dir, "not-a-directory").apply { writeText("synthetic") }
            val unavailable = ProfileStore(context, File(blockedParent, "profiles.json"))
            assertThrows(java.io.IOException::class.java) { unavailable.savePendingFormat(profile, "not-sent") }
            assertNull(unavailable.loadPendingFormat())
        } finally { dir.deleteRecursively() }
    }

    @Test fun grayEncodedSamplesProduceAReadableMp4AndTemporaryFilesAreRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val frames = syntheticAvcFrames()
        val exporter = RecordingExporter(context.cacheDir)
        var result: File? = null
        val destination = File.createTempFile("v380-synthetic-copy-", ".mp4", context.cacheDir)
        try {
            frames.forEachIndexed { index, frame ->
                exporter.write(EncodedFrame(VideoCodec.AVC, 0, frame.keyframe, index * 100000L, frame.bytes), 64, 64)
            }
            result = exporter.finish()
            assertTrue(result.length() > 0)
            runBlocking {
                copyRecording(result, context.contentResolver.openOutputStream(Uri.fromFile(destination), "wt")!!)
                verifyRecordingCopy(result, context.contentResolver.openInputStream(Uri.fromFile(destination))!!)
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(destination.path); assertEquals(1, extractor.trackCount)
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
        } finally { exporter.close(); destination.delete() }
        assertFalse(result!!.exists())
    }
}
