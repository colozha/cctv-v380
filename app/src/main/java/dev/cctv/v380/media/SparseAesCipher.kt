package dev.cctv.v380.media

import android.annotation.SuppressLint
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Verified primitive only. A session must establish key derivation and mode before calling this. */
object SparseAesCipher {
    enum class Mode { SPARSE_64_80, ALIGNED, FIRST_2048 }

    @SuppressLint("GetInstance") // Required by the observed camera primitive; password storage uses AES-GCM.
    fun decrypt(input: ByteArray, key: ByteArray, mode: Mode): ByteArray {
        require(input.size <= 8 * 1024 * 1024 && key.size == 16)
        val result = input.copyOf()
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        if (mode == Mode.SPARSE_64_80) {
            var offset = 0
            while (result.size - offset >= 64) {
                cipher.doFinal(input, offset, 64).copyInto(result, offset)
                offset += 80
            }
        } else {
            val count = (input.size / 16 * 16).let { if (mode == Mode.FIRST_2048) minOf(it, 2048) else it }
            if (count > 0) cipher.doFinal(input, 0, count).copyInto(result)
        }
        return result
    }
}
