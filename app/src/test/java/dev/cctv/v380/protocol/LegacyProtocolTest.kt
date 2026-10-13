package dev.cctv.v380.protocol

import dev.cctv.v380.control.Direction
import dev.cctv.v380.session.VideoCodec
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Every identity, password, handle and media sample here is synthetic. */
class LegacyProtocolTest {
    private fun le(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private fun login(version: Int = 31) = LegacyLogin(version, 0xfedcba98L, 0x87654321L, 2, true, true, 0, 0)
    private fun authResponse(result: Int = 1001, detail: Int = 0): ByteArray = ByteArray(256).also {
        le(it).apply { putInt(0, 1168); putInt(4, result); putInt(8, detail); putInt(13, 0xfedcba98.toInt()); putInt(17, 0x87654321.toInt()) }
        it[12] = 31; it[63] = 2; it[64] = 1; it[67] = 1
    }
    private fun streamResponse(size: Int = 25, result: Int = 0, version: Int = 20) = ByteArray(size).also {
        le(it).apply { putInt(0, 401); putInt(4, result); putShort(8, version.toShort()); putInt(10, 1920); putInt(14, 1080); putInt(18, 500) }
    }
    private fun inner(type: Int = 0, payload: ByteArray = ByteArray(1200) { (it * 7).toByte() }) = ByteArray(16 + payload.size).also {
        le(it).apply { putInt(0, 0xfedcba98.toInt()); putShort(4, type.toShort()); putShort(6, 25); putLong(8, 123456789L) }
        payload.copyInto(it, 16)
    }
    private fun fragments(full: ByteArray, rawType: Int = 0): List<ByteArray> {
        val parts = full.asList().chunked(500)
        return parts.mapIndexed { index, part -> ByteArray(12 + part.size).also {
            it[0] = 0x7f; it[1] = rawType.toByte()
            le(it).apply { putShort(3, parts.size.toShort()); putShort(5, index.toShort()); putShort(7, part.size.toShort()) }
            part.toByteArray().copyInto(it, 12)
        } }
    }

    @Test fun passwordThresholdCountsUtf16AndHashesMiddleHexAsText() {
        for (text in listOf("s".repeat(28), "s".repeat(29), "😀".repeat(14), "😀".repeat(15), "\ud800".repeat(29))) {
            val expected = if (text.length <= 28) text.toByteArray() else {
                val hex = MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
                Base64.getEncoder().encode(hex.substring(8, 24).toByteArray())
            }
            assertArrayEquals(expected, LegacyLanProtocol.transformedPassword(text.toCharArray()))
        }
    }

    @Test fun authSerializerUses520BytesDateNonceAndTwoIndependentPkcs5Layers() {
        val nonce = "SYNTHETIC1234567".toByteArray()
        val text = "synthetic-password"
        val request = LegacyLanProtocol.authRequest("synthetic", text.toCharArray(), 0xffffffffL, nonce,
            LocalDateTime.of(2026, 10, 13, 12, 34, 56))
        assertEquals(520, request.size)
        assertEquals(1167, le(request).getInt(0)); assertEquals(120, le(request).getInt(4))
        assertEquals(31, request[8].toInt()); assertEquals(10, le(request).getInt(9))
        assertEquals(-1, le(request).getInt(13))
        assertEquals("2026-10-13 12:34:56", String(request, 17, 19, Charsets.US_ASCII))
        assertArrayEquals(nonce, request.copyOfRange(81, 97))
        val aes = Cipher.getInstance("AES/ECB/PKCS5Padding")
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(nonce, "AES"))
        // 18 password bytes -> 32 inner bytes -> 48 outer bytes, including both padding blocks.
        val decryptedInner = aes.doFinal(request.copyOfRange(97, 145))
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(LegacyProtocolConstants.loginKey, "AES"))
        assertArrayEquals(text.toByteArray(), aes.doFinal(decryptedInner))
        assertTrue(request.copyOfRange(145, request.size).all { it == 0.toByte() })
        assertThrows(IllegalArgumentException::class.java) { LegacyLanProtocol.authRequest("é".repeat(17), charArrayOf('x'), 1, nonce) }
        assertThrows(IllegalArgumentException::class.java) { LegacyLanProtocol.authRequest("synthetic", charArrayOf('x'), 0, nonce) }
        assertThrows(IllegalArgumentException::class.java) { LegacyLanProtocol.authRequest("synthetic", charArrayOf('x'), 1, ByteArray(16)) }
    }

    @Test fun loginValidatesEverySuccessConditionAndPreservesUnsignedFields() {
        val value = LegacyLanProtocol.parseAuthResponse(authResponse(detail = -100))
        assertEquals(0xfedcba98L, value.handle); assertEquals(0x87654321L, value.session)
        assertEquals(2, value.channelCount); assertTrue(value.ptz); assertTrue(value.audio)
        assertThrows(LegacyAuthenticationException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse(1012)) }
        assertThrows(LegacyAuthenticationException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse(detail = -101)) }
        assertThrows(LegacyAuthenticationException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse(detail = 1)) }
        assertThrows(LegacyUnsupportedProtocolException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse().also { le(it).putInt(0, -100) }) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyLanProtocol.parseAuthResponse(ByteArray(255)) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse().also { it[63] = 0 }) }
        assertThrows(LegacyUnsupportedProtocolException::class.java) { LegacyLanProtocol.parseAuthResponse(authResponse().also { it[12] = 33 }) }
    }

    @Test fun streamAndPtzPacketsUseRecoveredNativeLayouts() {
        for (version in listOf(2, 31, 32)) {
            val request = LegacyLanProtocol.streamStart(1234, login(version), 1, 1, audio = false)
            assertEquals(301, le(request).getInt(0)); assertEquals(1234, le(request).getInt(4))
            assertEquals(1, le(request).getInt(8)); assertEquals(if (version == 32) 21 else 20, le(request).getShort(12).toInt())
            assertEquals(0xfedcba98.toInt(), le(request).getInt(14))
            assertEquals(4096, le(request).getInt(22)); assertEquals(1, le(request).getInt(26))
            assertEquals(257, le(request).getShort(30).toInt()); assertEquals(1, request[32].toInt())
        }
        val start = LegacyLanProtocol.streamContinue()
        assertEquals(303, le(start).getInt(0)); assertEquals(0x3001, le(start).getInt(4))
        assertTrue(start.copyOfRange(8, start.size).all { it == 0.toByte() })
        for ((direction, horizontal, vertical) in listOf(Triple(Direction.LEFT, 1001, 1000), Triple(Direction.RIGHT, 1002, 1000),
            Triple(Direction.UP, 1000, 1003), Triple(Direction.DOWN, 1000, 1004), Triple(null, 1000, 1000))) {
            val packet = LegacyLanProtocol.ptz(1, direction)
            assertEquals(16, packet.size); assertEquals(170, le(packet).getInt(0))
            assertEquals(horizontal, le(packet).getShort(8).toInt()); assertEquals(vertical, le(packet).getShort(10).toInt())
            assertEquals(1, le(packet).getShort(12).toInt()); assertEquals(1, le(packet).getShort(14).toInt())
        }
        val opposing = LegacyLanProtocol.ptz(0, left = true, right = true, up = true, down = true)
        assertArrayEquals(LegacyLanProtocol.ptz(0, null), opposing)
    }

    @Test fun streamResponseRejectsNegativeStatusesImpossibleDimensionsAndUnknownModes() {
        assertEquals(1920, LegacyLanProtocol.parseStreamResponse(streamResponse()).width)
        assertEquals(1080, LegacyLanProtocol.parseStreamResponse(streamResponse(412, result = 1001, version = 21)).height)
        assertThrows(LegacyAuthenticationException::class.java) { LegacyLanProtocol.parseStreamResponse(streamResponse(result = -11)) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyLanProtocol.parseStreamResponse(ByteArray(24)) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyLanProtocol.parseStreamResponse(streamResponse().also { le(it).putInt(10, -1) }) }
        assertThrows(LegacyUnsupportedProtocolException::class.java) { LegacyLanProtocol.parseStreamResponse(streamResponse(version = 22)) }
    }

    @Test fun arbitraryTcpSplitsAndCoalescingPreserveNativeFrameMetadata() {
        val payload = ByteArray(1200) { (it * 7).toByte() }
        val wire = (fragments(inner(payload = payload)) + fragments(inner(41, byteArrayOf(0, 0, 0, 1, 2)), 0x29)).fold(byteArrayOf()) { a, b -> a + b }
        for (chunkSize in 1..80) {
            val parser = LegacyV2FrameParser(channel = 1)
            val frames = wire.asList().chunked(chunkSize).flatMap { parser.feed(it.toByteArray()) }
            assertEquals(2, frames.size); assertEquals(VideoCodec.AVC, frames[0].codec)
            assertEquals(0xfedcba98L, frames[0].frameId); assertEquals(123456789L, frames[0].timestampTicks)
            assertTrue(frames[0].keyframe); assertEquals(1, frames[0].channel); assertEquals(25, frames[0].frameRate)
            assertArrayEquals(payload, frames[0].payload)
            assertEquals(VideoCodec.HEVC, frames[1].codec); assertFalse(frames[1].keyframe)
        }
    }

    @Test fun initialResponsePaddingIsBoundedAndOnlyAllowedBeforeFirstFrame() {
        val wire = fragments(inner(payload = byteArrayOf(1, 2, 3))).single()
        for (responseSize in listOf(25, 412)) {
            val parser = LegacyV2FrameParser(allowInitialResponsePadding = true)
            val remainingResponse = streamResponse(responseSize).copyOfRange(25, responseSize)
            assertTrue(parser.feed(remainingResponse + wire.copyOfRange(0, 7)).isEmpty())
            assertEquals(1, parser.feed(wire.copyOfRange(7, wire.size)).size)
            assertThrows(LegacyMalformedPacketException::class.java) { parser.feed(ByteArray(12)) }
            assertThrows(LegacyMalformedPacketException::class.java) { parser.feed(ByteArray(12) + wire) }
        }
        assertThrows(LegacyMalformedPacketException::class.java) {
            LegacyV2FrameParser(allowInitialResponsePadding = true).feed(ByteArray(388) + wire)
        }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyV2FrameParser().feed(ByteArray(12) + wire) }
    }

    @Test fun reassemblyRejectsMissingDuplicateOversizedAndInconsistentFragments() {
        val parts = fragments(inner())
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyV2FrameParser().feed(parts[1]) }
        val missing = LegacyV2FrameParser().also { it.feed(parts[0]) }
        assertThrows(LegacyMalformedPacketException::class.java) { missing.feed(parts[2]) }
        val duplicate = LegacyV2FrameParser().also { it.feed(parts[0]); it.feed(parts[1]) }
        assertThrows(LegacyMalformedPacketException::class.java) { duplicate.feed(parts[1]) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyV2FrameParser().feed(parts[0].copyOf().also { le(it).putShort(7, 501) }) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyV2FrameParser().feed(parts[0].copyOf().also { le(it).putShort(3, -1) }) }
        assertThrows(LegacyMalformedPacketException::class.java) { LegacyV2FrameParser(maxFrameBytes = 1000).feed(parts[0]) }
        val inconsistent = LegacyV2FrameParser().also { it.feed(parts[0]) }
        assertThrows(LegacyMalformedPacketException::class.java) { inconsistent.feed(parts[1].copyOf().also { it[1] = 1 }) }
        val partial = LegacyV2FrameParser()
        assertTrue(partial.feed(parts[0].copyOfRange(0, parts[0].size - 1)).isEmpty())
        partial.reset()
        assertEquals(1, partial.feed(fragments(inner(payload = byteArrayOf(4))).single()).size)
    }

    @Test fun innerTypesDetermineCodecKeyframeAndAudioWithoutPayloadGuessing() {
        for ((type, codec, key) in listOf(Triple(0, VideoCodec.AVC, true), Triple(1, VideoCodec.AVC, false),
            Triple(40, VideoCodec.HEVC, true), Triple(41, VideoCodec.HEVC, false), Triple(50, VideoCodec.AVC, true),
            Triple(51, VideoCodec.AVC, false), Triple(52, VideoCodec.HEVC, true), Triple(53, VideoCodec.HEVC, false))) {
            val frame = LegacyV2FrameParser().feed(fragments(inner(type, byteArrayOf(9)), 91).single()).single()
            assertEquals(codec, frame.codec); assertEquals(key, frame.keyframe); assertFalse(frame.audio)
        }
        for (type in listOf(21, 22, 24, 25, 26)) {
            val frame = LegacyV2FrameParser().feed(fragments(inner(type, byteArrayOf(9))).single()).single()
            assertNull(frame.codec); assertTrue(frame.audio)
        }
        assertNull(LegacyV2FrameParser().feed(fragments(inner(91, byteArrayOf(9))).single()).single().codec)
    }

    @Test fun mediaCipherSelectsNativeModesAndRejectsUseAfterClose() {
        val plaintext = ByteArray(2223) { (it * 19 + 3).toByte() }
        val key = ByteArray(16).also { le(it).putInt(0, login().handle.toInt()); LegacyProtocolConstants.mediaKeyTail.copyInto(it, 4) }
        for (version in listOf(20, 21)) for (audio in listOf(false, true)) {
            val encrypted = plaintext.copyOf()
            val aes = Cipher.getInstance("AES/ECB/NoPadding").also { it.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
            val offsets = when {
                audio -> 0 until plaintext.size / 16 * 16 step 16
                version == 21 -> 0 until 2048 step 16
                else -> (0 until plaintext.size step 80).filter { it + 64 <= plaintext.size }.flatMap { listOf(it, it + 16, it + 32, it + 48) }
            }
            for (offset in offsets) aes.doFinal(plaintext, offset, 16).copyInto(encrypted, offset)
            val frame = LegacyMediaFrame(0, 1, if (audio) 26 else 0, 25, 0, 0, VideoCodec.AVC, true, audio, encrypted)
            val cipher = LegacyMediaCipher(31, version, login().handle)
            assertArrayEquals(plaintext, cipher.decrypt(frame))
            cipher.close()
            assertThrows(IllegalStateException::class.java) { cipher.decrypt(frame) }
            LegacyMediaCipher(30, version, login().handle).use { assertArrayEquals(encrypted, it.decrypt(frame)) }
        }
    }
}
