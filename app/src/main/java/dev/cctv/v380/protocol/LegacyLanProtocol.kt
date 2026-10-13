package dev.cctv.v380.protocol

import android.annotation.SuppressLint
import dev.cctv.v380.control.Direction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class LegacyMalformedPacketException : IllegalArgumentException("Malformed legacy packet")
class LegacyUnsupportedProtocolException : IllegalArgumentException("Unsupported legacy protocol")
class LegacyAuthenticationException(val result: Int, val detail: Int = 0) :
    IllegalArgumentException("Legacy authentication rejected")

/** Deliberately has no generated toString: handles and sessions are authentication material. */
class LegacyLogin(
    val protocolVersion: Int,
    val handle: Long,
    val session: Long,
    val channelCount: Int,
    val ptz: Boolean,
    val audio: Boolean,
    val deviceType: Int,
    val cameraType: Int,
)

data class LegacyStreamInfo(
    val result: Int,
    val communicationVersion: Int,
    val width: Int,
    val height: Int,
    val field18: Long,
    val audioFrequency: Int,
    val audioBits: Int,
    val audioChannels: Int,
)

/** APK 6.4.90 LAN binary login plus its V2 stream/control format, with no fallback probing. */
object LegacyLanProtocol {
    const val AUTH_RESPONSE_SIZE = 256
    const val STREAM_RESPONSE_MIN_SIZE = 25
    const val STREAM_RESPONSE_MAX_SIZE = 412
    private val localDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
    private const val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun newNonce(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(16) { alphabet[random.nextInt(alphabet.length)].code.toByte() }

    /** Java String.length is measured in UTF-16 units, including both halves of surrogate pairs. */
    internal fun transformedPassword(password: CharArray): ByteArray {
        require(password.size <= 4096 && password.none { it == '\u0000' })
        val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE).encode(CharBuffer.wrap(password))
        val encoded = ByteArray(buffer.remaining()).also { buffer.get(it) }
        if (buffer.hasArray()) buffer.array().fill(0)
        if (password.size <= 28) return encoded
        return try {
            val digest = MessageDigest.getInstance("MD5").digest(encoded)
            val hex = "0123456789abcdef"
            val middle = ByteArray(16) { index ->
                val value = digest[4 + index / 2].toInt() and 255
                hex[if (index % 2 == 0) value ushr 4 else value and 15].code.toByte()
            }
            try { Base64.getEncoder().encode(middle) } finally { digest.fill(0); middle.fill(0) }
        } finally { encoded.fill(0) }
    }

