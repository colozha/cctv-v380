package dev.cctv.v380.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.cctv.v380.session.EncodedFrame
import dev.cctv.v380.session.VideoCodec
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/** One bounded, video-only MP4. Network EOF must be verified before finish() is called. */
class RecordingExporter(cacheDir: File) : AutoCloseable {
    private val file = File.createTempFile("v380-recording-", ".mp4", cacheDir)
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var format: VideoCodec? = null
    private var dimensions: Pair<Int, Int>? = null
    private val parameterSets = mutableMapOf<Int, ByteArray>()
    private var basePts: Long? = null
    private var lastPts = -1L
    private var samples = 0
    private var finished = false
    var bytesWritten = 0L
        private set

    fun write(frame: EncodedFrame, width: Int, height: Int) {
        check(!finished) { "Export is closed" }
        if (frame.bytes.size !in 1..8 * 1024 * 1024 || frame.ptsUs < 0 || width !in 1..8192 || height !in 1..8192 ||
            width.toLong() * height > 33_554_432 || file.usableSpace < frame.bytes.size + 1024 * 1024L) throw IOException("Export resource limit")
        if (format != null && (format != frame.codec || dimensions != width to height)) throw IOException("Recording format changed")
        format = frame.codec; dimensions = width to height
        val required = if (frame.codec == VideoCodec.AVC) setOf(7, 8) else setOf(32, 33, 34)
        val units = AnnexB.units(frame.bytes)
        if (units.isEmpty()) throw IOException("Missing access-unit framing")
        for (nal in units) {
            val type = AnnexB.type(nal, frame.codec)
            if (type in required) {
                val before = parameterSets.put(type, nal)
                if (muxer != null && before != null && !before.contentEquals(nal)) throw IOException("Recording parameters changed")
            }
        }
        val video = units.any { val t = AnnexB.type(it, frame.codec); if (frame.codec == VideoCodec.AVC) t in 1..5 else t in 0..31 }
        bytesWritten = Math.addExact(bytesWritten, frame.bytes.size.toLong())
        if (!video) return
        if (muxer == null) {
            if (!parameterSets.keys.containsAll(required) || !(frame.keyframe || AnnexB.isKeyframe(frame.bytes, frame.codec)))
                throw IOException("Recording does not start at a decodable keyframe")
            val mediaFormat = MediaFormat.createVideoFormat(frame.codec.mime, width, height)
            if (frame.codec == VideoCodec.AVC) {
                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(AnnexB.withStartCode(parameterSets.getValue(7))))
                mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(AnnexB.withStartCode(parameterSets.getValue(8))))
            } else mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(required.sorted().fold(byteArrayOf()) { bytes, type ->
                bytes + AnnexB.withStartCode(parameterSets.getValue(type))
            }))
            val writer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            track = writer.addTrack(mediaFormat); writer.start(); basePts = frame.ptsUs
        }
        val pts = frame.ptsUs - basePts!!
        if (pts < lastPts) throw IOException("Recording timestamp moved backwards")
        val info = MediaCodec.BufferInfo().apply {
            set(0, frame.bytes.size, pts, if (frame.keyframe || AnnexB.isKeyframe(frame.bytes, frame.codec)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
        }
        muxer!!.writeSampleData(track, ByteBuffer.wrap(frame.bytes), info)
        lastPts = pts; samples++
    }

    fun finish(): File {
        check(!finished) { "Export is closed" }
        finished = true
        val writer = muxer ?: throw IOException("Recording has no video")
        try { writer.stop() } finally { writer.release(); muxer = null }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            if (extractor.trackCount != 1) throw IOException("Invalid MP4 tracks")
            extractor.selectTrack(0)
            var count = 0
            while (extractor.sampleTime >= 0) { count++; if (!extractor.advance()) break }
            if (count != samples || count == 0) throw IOException("Incomplete MP4")
        } finally { extractor.release() }
        return file
    }

    override fun close() {
        finished = true
        runCatching { muxer?.stop() }; runCatching { muxer?.release() }; muxer = null
        file.delete()
    }
}
