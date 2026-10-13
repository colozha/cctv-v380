package dev.cctv.v380.transport

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.LinkProperties
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import dev.cctv.v380.session.SessionError
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraFailure(val reason: SessionError) : Exception(reason.name)

data class WifiLink(val network: Network, val broadcasts: List<InetAddress>, val localAddress: String,
    val gateway: String?) {
    fun socket(): Socket = Socket().also { socket ->
        try { network.bindSocket(socket) } catch (error: Exception) { socket.close(); throw error }
    }
}
data class NearbyWifi(val ssid: String, val security: String, val signal: Int)

class WifiNetwork(private val context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    @Volatile private var selected: Network? = null
    private val requestLock = Any()
    private class ApRequest(val callback: ConnectivityManager.NetworkCallback) {
        var registered = false
        var disposed = false
        var network: Network? = null
    }
    private var apRequest: ApRequest? = null

    fun select(network: Network): WifiLink = link(network).also { selected = network }

    fun resolve(): WifiLink {
        fun isWifi(candidate: Network?) = candidate != null &&
            manager.getNetworkCapabilities(candidate)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val networks = manager.allNetworks.filter { candidate ->
            manager.getNetworkCapabilities(candidate)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val network = selected?.takeIf { isWifi(it) } ?: manager.activeNetwork?.takeIf { isWifi(it) }
            ?: networks.singleOrNull() ?: throw CameraFailure(SessionError.NetworkUnavailable)
        return link(network)
    }

    private fun link(network: Network): WifiLink {
        if (manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true)
            throw CameraFailure(SessionError.NetworkUnavailable)
        val addresses = manager.getLinkProperties(network)?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address && it.prefixLength in 1..30 }
        if (addresses.isEmpty()) throw CameraFailure(SessionError.NetworkUnavailable)
        val broadcasts = addresses.map { link ->
            val bytes = link.address.address
            val ip = bytes.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
            val mask = (0xffffffffL shl (32 - link.prefixLength)) and 0xffffffffL
            val broadcast = ip or (mask xor 0xffffffffL)
            InetAddress.getByAddress(ByteArray(4) { i -> (broadcast shr ((3 - i) * 8)).toByte() })
        } + InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))
        val gateway = manager.getLinkProperties(network)?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
        return WifiLink(network, broadcasts.distinct(), addresses.first().address.hostAddress.orEmpty(), gateway)
    }

    /** The returned Network remains owned until releaseAp(), including local-only APs. */
    @SuppressLint("MissingPermission")
    suspend fun connectAp(ssid: String, password: CharArray): WifiLink {
        var owned: ApRequest? = null
        try {
        require(ssid.toByteArray(Charsets.UTF_8).size in 1..32)
        require(password.isEmpty() || password.size in 8..63)
        if (Build.VERSION.SDK_INT < 29) throw CameraFailure(SessionError.NetworkUnavailable)
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Wi-Fi connection permission")
        releaseAp()
            return withTimeout(30_000) {
                suspendCancellableCoroutine { continuation ->
                    val delivered = AtomicBoolean(false)
                    lateinit var registration: ApRequest
                    val callback = object : ConnectivityManager.NetworkCallback() {
                        private fun complete(network: Network) {
                            if (!continuation.isActive || delivered.get()) return
                            val available = runCatching { link(network) }.getOrNull() ?: return
                            synchronized(requestLock) {
                                if (registration.disposed || !continuation.isActive || !delivered.compareAndSet(false, true)) return
                                registration.network = network
                                selected = network
                                continuation.resume(available)
                            }
                        }
                        override fun onAvailable(network: Network) = complete(network)
                        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = complete(network)
                        override fun onUnavailable() {
                            if (continuation.isActive && delivered.compareAndSet(false, true))
                                continuation.resumeWithException(CameraFailure(SessionError.NetworkUnavailable))
                        }
                        override fun onLost(network: Network) { if (selected == network) selected = null }
                    }
                    registration = ApRequest(callback)
                    owned = registration
                    synchronized(requestLock) { apRequest = registration }
                    continuation.invokeOnCancellation { dispose(registration) }
                    val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
                        if (password.isNotEmpty()) setWpa2Passphrase(String(password))
                    }.build()
                    val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(specifier).build()
                    synchronized(requestLock) {
                        if (!registration.disposed && continuation.isActive) {
                            manager.requestNetwork(request, callback, 30_000)
                            registration.registered = true
                        }
                    }
                }
            }
        } catch (error: Exception) { owned?.let(::dispose); throw error }
        finally { password.fill('\u0000') }
    }

    private fun dispose(request: ApRequest) = synchronized(requestLock) {
        if (!request.disposed) {
            request.disposed = true
            if (apRequest === request) { apRequest = null; if (selected == request.network) selected = null }
            if (request.registered) runCatching { manager.unregisterNetworkCallback(request.callback) }
        }
    }
    fun releaseAp() {
        synchronized(requestLock) { apRequest }?.let(::dispose)
    }

    @SuppressLint("MissingPermission")
    suspend fun scanSsids(): List<NearbyWifi> {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Wi-Fi scan permission")
        return withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                val done = AtomicBoolean(false)
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (!done.compareAndSet(false, true)) return
                        runCatching { context.unregisterReceiver(this) }
                        val result = runCatching {
                            wifi.scanResults.filter { it.SSID.isNotEmpty() && SystemClock.elapsedRealtime() - it.timestamp / 1000 < 60_000 }
                                .sortedByDescending { it.level }.distinctBy { it.SSID }.take(50)
                                .map { NearbyWifi(it.SSID, when {
                                    "WPA3" in it.capabilities || "SAE" in it.capabilities -> "WPA3"
                                    "WPA" in it.capabilities -> "WPA2"
                                    "WEP" in it.capabilities -> "WEP"
                                    else -> "OPEN"
                                }, it.level) }
                        }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                }
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(receiver, filter)
                continuation.invokeOnCancellation { if (done.compareAndSet(false, true)) runCatching { context.unregisterReceiver(receiver) } }
                val started = runCatching { wifi.startScan() }
                if ((started.isFailure || started.getOrDefault(false).not()) && done.compareAndSet(false, true)) {
                    runCatching { context.unregisterReceiver(receiver) }
                    continuation.resumeWithException(CameraFailure(SessionError.NetworkUnavailable))
                }
            }
        }
    }
}
