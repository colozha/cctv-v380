package dev.cctv.v380

import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.media.*
import dev.cctv.v380.transport.TcpChannel
import dev.cctv.v380.ui.recordingDownloadMessage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypted, fragmented in-memory transcripts. No camera, network, decoder or vendor execution. */
class RecordingReplayTest {
    private val segment = RecordingSegment(10000, 30000, 2, 8000)

    @Test fun playbackAndDownloadCrossMultipleFrameWindowsAndFinishOnlyAfterEndNotification() = runBlocking {
        for (download in listOf(true, false)) {
            val peer = Peer(frames = 245)
            val diagnostics = Diagnostics()
            var delivered = 0
            run(peer, diagnostics, download) { frame -> assertEquals(delivered++.toLong(), frame.sequence) }
            assertEquals(245, delivered)
            assertEquals(3, peer.requests.size)
            val initial = peer.requests.first().getJSONObject("params")
            assertEquals(if (download) 2 else 0, initial.getInt("mode"))
            assertEquals(if (download) 8001L else 10001L, initial.getLong("pts"))
            assertEquals(10000L, initial.getLong("sts"))
            assertEquals(0, initial.getInt("audio"))
            peer.requests.drop(1).forEach { assertEquals(100, it.getJSONObject("params").getInt("frames")) }
            val events = diagnostics.events.value
            assertEquals(listOf(90, 180), events.filter { it.detail == DiagnosticDetail.REPLAY_REFILL }.map { it.framesReceived })
            assertEquals(DiagnosticDetail.REPLAY_END, events.last().detail)
            assertEquals(245, events.last().framesReceived)
            assertEquals(245 * 5L, events.last().downloadBytes)
            assertFalse(events.any { it.status == DiagnosticStatus.FAILED })
        }
    }

    @Test fun eofTimeoutRejectionCorrelationAndTimeIdFailuresKeepTheirSpecificCauses() = runBlocking {
        for ((variant, expected) in listOf("eof" to SessionError.NetworkUnavailable, "timeout" to SessionError.Timeout,
            "reject" to SessionError.CameraPermissionDenied, "wrong-id" to SessionError.MalformedPacket,
            "wrong-time" to SessionError.MalformedPacket, "wrong-frame-time" to SessionError.MalformedPacket, "wrong-session" to SessionError.MalformedPacket)) {
            val diagnostics = Diagnostics()
            val failure = runCatching { run(Peer(3, variant), diagnostics) {} }.exceptionOrNull()
            assertNotNull(variant, failure)
            assertEquals(variant, expected, CameraSession.reason(failure as Exception))
            val last = diagnostics.events.value.last()
            assertEquals(DiagnosticStatus.FAILED, last.status)
            assertEquals(expected, last.reason)
            assertFalse(diagnostics.events.value.any { it.detail == DiagnosticDetail.REPLAY_END && it.status == DiagnosticStatus.OK })
            if (variant == "reject") assertEquals(1109, last.code)
            if (variant == "wrong-frame-time") assertEquals(2, last.timeId)
            if (variant in listOf("eof", "timeout")) assertEquals(3, last.framesReceived)
        }
    }

    @Test fun invalidTimestampIsDistinctFromNetworkFailureAndHasReplayCounters() = runBlocking {
        for (variant in listOf("timestamp-back", "timestamp-ahead")) {
            val diagnostics = Diagnostics()
            var first: Long? = null
            val failure = runCatching { run(Peer(3, variant), diagnostics) { frame ->
                if (first == null) first = frame.timestampMs
                recordingTimestampDelta(frame.timestampMs, first!!, segment)
            } }.exceptionOrNull()
            assertTrue(failure is RecordingTimestampException)
            val event = diagnostics.events.value.last()
            assertEquals(SessionError.InvalidRecordingTimestamp, event.reason)
            assertEquals(DiagnosticDetail.REPLAY_TIMESTAMP, event.detail)
            assertEquals(1, event.framesReceived)
        }
    }

    @Test fun exporterWrappingPreservesTheUnderlyingCauseAndUserMessageDoesNotBlameEveryFailureOnWifi() {
        val timeout = SocketTimeoutException("synthetic")
        val failure = RecordingExportException(SessionError.Timeout, DiagnosticDetail.READ_HEADER, recordingDownloadMessage(timeout), timeout)
        assertSame(timeout, failure.cause)
        assertEquals(SessionError.Timeout, CameraSession.reason(failure))
        assertTrue(failure.userMessage.contains("batas waktu"))
        assertTrue(recordingDownloadMessage(EOFException()).contains("terputus"))
        assertTrue(recordingDownloadMessage(IoTProtocolException(IoTFailure.MalformedPacket)).contains("protokol"))
        assertTrue(recordingDownloadMessage(IoTProtocolException(IoTFailure.PermissionDenied, 1109)).contains("tidak mengizinkan"))
    }