    @SuppressLint("GetInstance") // Observed vendor wire format; never used for local secret storage.
    fun authRequest(
        username: String,
        password: CharArray,
        deviceId: Long,
        nonce: ByteArray = newNonce(),
        date: LocalDateTime = LocalDateTime.now(),
    ): ByteArray {
        require(deviceId in 1..0xffffffffL)
        val user = username.toByteArray(Charsets.UTF_8)
        require(user.size in 1..32 && username.none { it == '\u0000' })
        require(nonce.size == 16 && nonce.all { it.toInt().toChar() in alphabet })
        require(date.year in 1..9999)
        val dateBytes = date.format(localDateFormat).toByteArray(Charsets.US_ASCII)
        val plain = transformedPassword(password)
        val key = LegacyProtocolConstants.loginKey
        var inner: ByteArray? = null
        var outer: ByteArray? = null
        try {
            val aes = Cipher.getInstance("AES/ECB/PKCS5Padding")
            aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            inner = aes.doFinal(plain)
            aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(nonce, "AES"))
            outer = aes.doFinal(inner)
            require(outer.size <= 520 - 97)
            return ByteArray(520).also { packet ->
                val out = little(packet)
                out.putInt(0, 1167); out.putInt(4, 120)
                packet[8] = 31; out.putInt(9, 10); out.putInt(13, deviceId.toInt())
                dateBytes.copyInto(packet, 17); user.copyInto(packet, 49)
                nonce.copyInto(packet, 81); outer.copyInto(packet, 97)
            }
        } finally {
            plain.fill(0); key.fill(0); inner?.fill(0); outer?.fill(0); user.fill(0)
        }
    }

    fun parseAuthResponse(packet: ByteArray): LegacyLogin {
        if (packet.size != AUTH_RESPONSE_SIZE) throw LegacyMalformedPacketException()
        val input = little(packet)
        if (input.getInt(0) != 1168) throw LegacyUnsupportedProtocolException()
        val result = input.getInt(4)
        val detail = input.getInt(8) % 100 // Preserve Java signed remainder semantics.
        if (result != 1001 || detail != 0) throw LegacyAuthenticationException(result, detail)
        val version = packet[12].toInt() and 255
        if (version !in 2..32) throw LegacyUnsupportedProtocolException()
        val channels = packet[63].toInt() and 255
        if (channels !in 1..64) throw LegacyMalformedPacketException()
        return LegacyLogin(version, unsigned(input.getInt(13)), unsigned(input.getInt(17)), channels,
            packet[67] == 1.toByte(), packet[64] == 1.toByte(), packet[21].toInt() and 255,
            packet[22].toInt() and 255)
    }

    fun streamStart(deviceId: Long, login: LegacyLogin, channel: Int, streamType: Int, audio: Boolean = false): ByteArray {
        require(deviceId in 1..0xffffffffL && channel in 0 until login.channelCount && streamType in 0..1)
        return ByteArray(256).also { packet ->
            little(packet).apply {
                putInt(0, 301); putInt(4, deviceId.toInt()); putInt(8, channel)
                putShort(12, (if (login.protocolVersion == 32) 21 else 20).toShort())
                putInt(14, login.handle.toInt()); putInt(22, if (audio) 4097 else 4096)
                putInt(26, streamType); putShort(30, 0x0101)
            }
            packet[32] = 1
        }
    }

    /** Call with exactly the first 25 response bytes; reserved remainder belongs to initial parser padding. */
    fun parseStreamResponse(packet: ByteArray): LegacyStreamInfo {
        if (packet.size !in STREAM_RESPONSE_MIN_SIZE..STREAM_RESPONSE_MAX_SIZE) throw LegacyMalformedPacketException()
        val input = little(packet)
        if (input.getInt(0) != 401) throw LegacyUnsupportedProtocolException()
        val result = input.getInt(4)
        if (result < 0) throw LegacyAuthenticationException(result)
        val version = input.getShort(8).toInt() and 65535
        if (version !in 20..21) throw LegacyUnsupportedProtocolException()
        val width = input.getInt(10)
        val height = input.getInt(14)
        if (width !in 16..8192 || height !in 16..8192 || width.toLong() * height > 33_554_432L)
            throw LegacyMalformedPacketException()
        return LegacyStreamInfo(result, version, width, height, unsigned(input.getInt(18)),
            packet[22].toInt() and 255, packet[23].toInt() and 255, packet[24].toInt() and 255)
    }

    fun streamContinue(): ByteArray = ByteArray(256).also {
        little(it).putInt(0, 303).putInt(4, 0x3001)
    }

    fun ptz(channel: Int, left: Boolean = false, right: Boolean = false, up: Boolean = false,
            down: Boolean = false, parameter: Int = 1): ByteArray {
        require(channel in 0..65535 && parameter in 0..65535)
        return ByteArray(16).also {
            little(it).apply {
                putInt(0, 170); putShort(4, 1000); putShort(6, 1000)
                putShort(8, (when { left == right -> 1000; left -> 1001; else -> 1002 }).toShort())
                putShort(10, (when { up == down -> 1000; up -> 1003; else -> 1004 }).toShort())
                putShort(12, channel.toShort()); putShort(14, parameter.toShort())
            }
        }
    }

    fun ptz(channel: Int, direction: Direction?): ByteArray = ptz(channel,
        left = direction == Direction.LEFT, right = direction == Direction.RIGHT,
        up = direction == Direction.UP, down = direction == Direction.DOWN)

    internal fun little(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    internal fun unsigned(value: Int): Long = value.toLong() and 0xffffffffL
}
