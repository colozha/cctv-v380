package dev.cctv.v380.discovery

import dev.cctv.v380.session.isIpv4
import dev.cctv.v380.protocol.ApProtocol

data class DiscoveredCamera(
    val deviceId: String, val host: String, val port: Int, val mac: String,
    val generation: Int, val configId: Int?, val bindState: Int?, val configAbility: Int?,
    val platformType: Int? = null, val alarmState: Int? = null, val specialDeviceType: Int? = null,
)

object DiscoveryParser {
    fun parse(bytes: ByteArray, length: Int, sourceIp: String): DiscoveredCamera? {
        if (length !in 1..4096 || length > bytes.size || !isIpv4(sourceIp)) return null
        ApProtocol.parseBinaryDiscovery(bytes, length, sourceIp)?.let { binary ->
            return DiscoveredCamera(binary.deviceId, binary.host, binary.port, "", 1280,
                binary.configId, null, null, platformType = 1, alarmState = binary.alarmState, specialDeviceType = binary.deviceType)
        }
        val end = (0 until length).firstOrNull { bytes[it] == 0.toByte() } ?: length
        val parts = bytes.copyOfRange(0, end).toString(Charsets.US_ASCII).split('^')
        if (parts.size < 13 || parts[0] != "NVDEVRESULT") return null
        val generation = parts[1].toIntOrNull()?.takeIf { it in setOf(100, 200, 300) } ?: return null
        val deviceId = parts[12].takeIf { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) } ?: return null
        if (!isIpv4(parts[3]) || parts[3] != sourceIp) return null
        val port = if (parts[9].isEmpty() || parts[9] == "0") (if (generation == 300) 8089 else 8800) else
            parts[9].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val mac = parts[2].takeIf { it.matches(Regex("[0-9a-fA-F]{2}([:-][0-9a-fA-F]{2}){5}")) } ?: return null
        val metadata = ApProtocol.discoveryMetadata(parts)
        return DiscoveredCamera(deviceId, parts[3], port, mac.uppercase(), generation,
            metadata.configId, metadata.bindState, metadata.configAbility,
            platformType = if (generation == 300) 2 else 1,
            alarmState = metadata.alarmState, specialDeviceType = metadata.specialDeviceType)
    }
}
