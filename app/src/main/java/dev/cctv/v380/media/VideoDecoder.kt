package dev.cctv.v380.media

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.Surface
import dev.cctv.v380.session.EncodedFrame
import dev.cctv.v380.session.SessionError
import dev.cctv.v380.session.VideoCodec
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

/** Consumes complete, decrypted access units. No camera framing or key derivation lives here. */
class VideoDecoder(
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val format: VideoCodec,
    private val onFirstFrame: () -> Unit = {},
    private val paced: Boolean = false,
    private val onError: (SessionError) -> Unit,
) : AutoCloseable {
    private val running = AtomicBoolean(true)
    private val waitForKeyframe = AtomicBoolean(true)
    private val frames = ArrayBlockingQueue<Pair<Long, EncodedFrame>>(3)
    private var generation = 0L
    private val worker: Thread

    init {
        require(width in 1..8192 && height in 1..8192)
        worker = Thread(::decodeLoop, "v380-video-decoder").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun offer(frame: EncodedFrame): Boolean {
        if (!running.get() || frame.codec != format || frame.bytes.isEmpty() || frame.bytes.size > 8 * 1024 * 1024 || frame.ptsUs < 0) return false
        // Preserve parameter sets while waiting; the worker discards undecodable inter frames.
        if (!frames.offer(generation to frame)) {
            frames.clear()
            generation++
            waitForKeyframe.set(true)
            return frames.offer(generation to frame)
        }
        return true
    }

    /** Playback applies backpressure; live's intentional drop policy never reaches this path. */
    suspend fun offerPlayback(frame: EncodedFrame) {
        require(frame.codec == format && frame.bytes.size in 1..8 * 1024 * 1024 && frame.ptsUs >= 0)
        while (running.get() && currentCoroutineContext().isActive) {
            if (synchronized(this) { frames.offer(generation to frame) }) return
            delay(10)
        }
        throw kotlinx.coroutines.CancellationException("Decoder closed")
    }

    private fun decodeLoop() {
        var codec: MediaCodec? = null
        val parameterSets = mutableMapOf<Int, ByteArray>()
        val required = if (format == VideoCodec.AVC) setOf(7, 8) else setOf(32, 33, 34)
        val info = MediaCodec.BufferInfo()
        val firstRendered = AtomicBoolean(false)
        var renderAnchor: Long? = null
        fun drain(active: MediaCodec) {
            var index = active.dequeueOutputBuffer(info, 0)
            while (index >= 0 && running.get()) {
                if (paced) {
                    if (renderAnchor == null) renderAnchor = System.nanoTime() - info.presentationTimeUs * 1000
                    val due = renderAnchor!! + info.presentationTimeUs * 1000
                    // Bound decoded output too: do not queue an entire recording on the Surface.
                    while (running.get() && due - System.nanoTime() > 1_000_000)
                        Thread.sleep(minOf(20, (due - System.nanoTime()) / 1_000_000).coerceAtLeast(1))
                    active.releaseOutputBuffer(index, due)
                } else active.releaseOutputBuffer(index, true)
                index = active.dequeueOutputBuffer(info, 0)
            }
        }
        try {
            while (running.get() && surface.isValid) {
                val queued = frames.poll(20, TimeUnit.MILLISECONDS)
                if (queued != null) {
                    val frame = queued.second
                    var changed = false
                    for (nal in AnnexB.units(frame.bytes)) {
                        val type = AnnexB.type(nal, format)
                        if (type in required) {
                            val previous = parameterSets.put(type, nal)
                            if (previous != null && !previous.contentEquals(nal)) changed = true
                        }
                    }
                    if (changed && codec != null) {
                        codec.stop(); codec.release(); codec = null
                        waitForKeyframe.set(true)
                    }
                    if (codec == null && parameterSets.keys.containsAll(required)) {
                        val mediaFormat = MediaFormat.createVideoFormat(format.mime, width, height)
                        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8 * 1024 * 1024)
                        if (format == VideoCodec.AVC) {
                            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(AnnexB.withStartCode(parameterSets.getValue(7))))
                            mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(AnnexB.withStartCode(parameterSets.getValue(8))))
                        } else {
                            val csd = required.sorted().fold(byteArrayOf()) { data, type -> data + AnnexB.withStartCode(parameterSets.getValue(type)) }
                            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd))
                        }
                        val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(mediaFormat)
                        if (name == null) { onError(SessionError.UnsupportedCodec); return }
                        codec = MediaCodec.createByCodecName(name)
                        codec.configure(mediaFormat, surface, null, 0)
                        codec.setOnFrameRenderedListener({ _, _, _ ->
                            if (running.get() && firstRendered.compareAndSet(false, true)) onFirstFrame()
                        }, Handler(Looper.getMainLooper()))
                        codec.start()
                    }
                    val active = codec
                    val keyframe = frame.keyframe || AnnexB.isKeyframe(frame.bytes, format)
                    synchronized(this) {
                        if (active != null && queued.first == generation && (!waitForKeyframe.get() || keyframe)) {
                            var index = active.dequeueInputBuffer(10_000)
                            while (paced && index < 0 && running.get()) { drain(active); index = active.dequeueInputBuffer(10_000) }
                            if (index >= 0) {
                                val input = active.getInputBuffer(index)
                                if (input == null || input.capacity() < frame.bytes.size) {
                                    active.queueInputBuffer(index, 0, 0, frame.ptsUs, 0)
                                    waitForKeyframe.set(true)
                                } else {
                                    input.clear(); input.put(frame.bytes)
                                    active.queueInputBuffer(index, 0, frame.bytes.size, frame.ptsUs, 0)
                                    waitForKeyframe.set(false)
                                }
                            } else waitForKeyframe.set(true)
                        }
                    }
                }
                val active = codec
                if (active != null) drain(active)
            }
        } catch (_: InterruptedException) {
            // close() wakes the blocking queue; codec cleanup still runs below.
        } catch (_: Exception) {
            if (running.get()) onError(SessionError.UnsupportedCodec)
        } finally {
            running.set(false)
            frames.clear()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
        }
    }

    override fun close() { running.set(false); frames.clear(); worker.interrupt() }
    @Synchronized
    fun resetToKeyframe() { generation++; frames.clear(); waitForKeyframe.set(true) }
    suspend fun awaitClosed() = withContext(Dispatchers.IO) { if (Thread.currentThread() != worker) worker.join(3000) }
}
