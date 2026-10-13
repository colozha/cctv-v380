package dev.cctv.v380

import dev.cctv.v380.control.Direction
import dev.cctv.v380.control.PtzWatchdog
import dev.cctv.v380.discovery.DiscoveryParser
import dev.cctv.v380.media.AnnexB
import dev.cctv.v380.media.SparseAesCipher
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.ExactReader
import dev.cctv.v380.transport.TcpChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CoreTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun watchdogStopTransportFailureDoesNotEscapeItsBackgroundJob() = runTest {
        val commands = mutableListOf<Direction?>()
        var failed = false
        val watchdog = PtzWatchdog(this, { direction ->
            commands += direction
            if (direction == null && !failed) { failed = true; throw java.io.IOException("synthetic stop failure") }
        }, { testScheduler.currentTime })
        watchdog.move(Direction.LEFT)
        advanceTimeBy(1100); runCurrent()
        assertEquals(listOf(Direction.LEFT, null), commands)
        watchdog.move(Direction.RIGHT)
        watchdog.stop()
        assertEquals(listOf(Direction.LEFT, null, Direction.RIGHT, null), commands)
    }
    private fun discovery(vararg replacements: Pair<Int, String>): ByteArray {
        val fields = MutableList(18) { "" }
        mapOf(0 to "NVDEVRESULT", 1 to "100", 2 to "00:11:22:33:44:55", 3 to "192.168.1.10", 9 to "8800", 12 to "12345678").forEach { (i, v) -> fields[i] = v }
        replacements.forEach { (i, value) -> fields[i] = value }
        return fields.joinToString("^").toByteArray(Charsets.US_ASCII)
    }

    @Test fun discoveryChecksLengthSourceIdentityAndPort() {
        val data = discovery()
        val padded = data + "^ignored".toByteArray()
        assertEquals("12345678", DiscoveryParser.parse(padded, data.size, "192.168.1.10")?.deviceId)
        assertNull(DiscoveryParser.parse(data, 10, "192.168.1.10"))
        assertNull(DiscoveryParser.parse(data, data.size, "192.168.1.11"))
        for (port in listOf("-1", "65536", "bad")) {
            val bad = discovery(9 to port)
            assertNull(DiscoveryParser.parse(bad, bad.size, "192.168.1.10"))
        }
        for (port in listOf("", "0")) {
            val missing = discovery(9 to port)
            assertEquals(8800, DiscoveryParser.parse(missing, missing.size, "192.168.1.10")?.port)
        }
        val modern = discovery(1 to "300", 12 to "4294967295")
        assertEquals(300, DiscoveryParser.parse(modern, modern.size, "192.168.1.10")?.generation)
        val binary = byteArrayOf(0, 5, 0, 0)
        assertNull(DiscoveryParser.parse(binary, binary.size, "192.168.1.10"))
    }

    @Test fun exactReaderHandlesPartialCoalescedAndZeroReads() {
        val source = ByteArray(8) { it.toByte() }
        val chunks = object : ByteArrayInputStream(source) {
            var zero = true
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (zero) { zero = false; return 0 }
                return super.read(b, off, minOf(len, 2))
            }
        }
        val reader = ExactReader(chunks, 8)
        assertArrayEquals(source.copyOfRange(0, 3), reader.read(3))
        assertArrayEquals(source.copyOfRange(3, 8), reader.read(5))
        assertThrows(EOFException::class.java) { reader.read(1) }
        assertThrows(IllegalArgumentException::class.java) { reader.read(9) }
    }

    @Test fun sparseCipherMatchesIndependentBlockEncryptionAndPreservesTails() {
        val key = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val plaintext = ByteArray(2223).also { java.security.SecureRandom().nextBytes(it) }
        for (mode in SparseAesCipher.Mode.entries) {
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            val encrypted = plaintext.copyOf()
            val ranges = when (mode) {
                SparseAesCipher.Mode.SPARSE_64_80 -> (0 until plaintext.size step 80).filter { it + 64 <= plaintext.size }.map { it until it + 64 }
                SparseAesCipher.Mode.ALIGNED -> listOf(0 until plaintext.size / 16 * 16)
                SparseAesCipher.Mode.FIRST_2048 -> listOf(0 until 2048)
            }
            for (range in ranges) for (offset in range step 16) cipher.doFinal(plaintext, offset, 16).copyInto(encrypted, offset)
            assertArrayEquals(plaintext, SparseAesCipher.decrypt(encrypted, key, mode))
        }
        key.fill(0)
        assertThrows(IllegalArgumentException::class.java) { SparseAesCipher.decrypt(plaintext, byteArrayOf(), SparseAesCipher.Mode.ALIGNED) }
    }

    @Test fun annexBSupportsBothPrefixesAndBothCodecs() {
        val bytes = byteArrayOf(0, 0, 0, 1, 0x67, 3, 0, 0, 1, 0x68, 4, 0, 0, 0, 1, 0x65, 5)
        assertEquals(listOf(7, 8, 5), AnnexB.units(bytes).map { AnnexB.type(it, VideoCodec.AVC) })
        assertTrue(AnnexB.isKeyframe(bytes, VideoCodec.AVC))
        assertTrue(AnnexB.isKeyframe(byteArrayOf(0, 0, 1, 38, 1, 4), VideoCodec.HEVC))
        assertTrue(AnnexB.units(byteArrayOf(0, 0, 1)).isEmpty())
        assertTrue(AnnexB.units(byteArrayOf(1, 2, 3)).isEmpty())
    }

    @Test fun profileValidationUsesEncodedByteLength() {
        val profile = CameraProfile(name = "Kamera", host = "192.168.1.10", deviceId = "123", username = "synthetic")
        assertNull(profile.validationError())
        assertNotNull(profile.copy(username = "é".repeat(17)).validationError())
        assertNotNull(profile.copy(host = "256.1.1.1").validationError())
        assertNotNull(profile.copy(deviceId = "").validationError())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun ptzStopLeaseRenewalAndDirectionChange() = runTest {
        val commands = mutableListOf<Direction?>()
        val watchdog = PtzWatchdog(backgroundScope, { commands += it }, { testScheduler.currentTime })
        watchdog.move(Direction.LEFT)
        advanceTimeBy(800); watchdog.renew(); advanceTimeBy(800); runCurrent()
        assertEquals(listOf(Direction.LEFT), commands)
        watchdog.move(Direction.RIGHT)
        assertEquals(listOf(Direction.LEFT, null, Direction.RIGHT), commands)
        advanceTimeBy(1100); runCurrent()
        assertNull(commands.last())
        val count = commands.size
        watchdog.stop()
        assertEquals(count, commands.size)
    }

    @Test fun cancellingTcpReadClosesBlockedSocket() = runBlocking {
        ServerSocket(0).use { server ->
            val accepted = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val peer = Thread {
                server.accept().use { socket -> accepted.countDown(); if (socket.getInputStream().read() == -1) closed.countDown() }
            }.apply { isDaemon = true; start() }
            val channel = TcpChannel(Socket())
            channel.connect("127.0.0.1", server.localPort)
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            val read = launch { channel.readExact(4) }
            delay(50)
            withTimeout(1000) { read.cancelAndJoin() }
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            channel.close(); peer.join(1000)
        }
    }
}
