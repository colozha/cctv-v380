package dev.cctv.v380.media

/** PCM21 microphone path: 505 PCM16 samples -> one self-contained 256-byte IMA block.
 * Header: first sample LE16, starting step index, reserved0; low nibble precedes high nibble.
 * See validation/talkback-noise/native-adpcm.json. State is local to one talkback attempt.
 */
class TalkbackAdpcmEncoder {
    private var index = 0

    fun encode(pcm: ByteArray): ByteArray {
        require(pcm.size == 1010)
        fun sample(offset: Int) = ((pcm[offset].toInt() and 255) or (pcm[offset + 1].toInt() shl 8)).toShort().toInt()
        var predictor = sample(0)
        val block = ByteArray(256)
        block[0] = pcm[0]; block[1] = pcm[1]; block[2] = index.toByte()
        for (n in 1 until 505) {
            val step = steps[index]
            val difference = sample(n * 2) - predictor
            // Use full-width arithmetic: extreme PCM transitions must never wrap an index.
            val magnitude = (kotlin.math.abs(difference) * 4 / step).coerceAtMost(7)
            val code = magnitude or if (difference < 0) 8 else 0
            val change = ((magnitude * 2 + 1) * step) shr 3
            predictor = (predictor + if (difference < 0) -change else change).coerceIn(-32768, 32767)
            index = (index + adjustment[magnitude]).coerceIn(0, 88)
            val position = 4 + (n - 1) / 2
            block[position] = (block[position].toInt() or (code shl if (n % 2 == 1) 0 else 4)).toByte()
        }
        return block
    }

    companion object {
        private val adjustment = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)
        private val steps = intArrayOf(
            7,8,9,10,11,12,13,14,16,17,19,21,23,25,28,31,34,37,41,45,50,55,60,66,73,80,88,97,107,118,
            130,143,157,173,190,209,230,253,279,307,337,371,408,449,494,544,598,658,724,796,876,963,
            1060,1166,1282,1411,1552,1707,1878,2066,2272,2499,2749,3024,3327,3660,4026,4428,4871,
            5358,5894,6484,7132,7845,8630,9493,10442,11487,12635,13899,15289,16818,18500,20350,
            22385,24623,27086,29794,32767,
        )
    }
}
