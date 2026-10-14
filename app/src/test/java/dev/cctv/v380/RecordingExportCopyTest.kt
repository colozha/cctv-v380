package dev.cctv.v380

import dev.cctv.v380.media.copyRecording
import dev.cctv.v380.media.RecordingExporter
import dev.cctv.v380.media.RecordingStorageFullException
import dev.cctv.v380.media.verifyRecordingCopy
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class RecordingExportCopyTest {
    @Test fun fullCacheRejectsExportBeforeCreatingAPartialFile() {
        val directory = java.nio.file.Files.createTempDirectory("recording-full-cache-").toFile()
        try {
            val fullCache = object : File(directory.path) { override fun getUsableSpace() = 0L }
            assertThrows(RecordingStorageFullException::class.java) { RecordingExporter(fullCache) }
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.delete() }
    }

    @Test fun recordingCopyWritesEveryByteClosesDestinationAndPropagatesProviderFailureOrCancellation() = runBlocking {
        val file = File.createTempFile("copy-test-", ".mp4")
        val bytes = ByteArray(150000) { (it % 251).toByte() }
        file.writeBytes(bytes)
        class Destination(val fail: Boolean = false, val failFlush: Boolean = false, val cancel: (() -> Unit)? = null) : ByteArrayOutputStream() {
            var closed = false
            override fun write(buffer: ByteArray, offset: Int, count: Int) {
                if (fail) throw IOException("Synthetic provider refused write")
                super.write(buffer, offset, count)
                cancel?.invoke()
            }
            override fun flush() { if (failFlush) throw IOException("Synthetic provider failed to flush") }
            override fun close() { closed = true; super.close() }
        }
        try {
            val output = Destination()
            copyRecording(file, output)
            assertArrayEquals(bytes, output.toByteArray()); assertTrue(output.closed)
            verifyRecordingCopy(file, ByteArrayInputStream(output.toByteArray()))
            for (bad in listOf(bytes.copyOf(50), bytes + byteArrayOf(1), bytes.copyOf().also { it[70000] = 0 })) {
                try { verifyRecordingCopy(file, ByteArrayInputStream(bad)); fail("Invalid destination was accepted") } catch (_: IOException) { }
            }
            val refused = Destination(fail = true)
            try { copyRecording(file, refused); fail("Provider failure was ignored") } catch (_: IOException) { }
            assertTrue(refused.closed)
            val unflushed = Destination(failFlush = true)
            try { copyRecording(file, unflushed); fail("Flush failure was ignored") } catch (_: IOException) { }
            assertTrue(unflushed.closed)
            val job = Job()
            val cancelled = Destination(cancel = { job.cancel() })
            try {
                withContext(job) { copyRecording(file, cancelled) }
                fail("Copy ignored cancellation")
            } catch (_: CancellationException) { }
            assertTrue(cancelled.closed)
            assertTrue(cancelled.size() < bytes.size)
        } finally { file.delete() }
    }

}
