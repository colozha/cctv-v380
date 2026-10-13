package dev.cctv.v380.media

import dev.cctv.v380.session.VideoCodec

object AnnexB {
    fun units(data: ByteArray): List<ByteArray> {
        val starts = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i + 2 < data.size) {
            val size = when {
                i + 3 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte() -> 4
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() && data[i + 2] == 1.toByte() -> 3
                else -> 0
            }
            if (size > 0) { starts += i to size; i += size } else i++
        }
        return starts.mapIndexedNotNull { index, (offset, prefix) ->
            val end = starts.getOrNull(index + 1)?.first ?: data.size
            if (end > offset + prefix) data.copyOfRange(offset + prefix, end) else null
        }
    }

    fun type(nal: ByteArray, codec: VideoCodec): Int = if (codec == VideoCodec.AVC) nal[0].toInt() and 31 else (nal[0].toInt() ushr 1) and 63
    fun isKeyframe(data: ByteArray, codec: VideoCodec): Boolean = units(data).any {
        val type = type(it, codec)
        if (codec == VideoCodec.AVC) type == 5 else type in 16..21
    }
    fun withStartCode(nal: ByteArray): ByteArray = byteArrayOf(0, 0, 0, 1) + nal
}
