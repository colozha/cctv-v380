package dev.cctv.v380.discovery

import dev.cctv.v380.transport.WifiNetwork
import dev.cctv.v380.transport.WifiLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext

class LanDiscovery(private val wifi: WifiNetwork) {
    suspend fun scan(link: WifiLink = wifi.resolve(), target: java.net.InetAddress? = null,
        onFound: (DiscoveredCamera) -> Unit = {}): List<DiscoveredCamera> = suspendCancellableCoroutine { continuation ->
        val sender = AtomicReference<DatagramSocket?>()
        val receiver = AtomicReference<DatagramSocket?>()
        continuation.invokeOnCancellation { sender.get()?.close(); receiver.get()?.close() }
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
            val result = runCatching {
                val rx = DatagramSocket(null).also { receiver.set(it) }
                val tx = DatagramSocket(null).also { sender.set(it) }
                try {
                    if (!continuation.isActive) return@runCatching emptyList()
                    rx.reuseAddress = false
                    rx.bind(InetSocketAddress(10009))
                    rx.soTimeout = 200
                    rx.receiveBufferSize = 16384
                    tx.reuseAddress = false
                    tx.broadcast = true
                    tx.bind(InetSocketAddress(10008))
                    link.network.bindSocket(rx)
                    link.network.bindSocket(tx)
                    val request = "NVDEVSEARCH^100".toByteArray(Charsets.US_ASCII)
                    val start = System.nanoTime()
                    var nextSend = start
                    val found = linkedMapOf<String, DiscoveredCamera>()
                    while (continuation.isActive && System.nanoTime() - start < 3_000_000_000L) {
                        if (System.nanoTime() >= nextSend) {
                            (link.broadcasts + listOfNotNull(target)).distinct().forEach { address -> tx.send(DatagramPacket(request, request.size, address, 10008)) }
                            nextSend = System.nanoTime() + 500_000_000L
                        }
                        // Wake at the next broadcast instead of rounding 500 ms up to 600 ms.
                        rx.soTimeout = ((nextSend - System.nanoTime() + 999_999L) / 1_000_000L)
                            .coerceIn(1L, 200L).toInt()
                        val packet = DatagramPacket(ByteArray(4096), 4096)
                        try { rx.receive(packet) } catch (_: SocketTimeoutException) { continue }
                        val camera = DiscoveryParser.parse(packet.data, packet.length, packet.address.hostAddress.orEmpty()) ?: continue
                        if (found.put(camera.deviceId, camera) != camera) onFound(camera)
                    }
                    found.values.toList()
                } finally { tx.close(); rx.close() }
            }
            sender.get()?.close()
            receiver.get()?.close()
            continuation.resumeWith(result)
        })
    }
}