    private suspend fun run(peer: Peer, diagnostics: Diagnostics, download: Boolean = true, sample: (IoTMediaFrame) -> Unit) {
        val session = IoTSession(peer.key.copyOf(), 17, 3, 0)
        val channel = TcpChannel(peer.socket())
        try {
            channel.connect("192.0.2.10", 8800)
            RecordingReplay(diagnostics, AtomicInteger(100)::incrementAndGet).consume(channel, session, peer.key,
                segment, segment.startMs, download, { true }) { frame, info ->
                assertEquals(64, info.width); assertEquals(64, info.height)
                sample(frame)
            }
        } finally { channel.close(); session.close() }
    }

    private class Peer(val frames: Int, val variant: String = "ok") {
        val key = ByteArray(32) { (it + 1).toByte() }
        val requests = mutableListOf<JSONObject>()
        private val incoming = ArrayDeque<Int>()
        private var sent = 0
        private fun queue(packet: ByteArray) { packet.forEach { incoming.add(it.toInt() and 255) } }
        fun socket() = object : Socket() {
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun setTcpNoDelay(on: Boolean) = Unit
            override fun setSoTimeout(timeout: Int) = Unit
            override fun close() = Unit
            override fun getInputStream() = object : InputStream() {
                override fun read(): Int = if (incoming.isEmpty()) {
                    if (variant == "timeout") throw SocketTimeoutException("Synthetic replay stalled") else -1
                } else incoming.removeFirst()
                override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
                    if (incoming.isEmpty()) return read()
                    val n = minOf(count, 7, incoming.size)
                    repeat(n) { bytes[offset + it] = incoming.removeFirst().toByte() }
                    return n
                }
            }
            override fun getOutputStream() = object : OutputStream() {
                override fun write(b: Int) = error("Complete packet expected")
                override fun write(bytes: ByteArray, offset: Int, count: Int) {
                    val packet = bytes.copyOfRange(offset, offset + count)
                    val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
                    val request = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), key)
                    requests.add(request)
                    val first = requests.size == 1
                    queue(json(JSONObject().put("id", request.getInt("id") + if (variant == "wrong-id") 1 else 0)
                        .put("chn", 1).put("method_id", RecordingProtocol.CONTROL_RESPONSE)
                        .put("result", JSONObject().put("code", if (variant == "reject") 1109 else 1000))))
                    if (first) queue(information(1))
                    val limit = minOf(sent + 100, frames)
                    while (sent < limit) {
                        var timestamp = 1000L + sent * 10
                        if (sent == 1 && variant == "timestamp-back") timestamp = 999
                        if (sent == 1 && variant == "timestamp-ahead") timestamp = 81001
                        val payload = byteArrayOf(0, 0, 0, 1, if (sent == 0) 0x65 else 0x41)
                        val plain = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                            .put(if (variant == "wrong-frame-time" && sent == 1) 2 else 1).put(if (sent == 0) 1 else 2).putInt(sent).putInt(payload.size).putLong(timestamp)
                            .putInt(0).putInt(0).putInt(0).put(0).put(0).array()
                        queue(packet(IoTCommonProtocol.COMMAND_MEDIA, crypt(plain, false) + payload))
                        sent++
                    }
                    if (sent == frames && variant !in listOf("eof", "timeout")) queue(information(3))
                }
            }
        }
        private fun information(id: Int) = json(JSONObject().put("method_id", RecordingProtocol.INFORMATION).put("params",
            JSONObject().put("time_id", if (variant == "wrong-time") 2 else 1).put("info_id", id).apply {
                if (id == 1) put("info", JSONObject().put("vattr", JSONObject().put("codec", 4).put("width", 64).put("height", 64)))
            }))
        private fun json(value: JSONObject) = packet(IoTCommonProtocol.COMMAND_IOT, crypt(value.toString().toByteArray(), true))
        private fun packet(command: Int, body: ByteArray) = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).put(1).put(2).put(if (variant == "wrong-session") 18 else 17).put(0).putInt(0).putInt(body.size).put(body).array()
        private fun crypt(bytes: ByteArray, padding: Boolean) = Cipher.getInstance(if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
            doFinal(bytes)
        }
    }
}
