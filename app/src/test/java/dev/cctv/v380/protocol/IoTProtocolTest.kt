package dev.cctv.v380.protocol

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import java.util.TimeZone

class IoTProtocolTest {
    @Test fun streamMaySkipBoundedUnknownPacketsWhileTransactionsRemainStrict() {
        fun header(command: Int, size: Int) = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command).put(1).put(0).put(17).put(0).putInt(0).putInt(size).array()
        val unknown = header(123456, 0)
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.parseHeader(unknown) }
        assertEquals(0, IoTCommonProtocol.parseHeader(unknown, allowUnknownCommand = true).bodySize)
        assertThrows(IoTProtocolException::class.java) {
            IoTCommonProtocol.parseHeader(header(123456, IoTCommonProtocol.MAX_JSON_SIZE + 1), allowUnknownCommand = true)
        }
        assertThrows(IoTProtocolException::class.java) {
            IoTCommonProtocol.parseHeader(header(IoTCommonProtocol.COMMAND_IOT, 0), allowUnknownCommand = true)
        }
        assertEquals(0, IoTCommonProtocol.parseHeader(header(IoTCommonProtocol.COMMAND_SHUTDOWN, 0)).bodySize)
    }

    @Test fun decodeFeedbackEchoesOnlyTheNumericChallengeAndUsesTheVendorDefaultDeficit() {
        val challenge = JSONObject().put("params", JSONObject().put("tms1", 1700000000123L))
        val params = IoTControlProtocol.decodeFeedbackParams(challenge)!!
        assertEquals(2, params.getInt("flag"))
        assertEquals(1700000000123L, params.getLong("tms1"))
        assertEquals(0, params.getInt("decode_tms"))
        assertEquals(3, params.length())
        assertNull(IoTControlProtocol.decodeFeedbackParams(JSONObject()))
        assertNull(IoTControlProtocol.decodeFeedbackParams(JSONObject().put("params", JSONObject().put("tms1", "not-a-number"))))
        assertEquals(1e18, IoTControlProtocol.decodeFeedbackParams(JSONObject().put("params", JSONObject().put("tms1", 1e18)))!!
            .getDouble("tms1"), 0.0) // Its unit is opaque; do not impose an invented timestamp range.
        val key = ByteArray(32) { it.toByte() }
        val packet = IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.DECODE_FEEDBACK, requestId = 19,
            params = params, secureType = 2, sessionId = 17, key = key, timestampSeconds = 1700000000)
        val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
        assertEquals(IoTCommonProtocol.COMMAND_IOT, header.command)
        val json = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), key)
        assertEquals(197120, json.getInt("method_id"))
        assertEquals(1700000000123L, json.getJSONObject("params").getLong("tms1"))
    }
    private val alicePrivate = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
    private val alicePublic = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
    private val bobPublic = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
    private val shared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")

    @Test fun lanHandshakeUsesRfc7748AgreementAndAuthenticatesDigest() {
        val exchange = IoTKeyExchange.forVector(alicePrivate, 123, 7)
        val packet = exchange.request("synthetic-only".toCharArray(), 1700000000, 700)
        val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
        assertEquals(IoTCommonProtocol.COMMAND_IOT, header.command)
        assertEquals(2, header.secureType)
        assertEquals(7, header.magic)
        val request = IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size), IoTKeyExchange.bootstrapKey())
        val verify = request.getJSONObject("params").getJSONObject("verify")
        val masked = Base64.getDecoder().decode(verify.getString("cKM"))
        assertArrayEquals(ByteArray(32) { (alicePublic[it].toInt() xor 7).toByte() }, masked)
        val hash = MessageDigest.getInstance("SHA-256").apply { update(masked); update("synthetic-only".toByteArray()) }.digest()
        assertArrayEquals(hash, Base64.getDecoder().decode(verify.getString("cS")))
        assertEquals(1700000000L, verify.getLong("ts"))
        assertEquals(700, verify.getInt("tz"))
        val response = responsePacket(bobPublic)
        val session = exchange.complete(IoTCommonProtocol.parseHeader(response.copyOfRange(0, 16)), response.copyOfRange(16, response.size))
        assertArrayEquals(shared, session.keyBytes())
        assertEquals(17, session.sessionId)
        session.close()
        assertThrows(IllegalStateException::class.java) { session.keyBytes() }
        assertFalse(session.toString().contains("4a5d"))
    }

    @Test fun rejectsCorruptDigestLowOrderPointAndRepeatedExchange() {
        for (corrupt in listOf(true, false)) {
            val publicKey = if (corrupt) bobPublic else ByteArray(32)
            val packet = responsePacket(publicKey, corruptDigest = corrupt)
            val exchange = IoTKeyExchange.forVector(alicePrivate, 123, 7)
            val failure = assertThrows(IoTProtocolException::class.java) {
                exchange.complete(IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16)), packet.copyOfRange(16, packet.size))
            }
            assertEquals(IoTFailure.AuthenticationFailed, failure.kind)
            assertThrows(IllegalStateException::class.java) { exchange.request(charArrayOf()) }
        }
    }

    @Test fun rejectsUncorrelatedOversizedAndTruncatedPackets() {
        val packet = responsePacket(bobPublic)
        val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size - 1), IoTKeyExchange.bootstrapKey()) }
        val oversized = packet.copyOfRange(0, 16)
        ByteBuffer.wrap(oversized).order(ByteOrder.LITTLE_ENDIAN).putInt(12, IoTCommonProtocol.MAX_JSON_SIZE + 1)
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.parseHeader(oversized) }
        val json = JSONObject().put("id", 124).put("chn", 1).put("method_id", IoTCommonProtocol.LAN_PASSWORD_RESPONSE)
            .put("result", JSONObject().put("code", 1000))
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.validateResponse(json, 123, methodId = IoTCommonProtocol.LAN_PASSWORD_RESPONSE) }
        json.put("id", 123).put("chn", 0)
        assertEquals(1000, IoTCommonProtocol.validateResponse(json, 123, methodId = IoTCommonProtocol.LAN_PASSWORD_RESPONSE).code)
        json.put("id", "123")
        assertThrows(IoTProtocolException::class.java) { IoTCommonProtocol.validateResponse(json, 123, methodId = IoTCommonProtocol.LAN_PASSWORD_RESPONSE) }
    }

    @Test fun returnsTypedDeviceFailuresAndApHeader() {
        for ((code, kind) in listOf(1119 to IoTFailure.AuthenticationFailed, 1104 to IoTFailure.SessionExpired, 1109 to IoTFailure.PermissionDenied)) {
            val failure = assertThrows(IoTProtocolException::class.java) { IoTResponse(code, null).requireSuccess() }
            assertEquals(kind, failure.kind)
            assertEquals(code, failure.deviceCode)
        }
        val packet = IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.BIND_ACK_REQUEST,
            requestId = 10, params = JSONObject().put("action", 1), timestampSeconds = 1700000000)
        val header = IoTCommonProtocol.parseHeader(packet.copyOfRange(0, 16))
        assertEquals(0, header.secureType)
        assertEquals(0, header.sessionId)
        assertEquals("0100000000000000", packet.copyOfRange(4, 12).joinToString("") { "%02x".format(it) })
        assertEquals(196864, IoTCommonProtocol.decodeJson(header, packet.copyOfRange(16, packet.size)).getInt("method_id"))
        assertEquals(530, IoTKeyExchange.wireTimezone(TimeZone.getTimeZone("GMT+05:30")))
        assertEquals(-330, IoTKeyExchange.wireTimezone(TimeZone.getTimeZone("GMT-03:30")))
    }

    private fun responsePacket(publicKey: ByteArray, corruptDigest: Boolean = false): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)
        if (corruptDigest) digest[0] = (digest[0].toInt() xor 1).toByte()
        val verify = JSONObject().put("dK", Base64.getEncoder().encodeToString(publicKey))
            .put("dS", Base64.getEncoder().encodeToString(digest)).put("sid", 17).put("perm", 3).put("origin", 0)
        val json = JSONObject().put("id", 123).put("method_id", IoTCommonProtocol.LAN_PASSWORD_RESPONSE)
            .put("chn", 1).put("result", JSONObject().put("code", 1000)).put("data", JSONObject().put("verify", verify))
        // Independent JCA encoding of a synthetic device reply; no vendor binary is executed.
        val key = IoTKeyExchange.bootstrapKey()
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key.copyOfRange(0,16), "AES"),
            javax.crypto.spec.IvParameterSpec(key.copyOfRange(16,32)))
        val body = cipher.doFinal(json.toString().toByteArray())
        return ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN).putInt(IoTCommonProtocol.COMMAND_IOT)
            .put(1).put(2).put(17).put(7).putInt(0).putInt(body.size).put(body).array()
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
