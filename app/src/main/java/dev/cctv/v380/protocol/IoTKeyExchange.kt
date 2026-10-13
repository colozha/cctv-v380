package dev.cctv.v380.protocol

import dev.cctv.v380.transport.TcpChannel
import dev.cctv.v380.transport.deadlineAfterMillis
import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONObject
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.TimeZone

/** Ephemeral session material. Never serialize this object or include it in logs. */
class IoTSession internal constructor(private val secret: ByteArray, val sessionId: Int, val permissions: Long, val origin: Int) : AutoCloseable {
    fun keyBytes(): ByteArray = synchronized(secret) {
        check(!closed) { "Session is closed" }
        secret.copyOf()
    }
    @Volatile private var closed = false
    override fun close() = synchronized(secret) { secret.fill(0); closed = true }
    override fun toString() = "IoTSession(redacted)"
}

/** One LAN password exchange; owns a fresh X25519 private scalar and is single-use. */
class IoTKeyExchange private constructor(private val privateKey: ByteArray, val requestId: Int, val magic: Int) : AutoCloseable {
    private var used = false

    fun request(lanPassword: CharArray, timestampSeconds: Long = System.currentTimeMillis() / 1000,
                timezone: Int = wireTimezone(TimeZone.getDefault())): ByteArray {
        check(!used) { "Exchange is closed" }
        val pub = ByteArray(32)
        X25519.scalarMultBase(privateKey, 0, pub, 0)
        val masked = ByteArray(32) { (pub[it].toInt() xor magic).toByte() }
        val passwordBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(lanPassword))
        val password = ByteArray(passwordBuffer.remaining()).also { passwordBuffer.get(it) }
        try {
            val hash = MessageDigest.getInstance("SHA-256").apply { update(masked); update(password) }.digest()
            val verify = JSONObject().put("cKM", Base64.getEncoder().encodeToString(masked))
                .put("cS", Base64.getEncoder().encodeToString(hash)).put("tz", timezone).put("ts", timestampSeconds)
            hash.fill(0)
            return IoTCommonProtocol.encodeRequest(methodId = IoTCommonProtocol.LAN_PASSWORD_REQUEST,
                requestId = requestId, params = JSONObject().put("act", 1).put("verify", verify),
                secureType = 2, magic = magic, key = bootstrapKey(), timestampSeconds = timestampSeconds)
        } finally {
            password.fill(0)
            if (passwordBuffer.hasArray()) passwordBuffer.array().fill(0)
            pub.fill(0)
            masked.fill(0)
        }
    }

    fun complete(header: IoTHeader, body: ByteArray): IoTSession {
        check(!used) { "Exchange is closed" }
        try {
            if (header.command != IoTCommonProtocol.COMMAND_IOT) throw IoTProtocolException(IoTFailure.MalformedPacket)
            val json = IoTCommonProtocol.decodeJson(header, body, bootstrapKey())
            val response = IoTCommonProtocol.validateResponse(json, requestId, methodId = IoTCommonProtocol.LAN_PASSWORD_RESPONSE)
            val data = response.requireSuccess() ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
            if (header.secureType != 2) throw IoTProtocolException(IoTFailure.MalformedPacket)
            val verify = data.optJSONObject("verify") ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
            val publicKey = decode32(verify, "dK")
            try {
                val expected = decode32(verify, "dS")
                val actual = MessageDigest.getInstance("SHA-256").digest(publicKey)
                try {
                    if (!MessageDigest.isEqual(actual, expected)) throw IoTProtocolException(IoTFailure.AuthenticationFailed)
                } finally { expected.fill(0); actual.fill(0) }
                val sid = IoTCommonProtocol.exactInt(verify, "sid")
                val permissions = IoTCommonProtocol.exactLong(verify, "perm")
                val origin = IoTCommonProtocol.exactInt(verify, "origin")
                if (sid !in 0..255) throw IoTProtocolException(IoTFailure.MalformedPacket)
                val secret = ByteArray(32)
                try {
                    if (!X25519.calculateAgreement(privateKey, 0, publicKey, 0, secret, 0))
                        throw IoTProtocolException(IoTFailure.AuthenticationFailed)
                    return IoTSession(secret, sid, permissions, origin)
                } catch (failure: Throwable) {
                    secret.fill(0)
                    throw failure
                }
            } finally { publicKey.fill(0) }

        } finally { close() }
    }

    override fun close() { privateKey.fill(0); used = true }
    override fun toString() = "IoTKeyExchange(redacted)"

    companion object {
        fun create(random: SecureRandom = SecureRandom()): IoTKeyExchange =
            IoTKeyExchange(ByteArray(32).also(random::nextBytes), random.nextInt(999999) + 10, random.nextInt(127) + 1)

        /** Deterministic synthetic-vector entry point; callers must supply an independent fresh scalar. */
        internal fun forVector(privateKey: ByteArray, requestId: Int, magic: Int): IoTKeyExchange {
            require(privateKey.size == 32 && magic in 0..255)
            return IoTKeyExchange(privateKey.copyOf(), requestId, magic)
        }

        fun wireTimezone(timezone: TimeZone): Int {
            val hours = timezone.rawOffset / 3_600_000
            return hours * 100 + (timezone.rawOffset - hours * 3_600_000) / 60_000
        }

        private fun decode32(json: JSONObject, name: String): ByteArray {
            val value = json.opt(name) as? String ?: throw IoTProtocolException(IoTFailure.MalformedPacket)
            if (value.length !in 43..44) throw IoTProtocolException(IoTFailure.MalformedPacket)
            val bytes = try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) {
                throw IoTProtocolException(IoTFailure.MalformedPacket)
            }
            if (bytes.size != 32) { bytes.fill(0); throw IoTProtocolException(IoTFailure.MalformedPacket) }
            return bytes
        }

        // Shipped interoperable bootstrap cipher constant, not an account credential or session key.
        internal fun bootstrapKey(): ByteArray = byteArrayOf(54, 58, -27, 40, 22, 56, -49, -124, -106, -102, 99, 78, -97, -106, 106, -104, -75, 115, -47, -1, -23, 55, -78, 119, -47, -15, -42, 43, 56, 116, -118, -27)
    }
}

/** Transport is injected so the caller binds sockets to the selected Wi-Fi network. No cloud fallback. */
object IoTLanProtocol {
    suspend fun authenticate(channel: TcpChannel, lanPassword: CharArray): IoTSession =
        IoTKeyExchange.create().use { exchange ->
            val deadline = deadlineAfterMillis()
            val request = exchange.request(lanPassword)
            try { channel.write(request, deadline) } finally { request.fill(0) }
            val header = IoTCommonProtocol.parseHeader(channel.readExact(IoTCommonProtocol.HEADER_SIZE, deadline))
            val body = channel.readExact(header.bodySize, deadline)
            try { exchange.complete(header, body) } finally { body.fill(0) }
        }
}
