package dev.cctv.v380.media

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.cctv.v380.protocol.IoTTalkbackProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean

enum class TalkbackError { NOT_READY, BUSY, CAPABILITY_UNKNOWN, UNSUPPORTED, METADATA_MISSING, METADATA_ABSENT, METADATA_INVALID, METADATA_UNTRUSTED, METADATA_COMMAND, METADATA_CIPHER, UNSUPPORTED_AUDIO,
    MICROPHONE_PERMISSION, MICROPHONE_UNAVAILABLE, CAPTURE_FAILED, BUFFER_FULL, CAMERA_PERMISSION, DEVICE_REJECTED,
    NETWORK, TIMEOUT, MALFORMED }
class TalkbackFailure(val reason: TalkbackError) : java.io.IOException(reason.name)

/** Function seams let synthetic tests exercise the exact capture loop without opening a microphone. */
class TalkbackRecorder internal constructor(val sampleRate: Int,
    private val startCapture: () -> Unit, private val read: (ByteArray, Int, Int) -> Int,
    private val stopCapture: () -> Unit, private val releaseCapture: () -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val captureLock = Any()
    private val frameSize = IoTTalkbackProtocol.PCM_FRAME_BYTES
    // Include the producer and in-flight sender buffers in the 200 ms memory ceiling.
    internal val queuedFrames = sampleRate * 2 / 5 / frameSize - 2
    init { require(sampleRate in setOf(8000, 16000) && queuedFrames > 0) }

    suspend fun sendFrames(onStarted: () -> Unit, send: suspend (ByteArray) -> Unit) = coroutineScope {
        val queue = Channel<ByteArray>(queuedFrames, onUndeliveredElement = { it.fill(0) })
        try {
            synchronized(captureLock) {
                if (closed.get()) throw CancellationException("Recorder already closed")
                try { startCapture() }
                catch (error: SecurityException) { throw error }
                catch (_: Exception) { throw TalkbackFailure(TalkbackError.MICROPHONE_UNAVAILABLE) }
                running.set(true)
            }
            onStarted()
            launch(Dispatchers.IO) {
                var pending = ByteArray(frameSize)
                var filled = 0
                try {
                    while (isActive && running.get()) {
                        val count = try { read(pending, filled, frameSize - filled) }
                        catch (error: SecurityException) { throw error }
                        catch (_: Exception) { throw TalkbackFailure(TalkbackError.CAPTURE_FAILED) }
                        if (!running.get()) break
                        if (count < 0 || count > frameSize - filled) throw TalkbackFailure(TalkbackError.CAPTURE_FAILED)
                        if (count == 0) { delay(5); continue }
                        filled += count
                        if (filled == frameSize) {
                            if (queue.trySend(pending).isFailure) throw TalkbackFailure(TalkbackError.BUFFER_FULL)
                            pending = ByteArray(frameSize)
                            filled = 0
                        }
                    }
                } finally { pending.fill(0); queue.close() }
            }
            for (frame in queue) try {
                if (!running.get()) break
                send(frame)
            } finally { frame.fill(0) }
        } finally { stop(); queue.cancel() }
    }

    fun stop() = synchronized(captureLock) {
        if (running.getAndSet(false)) runCatching(stopCapture)
    }
    override fun close() {
        stop()
        synchronized(captureLock) { if (closed.compareAndSet(false, true)) runCatching(releaseCapture) }
    }

    companion object {
        @Suppress("MissingPermission") // Owner rechecks permission before construction and every send.
        fun android(sampleRate: Int): TalkbackRecorder {
            require(sampleRate in setOf(8000, 16000))
            val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0 || minimum > sampleRate * 2 / 5) throw TalkbackFailure(TalkbackError.MICROPHONE_UNAVAILABLE)
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, sampleRate * 2 / 5))
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw TalkbackFailure(TalkbackError.MICROPHONE_UNAVAILABLE)
            }
            return TalkbackRecorder(sampleRate, {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING)
                    throw TalkbackFailure(TalkbackError.MICROPHONE_UNAVAILABLE)
            }, { bytes, offset, count -> record.read(bytes, offset, count, AudioRecord.READ_NON_BLOCKING) },
                { record.stop() }, { record.release() })
        }
    }
}
