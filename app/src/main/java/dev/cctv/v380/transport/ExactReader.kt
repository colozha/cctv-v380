package dev.cctv.v380.transport

import java.io.EOFException
import java.io.InputStream

class ExactReader(private val input: InputStream, private val limit: Int = 8 * 1024 * 1024,
    private val beforeRead: () -> Unit = {}) {
    fun read(size: Int): ByteArray {
        require(size in 0..limit) { "Message length outside the configured bound" }
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            beforeRead()
            val count = input.read(bytes, offset, size - offset)
            if (count < 0) throw EOFException("Truncated message")
            if (count == 0) {
                beforeRead()
                val value = input.read()
                if (value < 0) throw EOFException("Truncated message")
                bytes[offset++] = value.toByte()
            } else offset += count
        }
        return bytes
    }
}
