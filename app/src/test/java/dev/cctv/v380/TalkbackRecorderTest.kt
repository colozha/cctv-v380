package dev.cctv.v380

import dev.cctv.v380.media.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TalkbackRecorderTest {
    @Test fun partialReadsOnlyEmitCompleteFramesAndWipeAfterCancellation() = runBlocking {
        val readBytes = AtomicInteger()
        val starts = AtomicInteger(); val stops = AtomicInteger(); val releases = AtomicInteger()
        val buffers = mutableListOf<ByteArray>()
        val recorder = TalkbackRecorder(8000, { starts.incrementAndGet() }, { bytes, offset, count ->
            if (readBytes.get() >= 1010) 0 else minOf(37, count).also { n ->
                repeat(n) { bytes[offset + it] = (readBytes.getAndIncrement() % 127).toByte() }
            }
        }, { stops.incrementAndGet() }, { releases.incrementAndGet() })
        val sent = CompletableDeferred<Unit>()
        val job = launch {
            recorder.sendFrames({}) { pcm ->
                assertEquals(1010, pcm.size)
                assertArrayEquals(ByteArray(1010) { (it % 127).toByte() }, pcm)
                buffers.add(pcm)
                sent.complete(Unit)
            }
        }
        try {
            withTimeout(2000) { sent.await() }
            recorder.close()
            withTimeout(1000) { job.cancelAndJoin() }
            recorder.close()
            assertEquals(1, starts.get()); assertEquals(1, stops.get()); assertEquals(1, releases.get())
            assertEquals(1, buffers.size); assertTrue(buffers.single().all { it == 0.toByte() })
        } finally { recorder.close(); job.cancelAndJoin() }
    }

    @Test fun overflowStopsCaptureRatherThanBuildingAStaleAudioQueue() = runBlocking {
        for (rate in listOf(8000, 16000)) {
            val buffers = mutableListOf<ByteArray>()
            var stopped = false
            val recorder = TalkbackRecorder(rate, {}, { bytes, offset, count ->
                synchronized(buffers) { buffers.add(bytes) }; bytes.fill(7, offset, offset + count); count
            }, { stopped = true }, {})
            assertTrue((recorder.queuedFrames + 2) * 1010 <= rate * 2 / 5)
            val error = runCatching {
                withTimeout(2000) { recorder.sendFrames({}) { awaitCancellation() } }
            }.exceptionOrNull()
            recorder.close()
            assertTrue(error.toString(), error is TalkbackFailure)
            assertEquals(TalkbackError.BUFFER_FULL, (error as TalkbackFailure).reason)
            assertTrue(stopped)
            assertTrue(synchronized(buffers) { buffers.all { b -> b.all { it == 0.toByte() } } })
        }
    }

    @Test fun invalidReadStopsAndClosedRecorderCannotRestart() = runBlocking {
        val starts = AtomicInteger()
        val recorder = TalkbackRecorder(8000, { starts.incrementAndGet() }, { _, _, _ -> -3 }, {}, {})
        val error = runCatching { withTimeout(1000) { recorder.sendFrames({}) {} } }.exceptionOrNull()
        assertEquals(TalkbackError.CAPTURE_FAILED, (error as TalkbackFailure).reason)
        recorder.close()
        assertTrue(runCatching { recorder.sendFrames({}) {} }.exceptionOrNull() is CancellationException)
        assertEquals(1, starts.get())
    }

    @Test fun audioFrontendIsAcquiredBeforeCaptureAndClosedOnlyOnce() = runBlocking {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val audio = object : TalkbackAudioSession {
            override fun acquire(): Boolean { events.add("acquire"); return true }
            override fun recordSource() = TalkbackAudioSession.SOURCE_MIC
            override fun attachEchoCanceller(audioSessionId: Int) { events.add("attach") }
            override fun close() { events.add("close") }
        }
        var filled = 0
        val recorder = TalkbackRecorder(8000, { events.add("start") }, { bytes, offset, count ->
            if (filled >= 1010) 0 else minOf(1010 - filled, count).also { n ->
                java.util.Arrays.fill(bytes, offset, offset + n, 3); filled += n
            }
        }, { events.add("stop") }, { events.add("release") }, audio)
        val sent = CompletableDeferred<Unit>()
        val job = launch { recorder.sendFrames({}) { sent.complete(Unit) } }
        withTimeout(2000) { sent.await() }
        recorder.close()
        job.cancelAndJoin()
        recorder.close()
        assertEquals(listOf("acquire", "start", "stop", "release", "close"), events.toList())
    }
}
