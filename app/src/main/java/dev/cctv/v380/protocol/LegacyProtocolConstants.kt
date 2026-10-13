package dev.cctv.v380.protocol

/** Public wire algorithm constants recovered statically; never camera credentials. */
internal object LegacyProtocolConstants {
    val loginKey: ByteArray get() = byteArrayOf(109, 97, 99, 114, 111, 118, 105, 100, 101, 111, 43, 42, 35, 33, 94, 64)
    val mediaKeyTail: ByteArray get() = byteArrayOf(92, 121, 20, 44, 70, 35, -127, 97, -16, 13, -128, -126)
}
