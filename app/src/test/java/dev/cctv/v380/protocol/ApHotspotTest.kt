package dev.cctv.v380.protocol

import dev.cctv.v380.session.*
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** In-memory socket transcripts only: no network, camera, emulator, or vendor execution. */
class ApHotspotTest {
    private val profile = CameraProfile(id = "synthetic", name = "Synthetic AP", host = "192.0.2.10",
        deviceId = "424242", protocolProfileId = "iot-lan")

    @Test fun codecKeepsRouterModeSeparateAndUsesRecoveredSoftApCapabilityAndInputRules() {
        val password = "Secret123@".toCharArray()
        val action = ApHotspotProtocol.actionParams("CameraNew", password, "HSC_OLD")
        assertEquals(262656, action.getInt("daid"))
        assertEquals(1, action.getJSONObject("in").getInt("mode"))
        assertEquals(2, ApProtocol.wifiConfigParams("router", password).getInt("mode"))
        assertEquals("CameraNew", String(Base64.getDecoder().decode(action.getJSONObject("in").getString("ssid"))))
        assertEquals("Secret123@", String(Base64.getDecoder().decode(action.getJSONObject("in").getString("pwd"))))
        assertFalse(action.has("no_rsp"))
        assertNull(ApHotspotProtocol.supportsSoftAp(JSONObject()))
        assertEquals(false, ApHotspotProtocol.supportsSoftAp(JSONObject().put("net", JSONArray(listOf(1, 1)))))
        assertEquals(true, ApHotspotProtocol.supportsSoftAp(JSONObject().put("net", JSONArray(listOf(1, 4)))))
        for (ssid in listOf("", " ", " Camera", "Camera ", "New_Name", "a".repeat(31), "\u0000"))
            assertNotNull(ApHotspotProtocol.inputError(ssid, password, "HSC_OLD"))
        assertNull(ApHotspotProtocol.inputError("HSC_OLD", password, "HSC_OLD"))
        for (invalid in listOf("short", "a".repeat(31), "Secret123 ", "Secret!123", "pässword123"))
            assertNotNull(ApHotspotProtocol.inputError("Camera", invalid.toCharArray()))
        assertNull(ApHotspotProtocol.inputError("a".repeat(30), "a".repeat(30).toCharArray()))
        val read = snapshot("HSC_OLD")
        assertEquals("HSC_OLD", ApHotspotProtocol.parseSnapshot(read).ssid)
        read.getJSONArray("dplist_result").getJSONObject(1).put("dpid", ApHotspotProtocol.NETWORK_MODE)
        assertThrows(IoTProtocolException::class.java) { ApHotspotProtocol.parseSnapshot(read) }
    }

    @Test fun unconnectedCameraAndPreAuthProfileCannotAuthorizeAnAction() = runBlocking {
        var sockets = 0
        val session = CameraSession(channelFactory = { sockets++; error("Must not create a socket") })
        val secret = "Secret123".toCharArray()
        try {
            assertFalse(session.canEditAp(profile.id))
            assertTrue(runCatching { session.configureApHotspot(profile.id, profile.deviceId, "CameraNew", secret, {}) }.isFailure)
            assertEquals(0, sockets)
            assertTrue(secret.all { it == '\u0000' })
        } finally { session.abort() }
    }

