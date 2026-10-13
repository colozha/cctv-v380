package dev.cctv.v380

import dev.cctv.v380.session.*
import dev.cctv.v380.transport.CameraFailure
import dev.cctv.v380.transport.ExactReader
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Complete synthetic legacy transactions over loopback; never creates a Surface or MediaCodec. */
class CameraSessionTest {
    private fun profile(port: Int) = CameraProfile(name = "Synthetic camera", host = "127.0.0.1", port = port,
        deviceId = "424242", username = "synthetic", protocolProfileId = "legacy-v2")
    private fun response(command: Int = 1168, result: Int = 1001) = ByteArray(256).also {
        ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0, command); putInt(4, result); putInt(13, 0x11223344); putInt(17, 0x55667788)
        }
        it[12] = 31; it[63] = 1; it[64] = 1; it[67] = 1
    }
    private fun readSyntheticLogin(socket: Socket) {
        val request = ExactReader(socket.getInputStream()).read(520)
        try { assertEquals(1167, ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN).getInt(0)) }
        finally { request.fill(0) }
    }

    @Test fun resolvedAddressAndMetadataDriveOneLoginThenReadyAndCloseClearsState() = runBlocking {
        withPeer({ socket ->
            readSyntheticLogin(socket)
            val reply = response()
            socket.getOutputStream().apply { write(reply, 0, 37); flush(); write(reply, 37, reply.size - 37); flush() }
            reply.fill(0)
            assertEquals("Authentication channel should close after its reply", -1, socket.getInputStream().read())
        }) { port ->
            val attempts = AtomicInteger()
            val resolutions = AtomicInteger()
            val resolvedTarget = AtomicReference<CameraProfile>()
            val session = CameraSession(channelFactory = { target ->
                attempts.incrementAndGet(); resolvedTarget.set(target); TcpChannel(Socket())
            }, resolveTarget = { target ->
                resolutions.incrementAndGet()
                target.copy(host = "127.0.0.1", port = port, platformType = 1)
            })
            val password = "synthetic-local-password".toCharArray()
            try {
                val input = profile(1).copy(host = "127.0.0.2", protocolProfileId = "auto")
                val caps = withTimeout(2000) { session.connect(input, password) }
                assertEquals(1, resolutions.get()); assertEquals(1, attempts.get())
                assertEquals(port, resolvedTarget.get().port); assertEquals("127.0.0.1", resolvedTarget.get().host)
                assertEquals(input.deviceId, resolvedTarget.get().deviceId)
                assertEquals(SessionState.Ready(caps), session.state.value)
                assertEquals(caps, session.capabilities.value); assertTrue(caps.ptz)
                assertEquals(setOf(Quality.SD, Quality.HD), caps.qualities)
                assertTrue("Codec remains unknown until actual media arrives", caps.codecs.isEmpty())
                assertTrue(password.all { it == '\u0000' })
                withTimeout(1000) { session.close() }
                assertEquals(SessionState.Idle, session.state.value)
                assertNull(session.capabilities.value); assertNull(session.connectedProfile)
                withTimeout(1000) { session.close() }
            } finally { session.abort(); password.fill('\u0000') }
        }
    }

    @Test fun rejectedPasswordAndUnsupportedCommandNeverProbeASecondLoginFormat() = runBlocking {
        for ((command, result, expected) in listOf(Triple(1168, 1012, SessionError.AuthenticationFailed),
            Triple(-100, 1001, SessionError.UnsupportedProtocol))) {
            withPeer({ socket ->
                readSyntheticLogin(socket)
                val reply = response(command, result)
                try { socket.getOutputStream().apply { write(reply); flush() } } finally { reply.fill(0) }
                assertEquals("Rejected login must close rather than reuse its socket for another format", -1, socket.getInputStream().read())
            }) { port ->
                val attempts = AtomicInteger()
                val session = CameraSession(channelFactory = { attempts.incrementAndGet(); TcpChannel(Socket()) })
                val password = "synthetic-rejected-password".toCharArray()
                try {
                    val failure = runCatching { withTimeout(2000) { session.connect(profile(port), password) } }.exceptionOrNull()
                    assertTrue(failure is CameraFailure)
                    assertEquals(expected, (failure as CameraFailure).reason)
                    assertEquals(SessionState.Error(expected), session.state.value)
                    assertEquals("Only the selected authentication format may be sent", 1, attempts.get())
                    assertNull(session.capabilities.value)
                    assertTrue(password.all { it == '\u0000' })
                    withTimeout(1000) { session.close() }
                    assertEquals(SessionState.Idle, session.state.value)
                } finally { session.abort(); password.fill('\u0000') }
            }
        }
    }

    @Test fun cancellingAuthenticationClosesSocketAndWipesCallerPassword() = runBlocking {
        val requestReceived = CountDownLatch(1)
        val peerSawClose = CountDownLatch(1)
        withPeer({ socket ->
            readSyntheticLogin(socket); requestReceived.countDown()
            if (socket.getInputStream().read() == -1) peerSawClose.countDown()
        }) { port ->
            val clientSocket = AtomicReference<Socket>()
            val session = CameraSession(channelFactory = { TcpChannel(Socket().also(clientSocket::set)) })
            val password = "synthetic-cancelled-password".toCharArray()
            try {
                val authentication = launch { session.connect(profile(port), password) }
                assertTrue(withContext(Dispatchers.IO) { requestReceived.await(1, TimeUnit.SECONDS) })
                withTimeout(1000) { authentication.cancelAndJoin() }
                assertTrue(authentication.isCancelled)
                assertTrue(clientSocket.get().isClosed)
                assertTrue(withContext(Dispatchers.IO) { peerSawClose.await(1, TimeUnit.SECONDS) })
                assertTrue(password.all { it == '\u0000' })
                assertNull(session.capabilities.value)
                withTimeout(1000) { session.close() }
                assertEquals(SessionState.Idle, session.state.value)
                assertNull(session.connectedProfile)
            } finally { session.abort(); password.fill('\u0000') }
        }
    }

    @Test fun unknownHandshakeFamilyCannotSendCredentialsEvenWithCameraLikeName() = runBlocking {
        val attempts = AtomicInteger()
        val session = CameraSession(channelFactory = {
            attempts.incrementAndGet(); error("Unknown protocol must never allocate a transport")
        }, resolveTarget = { it.copy(platformType = 99) })
        val password = "synthetic-explicit-password".toCharArray()
        try {
            val target = profile(8800).copy(name = "HSC synthetic", protocolProfileId = "auto")
            val failure = runCatching { session.connect(target, password) }.exceptionOrNull()
            assertTrue(failure is CameraFailure)
            assertEquals(SessionError.UnsupportedProtocol, (failure as CameraFailure).reason)
            assertEquals(0, attempts.get()); assertNull(session.capabilities.value)
            assertTrue(password.all { it == '\u0000' })
        } finally { session.abort(); password.fill('\u0000') }
    }

    @Test fun cancellingQueuedConnectDoesNotAbortTheCurrentAuthentication() = runBlocking {
        val received = CountDownLatch(1)
        val releaseReply = CountDownLatch(1)
        withPeer({ socket ->
            readSyntheticLogin(socket); received.countDown()
            assertTrue(releaseReply.await(3, TimeUnit.SECONDS))
            val reply = response()
            try { socket.getOutputStream().apply { write(reply); flush() } } finally { reply.fill(0) }
        }) { port ->
            val attempts = AtomicInteger()
            val socket = AtomicReference<Socket>()
            val session = CameraSession(channelFactory = {
                attempts.incrementAndGet(); TcpChannel(Socket().also(socket::set))
            })
            val activePassword = "synthetic-active-password".toCharArray()
            val queuedPassword = "synthetic-queued-password".toCharArray()
            try {
                val active = async { runCatching { session.connect(profile(port), activePassword) } }
                assertTrue(withContext(Dispatchers.IO) { received.await(1, TimeUnit.SECONDS) })
                val queuedStarted = CompletableDeferred<Unit>()
                val queued = launch { queuedStarted.complete(Unit); session.connect(profile(port), queuedPassword) }
                queuedStarted.await(); yield()
                withTimeout(1000) { queued.cancelAndJoin() }
                assertTrue(queuedPassword.all { it == '\u0000' })
                assertFalse("Cancelling a waiter must not close the active owner's socket", socket.get().isClosed)
                assertEquals(1, attempts.get())
                releaseReply.countDown()
                val result = withTimeout(1500) { active.await() }
                assertTrue("The original authentication must still succeed", result.isSuccess)
                assertTrue(session.state.value is SessionState.Ready)
                withTimeout(1000) { session.close() }
            } finally {
                releaseReply.countDown(); session.abort()
                activePassword.fill('\u0000'); queuedPassword.fill('\u0000')
            }
        }
    }

    private suspend fun withPeer(peerAction: (Socket) -> Unit, action: suspend (Int) -> Unit) {
        ServerSocket().use { server ->
            server.bind(java.net.InetSocketAddress("127.0.0.1", 0))
            val error = AtomicReference<Throwable>()
            val finished = CountDownLatch(1)
            val peer = Thread {
                try { server.accept().use { socket -> socket.soTimeout = 2500; peerAction(socket) } }
                catch (failure: Throwable) { error.set(failure) }
                finally { finished.countDown() }
            }.apply { isDaemon = true; start() }
            try { action(server.localPort) }
            finally {
                server.close()
                assertTrue("Synthetic peer must terminate", withContext(Dispatchers.IO) { finished.await(3, TimeUnit.SECONDS) })
                peer.join(1000)
            }
            error.get()?.let { throw AssertionError("Synthetic loopback peer failed", it) }
        }
    }
}
