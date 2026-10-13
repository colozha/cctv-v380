package dev.cctv.v380.control

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** Protocol-neutral lease. Callback commands require a verified camera profile. */
class PtzWatchdog(
    private val scope: CoroutineScope,
    private val send: suspend (Direction?) -> Unit,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private var direction: Direction? = null
    private var lastInput = 0L
    private var watcher: Job? = null

    suspend fun move(value: Direction) = mutex.withLock {
        if (direction != value) {
            if (direction != null) send(null)
            send(value)
            direction = value
        }
        lastInput = nowMs()
        if (watcher?.isActive != true) watcher = scope.launch {
            while (true) {
                delay(100)
                val stopped = mutex.withLock {
                    if (direction == null) true else if (nowMs() - lastInput >= 1000) {
                        direction = null
                        // The session callback records/recoveries transport failure. A failed
                        // best-effort watchdog STOP must not escape as an uncaught coroutine error.
                        try { send(null) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { }
                        true
                    } else false
                }
                if (stopped) break
            }
        }
    }

    suspend fun renew(resend: Boolean = false) = mutex.withLock {
        direction?.let { active ->
            lastInput = nowMs()
            if (resend) send(active)
        }
    }

    suspend fun stop() = mutex.withLock {
        watcher?.cancel()
        watcher = null
        if (direction != null) { direction = null; send(null) }
    }
}
