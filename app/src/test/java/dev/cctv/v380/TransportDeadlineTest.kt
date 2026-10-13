package dev.cctv.v380

import dev.cctv.v380.transport.TcpChannel
import dev.cctv.v380.transport.deadlineAfterMillis
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Local loopback peers only. No camera protocol, credentials, discovery or external traffic. */
class TransportDeadlineTest {
    @Test fun slowTrickleCannotRestartTheAbsoluteReadDeadline() = runBlocking {
        ServerSocket(0).use { server ->
            val peerFinished = CountDownLatch(1)
            val peer = Thread {
                try {
                    server.accept().use { socket ->
                        socket.tcpNoDelay = true
                        repeat(8) {
                            socket.getOutputStream().write(it)
                            socket.getOutputStream().flush()
                            Thread.sleep(70)
                        }
                    }
                } catch (_: Exception) { /* Client deadline closes its socket while the peer is sending. */ }
                finally { peerFinished.countDown() }
            }.apply { isDaemon = true; start() }
            val socket = Socket()
            TcpChannel(socket).use { channel ->
                channel.connect("127.0.0.1", server.localPort)
                val start = System.nanoTime()
                val failure = runCatching {
                    withTimeout(2000) { channel.readExact(8, deadlineAfterMillis(210)) }
                }.exceptionOrNull()
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                assertTrue("Trickled bytes must not extend the transaction deadline", failure is SocketTimeoutException)
                assertTrue("Read should stop near its total budget, elapsed=$elapsedMs ms", elapsedMs < 1200)
                assertTrue("An expired transaction must release the socket", socket.isClosed)
            }
            assertTrue(peerFinished.await(2, TimeUnit.SECONDS))
            peer.join(1000)
        }
    }

    @Test fun backpressuredWriteHonorsItsTotalDeadline() = runBlocking {
        withNonReadingPeer { channel, socket ->
            val start = System.nanoTime()
            val failure = runCatching {
                withTimeout(2500) { channel.write(ByteArray(8 * 1024 * 1024), deadlineAfterMillis(200)) }
            }.exceptionOrNull()
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            assertTrue("A peer that does not read must cause a write deadline", failure is SocketTimeoutException)
            assertTrue("Write deadline must close the socket", socket.isClosed)
            assertTrue("Blocked write should stop within its total budget, elapsed=$elapsedMs ms", elapsedMs < 1500)
        }
    }

    @Test fun cancellingBackpressuredWriteClosesSocketAndCompletesCancellation() = runBlocking {
        withNonReadingPeer { channel, socket ->
            val completed = AtomicBoolean(false)
            val write = launch {
                channel.write(ByteArray(8 * 1024 * 1024), deadlineAfterMillis(10_000))
                completed.set(true)
            }
            delay(80)
            assertFalse("The bounded receive window should keep this write blocked", completed.get())
            withTimeout(1000) { write.cancelAndJoin() }
            assertTrue("Cancellation must close the underlying socket", socket.isClosed)
            assertTrue(write.isCancelled)
        }
    }

    private suspend fun withNonReadingPeer(block: suspend (TcpChannel, Socket) -> Unit) {
        ServerSocket().use { server ->
            // Set the small receive window before the SYN/accept so loopback autotuning cannot absorb 8 MiB.
            server.receiveBufferSize = 1024
            server.bind(java.net.InetSocketAddress("127.0.0.1", 0))
            val accepted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val peer = Thread {
                try {
                    server.accept().use { socket ->
                        socket.receiveBufferSize = 1024
                        accepted.countDown()
                        release.await(4, TimeUnit.SECONDS)
                    }
                } catch (_: Exception) { /* Test cleanup may close the listener before accept finishes. */ }
                finally { finished.countDown() }
            }.apply { isDaemon = true; start() }
            val socket = Socket().apply { sendBufferSize = 1024 }
            try {
                TcpChannel(socket).use { channel ->
                    channel.connect("127.0.0.1", server.localPort)
                    assertTrue(accepted.await(1, TimeUnit.SECONDS))
                    block(channel, socket)
                }
            } finally {
                socket.close(); release.countDown()
                assertTrue("Loopback peer must be released", finished.await(2, TimeUnit.SECONDS))
                peer.join(1000)
            }
        }
    }
}
