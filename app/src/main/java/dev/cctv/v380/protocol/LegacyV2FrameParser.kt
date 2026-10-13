package dev.cctv.v380.protocol

import dev.cctv.v380.media.SparseAesCipher
import dev.cctv.v380.session.VideoCodec
import java.io.ByteArrayOutputStream

class LegacyMediaFrame(
    val rawType: Int,
    val frameId: Long,
    val frameType: Int,
    val frameRate: Int,
    /** Raw wire ticks. Their unit is not established by static evidence; use arrival PTS for display. */
    val timestampTicks: Long,
    val channel: Int,
    val codec: VideoCodec?,
    val keyframe: Boolean,
    val audio: Boolean,
    val payload: ByteArray,
)

/** Incremental TCP framing. A malformed packet resets assembly before throwing; no payload scanning. */
class LegacyV2FrameParser(
    private val channel: Int = 0,
    private val allowInitialResponsePadding: Boolean = false,
    private val maxFrameBytes: Int = 8 * 1024 * 1024,
) {
    private val fragment = ByteArray(512)
    private var buffered = 0
    private var targetSize = 12
    private var firstHeader = true
    private var paddingBytes = 0
    private val assembled = ByteArrayOutputStream()
    private var expectedIndex = 0
    private var expectedCount = 0
    private var expectedType = -1

    init { require(channel in 0..65535 && maxFrameBytes in 16..8 * 1024 * 1024) }

    fun feed(bytes: ByteArray): List<LegacyMediaFrame> {
        require(bytes.size <= 1024 * 1024)
        val frames = mutableListOf<LegacyMediaFrame>()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(targetSize - buffered, bytes.size - offset)
            bytes.copyInto(fragment, buffered, offset, offset + count)
            buffered += count; offset += count
            if (buffered < targetSize) continue
            if (targetSize == 12) {
                if (!validHeader(firstHeader)) {
                    if (firstHeader && allowInitialResponsePadding && paddingBytes < 387) {
                        fragment.copyInto(fragment, 0, 1, 12)
                        buffered = 11; paddingBytes++
                        continue
                    }
                    malformed()
                }
                firstHeader = false
                targetSize = 12 + u16(7)
                continue
            }
            acceptFragment()?.let(frames::add)
            fragment.fill(0, 0, targetSize)
            buffered = 0; targetSize = 12
        }
        return frames
    }

    private fun validHeader(initial: Boolean): Boolean {
        if ((fragment[0].toInt() and 255) != 0x7f) return false
        val total = u16(3); val index = u16(5); val length = u16(7)
        return total in 1..((maxFrameBytes + 499) / 500) && index < total && length in 1..500 &&
            (index == total - 1 || length == 500) && (!initial || index == 0) &&
            (total - 1L) * 500 + (if (index == total - 1) length else 1) <= maxFrameBytes
    }

    private fun acceptFragment(): LegacyMediaFrame? {
        val total = u16(3); val index = u16(5); val type = fragment[1].toInt() and 255
        if (index == 0) {
            assembled.reset(); expectedIndex = 0; expectedCount = total; expectedType = type
        }
        if (index != expectedIndex || total != expectedCount || type != expectedType) malformed()
        if (assembled.size() + targetSize - 12 > maxFrameBytes) malformed()
        assembled.write(fragment, 12, targetSize - 12)
        expectedIndex++
        if (expectedIndex != expectedCount) return null
        val full = assembled.toByteArray()
        assembled.reset(); expectedCount = 0; expectedIndex = 0; expectedType = -1
        if (full.size < 16) malformed()
        val input = LegacyLanProtocol.little(full)
        val wireType = input.getShort(4).toInt() and 65535
        val normalized = when (wireType) { 50, 51 -> wireType - 50; 52, 53 -> wireType - 12; else -> wireType }
        val codec = when (normalized) { 0, 1 -> VideoCodec.AVC; 40, 41 -> VideoCodec.HEVC; else -> null }
        return LegacyMediaFrame(type, LegacyLanProtocol.unsigned(input.getInt(0)), wireType,
            input.getShort(6).toInt() and 65535, input.getLong(8), channel, codec,
            normalized in setOf(0, 2, 9, 36, 40), wireType in setOf(21, 22, 24, 25, 26),
            full.copyOfRange(16, full.size))
    }

    fun reset() {
        fragment.fill(0); buffered = 0; targetSize = 12
        assembled.reset(); expectedIndex = 0; expectedCount = 0; expectedType = -1
        firstHeader = true; paddingBytes = 0
    }

    private fun u16(offset: Int): Int = (fragment[offset].toInt() and 255) or
        ((fragment[offset + 1].toInt() and 255) shl 8)

    private fun malformed(): Nothing {
        reset()
        // A corrupt frame cannot reopen the one-time response401 padding allowance.
        firstHeader = false
        throw LegacyMalformedPacketException()
    }
}

/** Cipher mode is selected only from authenticated protocol/communication metadata and inner media type. */
class LegacyMediaCipher(private val protocolVersion: Int, private val communicationVersion: Int, handle: Long) : AutoCloseable {
    private val key = ByteArray(16).also {
        LegacyLanProtocol.little(it).putInt(0, handle.toInt())
        LegacyProtocolConstants.mediaKeyTail.let { tail -> tail.copyInto(it, 4); tail.fill(0) }
    }
    private var closed = false

    init { require(protocolVersion in 2..32 && communicationVersion in 20..21) }

    fun decrypt(frame: LegacyMediaFrame): ByteArray {
        check(!closed)
        if (protocolVersion < 31) return frame.payload.copyOf()
        val mode = when {
            frame.audio -> SparseAesCipher.Mode.ALIGNED
            communicationVersion == 21 -> SparseAesCipher.Mode.FIRST_2048
            else -> SparseAesCipher.Mode.SPARSE_64_80
        }
        return SparseAesCipher.decrypt(frame.payload, key, mode)
    }

    override fun close() { key.fill(0); closed = true }
}
