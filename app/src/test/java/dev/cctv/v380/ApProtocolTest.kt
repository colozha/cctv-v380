package dev.cctv.v380

import dev.cctv.v380.protocol.ApProtocol
import dev.cctv.v380.protocol.IoTProtocolException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

class ApProtocolTest {
    @Test fun bindingFieldsAndPasswordProofUseTheRecoveredSchema() {
        val bind = ApProtocol.bindInfo(2, "synthetic", 1234, "synthetic-bind", 123L, 60L)
        assertEquals(2, bind.getInt("action"))
        assertEquals("synthetic", bind.getString("fastpin"))
        assertEquals(1234, bind.getInt("pincode"))
        assertEquals("synthetic-bind", bind.getString("bindinfo"))
        assertEquals(123L, bind.getLong("clicode"))
        assertEquals(60L, bind.getLong("timeout"))
        assertFalse(ApProtocol.bindInfo(1, null, 0, "", 123L, 0L).has("fastpin"))
        val old = "synthetic-old".toCharArray()
        val new = "synthetic-new".toCharArray()
        val change = ApProtocol.changeLanPasswordParams(old, new, 0x5a)
        assertEquals(2, change.getInt("act"))
        val masked = Base64.getDecoder().decode(change.getJSONObject("change").getString("lprC"))
        assertArrayEquals(String(new).toByteArray().map { (it.toInt() xor 0x5a).toByte() }.toByteArray(), masked)
        val digest = Base64.getDecoder().decode(change.getJSONObject("change").getString("lprS"))
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(masked + String(old).toByteArray()), digest)
        assertArrayEquals("synthetic-old".toCharArray(), old)
        assertArrayEquals("synthetic-new".toCharArray(), new)
        assertThrows(IllegalArgumentException::class.java) { ApProtocol.changeLanPasswordParams(old, new, 256) }
    }

    @Test fun wifiSerializersKeepTheLegacyAndIotEncodingsDistinct() {
        val packet = ApProtocol.legacyWifiRequest("4294967295", 0x12345678, "synthetic-wifi", "synthetic".toCharArray())
        val view = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(256, packet.size)
        assertEquals(320, view.getInt(0))
        assertEquals(-1, view.getInt(4))
        assertEquals(1, packet[8].toInt())
        assertEquals(0x12345678, view.getInt(9))
        assertEquals(1002, view.getInt(13))
        assertArrayEquals("synthetic-wifi".toByteArray(), packet.copyOfRange(17, 31))
        assertArrayEquals("synthetic".toByteArray(), packet.copyOfRange(81, 90))
        assertTrue(packet.copyOfRange(113, 256).all { it == 0.toByte() })
        val iot = ApProtocol.wifiConfigParams("synthetic-wifi", "synthetic".toCharArray())
        assertEquals(2, iot.getInt("mode"))
        assertArrayEquals("synthetic-wifi".toByteArray(), Base64.getDecoder().decode(iot.getString("ssid")))
        assertArrayEquals("synthetic".toByteArray(), Base64.getDecoder().decode(iot.getString("pwd")))
        assertThrows(IllegalArgumentException::class.java) {
            ApProtocol.legacyWifiRequest("123", 5, "synthetic", CharArray(33) { 'x' })
        }
        val response = ByteArray(32)
        ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 420).putInt(4, 1001)
        assertTrue(ApProtocol.parseLegacyWifiResponse(response).acknowledged)
        ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 1002).putInt(8, 1012)
        val denied = ApProtocol.parseLegacyWifiResponse(response)
        assertFalse(denied.acknowledged)
        assertEquals(1012, denied.reason)
        ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 421)
        assertThrows(IoTProtocolException::class.java) { ApProtocol.parseLegacyWifiResponse(response) }
    }

    @Test fun bindingTransactionRequiresAnActualCorrelatedReply() = runBlocking {
        val params = ApProtocol.bindInfo(1, null, 0, "", 123L, 0L)
        var written = false
        fun reply(id: Int, code: Int): ByteArray {
            val body = "{\"id\":$id,\"chn\":1,\"method_id\":196865,\"result\":{\"code\":$code}}".toByteArray()
            return ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(939722776).put(1).put(0).put(0).put(0).putInt(0).putInt(body.size).put(body).array()
        }
        var response = reply(42, 1000)
        var offset = 0
        val read: suspend (Int) -> ByteArray = { size -> response.copyOfRange(offset, offset + size).also { offset += size } }
        val write: suspend (ByteArray) -> Unit = { request ->
            assertEquals(939722776, ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN).int)
            assertEquals(0, request[5].toInt())
            written = true
        }
        assertEquals(1000, ApProtocol.acknowledgeBinding(params, 42, write, read).code)
        assertTrue(written)
        response = reply(42, 1119); offset = 0
        assertEquals(1119, ApProtocol.acknowledgeBinding(params, 42, write, read).code)
        response = reply(43, 1000); offset = 0
        try {
            ApProtocol.acknowledgeBinding(params, 42, write, read)
            fail("A reply for another request must be rejected")
        } catch (_: IoTProtocolException) { }
    }

    @Test fun encryptedWifiTransactionUsesTheProvedActionAndRejectsPlaintextSuccess() = runBlocking {
        val key = ByteArray(32) { (it + 1).toByte() }
        fun crypt(bytes: ByteArray, mode: Int): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(mode, SecretKeySpec(key.copyOfRange(0, 16), "AES"), IvParameterSpec(key.copyOfRange(16, 32)))
            doFinal(bytes)
        }
        val plain = "{\"id\":77,\"chn\":1,\"method_id\":197385,\"result\":{\"code\":1000}}".toByteArray()
        fun packet(body: ByteArray, secure: Int): ByteArray = ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(939722776).put(1).put(secure.toByte()).put(5).put(0).putInt(0).putInt(body.size).put(body).array()
        var reply = packet(crypt(plain, Cipher.ENCRYPT_MODE), 2)
        var offset = 0
        var writeCount = 0
        val write: suspend (ByteArray) -> Unit = { request ->
            writeCount++
            assertEquals(2, request[5].toInt())
            assertEquals(5, request[6].toInt())
            assertEquals(0, request[7].toInt())
            val json = JSONObject(crypt(request.copyOfRange(16, request.size), Cipher.DECRYPT_MODE).toString(Charsets.UTF_8))
            assertEquals(197384, json.getInt("method_id"))
            assertEquals(1, json.getInt("chn"))
            val params = json.getJSONObject("params")
            assertEquals(262656L, params.getLong("daid"))
            assertFalse(params.has("vsbmp"))
            assertFalse(params.has("no_rsp"))
            assertEquals(2, params.getJSONObject("in").getInt("mode"))
        }
        val read: suspend (Int) -> ByteArray = { size -> reply.copyOfRange(offset, offset + size).also { offset += size } }
        assertEquals(1000, ApProtocol.configureWifi("synthetic-wifi", "synthetic".toCharArray(), 77, 5,
            sessionKey = key, write = write, readExact = read).code)
        assertEquals(1, writeCount)
        for ((id, channel, method, sid) in listOf(
            listOf(78, 1, 197385, 5), listOf(77, 2, 197385, 5),
            listOf(77, 1, 197384, 5), listOf(77, 1, 197385, 6))) {
            val body = JSONObject().put("id", id).put("chn", channel).put("method_id", method)
                .put("result", JSONObject().put("code", 1000)).toString().toByteArray()
            reply = packet(crypt(body, Cipher.ENCRYPT_MODE), 2).also { it[6] = sid.toByte() }; offset = 0
            try {
                ApProtocol.configureWifi("synthetic-wifi", "synthetic".toCharArray(), 77, 5,
                    sessionKey = key, write = write, readExact = read)
                fail("Unrelated Wi-Fi responses cannot acknowledge a write")
            } catch (_: IoTProtocolException) { }
        }
        reply = packet(crypt("{\"id\":77,\"chn\":1,\"method_id\":197385,\"result\":{\"code\":1109}}".toByteArray(), Cipher.ENCRYPT_MODE), 2); offset = 0
        val refusal = ApProtocol.configureWifi("synthetic-wifi", "synthetic".toCharArray(), 77, 5,
            sessionKey = key, write = write, readExact = read)
        try { refusal.requireSuccess(); fail("Authenticated refusal must retain its typed reason") }
        catch (error: IoTProtocolException) {
            assertEquals(dev.cctv.v380.protocol.IoTFailure.PermissionDenied, error.kind)
            assertEquals(1109, error.deviceCode)
        }
        // A plaintext refusal cannot establish that a camera write was rejected either.
        reply = packet("{\"result\":{\"code\":1103}}".toByteArray(), 0); offset = 0
        try {
            ApProtocol.configureWifi("synthetic-wifi", "synthetic".toCharArray(), 77, 5,
                sessionKey = key, write = write, readExact = read)
            fail("Uncorrelated plaintext refusal must leave the outcome uncertain")
        } catch (_: IoTProtocolException) { }
        reply = packet(plain, 0); offset = 0
        try {
            ApProtocol.configureWifi("synthetic-wifi", "synthetic".toCharArray(), 77, 5,
                sessionKey = key, write = write, readExact = read)
            fail("An unauthenticated success must not acknowledge encrypted configuration")
        } catch (_: IoTProtocolException) { }
    }

    @Test fun discoveryMetadataDistinguishesConfigAndAlarmFromPlatform() {
        val fields = MutableList(18) { "" }
        fields[13] = "7"; fields[14] = "1000"; fields[15] = "-1"; fields[16] = "64"; fields[17] = "2"
        val metadata = ApProtocol.discoveryMetadata(fields)
        assertEquals(7, metadata.configId)
        assertEquals(1000, metadata.reportedAlarm)
        assertEquals(2, metadata.alarmState)
        assertEquals(-1, metadata.bindState)
        assertEquals(64, metadata.configAbility)
        assertEquals(2, metadata.specialDeviceType)
        assertNull(ApProtocol.discoveryMetadata(emptyList()).alarmState)
    }

    @Test fun binaryDiscoveryValidatesDatagramBoundariesIdentityAndSource() {
        val bytes = ByteArray(512)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, 1280); bytes[4] = 7; bytes[5] = 2
        buffer.putInt(6, -1)
        "192.168.1.10".toByteArray().copyInto(bytes, 10)
        buffer.putInt(42, 8800); bytes[46] = 1; bytes[62] = 2
        buffer.putInt(63, 12345); buffer.putInt(67, 54321)
        val found = ApProtocol.parseBinaryDiscovery(bytes, 71, "192.168.1.10")!!
        assertEquals("4294967295", found.deviceId)
        assertEquals(listOf("12345", "54321"), found.subDeviceIds)
        assertEquals(7, found.configId)
        assertNull(ApProtocol.parseBinaryDiscovery(bytes, 70, "192.168.1.10"))
        assertNull(ApProtocol.parseBinaryDiscovery(bytes, bytes.size + 1, "192.168.1.10"))
        assertNull(ApProtocol.parseBinaryDiscovery(bytes, 71, "192.168.1.11"))
        bytes[62] = 100
        assertNull(ApProtocol.parseBinaryDiscovery(bytes, bytes.size, "192.168.1.10"))
        bytes[62] = 0
        buffer.putInt(42, 65536)
        assertNull(ApProtocol.parseBinaryDiscovery(bytes, bytes.size, "192.168.1.10"))
    }
}
