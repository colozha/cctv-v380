package dev.cctv.v380

import dev.cctv.v380.media.*
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
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

    @Test fun codec102StartsCaptureAndWritesAfterAcknowledgement() = runBlocking {
        Fixture().use { f ->
            f.audio.value = IoTAudioInfo(102, 16000, 16, 1)
            f.peer.expectedCodec = 102
            assertNull(f.session.talkbackBlock("fixture"))
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            assertEquals(1, f.created.get()); assertEquals(1, f.started.get())
            f.frames.set(1)
            waitFor { f.peer.media.get() == 1 }
            f.session.requestStopTalkback()
            f.session.stopTalkback()
            waitFor { f.session.talkback.value is TalkbackState.Idle }
            assertEquals(1, f.released.get())
            assertFalse(f.peer.isClosed)
        }
    }

    @Test fun probeIsFailClosedWhileBuildFlagIsDisabled() = runBlocking {
        Fixture().use { f ->
            assumeFalse(BuildConfig.TALKBACK_PROBE_ENABLED)
            f.audio.value = IoTAudioInfo(102, 16000, 16, 1)
            f.session.startTalkbackProbe("fixture") { true }
            assertEquals(TalkbackState.Error(TalkbackError.UNSUPPORTED), f.session.talkback.value)
            assertEquals(0, f.peer.writes.get()); assertEquals(0, f.peer.media.get())
        }
    }

    @Test fun probeSendsBoundedSilenceFramesWithoutOpeningCapture() = runBlocking {
        Fixture().use { f ->
            assumeTrue(BuildConfig.TALKBACK_PROBE_ENABLED)
            f.session.startTalkbackProbe("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            assertEquals(0, f.created.get())
            f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Idle }
            assertEquals(15, f.peer.media.get())
            assertEquals(0, f.started.get()); assertEquals(0, f.released.get())
            assertFalse(f.peer.isClosed)
            assertEquals(SessionState.Streaming(Quality.HD), f.state.value)
        }
    }

    @Test fun probeSendsSilenceForCodec102() = runBlocking {
        Fixture().use { f ->
            assumeTrue(BuildConfig.TALKBACK_PROBE_ENABLED)
            f.audio.value = IoTAudioInfo(102, 16000, 16, 1)
            f.peer.expectedCodec = 102
            f.peer.expectedIma = ByteArray(256)
            assertNull(f.session.talkbackBlock("fixture"))
            f.session.startTalkbackProbe("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            assertEquals(0, f.created.get())
            f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Idle }
            assertEquals(15, f.peer.media.get())
            assertEquals(0, f.started.get())
            assertFalse(f.peer.isClosed)
        }
    }

    @Test fun voiceTestProbeCapturesAndStopsOnRequest() = runBlocking {
        Fixture().use { f ->
            assumeTrue(BuildConfig.TALKBACK_PROBE_ENABLED)
            f.session.startTalkbackVoiceTest("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            f.peer.reply()
            waitFor { f.started.get() == 1 }
            assertEquals(1, f.created.get())
            f.session.requestStopTalkback()
            f.session.stopTalkback()
            waitFor { f.session.talkback.value is TalkbackState.Idle }
            assertEquals(1, f.released.get())
            assertFalse(f.peer.isClosed)
            assertEquals(SessionState.Streaming(Quality.HD), f.state.value)
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

    @Test fun readerAcceptsAudioOnlyMetadataAndPreservesItAcrossVideoOnlyUpdates() = runBlocking {
        Fixture().use { f ->
            f.audio.value = null
            assertEquals(TalkbackError.METADATA_MISSING, f.session.talkbackBlock("fixture"))
            f.information(JSONObject().put("vattr", videoAttr()))
            assertEquals(TalkbackError.METADATA_ABSENT, f.session.talkbackBlock("fixture"))
            f.information(JSONObject().put("aattr", audioAttr()))
            assertNull(f.session.talkbackBlock("fixture"))
            val readyCount = f.diagnostics.events.value.count { it.detail == DiagnosticDetail.TALKBACK_METADATA_READY }
            f.information(JSONObject().put("aattr", audioAttr()))
            f.information(JSONObject().put("vattr", videoAttr()))
            assertEquals(IoTAudioInfo(100, 8000, 16, 1), f.audio.value)
            assertEquals(readyCount, f.diagnostics.events.value.count { it.detail == DiagnosticDetail.TALKBACK_METADATA_READY })
            assertNull(f.session.talkbackBlock("fixture"))
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }
            assertEquals(0, f.started.get())
            f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            f.information(JSONObject().put("vattr", videoAttr()))
            assertTrue(f.session.talkback.value is TalkbackState.Sending)
            assertFalse(f.peer.isClosed)
        }
    }

    @Test fun readerRejectsInvalidAndUntrustedMetadataWithoutOpeningCapture() = runBlocking {
        Fixture().use { f ->
            for (attr in listOf<Any>(JSONObject.NULL, "invalid", audioAttr().put("samplerate", 8000.5),
                    audioAttr().apply { remove("bits") })) {
                f.information(JSONObject().put("aattr", attr))
                assertEquals(TalkbackError.METADATA_INVALID, f.session.talkbackBlock("fixture"))
                f.session.startTalkback("fixture") { true }
                f.information(JSONObject().put("vattr", videoAttr()))
                assertEquals(TalkbackError.METADATA_INVALID, f.session.talkbackBlock("fixture"))
            }
            for ((security, sid, command) in listOf(Triple(0, 17, IoTCommonProtocol.COMMAND_NOTIFY),
                    Triple(1, 17, IoTCommonProtocol.COMMAND_NOTIFY),
                    Triple(2, 17, IoTCommonProtocol.COMMAND_IOT))) {
                f.information(JSONObject().put("aattr", audioAttr()), security, sid, command)
                assertEquals(if (command != IoTCommonProtocol.COMMAND_NOTIFY) TalkbackError.METADATA_COMMAND
                    else TalkbackError.METADATA_CIPHER, f.session.talkbackBlock("fixture"))
                f.session.startTalkback("fixture") { true }
            }
            assertEquals(0, f.created.get()); assertEquals(0, f.peer.writes.get())
            assertFalse(f.peer.isClosed)
            f.information(JSONObject().put("aattr", audioAttr()))
            assertNull(f.session.talkbackBlock("fixture"))
            assertEquals(TalkbackState.Idle, f.session.talkback.value)
            val exported = f.diagnostics.exportJson()
            assertTrue(exported.contains("TALKBACK_METADATA_CIPHER"))
            assertFalse(exported.contains("aattr")); assertFalse(exported.contains("sessionId"))
            assertTrue(exported.contains("sessionMatches"))
        }
    }

    @Test fun cameraEnvelopeWithDifferentSidKeepsCodec102Metadata() = runBlocking {
        Fixture().use { f ->
            f.audio.value = null
            // Observed shape: NOTIFY/CBC, different header SID, codec102 mono16 at16kHz.
            // All packet/key/SID values here are synthetic.
            f.information(JSONObject().put("aattr", audioAttr().put("codec", 102).put("samplerate", 16000)), sid = 18)
            assertEquals(IoTAudioInfo(102, 16000, 16, 1), f.audio.value)
            assertNull(f.session.talkbackMetadataIssue.value)
            assertNull(f.session.talkbackBlock("fixture"))
            assertTrue(f.diagnostics.exportJson().contains("TALKBACK_METADATA_READY"))
            assertFalse(f.diagnostics.exportJson().contains("sessionId"))
            f.information(JSONObject().put("aattr", audioAttr().put("codec", 103).put("samplerate", 16000)), sid = 18)
            assertEquals(TalkbackError.UNSUPPORTED_AUDIO, f.session.talkbackBlock("fixture"))
            f.session.startTalkback("fixture") { true }
            assertEquals(0, f.created.get()); assertEquals(0, f.peer.writes.get())
            assertEquals(false, f.diagnostics.events.value.mapNotNull { it.talkbackMetadata }.last().sessionMatches)
            for (sid in listOf(0, 18, 255)) {
                f.information(JSONObject().put("aattr", audioAttr()), sid = sid)
                f.information(JSONObject().put("vattr", videoAttr()), sid = sid)
                assertNull(f.session.talkbackBlock("fixture"))
                assertEquals(IoTAudioInfo(100, 8000, 16, 1), f.audio.value)
            }
            assertEquals(0, f.created.get()); assertEquals(0, f.peer.writes.get())
        }
    }

    @Test fun formatChangeStopsCaptureAndStreamTeardownClearsMetadata() = runBlocking {
        Fixture().use { f ->
            f.session.startTalkback("fixture") { true }
            waitFor { f.peer.writes.get() == 1 }; f.peer.reply()
            waitFor { f.session.talkback.value is TalkbackState.Sending }
            f.information(JSONObject().put("aattr", audioAttr().put("codec", 102).put("samplerate", 16000)))
            waitFor { f.released.get() == 1 }
            assertNull(f.session.talkbackBlock("fixture"))
            f.information(JSONObject().put("aattr", audioAttr().put("codec", 101)))
            waitFor { f.released.get() == 1 }
            assertEquals(TalkbackError.UNSUPPORTED_AUDIO, f.session.talkbackBlock("fixture"))
            f.session.startTalkback("fixture") { true }
            assertEquals(1, f.created.get())
            f.information(JSONObject().put("aattr", audioAttr().put("samplerate", 16000)))
            assertNull(f.session.talkbackBlock("fixture"))
            assertFalse(f.session.talkback.value is TalkbackState.Sending)
            f.session.detachSurface()
            assertNull(f.audio.value)
            assertEquals(TalkbackError.METADATA_MISSING, f.session.talkbackMetadataIssue.value)
            // A late callback from the previous reader cannot repopulate the cleared stream.
            val update = f.session.javaClass.declaredMethods.single { it.name == "updateTalkbackMetadata" }
            update.isAccessible = true
            update.invoke(f.session, IoTHeader(IoTCommonProtocol.COMMAND_NOTIFY, 1, 2, 17, 0, 0),
                JSONObject().put("params", JSONObject().put("aattr", audioAttr())), f.owner, f.channel, 0L)
            assertNull(f.audio.value)
        }
    }

    private fun audioAttr() = JSONObject().put("codec", 100).put("samplerate", 8000).put("bits", 16).put("chns", 1)
    private fun videoAttr() = JSONObject().put("codec", 4).put("width", 640).put("height", 480)

    private suspend fun waitFor(check: () -> Boolean) { withTimeout(2500) { while (!check()) delay(5) } }

    private inner class Fixture : AutoCloseable {
        val created = AtomicInteger(); val started = AtomicInteger(); val released = AtomicInteger()
        val frames = AtomicInteger()
        val peer = Peer()
        val channel = TcpChannel(peer)
        val owner = IoTSession(key.copyOf(), 17, 0, 0)
        val session = CameraSession(channelFactory = { error("No external transport") }, talkbackRecorderFactory = { info, _ ->
            created.incrementAndGet()
            TalkbackRecorder(info.sampleRate, { started.incrementAndGet() }, { bytes, offset, count ->
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
        val diagnostics = field<Diagnostics>(session, "diagnostics")
        suspend fun information(params: JSONObject, security: Int = 2, sid: Int = 17,
            command: Int = IoTCommonProtocol.COMMAND_NOTIFY) {
            val count = diagnostics.events.value.count { it.stage == DiagnosticStage.STREAM && it.bytes != null }
            peer.sendJson(JSONObject().put("method_id", IoTCommonProtocol.LIVE_INFORMATION).put("chn", 1)
                .put("params", params), security, sid, command)
            waitFor { diagnostics.events.value.count { it.stage == DiagnosticStage.STREAM && it.bytes != null } > count }
        }
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
        var expectedCodec = 100
        var expectedIma: ByteArray? = null
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
                        val frame = IoTMediaProtocol.parseFrame(header, packet.copyOfRange(16, packet.size), key)
                        assertTrue(frame.audio)
                        if (expectedCodec == 102) {
                            val expected = expectedIma ?: ByteArray(256).apply { this[0] = 5; this[1] = 5 }
                            assertArrayEquals("PCM21 path must send a native IMA block, never ADTS", expected, frame.bytes)
                        }
                        media.incrementAndGet(); mediaWriting.set(false)
                    } else {
                        val json = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), key)
                        requestId = json.getInt("id")
                        assertEquals(expectedCodec, json.getJSONObject("params").getJSONObject("down").getJSONObject("aattr").getInt("codec"))
                        writes.incrementAndGet()
                    }
                } finally { packet.fill(0) }
            }
        }
        fun reply(code: Int = 1000, method: Int = IoTCommonProtocol.LIVE_CONTROL_RESPONSE) {
            sendJson(JSONObject().put("id", requestId).put("method_id", method).put("chn", 1)
                .put("result", JSONObject().put("code", code)))
        }
        fun sendJson(json: JSONObject, security: Int = 2, sid: Int = 17,
            command: Int = IoTCommonProtocol.COMMAND_IOT) {
            val plain = json.toString().toByteArray()
            val body = if (security == 0) plain.copyOf() else {
                val cipher = Cipher.getInstance(if (security == 1) "AES/ECB/PKCS5Padding" else "AES/CBC/PKCS5Padding")
                val secret = SecretKeySpec(key.copyOfRange(0, 16), "AES")
                if (security == 1) cipher.init(Cipher.ENCRYPT_MODE, secret)
                else cipher.init(Cipher.ENCRYPT_MODE, secret, IvParameterSpec(key.copyOfRange(16, 32)))
                cipher.doFinal(plain)
            }
            val packet = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(command).put(1).put(security.toByte()).put(sid.toByte()).put(0).putInt(0).putInt(body.size).put(body).array()
            packet.forEach { incoming.offer(it.toInt() and 255) }
            packet.fill(0); body.fill(0); plain.fill(0)
        }
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) as T }
    private fun set(owner: Any, name: String, value: Any) { owner.javaClass.getDeclaredField(name).also { it.isAccessible = true }.set(owner, value) }
}
