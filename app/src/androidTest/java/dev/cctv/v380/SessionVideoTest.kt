package dev.cctv.v380

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.test.core.app.ActivityScenario
import dev.cctv.v380.ui.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.control.Direction
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SessionVideoTest {
    @Test fun syntheticPeerAuthenticatesStreamsToSurfaceAndReceivesPtzStop(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 37) instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} android.permission.ACCESS_LOCAL_NETWORK").close()
        val packets = syntheticAvcFrames()
        val peerFailure = AtomicReference<Throwable?>()
        val peerDone = CountDownLatch(1)
        val controlsReceived = CountDownLatch(1)
        // Android8's ImageReader consumer does not report MediaCodec's display render callback.
        // Use the same visible SurfaceView consumer as the player, instead of weakening Streaming.
        val ready = CountDownLatch(1)
        val display = AtomicReference<Surface>()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
        activity.onActivity { host ->
            host.setContentView(SurfaceView(host).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) { display.set(holder.surface); ready.countDown() }
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
            })
        }
        assertTrue("Display Surface created", ready.await(5, TimeUnit.SECONDS))
        ServerSocket(0).use { server ->
            server.soTimeout = 6000
            val peer = Thread {
                try {
                    server.accept().use { auth ->
                        auth.soTimeout = 5000
                        val request = ExactReader(auth.getInputStream()).read(520)
                        assertEquals(1167, little(request).getInt(0)); request.fill(0)
                        val response = ByteArray(256)
                        little(response).putInt(0, 1168).putInt(4, 1001).putInt(13, 12345).putInt(17, 67890)
                        response[12] = 2; response[63] = 1; response[67] = 1
                        auth.getOutputStream().apply { write(response); flush() }
                        assertEquals(-1, auth.getInputStream().read())
                    }
                    server.accept().use { stream ->
                        stream.soTimeout = 5000
                        val input = ExactReader(stream.getInputStream())
                        val start = input.read(256)
                        assertEquals(301, little(start).getInt(0))
                        assertEquals("APK selector1 is HD", 1, little(start).getInt(26))
                        val response = ByteArray(25)
                        little(response).putInt(0, 401).putInt(4, 1001).putShort(8, 20).putInt(10, 64).putInt(14, 64)
                        stream.getOutputStream().apply { write(response); flush() }
                        val continuation = input.read(256)
                        assertEquals(303, little(continuation).getInt(0)); assertEquals(0x3001, little(continuation).getInt(4))
                        packets.forEachIndexed { index, frame ->
                            val body = ByteArray(16 + frame.bytes.size)
                            little(body).putInt(0, index).putShort(4, if (frame.keyframe) 0 else 1).putShort(6, 10).putLong(8, index * 100L)
                            frame.bytes.copyInto(body, 16)
                            val count = (body.size + 499) / 500
                            repeat(count) { fragment ->
                                val offset = fragment * 500
                                val size = minOf(500, body.size - offset)
                                val packet = ByteArray(12 + size)
                                packet[0] = 0x7f
                                little(packet).putShort(3, count.toShort()).putShort(5, fragment.toShort()).putShort(7, size.toShort())
                                body.copyInto(packet, 12, offset, offset + size)
                                stream.getOutputStream().write(packet)
                            }
                            stream.getOutputStream().flush()
                            Thread.sleep(50)
                        }
                        val move = input.read(16); val stop = input.read(16)
                        assertEquals(170, little(move).getInt(0)); assertEquals(1001, little(move).getShort(8).toInt())
                        assertEquals(170, little(stop).getInt(0)); assertEquals(1000, little(stop).getShort(8).toInt())
                        assertEquals(1000, little(stop).getShort(10).toInt())
                        controlsReceived.countDown()
                        assertEquals(-1, stream.getInputStream().read())
                    }
                } catch (failure: Throwable) { peerFailure.set(failure) }
                finally { peerDone.countDown() }
            }.apply { isDaemon = true; start() }
            val session = CameraSession(channelFactory = { TcpChannel(Socket()) })
            try {
                session.connect(CameraProfile(name = "Synthetic loopback", host = "127.0.0.1", port = server.localPort,
                    deviceId = "424242", username = "synthetic", protocolProfileId = "legacy-v2"), "synthetic-test-only".toCharArray())
                assertTrue(session.state.value is SessionState.Ready)
                session.play(display.get(), Quality.HD)
                assertTrue("Streaming is emitted only after an actual render callback", session.state.value is SessionState.Streaming)
                session.move(Direction.LEFT); delay(20); session.stopPtz()
                assertTrue("Synthetic peer received move and stop", withContext(Dispatchers.IO) { controlsReceived.await(2, TimeUnit.SECONDS) })
                session.close()
                assertEquals(SessionState.Idle, session.state.value)
                assertTrue(withContext(Dispatchers.IO) { peerDone.await(2, TimeUnit.SECONDS) })
                peerFailure.get()?.let { throw AssertionError("Synthetic peer failed", it) }
            } finally { session.dispose(); server.close(); peer.join(1000) }
        }
        } finally { activity.close() }
    }
    private fun little(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
}