    @Test fun authenticatedReadyWithoutVideoCanWriteOnceAndInvalidatesTheOldSession() = runBlocking {
        val peer = Peer()
        lateinit var session: CameraSession
        session = CameraSession(channelFactory = {
            if (peer.writes == 0) {
                assertNotNull(session.connectedProfile) // assigned before login, but never sufficient
                assertFalse(session.isAuthenticated(profile.id))
                assertFalse(session.canEditAp(profile.id))
            }
            TcpChannel(peer.socket())
        }, verifyApNetwork = { target, ssid ->
            assertEquals(profile.deviceId, target.deviceId); assertEquals("HSC_OLD", ssid)
            val proof = { true }
            proof
        })
        try {
            session.connect(profile, "Local123".toCharArray())
            assertTrue(session.state.value is SessionState.Ready)
            assertFalse(session.canEditAp(profile.id))
            session.readApHotspot(profile.id)
            assertTrue(session.canEditAp(profile.id))
            assertFalse(session.canEditAp("other"))
            var markers = 0
            val result = session.configureApHotspot(profile.id, profile.deviceId, "CameraNew", "Secret123".toCharArray(), { markers++ })
            assertEquals(ApWriteResult.ACCEPTED, result)
            assertEquals(1, markers); assertEquals(1, peer.actions)
            assertNull(session.connectedProfile); assertNull(session.apAccess.value)
            assertFalse(session.canEditAp(profile.id))
            assertTrue(runCatching { session.configureApHotspot(profile.id, profile.deviceId, "CameraNew", "Secret123".toCharArray(), {}) }.isFailure)
            assertEquals(1, peer.actions)
        } finally { session.abort() }
    }

    @Test fun abortAfterDurableMarkerBlocksWriteAndResponseLossNeverReplaysAction() = runBlocking {
        for (response in listOf("abort", "close", "eof", "wrong-id", "plaintext", "reject", "uncorrelated-auth")) {
            val peer = Peer(response)
            val session = CameraSession(channelFactory = { TcpChannel(peer.socket()) }, verifyApNetwork = { _, _ -> { true } })
            try {
                session.connect(profile, "Local123".toCharArray()); session.readApHotspot(profile.id)
                var markers = 0
                var closeJob: Job? = null
                val result = runCatching { session.configureApHotspot(profile.id, profile.deviceId, "CameraNew", "Secret123".toCharArray(), {
                    markers++
                    if (response == "abort") session.abort()
                    if (response == "close") closeJob = launch(start = CoroutineStart.UNDISPATCHED) { session.close() }
                }) }
                closeJob?.join()
                assertEquals(1, markers)
                if (response in listOf("abort", "close")) { assertTrue(result.isFailure); assertEquals(0, peer.actions) }
                else {
                    assertEquals(if (response == "reject") ApWriteResult.REJECTED else ApWriteResult.UNKNOWN, result.getOrThrow())
                    assertEquals(1, peer.actions)
                    assertNull(session.connectedProfile)
                }
            } finally { session.abort() }
        }
    }

    @Test fun explicitCapabilityAndLiveNetworkProofAreRequiredEvenAfterSuccessfulLogin() = runBlocking {
        for (mode in listOf("unsupported", "lost-network")) {
            val peer = Peer(mode)
            var network = true
            val session = CameraSession(channelFactory = { TcpChannel(peer.socket()) }, verifyApNetwork = { _, _ -> { network } })
            try {
                session.connect(profile, "Local123".toCharArray())
                if (mode == "unsupported") assertTrue(runCatching { session.readApHotspot(profile.id) }.isFailure)
                else { session.readApHotspot(profile.id); network = false }
                assertFalse(session.canEditAp(profile.id))
                assertTrue(runCatching { session.configureApHotspot(profile.id, profile.deviceId, "CameraNew", "Secret123".toCharArray(), {}) }.isFailure)
                assertEquals(0, peer.actions)
            } finally { session.abort() }
        }
    }

    @Test fun verificationRequiresBothRequestedValuesAndNeverConfusesEncodedNames() {
        val data = JSONObject().put("dplist_result", JSONArray().put(row(ApHotspotProtocol.AP_SSID, "CameraNew"))
            .put(row(ApHotspotProtocol.AP_PASSWORD, "Secret123")))
        assertTrue(ApHotspotProtocol.verifyReadback(data, "CameraNew", "Secret123".toCharArray()))
        assertFalse(ApHotspotProtocol.verifyReadback(data, "CameraNew", "Different123".toCharArray()))
        assertFalse(ApHotspotProtocol.verifyReadback(data, "other", "Secret123".toCharArray()))
        data.getJSONArray("dplist_result").getJSONObject(1).put("code", 1109)
        assertThrows(IoTProtocolException::class.java) { ApHotspotProtocol.verifyReadback(data, "CameraNew", "Secret123".toCharArray()) }
    }

