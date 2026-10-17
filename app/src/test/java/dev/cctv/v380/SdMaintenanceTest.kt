package dev.cctv.v380

import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.CameraFailure
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic authenticated state and encrypted streams in memory; no network or vendor execution. */
class SdMaintenanceTest {
    private val profile = CameraProfile(name = "Synthetic SD", host = "192.0.2.10", deviceId = "sd-fixture")

    @Test fun recoveryReadsExactlyThreePropertiesWhileWritesAreBlocked() = runBlocking {
        val peer = Peer()
        val session = authenticated(peer)
        try {
            session.blockMaintenanceWrites(true)
            repeat(2) {
                val settings = session.readSdFormatStatus(profile.id, profile.deviceId)
                assertEquals(1, settings.int(CameraSettingsProtocol.SD_STATUS))
                assertEquals(777, settings.int(CameraSettingsProtocol.SD_FORMAT_STATE))
                assertFalse(CameraSettingsProtocol.formatInProgress(settings))
            }
            assertTrue(session.maintenancePending)
            assertEquals(2, peer.requests.size)
            assertTrue(peer.closed)
            for (request in peer.requests) {
                assertEquals(CameraSettingsProtocol.GET, request.getInt("method_id"))
                val ids = request.getJSONObject("params").getJSONArray("dplist")
                assertEquals(CameraSettingsProtocol.sdFormatProperties, List(ids.length()) { ids.getInt(it) })
            }
            repeat(2) {
                val error = runCatching {
                    session.sendSdFormat(profile.id, profile.deviceId, "same-operation",
                        beforeWrite = { fail("Blocked maintenance must not save a marker") },
                        onWriteStarted = { fail("Blocked maintenance must not write") })
                }.exceptionOrNull()
                assertEquals(SessionError.FeatureUnsupported, (error as CameraFailure).reason)
            }
            assertEquals(2, peer.requests.size)
        } finally { session.dispose() }
    }

    @Test fun recoveryRejectsWrongProfileOrDeviceBeforeOpeningTransport() = runBlocking {
        val peer = Peer()
        val session = authenticated(peer)
        try {
            assertEquals(SessionError.AuthenticationFailed, (runCatching {
                session.readSdFormatStatus("other-profile", profile.deviceId)
            }.exceptionOrNull() as CameraFailure).reason)
            assertEquals(SessionError.IdentityMismatch, (runCatching {
                session.readSdFormatStatus(profile.id, "other-device")
            }.exceptionOrNull() as CameraFailure).reason)
            assertTrue(peer.requests.isEmpty())
        } finally { session.dispose() }
    }

    @Test fun everyResponseIncludingAuthenticationErrorsRequiresStrictCorrelation() = runBlocking {
        for (variant in listOf("id", "method", "channel", "sid", "uncorrelated-auth", "uncorrelated-expired")) {
            val peer = Peer(variant)
            val session = authenticated(peer)
            try {
                val error = runCatching { session.readSdFormatStatus(profile.id, profile.deviceId) }.exceptionOrNull()
                assertEquals(variant, IoTFailure.MalformedPacket, (error as IoTProtocolException).kind)
                assertEquals(1, peer.requests.size)
                assertTrue(peer.closed)
            } finally { session.dispose() }
        }
    }

    @Test fun timeoutAndDenialDoNotRetryOrAuthorizeAWrite() = runBlocking {
        for (variant in listOf("timeout", "denied")) {
            val peer = Peer(variant)
            val session = authenticated(peer)
            try {
                session.blockMaintenanceWrites(true)
                val error = runCatching { session.readSdFormatStatus(profile.id, profile.deviceId) }.exceptionOrNull()
                assertEquals(if (variant == "timeout") SessionError.Timeout else SessionError.CameraPermissionDenied,
                    CameraSession.reason(error as Exception))
                assertEquals(1, peer.requests.size)
                assertTrue(session.maintenancePending)
                assertTrue(peer.closed)
            } finally { session.dispose() }
        }
    }

    @Test fun revokedSessionCannotCommitReadbackAndMissingPropertiesStayUnknown() = runBlocking {
        val peer = Peer("missing")
        val session = authenticated(peer)
        try {
            val settings = session.readSdFormatStatus(profile.id, profile.deviceId)
            assertNull(settings.int(CameraSettingsProtocol.SD_FORMAT_STATE))
            assertEquals(1109, settings.rejected[CameraSettingsProtocol.SD_CAPACITY])
            peer.onRequest = { set(session, "accessRevoked", true) }
            assertEquals(SessionError.AuthenticationFailed, (runCatching {
                session.readSdFormatStatus(profile.id, profile.deviceId)
            }.exceptionOrNull() as CameraFailure).reason)
        } finally { session.dispose() }
    }

