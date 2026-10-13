package dev.cctv.v380.transport

import dev.cctv.v380.session.isIpv4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

fun deadlineAfterMillis(milliseconds: Long = 5000): Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds)

/** Deadlines include every partial read and a blocked write; cancellation closes the socket. */
class TcpChannel(private val socket: Socket) : AutoCloseable {
    private val writeLock = Any()

    suspend fun connect(host: String, port: Int) {
        require(isIpv4(host) && port in 1..65535)
        io(deadlineAfterMillis(3000)) { socket.tcpNoDelay = true; socket.connect(InetSocketAddress(host, port), 3000) }
    }

    suspend fun readExact(size: Int, deadlineNanos: Long = deadlineAfterMillis()): ByteArray = io(deadlineNanos) {
        ExactReader(socket.getInputStream(), beforeRead = { readTimeout(deadlineNanos) }).read(size)
    }

    suspend fun readSome(maxBytes: Int = 64 * 1024, deadlineNanos: Long = deadlineAfterMillis()): ByteArray = io(deadlineNanos) {
        require(maxBytes in 1..(8 * 1024 * 1024))
        readTimeout(deadlineNanos)
        val bytes = ByteArray(maxBytes)
        val count = socket.getInputStream().read(bytes)
        if (count < 0) throw EOFException("Camera connection closed")
        if (count == 0) {
            readTimeout(deadlineNanos)
            val next = socket.getInputStream().read()
            if (next < 0) throw EOFException("Camera connection closed")
            byteArrayOf(next.toByte())
        } else bytes.copyOf(count)
    }

    suspend fun write(bytes: ByteArray, deadlineNanos: Long = deadlineAfterMillis()) = io(deadlineNanos) {
        require(bytes.size <= 8 * 1024 * 1024)
        synchronized(writeLock) {
            checkDeadline(deadlineNanos)
            socket.getOutputStream().write(bytes)
            socket.getOutputStream().flush()
        }
    }

    private fun readTimeout(deadline: Long) { socket.soTimeout = checkDeadline(deadline) }
    private fun checkDeadline(deadline: Long): Int {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw SocketTimeoutException("Camera transaction deadline")
        return ((remaining + 999_999) / 1_000_000).coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }

    private suspend fun <T> io(deadline: Long, block: () -> T): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            if (!continuation.isActive) return@Runnable
            val completed = AtomicBoolean(false)
            val timer = deadlines.schedule({ if (completed.compareAndSet(false, true)) close() },
                (deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
            val result = runCatching { checkDeadline(deadline); block() }
            val finishedInTime = completed.compareAndSet(false, true)
            timer.cancel(false)
            if (result.exceptionOrNull() is SocketTimeoutException) close()
            if (continuation.isActive) continuation.resumeWith(if (!finishedInTime)
                Result.failure(SocketTimeoutException("Camera transaction deadline")) else result)
        })
    }

    override fun close() { runCatching { socket.close() } }
    companion object {
        private val deadlines = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "v380-socket-deadline").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
