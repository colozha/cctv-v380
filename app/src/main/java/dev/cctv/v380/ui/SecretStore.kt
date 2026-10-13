package dev.cctv.v380.ui

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSecret(val iv: ByteArray, val ciphertext: ByteArray)

class SecretStore(private val alias: String = "v380.lan.credentials.v1") {
    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null)
        getKey(alias, null) as? SecretKey
    }

    private fun keyForEncryption(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
    ).run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generateKey()
    }

    @Synchronized
    fun encrypt(profileId: String, plaintext: ByteArray): EncryptedSecret {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyForEncryption())
        cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
        return EncryptedSecret(cipher.iv, cipher.doFinal(plaintext))
    }

    @Synchronized
    fun decrypt(profileId: String, encrypted: EncryptedSecret): ByteArray {
        val key = existingKey() ?: throw IllegalStateException("Credential key unavailable")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, encrypted.iv))
        cipher.updateAAD(profileId.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted.ciphertext)
    }
}