    @Test fun actionAckIsTheOnlySuccessContractAndMarkerPrecedesTheSoleWrite() = runBlocking {
        val peer = Peer()
        val session = authenticated(peer)
        var marker = false
        var writeStarted = false
        var markerBeforeAction = false
        peer.onRequest = {
            if (peer.requests.last().getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST)
                markerBeforeAction = marker
        }
        try {
            val ack = session.sendSdFormat(profile.id, profile.deviceId, "op-ack",
                beforeWrite = { marker = true }, onWriteStarted = { writeStarted = true })
            assertEquals(SdFormatAck.ACCEPTED, ack)
            assertTrue(marker); assertTrue(writeStarted); assertTrue(markerBeforeAction)
            assertTrue(session.maintenancePending)
            assertEquals(2, peer.requests.size)
            val action = peer.requests.last()
            assertEquals(IoTCommonProtocol.ACTION_REQUEST, action.getInt("method_id"))
            assertEquals(131584, action.getJSONObject("params").getInt("daid"))
            assertEquals(1, action.getJSONObject("params").length())
        } finally { session.dispose() }
    }

    @Test fun correlatedActionRejectionIsTypedAndNeverRetried() = runBlocking {
        val peer = Peer("action-denied")
        val session = authenticated(peer)
        try {
            val error = runCatching {
                session.sendSdFormat(profile.id, profile.deviceId, "op-denied", beforeWrite = {})
            }.exceptionOrNull()
            val failure = error as IoTProtocolException
            assertEquals(IoTFailure.PermissionDenied, failure.kind)
            assertEquals(1109, failure.deviceCode)
            assertEquals(1, peer.requests.count { it.getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST })
            assertTrue(session.maintenancePending)
        } finally { session.dispose() }
    }

    @Test fun actionTimeoutAfterTheSoleWriteIsAmbiguousAndNeverRetried() = runBlocking {
        val peer = Peer("action-timeout")
        val session = authenticated(peer)
        var marker = false
        try {
            val error = runCatching {
                session.sendSdFormat(profile.id, profile.deviceId, "op-timeout", beforeWrite = { marker = true })
            }.exceptionOrNull()
            assertEquals(SessionError.Timeout, CameraSession.reason(error as Exception))
            assertTrue(marker)
            assertTrue(session.maintenancePending)
            assertEquals(1, peer.requests.count { it.getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST })
        } finally { session.dispose() }
    }

    @Test fun missingFormatStateDoesNotBlockAnEligibleCard() = runBlocking {
        val peer = Peer("missing")
        val session = authenticated(peer)
        try {
            assertEquals(SdFormatAck.ACCEPTED,
                session.sendSdFormat(profile.id, profile.deviceId, "op-missing", beforeWrite = {}))
            assertEquals(1, peer.requests.count { it.getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST })
        } finally { session.dispose() }
    }

    @Test fun missingOrIneligibleCardIsRejectedBeforeMarkerAndAction() = runBlocking {
        for (status in listOf<Int?>(null, 4, 5, 6, 7)) {
            val peer = Peer(sdStatus = status)
            val session = authenticated(peer)
            var marker = false
            try {
                val error = runCatching {
                    session.sendSdFormat(profile.id, profile.deviceId, "op-$status", beforeWrite = { marker = true })
                }.exceptionOrNull()
                assertEquals("status=$status", SessionError.FeatureUnsupported, (error as CameraFailure).reason)
                assertFalse("status=$status", marker)
                assertEquals("status=$status", 1, peer.requests.size)
                assertFalse(peer.requests.any { it.getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST })
            } finally { session.dispose() }
        }
    }

    @Test fun onlySdStatusFourProvesProgressAndTerminalEnumRemainsUnverified() {
        assertTrue(CameraSettingsProtocol.FORMAT_ACTION_VERIFIED)
        assertFalse(CameraSettingsProtocol.FORMAT_TERMINAL_VERIFIED)
        for (raw in listOf(-1, 0, 1, 2, 3, 4, 1000, Int.MAX_VALUE)) {
            for (status in listOf(1, 3, 4, 5, 8)) {
                val settings = CameraSettings(mapOf(CameraSettingsProtocol.SD_STATUS to status,
                    CameraSettingsProtocol.SD_FORMAT_STATE to raw), emptyMap())
                assertEquals(status == 4, CameraSettingsProtocol.formatInProgress(settings))
            }
        }
        assertFalse(CameraSettingsProtocol.formatInProgress(CameraSettings(emptyMap(), emptyMap())))
    }

