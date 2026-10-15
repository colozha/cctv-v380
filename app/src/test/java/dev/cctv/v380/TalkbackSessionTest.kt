package dev.cctv.v380

import dev.cctv.v380.media.*
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Entirely synthetic sockets/capture. No microphone, Surface, decoder or camera is opened. */
class TalkbackSessionTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test fun gatingCannotConstructCaptureOrWriteBeforeAuthorization() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture")
            assertEquals(TalkbackState.Error(TalkbackError.NOT_READY), f.session.talkback.value)
            assertEquals(0, f.created.get()); assertEquals(0, f.peer.writes.get())
            f.capabilities.value = f.capabilities.value!!.copy(talkback = null)
            f.session.startTalkback("fixture") { true }
            assertEquals(TalkbackState.Error(TalkbackError.CAPABILITY_UNKNOWN), f.session.talkback.value)
            f.capabilities.value = f.capabilities.value!!.copy(talkback = true)
            f.audio.value = IoTAudioInfo(101, 8000, 16, 1)
            f.session.startTalkback("fixture") { true }
            assertEquals(TalkbackState.Error(TalkbackError.UNSUPPORTED_AUDIO), f.session.talkback.value)
            assertEquals(0, f.created.get()); assertEquals(0, f.peer.writes.get())
        }
    }

    @Test fun ackPrecedesCaptureDoubleTapCannotDuplicateAndNormalStopKeepsVideoSocket() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture") { true }
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            assertEquals(0, f.started.get())
            f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            assertEquals(1, f.created.get()); assertEquals(1, f.started.get())
            f.frames.set(1)
            waitFor { f.peer.media.get() == 1 }
            f.session.requestStopTalkback()
            f.session.stopTalkback()
            waitFor { f.session.talkback.value is TalkbackState.Idle }
            assertFalse(f.peer.isClosed)
            assertEquals(1, f.released.get())
            assertEquals(SessionState.Streaming(Quality.HD), f.state.value)
            f.frames.set(3); delay(50)
            assertEquals(1, f.peer.media.get())
        }
    }

    @Test fun cancellingAnUnacknowledgedStartClosesSharedSocketWithoutStartingMicrophone() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            f.session.requestStopTalkback()
            f.session.stopTalkback()
            waitFor { f.released.get() == 1 }
            assertTrue(f.peer.isClosed); assertEquals(0, f.started.get())
            delay(30); assertFalse(f.session.talkback.value is TalkbackState.Sending)
        }
    }

    @Test fun rejectedStartPreservesVideoAndNeverStartsCapture() = runBlocking {
        for ((code, expected) in listOf(1109 to TalkbackError.CAMERA_PERMISSION, 1199 to TalkbackError.DEVICE_REJECTED)) {
            Fixture().use { f ->
                f.session.startTalkback("fixture") { true }
                waitFor { f.peer.writes.get() == 1 }
                f.peer.reply(code)
                waitFor { f.session.talkback.value is TalkbackState.Error }
                assertEquals(TalkbackState.Error(expected), f.session.talkback.value)
                assertEquals(0, f.started.get()); assertFalse(f.peer.isClosed)
                assertEquals(SessionState.Streaming(Quality.HD), f.state.value)
            }
        }
    }

    @Test fun mismatchedResponseCannotAuthorizeCapture() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            f.peer.reply(method = IoTCommonProtocol.LIVE_CONTROL_RESPONSE + 1)
            waitFor { f.peer.isClosed }
            assertEquals(0, f.started.get())
        }
    }

    @Test fun stopDuringBlockedAudioWriteClosesSocketAndCannotResumeCapture() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }; f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            f.peer.blockMedia.set(true)
            f.frames.set(1)
            waitFor { f.peer.mediaWriting.get() }
            f.session.requestStopTalkback(); f.session.stopTalkback()
            waitFor { f.peer.isClosed && f.released.get() == 1 }
            assertEquals(1, f.started.get()); assertEquals(0, f.peer.media.get())
            assertFalse(f.session.talkback.value is TalkbackState.Sending)
        }
    }

    @Test fun revokingForegroundAuthorizationAndMaintenanceStopsCaptureWithoutResume() = runBlocking {
        Fixture().use { f ->
            val foreground = AtomicBoolean(true)
            f.session.startTalkback("fixture") { foreground.get() }
            waitFor { f.peer.writes.get() == 1 }; f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            foreground.set(false)
            f.session.blockMaintenanceWrites(true)
            f.session.stopTalkback()
            waitFor { f.released.get() == 1 }
            f.session.blockMaintenanceWrites(false)
            foreground.set(true); delay(30)
            assertEquals(1, f.started.get()); assertEquals(TalkbackState.Idle, f.session.talkback.value)
        }
    }

    @Test fun mediaCloseSurfaceLossAndAbortAlwaysReleaseCapture() = runBlocking {
        for (action in listOf<suspend (CameraSession) -> Unit>({ it.stopMedia() }, { it.detachSurface() }, { it.close() }, { it.abort() })) {
            Fixture().use { f ->
                f.session.startTalkback("fixture") { true }
                waitFor { f.peer.writes.get() == 1 }; f.peer.reply()
                waitFor { f.session.talkback.value is TalkbackState.Sending }
                action(f.session)
                waitFor { f.released.get() == 1 }
                assertTrue(f.peer.isClosed)
                assertFalse(f.session.talkback.value is TalkbackState.Sending)
                assertEquals(1, f.started.get())
            }
        }
    }

    @Test fun startTimeoutAndStaleIdentityNeverActivateCapture() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("different-profile") { true }
            assertEquals(TalkbackState.Error(TalkbackError.NOT_READY), f.session.talkback.value)
            assertEquals(0, f.created.get())
            f.session.startTalkback("fixture") { true }
            withTimeout(6500) { while (!f.peer.isClosed) delay(10) }
            assertEquals(0, f.started.get())
            assertFalse(f.session.talkback.value is TalkbackState.Sending)
        }
    }

    @Test fun sharedSocketSerializesAudioPtzAndFeedbackWithoutByteInterleaving() = runBlocking {
        val output = java.io.ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getOutputStream() = object : OutputStream() {
                override fun write(value: Int) { output.write(value); Thread.yield() }
                override fun write(bytes: ByteArray, offset: Int, count: Int) {
                    for (i in offset until offset + count) write(bytes[i].toInt())
                }
            }
        }
        val channel = TcpChannel(socket)
        val packets = listOf(
            IoTTalkbackProtocol.audioPacket(ByteArray(1010) { 7 }, 1, 1000, 17, key),
            IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.ACTION_REQUEST, requestId = 2,
                params = IoTControlProtocol.ptzParams(IoTPtzOperation.Stop), secureType = 2, sessionId = 17, key = key),
            IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.DECODE_FEEDBACK, requestId = 3,
                params = JSONObject().put("flag", 2).put("tms1", 1000).put("decode_tms", 0), secureType = 2, sessionId = 17, key = key))
        try {
            coroutineScope { repeat(12) { index -> launch { channel.write(packets[index % packets.size]) } } }
            val bytes = output.toByteArray()
            var offset = 0
            var parsed = 0
            while (offset < bytes.size) {
                val header = IoTCommonProtocol.parseHeader(bytes.copyOfRange(offset, offset + 16))
                val packet = bytes.copyOfRange(offset, offset + 16 + header.bodySize)
                assertTrue("Every shared-channel packet must remain intact", packets.any { it.contentEquals(packet) })
                offset += packet.size; parsed++
            }
            assertEquals(12, parsed)
        } finally { channel.close(); packets.forEach { it.fill(0) } }
    }

    private suspend fun waitFor(check: () -> Boolean) { withTimeout(2500) { while (!check()) delay(5) } }

    private inner class Fixture : AutoCloseable {
        val created = AtomicInteger(); val started = AtomicInteger(); val released = AtomicInteger()
        val frames = AtomicInteger()
        val peer = Peer()
        val channel = TcpChannel(peer)
        val owner = IoTSession(key.copyOf(), 17, 0, 0)
        val session = CameraSession(channelFactory = { error("No external transport") }, talkbackRecorderFactory = { rate ->
            created.incrementAndGet()
            TalkbackRecorder(rate, { started.incrementAndGet() }, { bytes, offset, count ->
                if (frames.get() <= 0) 0 else {
                    bytes.fill(5, offset, offset + count)
                    if (offset + count == 1010) frames.decrementAndGet()
                    count
                }
            }, {}, { released.incrementAndGet() })
        })
        val state = field<MutableStateFlow<SessionState>>(session, "mutableState")
        val capabilities = field<MutableStateFlow<CameraCapabilities?>>(session, "mutableCapabilities")
        val audio = field<MutableStateFlow<IoTAudioInfo?>>(session, "mutableTalkbackAudio")
        init {
            set(session, "profile", CameraProfile(id = "fixture", name = "Synthetic", deviceId = "synthetic", protocolProfileId = "iot-lan"))
            set(session, "iot", owner); set(session, "authenticatedIoT", owner); set(session, "accessRevoked", false)
            set(session, "streamChannel", channel)
            state.value = SessionState.Streaming(Quality.HD)
            capabilities.value = CameraCapabilities(emptySet(), Quality.entries.toSet(), false, talkback = true)
            audio.value = IoTAudioInfo(100, 8000, 16, 1)
            val reader = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                        val method = session.javaClass.declaredMethods.single { it.name == "readIoTStream" }
                        method.isAccessible = true
                        method.invoke(session, channel, owner, key.copyOf(), 0L, continuation)
                    }
                } catch (error: Exception) {
                    val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
                    if (cause !is CancellationException) {
                        session.javaClass.getDeclaredMethod("streamFailure", Long::class.javaPrimitiveType, Exception::class.java,
                            DiagnosticStage::class.java).also { it.isAccessible = true }
                            .invoke(session, 0L, cause as Exception, DiagnosticStage.STREAM)
                    }
                }
            }
            set(session, "streamJob", reader)
        }
        override fun close() { session.dispose(); peer.close() }
    }

    private inner class Peer : Socket() {
        val writes = AtomicInteger(); val media = AtomicInteger()
        val blockMedia = AtomicBoolean(); val mediaWriting = AtomicBoolean()
        private val closedFlag = AtomicBoolean()
        private val incoming = LinkedBlockingQueue<Int>()
        private var requestId = 0
        override fun isClosed() = closedFlag.get()
        override fun close() { closedFlag.set(true); incoming.offer(-1) }
        override fun getInputStream() = object : InputStream() {
            override fun read(): Int = incoming.take()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                val first = read(); if (first < 0) return -1
                bytes[offset] = first.toByte()
                var count = 1
                while (count < length) {
                    val next = incoming.poll() ?: break
                    if (next < 0) { incoming.offer(-1); break }
                    bytes[offset + count++] = next.toByte()
                }
                return count
            }
        }
        override fun getOutputStream() = object : OutputStream() {
            override fun write(value: Int) = error("Expected atomic packet")
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                val packet = bytes.copyOfRange(offset, offset + length)
                try {
                    val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
                    if (header.command == IoTCommonProtocol.COMMAND_MEDIA) {
                        mediaWriting.set(true)
                        while (blockMedia.get() && !isClosed) Thread.sleep(2)
                        if (isClosed) throw java.io.IOException("Synthetic closed write")
                        assertTrue(IoTMediaProtocol.parseFrame(header, packet.copyOfRange(16, packet.size), key).audio)
                        media.incrementAndGet(); mediaWriting.set(false)
                    } else {
                        val json = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), key)
                        requestId = json.getInt("id")
                        assertEquals(100, json.getJSONObject("params").getJSONObject("down").getJSONObject("aattr").getInt("codec"))
                        writes.incrementAndGet()
                    }
                } finally { packet.fill(0) }
            }
        }
        fun reply(code: Int = 1000, method: Int = IoTCommonProtocol.LIVE_CONTROL_RESPONSE) {
            val json = JSONObject().put("id", requestId).put("method_id", method).put("chn", 1)
                .put("result", JSONObject().put("code", code)).toString().toByteArray()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
            val body = cipher.doFinal(json)
            val packet = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(IoTCommonProtocol.COMMAND_IOT).put(1).put(2).put(17).put(0).putInt(0).putInt(body.size).put(body).array()
            packet.forEach { incoming.offer(it.toInt() and 255) }
            packet.fill(0); body.fill(0); json.fill(0)
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) as T }
    private fun set(owner: Any, name: String, value: Any) { owner.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(owner, value) }
}
