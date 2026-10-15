package dev.cctv.v380

import dev.cctv.v380.provisioning.RouterCheck
import dev.cctv.v380.provisioning.routerCheck
import dev.cctv.v380.protocol.ApProtocol
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic loopback only. No Wi-Fi radio, owner camera, cloud or real credentials. */
class WifiProvisioningTest {
    @Test fun routerNameRequiresAnExactMatchOrExplicitConfirmationWhenUnavailable() {
        assertEquals(RouterCheck.MATCHED, routerCheck("synthetic-router", "synthetic-router", false))
        assertEquals(RouterCheck.WRONG_NETWORK, routerCheck("synthetic-router", "synthetic-ap", true))
        assertEquals(RouterCheck.WRONG_NETWORK, routerCheck("synthetic-router", "synthetic-router ", false))
        assertEquals(RouterCheck.CONFIRMATION_REQUIRED, routerCheck("synthetic-router", null, false))
        assertEquals(RouterCheck.MATCHED, routerCheck("synthetic-router", null, true))
    }

    @Test fun encodingLimitsAreCheckedWithoutWritingOrChangingCallerSecrets() {
        val password = "密".repeat(16).toCharArray()
        ApProtocol.validateWifiInput("é".repeat(16), password, true)
        assertTrue(password.all { it == '密' })
        for ((ssid, secret, legacy) in listOf(
            Triple("é".repeat(17), "synthetic", false), Triple("synthetic", "密".repeat(17), true),
            Triple("synthetic", "camera🔒", true), Triple("synthetic", "密".repeat(22), false),
            Triple("synthetic\u0000", "synthetic", true), Triple("synthetic", "syn\u0000thetic", false))) {
            assertTrue(runCatching { ApProtocol.validateWifiInput(ssid, secret.toCharArray(), legacy) }.isFailure)
        }
        ApProtocol.validateWifiInput("synthetic", charArrayOf(), true)
        ApProtocol.validateWifiInput("synthetic", "a".repeat(63).toCharArray(), false)
    }

    @Test fun wifiWriteRequiresTheSameAuthenticatedProfileRevisionAndNetwork() = withSession(0) { session, profile, attempts ->
        val revision = session.authenticationRevision.value
        var pending = 0
        for ((id, device, rev, network) in listOf(
            listOf("another-profile", profile.deviceId, revision, true),
            listOf(profile.id, "another-device", revision, true),
            listOf(profile.id, profile.deviceId, revision - 1, true),
            listOf(profile.id, profile.deviceId, revision, false))) {
            val password = "synthetic".toCharArray()
            assertTrue(runCatching { session.configureWifi(id as String, device as String, rev as Long,
                "synthetic-router", password, { network as Boolean }, { pending++ }) }.isFailure)
            assertTrue(password.all { it == '\u0000' })
        }
        assertEquals(0, pending)
        assertEquals(1, attempts.get())
    }

    @Test fun invalidLegacyEncodingCannotCreatePendingMetadataOrAnotherSocket() = withSession(0) { session, profile, attempts ->
        var pending = 0
        val secret = "synthetic🔒".toCharArray()
        val result = runCatching { session.configureWifi(profile.id, profile.deviceId, session.authenticationRevision.value,
            "synthetic-router", secret, { true }, { pending++ }) }
        assertEquals(SessionError.InvalidInput, CameraSession.reason(result.exceptionOrNull() as Exception))
        assertEquals(0, pending); assertEquals(1, attempts.get())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun pendingCallbackPrecedesTheOnlyWriteAndLostReplyNeverTriggersReplay() = withSession(1, reply = null) { session, profile, attempts ->
        var pending = 0
        var started = 0
        val secret = "synthetic".toCharArray()
        val result = runCatching { session.configureWifi(profile.id, profile.deviceId, session.authenticationRevision.value,
            "synthetic-router", secret, { true }, { assertEquals(0, started); pending++ },
            { assertEquals(1, pending); started++ }) }
        assertTrue(result.isFailure)
        assertEquals(1, pending); assertEquals(1, started); assertEquals(2, attempts.get())
        assertTrue(secret.all { it == '\u0000' })
    }

    @Test fun cancellationOrNetworkLossAfterPendingPreventsCredentialWrite() = runBlocking {
        for (cancel in listOf(false, true)) withSession(1, expectRequest = false) { session, profile, _ ->
            var network = true
            var started = 0
            val secret = "synthetic".toCharArray()
            val worker = async {
                runCatching { session.configureWifi(profile.id, profile.deviceId, session.authenticationRevision.value,
                    "synthetic-router", secret, { network }, {
                        if (cancel) currentCoroutineContext().cancel() else network = false
                    }, { started++ }) }
            }
            runCatching { worker.await() }
            assertEquals(0, started)
            assertTrue(secret.all { it == '\u0000' })
        }
    }

    @Test fun explicitRefusalDiffersFromAValidAcknowledgement() = runBlocking {
        for (status in listOf(1001, 1002)) withSession(1, reply = status) { session, profile, _ ->
            var pending = 0
            val result = runCatching { session.configureWifi(profile.id, profile.deviceId, session.authenticationRevision.value,
                "synthetic-router", "synthetic".toCharArray(), { true }, { pending++ }) }
            assertEquals(1, pending)
            if (status == 1001) assertTrue(result.isSuccess)
            else assertEquals(SessionError.WifiConfigurationRejected, CameraSession.reason(result.exceptionOrNull() as Exception))
        }
    }

    private fun withSession(commands: Int, reply: Int? = 1001, expectRequest: Boolean = true,
        block: suspend (CameraSession, CameraProfile, AtomicInteger) -> Unit,
    ) = runBlocking {
        ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 5000
            val peer = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val login = ExactReader(socket.getInputStream()).read(520)
                    try { assertEquals(1167, ByteBuffer.wrap(login).order(ByteOrder.LITTLE_ENDIAN).getInt(0)) }
                    finally { login.fill(0) }
                    val response = ByteArray(256)
                    ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt(0, 1168); putInt(4, 1001); putInt(13, 123); putInt(17, 456)
                    }
                    response[12] = 31; response[63] = 1
                    socket.getOutputStream().apply { write(response); flush() }
                    response.fill(0)
                }
                repeat(commands) { server.accept().use { socket ->
                    socket.soTimeout = 3000
                    if (!expectRequest) assertEquals(-1, socket.getInputStream().read())
                    else {
                        val request = ExactReader(socket.getInputStream()).read(256)
                        try {
                            val bytes = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
                            assertEquals(320, bytes.getInt(0)); assertEquals(1002, bytes.getInt(13))
                        } finally { request.fill(0) }
                        if (reply != null) {
                            val response = ByteArray(32)
                            ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 420).putInt(4, reply)
                            socket.getOutputStream().apply { write(response); flush() }
                            assertEquals(-1, socket.getInputStream().read())
                        }
                    }
                } }
            }
            val attempts = AtomicInteger()
            val session = CameraSession(channelFactory = { attempts.incrementAndGet(); TcpChannel(Socket()) })
            val profile = CameraProfile(name = "Synthetic setup", host = "127.0.0.1", port = server.localPort,
                deviceId = "424242", username = "synthetic", protocolProfileId = "legacy-v2")
            try {
                session.connect(profile, "synthetic".toCharArray())
                withTimeout(4000) { block(session, profile, attempts) }
                withTimeout(4000) { peer.await() }
            } finally { session.abort(); peer.cancel() }
        }
    }
}