    private fun authenticated(peer: Peer): CameraSession {
        val session = CameraSession(channelFactory = { TcpChannel(peer.socket()) })
        val owner = IoTSession(peer.key.copyOf(), 17, 3, 0)
        set(session, "profile", profile)
        set(session, "iot", owner)
        set(session, "authenticatedIoT", owner)
        set(session, "accessRevoked", false)
        @Suppress("UNCHECKED_CAST")
        val state = session.javaClass.getDeclaredField("mutableState").apply { isAccessible = true }
            .get(session) as MutableStateFlow<SessionState>
        state.value = SessionState.Ready(CameraCapabilities(emptySet(), emptySet(), false))
        return session
    }

    private fun set(target: Any, field: String, value: Any) = target.javaClass.getDeclaredField(field)
        .apply { isAccessible = true }.set(target, value)

    private class Peer(val variant: String = "ok", private val sdStatus: Int? = 1) {
        val key = ByteArray(32) { (it + 1).toByte() }
        val requests = mutableListOf<JSONObject>()
        var onRequest: () -> Unit = {}
        var closed = false
        fun socket() = object : Socket() {
            private var input: InputStream = ByteArrayInputStream(byteArrayOf())
            override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
            override fun setTcpNoDelay(on: Boolean) = Unit
            override fun setSoTimeout(timeout: Int) = Unit
            override fun close() { closed = true }
            override fun getInputStream(): InputStream {
                if (variant == "timeout") throw SocketTimeoutException("Synthetic status timeout")
                return input
            }
            override fun getOutputStream() = object : OutputStream() {
                override fun write(value: Int) = error("Complete packet expected")
                override fun write(bytes: ByteArray, offset: Int, count: Int) {
                    val packet = bytes.copyOfRange(offset, offset + count)
                    val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
                    val request = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), key)
                    requests.add(request)
                    onRequest()
                    if (request.getInt("method_id") == IoTCommonProtocol.ACTION_REQUEST) {
                        if (variant == "action-timeout") {
                            input = object : InputStream() {
                                override fun read(): Int = throw SocketTimeoutException("Synthetic action timeout")
                                override fun read(buffer: ByteArray, off: Int, len: Int): Int =
                                    throw SocketTimeoutException("Synthetic action timeout")
                            }
                            return
                        }
                        val response = JSONObject().put("id", request.getInt("id")).put("chn", 1)
                            .put("method_id", IoTCommonProtocol.ACTION_RESPONSE)
                            .put("result", JSONObject().put("code", if (variant == "action-denied") 1109 else 1000))
                        input = encrypted(response)
                        return
                    }
                    val rows = JSONArray()
                    sdStatus?.let { rows.put(row(CameraSettingsProtocol.SD_STATUS, it)) }
                    rows.put(row(CameraSettingsProtocol.SD_CAPACITY, "1000|900|0", if (variant == "missing") 1109 else 1000))
                    if (variant != "missing") rows.put(row(CameraSettingsProtocol.SD_FORMAT_STATE, 777))
                    val response = JSONObject().put("id", request.getInt("id") + if (variant == "id") 1 else 0)
                        .put("chn", if (variant == "channel") 9 else 1)
                        .put("method_id", if (variant == "method") CameraSettingsProtocol.SET_RESPONSE else CameraSettingsProtocol.GET_RESPONSE)
                        .put("result", JSONObject().put("code", when (variant) {
                            "denied" -> 1109; "uncorrelated-auth" -> 1103; "uncorrelated-expired" -> 1104; else -> 1000
                        })).put("data", JSONObject().put("dplist_result", rows))
                    if (variant.startsWith("uncorrelated")) response.remove("id")
                    input = encrypted(response)
                }
            }
            private fun encrypted(response: JSONObject): InputStream {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
                }
                val body = cipher.doFinal(response.toString().toByteArray())
                return ByteArrayInputStream(ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(IoTCommonProtocol.COMMAND_IOT).put(1).put(2).put(if (variant == "sid") 18 else 17)
                    .put(0).putInt(0).putInt(body.size).put(body).array())
            }
        }
        private fun row(id: Int, value: Any, code: Int = 1000) =
            JSONObject().put("dpid", id).put("code", code).put("value", value)
    }
}
