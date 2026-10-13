package dev.cctv.v380

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.control.Direction
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.*
import dev.cctv.v380.ui.MainActivity
import kotlinx.coroutines.*
import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic peer builds response bytes independently; no device or vendor app traffic. */
class IoTSessionVideoTest {
    @Test fun keyExchangeInfoBeforeAckEncryptedAvcAndPtzReachTheRealSession(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} android.permission.ACCESS_LOCAL_NETWORK").close()
        val frames = syntheticAvcFrames()
        val display = AtomicReference<Surface>()
        val ready = CountDownLatch(1)
        val activity = ActivityScenario.launch(MainActivity::class.java)
        val peerError = AtomicReference<Throwable?>()
        val controls = CountDownLatch(1)
        val finished = CountDownLatch(1)
        try {
            activity.onActivity { host -> host.setContentView(SurfaceView(host).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { display.set(holder.surface); ready.countDown() }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
            }) }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            ServerSocket(0).use { server ->
                server.soTimeout = 6000
                val peer = Thread {
                    val shared = ByteArray(32)
                    val privateKey = ByteArray(32) { (it + 43).toByte() }
                    val bootstrap = IoTKeyExchange.bootstrapKey()
                    try {
                        server.accept().use { auth ->
                            auth.soTimeout = 5000
                            val (header, request) = readJson(auth, bootstrap)
                            assertEquals(196870, request.getInt("method_id"))
                            val verify = request.getJSONObject("params").getJSONObject("verify")
                            val masked = Base64.getDecoder().decode(verify.getString("cKM"))
                            val digest = MessageDigest.getInstance("SHA-256").digest(masked + "synthetic-local-only".toByteArray())
                            assertArrayEquals(digest, Base64.getDecoder().decode(verify.getString("cS")))
                            val clientPublic = ByteArray(32) { (masked[it].toInt() xor header.magic).toByte() }
                            val devicePublic = ByteArray(32).also { X25519.scalarMultBase(privateKey, 0, it, 0) }
                            assertTrue(X25519.calculateAgreement(privateKey, 0, clientPublic, 0, shared, 0))
                            val proof = JSONObject().put("dK", Base64.getEncoder().encodeToString(devicePublic))
                                .put("dS", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(devicePublic)))
                                .put("sid", 17).put("perm", 3L).put("origin", 0)
                            writeJson(auth, response(request, 196871, JSONObject().put("verify", proof)), bootstrap)
                            assertEquals(-1, auth.getInputStream().read())
                            masked.fill(0); digest.fill(0); clientPublic.fill(0); devicePublic.fill(0)
                        }
                        server.accept().use { ability ->
                            ability.soTimeout = 5000
                            val (_, request) = readJson(ability, shared)
                            assertEquals(197386, request.getInt("method_id"))
                            writeJson(ability, response(request, 197387, JSONObject().put("ctrl", JSONArray().put(3))), shared)
                            assertEquals(-1, ability.getInputStream().read())
                        }
                        server.accept().use { stream ->
                            stream.soTimeout = 5000
                            val (_, request) = readJson(stream, shared)
                            assertEquals(197122, request.getInt("method_id"))
                            assertEquals(2, request.getJSONObject("params").getJSONObject("up").getInt("quality"))
                            val info = JSONObject().put("method_id", 197124).put("chn", 1).put("params",
                                JSONObject().put("vattr", JSONObject().put("codec", 4).put("width", 64).put("height", 64)))
                            writeJson(stream, info, shared, IoTCommonProtocol.COMMAND_NOTIFY)
                            writeJson(stream, response(request, 197123, null), shared)
                            frames.forEachIndexed { index, frame ->
                                val chunk = if (frame.bytes.size >= 16) 16 else 0
                                val body = ByteBuffer.allocate(32 + frame.bytes.size).order(ByteOrder.LITTLE_ENDIAN)
                                    .put(1).put(if (frame.keyframe) 1 else 2).putInt(index).putInt(frame.bytes.size)
                                    .putLong(index * 100L).putInt(0).putInt(chunk).putInt(0).put(0).put(0).put(frame.bytes).array()
                                val aligned = if (chunk == 0) 32 else body.size / 16 * 16
                                val encrypted = crypt(body.copyOfRange(0, aligned), shared, Cipher.ENCRYPT_MODE, false) + body.copyOfRange(aligned, body.size)
                                stream.getOutputStream().apply { write(packet(IoTCommonProtocol.COMMAND_MEDIA, encrypted)); flush() }
                                body.fill(0); encrypted.fill(0)
                                Thread.sleep(50)
                            }
                            val (_, move) = readJson(stream, shared)
                            val (_, stop) = readJson(stream, shared)
                            assertEquals(197384, move.getInt("method_id"))
                            assertEquals(393728, move.getJSONObject("params").getInt("daid"))
                            assertEquals(3, move.getJSONObject("params").getJSONObject("in").getInt("op"))
                            assertEquals(0, stop.getJSONObject("params").getJSONObject("in").getInt("op"))
                            controls.countDown()
                            assertEquals(-1, stream.getInputStream().read())
                        }
                    } catch (error: Throwable) { peerError.set(error) }
                    finally { shared.fill(0); privateKey.fill(0); bootstrap.fill(0); finished.countDown() }
                }.apply { isDaemon = true; start() }
                val session = CameraSession(channelFactory = { TcpChannel(Socket()) })
                try {
                    val caps = session.connect(CameraProfile(name = "Synthetic IoT", host = "127.0.0.1", port = server.localPort,
                        deviceId = "synthetic-iot", protocolProfileId = "iot-lan"), "synthetic-local-only".toCharArray())
                    assertTrue(caps.horizontalPtz && caps.verticalPtz)
                    session.play(display.get(), Quality.HD)
                    assertTrue("Encrypted IoT frame reached the display render callback", session.state.value is SessionState.Streaming)
                    session.move(Direction.LEFT); delay(20); session.stopPtz()
                    assertTrue(withContext(Dispatchers.IO) { controls.await(2, TimeUnit.SECONDS) })
                    session.close()
                    assertTrue(withContext(Dispatchers.IO) { finished.await(2, TimeUnit.SECONDS) })
                    peerError.get()?.let { throw AssertionError("Synthetic IoT peer failed", it) }
                } finally { session.dispose(); server.close(); peer.join(1000) }
            }
        } finally { activity.close() }
    }

    private fun response(request: JSONObject, method: Int, data: JSONObject?) = JSONObject()
        .put("id", request.getInt("id")).put("chn", 1).put("method_id", method)
        .put("result", JSONObject().put("code", 1000)).apply { data?.let { put("data", it) } }
    private fun packet(command: Int, body: ByteArray) = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(command).put(1).put(2).put(17).put(0).putInt(0).putInt(body.size).put(body).array()
    private fun writeJson(socket: Socket, json: JSONObject, key: ByteArray, command: Int = IoTCommonProtocol.COMMAND_IOT) {
        val body = crypt(json.toString().toByteArray(), key, Cipher.ENCRYPT_MODE)
        try { socket.getOutputStream().apply { write(packet(command, body)); flush() } } finally { body.fill(0) }
    }
    private fun readJson(socket: Socket, key: ByteArray): Pair<IoTHeader, JSONObject> {
        val reader = ExactReader(socket.getInputStream())
        val header = IoTCommonProtocol.parseHeader(reader.read(16))
        val body = reader.read(header.bodySize)
        val plain = crypt(body, key, Cipher.DECRYPT_MODE)
        try { return header to JSONObject(plain.toString(Charsets.UTF_8)) }
        finally { body.fill(0); plain.fill(0) }
    }
    private fun crypt(bytes: ByteArray, key: ByteArray, mode: Int, padding: Boolean = true): ByteArray =
        Cipher.getInstance(if (padding) "AES/CBC/PKCS5Padding" else "AES/CBC/NoPadding").run {
            init(mode, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
            doFinal(bytes)
        }
}
