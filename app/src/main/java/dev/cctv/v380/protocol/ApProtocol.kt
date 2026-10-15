package dev.cctv.v380.protocol

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Local AP/LAN operations recovered from V380 6.4.90. No cloud account binding is fabricated. */
object ApProtocol {
    /** Validate before persisting pending metadata or opening a credential-bearing transaction. */
    fun validateWifiInput(ssid: String, password: CharArray, legacy: Boolean) {
        require(ssid.toByteArray(Charsets.UTF_8).size in 1..32 && '\u0000' !in ssid && password.none { it == '\u0000' })
        if (legacy) legacyPassword(password).fill(0)
        else require(Charsets.UTF_8.encode(CharBuffer.wrap(password)).let { buffer ->
            try { buffer.remaining() <= 63 } finally { if (buffer.hasArray()) buffer.array().fill(0) }
        })
    }

    private fun legacyPassword(password: CharArray): ByteArray {
        val buffer = try {
            Charset.forName("GBK").newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(password))
        } catch (_: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("Invalid Wi-Fi encoding") }
        try {
            require(buffer.remaining() <= 32)
            return ByteArray(buffer.remaining()).also { buffer.get(it) }
        } finally { if (buffer.hasArray()) buffer.array().fill(0) }
    }

    /** These values must come from the user's authorized provisioning flow. timeout is passed verbatim. */
    fun bindInfo(action: Int, fastPin: String?, pinCode: Int, bindInfo: String, clientCode: Long, timeout: Long): JSONObject {
        require(bindInfo.toByteArray(Charsets.UTF_8).size <= 65536 && (fastPin?.length ?: 0) <= 1024)
        return JSONObject().put("action", action).apply {
            if (!fastPin.isNullOrEmpty()) put("fastpin", fastPin)
        }.put("pincode", pinCode).put("bindinfo", bindInfo).put("clicode", clientCode).put("timeout", timeout)
    }

    /** DeviceNetworkSetting.setNetworkConfig new-platform action arguments. */
    fun wifiConfigParams(ssid: String, wifiPassword: CharArray): JSONObject {
        validateWifiInput(ssid, wifiPassword, legacy = false)
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        val passwordBytes = String(wifiPassword).toByteArray(Charsets.UTF_8)
        try {
            require(ssidBytes.size in 1..32 && passwordBytes.size <= 63 && '\u0000' !in ssid && wifiPassword.none { it == '\u0000' })
            return JSONObject().put("mode", 2).put("ssid", Base64.getEncoder().encodeToString(ssidBytes))
                .put("pwd", Base64.getEncoder().encodeToString(passwordBytes))
        } finally {
            ssidBytes.fill(0)
            passwordBytes.fill(0)
        }
    }

    /** The setting-version >= 2 LAN path uses the authenticated legacy login handle. */
    fun legacyWifiRequest(deviceId: String, handle: Long, ssid: String, wifiPassword: CharArray): ByteArray {
        val id = deviceId.toLongOrNull()
        require(id != null && id in 1..0xffffffffL && handle in 0..0xffffffffL)
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        val password = legacyPassword(wifiPassword)
        try {
            require(ssidBytes.size in 1..32 && password.size <= 32 && '\u0000' !in ssid && wifiPassword.none { it == '\u0000' })
            val packet = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(0, 320).putInt(4, id.toInt()).put(8, 1).putInt(9, handle.toInt()).putInt(13, 1002)
            ssidBytes.copyInto(packet.array(), 17)
            password.copyInto(packet.array(), 81)
            return packet.array()
        } finally {
            ssidBytes.fill(0)
            password.fill(0)
        }
    }

    data class LegacyWifiResponse(val status: Int, val reason: Int) {
        val acknowledged: Boolean get() = status == 1001
    }

    fun parseLegacyWifiResponse(bytes: ByteArray): LegacyWifiResponse {
        if (bytes.size != 32) throw IoTProtocolException(IoTFailure.MalformedPacket)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != 420) throw IoTProtocolException(IoTFailure.MalformedPacket)
        return LegacyWifiResponse(buffer.getInt(4), buffer.getInt(8))
    }

    /** Confirmation from the camera still needs network migration and same-ID rediscovery. */
    suspend fun configureLegacyWifi(
        deviceId: String, handle: Long, ssid: String, wifiPassword: CharArray,
        write: suspend (ByteArray) -> Unit, readExact: suspend (Int) -> ByteArray,
    ): LegacyWifiResponse {
        val request = legacyWifiRequest(deviceId, handle, ssid, wifiPassword)
        try { write(request) } finally { request.fill(0) }
        val response = readExact(32)
        try { return parseLegacyWifiResponse(response) } finally { response.fill(0) }
    }

    /** DAID_NET_CONFIG via the direct common-IoT action transport; the APK uses magic 0. */
    fun wifiActionParams(ssid: String, wifiPassword: CharArray): JSONObject =
        JSONObject().put("daid", 262656L).put("in", wifiConfigParams(ssid, wifiPassword))

    suspend fun configureWifi(
        ssid: String, wifiPassword: CharArray, requestId: Int, sessionId: Int, magic: Int = 0,
        sessionKey: ByteArray,
        write: suspend (ByteArray) -> Unit, readExact: suspend (Int) -> ByteArray,
    ): IoTResponse {
        require(magic == 0)
        return exchange(wifiActionParams(ssid, wifiPassword), IoTCommonProtocol.ACTION_REQUEST,
            IoTCommonProtocol.ACTION_RESPONSE, requestId, write, readExact, sessionId, 0, sessionKey,
            strict = true)
    }

    /** LoginHelper.changeLanPassSync: the old password authenticates the masked new password. */
    fun changeLanPasswordParams(oldPassword: CharArray, newPassword: CharArray, magic: Int): JSONObject {
        require(magic in 0..255 && oldPassword.size <= 1024 && newPassword.size in 1..1024)
        val old = String(oldPassword).toByteArray(Charsets.UTF_8)
        val masked = String(newPassword).toByteArray(Charsets.UTF_8)
        var signed: ByteArray? = null
        var digest: ByteArray? = null
        try {
            for (i in masked.indices) masked[i] = (masked[i].toInt() xor magic).toByte()
            signed = masked + old
            digest = MessageDigest.getInstance("SHA-256").digest(signed)
            return JSONObject().put("act", 2).put("change", JSONObject()
                .put("lprC", Base64.getEncoder().encodeToString(masked))
                .put("lprS", Base64.getEncoder().encodeToString(digest)))
        } finally {
            old.fill(0)
            masked.fill(0)
            signed?.fill(0)
            digest?.fill(0)
        }
    }

    /** A positive acknowledgement only confirms this request; LAN rediscovery is a separate step. */
    suspend fun acknowledgeBinding(
        params: JSONObject, requestId: Int,
        write: suspend (ByteArray) -> Unit, readExact: suspend (Int) -> ByteArray,
    ): IoTResponse = exchange(params, IoTCommonProtocol.BIND_ACK_REQUEST, IoTCommonProtocol.BIND_ACK_RESPONSE,
        requestId, write, readExact)

    /** Requires a session established with the camera and the user's current LAN password. */
    suspend fun changeLanPassword(
        oldPassword: CharArray, newPassword: CharArray, requestId: Int, sessionId: Int,
        magic: Int, sessionKey: ByteArray,
        write: suspend (ByteArray) -> Unit, readExact: suspend (Int) -> ByteArray,
    ): IoTResponse = exchange(changeLanPasswordParams(oldPassword, newPassword, magic),
        IoTCommonProtocol.LAN_PASSWORD_REQUEST, IoTCommonProtocol.LAN_PASSWORD_RESPONSE,
        requestId, write, readExact, sessionId, magic, sessionKey)

    private suspend fun exchange(
        params: JSONObject, method: Int, responseMethod: Int, requestId: Int,
        write: suspend (ByteArray) -> Unit, readExact: suspend (Int) -> ByteArray,
        sessionId: Int = 0, magic: Int = 0, key: ByteArray? = null,
        strict: Boolean = false,
    ): IoTResponse {
        val request = IoTCommonProtocol.encodeRequest(methodId = method, requestId = requestId,
            params = params, secureType = if (key == null) 0 else 2, sessionId = sessionId, magic = magic, key = key)
        try { write(request) } finally { request.fill(0) }
        val header = IoTCommonProtocol.parseHeader(readExact(IoTCommonProtocol.HEADER_SIZE))
        if (header.command != IoTCommonProtocol.COMMAND_IOT)
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        if (strict && (header.secureType != 2 || header.sessionId !in setOf(0, sessionId)))
            throw IoTProtocolException(IoTFailure.MalformedPacket)
        val payload = readExact(header.bodySize)
        try {
            val json = IoTCommonProtocol.decodeJson(header, payload, key)
            if (strict && (IoTCommonProtocol.exactInt(json, "id") != requestId ||
                    IoTCommonProtocol.exactInt(json, "chn") !in setOf(0, 1) ||
                    IoTCommonProtocol.exactInt(json, "method_id") != responseMethod))
                throw IoTProtocolException(IoTFailure.MalformedPacket)
            val response = IoTCommonProtocol.validateResponse(json, requestId, methodId = responseMethod)
            if (key != null && response.code == 1000 && header.secureType != 2)
                throw IoTProtocolException(IoTFailure.MalformedPacket)
            return response
        } finally { payload.fill(0) }
    }

    data class DiscoveryMetadata(
        val configId: Int?, val reportedAlarm: Int?, val alarmState: Int?, val bindState: Int?,
        val configAbility: Int?, val specialDeviceType: Int?,
    )

    /** Fields 13..17 from Functions.ParseDevice. The text prefix still determines the receiver platform. */
    fun discoveryMetadata(fields: List<String>): DiscoveryMetadata {
        val rawAlarm = fields.getOrNull(14)?.toIntOrNull()
        return DiscoveryMetadata(fields.getOrNull(13)?.toIntOrNull(), rawAlarm, when (rawAlarm) {
            1000 -> 2
            1001 -> 1
            1, 2 -> rawAlarm
            null -> null
            else -> 0
        }, fields.getOrNull(15)?.toIntOrNull(), fields.getOrNull(16)?.toIntOrNull(),
            fields.getOrNull(17)?.toIntOrNull())
    }

    data class BinaryDiscoveryRecord(
        val deviceId: String, val host: String, val port: Int, val configId: Int,
        val deviceType: Int, val alarmState: Int, val subDeviceIds: List<String>,
    )

    /** CMD1280 receiver + Functions.parseResult layout, bounded to the actual UDP datagram length. */
    fun parseBinaryDiscovery(bytes: ByteArray, length: Int, sourceIp: String): BinaryDiscoveryRecord? {
        if (length !in 63..4096 || length > bytes.size || !ipv4(sourceIp)) return null
        val buffer = ByteBuffer.wrap(bytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt(0) != 1280) return null
        val ipEnd = (10 until 42).firstOrNull { bytes[it] == 0.toByte() } ?: return null
        if (ipEnd == 10 || (10 until ipEnd).any { bytes[it].toInt() !in 0x20..0x7e }) return null
        val ip = bytes.copyOfRange(10, ipEnd).toString(Charsets.US_ASCII)
        val port = buffer.getInt(42)
        if (ip != sourceIp || !ipv4(ip) || port !in 1..65535) return null
        val deviceId = buffer.getInt(6).toLong() and 0xffffffffL
        if (deviceId == 0L) return null
        val count = bytes[62].toInt() and 255
        if (count > 99 || length < 63 + count * 4) return null
        val subIds = (0 until count).map {
            (buffer.getInt(63 + it * 4).toLong() and 0xffffffffL).takeIf { id -> id != 0L }?.toString() ?: return null
        }
        return BinaryDiscoveryRecord(deviceId.toString(), ip, port, bytes[4].toInt() and 255,
            bytes[5].toInt() and 255, bytes[46].toInt() and 255, subIds)
    }

    private fun ipv4(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 &&
            it.all { c -> c in '0'..'9' } && it.toIntOrNull() in 0..255 }
    }
}