    private class Peer(private val reply: String = "ok") {
        var writes = 0
        var actions = 0
        private var key = ByteArray(32)
        fun socket(): Socket = object : Socket() {
            var incoming: InputStream = ByteArrayInputStream(byteArrayOf())
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun setTcpNoDelay(on: Boolean) = Unit
            override fun setSoTimeout(timeout: Int) = Unit
            override fun getInputStream() = incoming
            override fun getOutputStream(): OutputStream = object : OutputStream() {
                override fun write(b: Int) = error("Expected complete synthetic packet")
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    writes++
                    val packet = bytes.copyOfRange(offset, offset + length)
                    val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
                    val login = header.sessionId == 0
                    val json = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), if (login) IoTKeyExchange.bootstrapKey() else key)
                    val method = json.getInt("method_id")
                    var data = JSONObject()
                    var code = 1000
                    if (method == IoTCommonProtocol.LAN_PASSWORD_REQUEST) {
                        val scalar = ByteArray(32) { (it + 1).toByte() }
                        val public = ByteArray(32).also { X25519.scalarMultBase(scalar, 0, it, 0) }
                        val remote = Base64.getDecoder().decode(json.getJSONObject("params").getJSONObject("verify").getString("cKM"))
                        remote.indices.forEach { remote[it] = (remote[it].toInt() xor header.magic).toByte() }
                        assertTrue(X25519.calculateAgreement(scalar, 0, remote, 0, key, 0))
                        data.put("verify", JSONObject().put("dK", Base64.getEncoder().encodeToString(public))
                            .put("dS", Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(public)))
                            .put("sid", 17).put("perm", 3).put("origin", 0))
                    } else if (method == IoTCommonProtocol.ABILITY_REQUEST) data.put("net", JSONArray(listOf(1, if (reply == "unsupported") 1 else 4))).put("ctrl", JSONArray(listOf(0)))
                    else if (method == CameraSettingsProtocol.GET) data = snapshot("HSC_OLD")
                    else if (method == IoTCommonProtocol.ACTION_REQUEST) {
                        actions++
                        assertEquals(1, json.getJSONObject("params").getJSONObject("in").getInt("mode"))
                        if (reply == "eof") { incoming = ByteArrayInputStream(byteArrayOf()); return }
                        if (reply == "reject") code = 1109
                        if (reply == "uncorrelated-auth") code = 1103
                    } else error("Unexpected synthetic method")
                    val response = JSONObject().put("id", json.getInt("id") + if (actions > 0 && reply in listOf("wrong-id", "uncorrelated-auth")) 1 else 0)
                        .put("chn", 1).put("method_id", method + 1).put("result", JSONObject().put("code", code)).put("data", data)
                    val secure = if (actions > 0 && reply == "plaintext") 0 else 2
                    incoming = ByteArrayInputStream(response(response, if (login) IoTKeyExchange.bootstrapKey() else key, secure))
                }
            }
            override fun close() = Unit
        }
    }
    companion object {
        private fun row(id: Int, value: Any) = JSONObject().put("dpid", id).put("code", 1000).put("value", value)
        private fun snapshot(name: String) = JSONObject().put("dplist_result", JSONArray()
            .put(row(ApHotspotProtocol.NETWORK_MODE, 1)).put(row(ApHotspotProtocol.AP_SSID, name)))
        private fun response(json: JSONObject, key: ByteArray, secure: Int): ByteArray {
            val bytes = json.toString().toByteArray()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
            val body = if (secure == 0) bytes else cipher.doFinal(bytes)
            return ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN).putInt(IoTCommonProtocol.COMMAND_IOT)
                .put(1).put(secure.toByte()).put(17).put(0).putInt(0).putInt(body.size).put(body).array()
        }
    }
}
