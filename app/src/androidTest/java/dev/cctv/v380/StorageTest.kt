package dev.cctv.v380

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.ui.EncryptedSecret
import dev.cctv.v380.ui.ProfileStore
import dev.cctv.v380.ui.SecretStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import org.json.JSONObject
import org.json.JSONArray
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class StorageTest {
    @Test fun migratesVersionOneWithoutChangingIdentityOrCiphertext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val alias = "v380.migration.$id"
        val file = File(context.noBackupFilesDir, "migration-$id.json")
        val vault = SecretStore(alias)
        val password = UUID.randomUUID().toString().toCharArray()
        val bytes = String(password).toByteArray(Charsets.UTF_8)
        try {
            val encrypted = vault.encrypt(id, bytes)
            val row = JSONObject().put("id", id).put("name", "Synthetic migration").put("host", "192.168.1.10")
                .put("port", 8800).put("deviceId", 12345678L).put("username", "synthetic").put("protocolProfileId", "unverified")
                .put("secret", JSONObject().put("iv", Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                    .put("data", Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)))
            file.writeText(JSONObject().put("version", 1).put("profiles", JSONArray().put(row)).toString())
            val store = ProfileStore(context, file, vault)
            val migrated = store.load().single()
            assertEquals(id, migrated.id); assertEquals("12345678", migrated.deviceId)
            assertEquals("auto", migrated.protocolProfileId)
            assertArrayEquals(password, store.password(id))
            val json = JSONObject(file.readText())
            assertEquals(2, json.getInt("version"))
            val newRow = json.getJSONArray("profiles").getJSONObject(0)
            assertTrue(newRow.get("deviceId") is String)
            assertEquals(row.getJSONObject("secret").toString(), newRow.getJSONObject("secret").toString())
            assertFalse(file.readText().contains(String(password)))
        } finally {
            password.fill('\u0000'); bytes.fill(0); file.delete()
            KeyStore.getInstance("AndroidKeyStore").run { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        }
    }

    @Test fun pendingSetupPersistsMetadataWithoutCredentialsAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "pending-test-${UUID.randomUUID()}.json")
        val store = ProfileStore(context, file)
        val profile = CameraProfile(name = "Synthetic pending", host = "192.168.1.10", deviceId = "HSC-synthetic", username = "synthetic", hasSavedPassword = true)
        val pendingFile = File(file.parentFile, "pending-${file.name}")
        try {
            store.savePendingSetup(profile, "synthetic-home")
            val restored = ProfileStore(context, file).loadPendingSetup()!!
            assertEquals(profile.id, restored.profile.id)
            assertEquals(profile.deviceId, restored.profile.deviceId)
            assertEquals("synthetic-home", restored.targetSsid)
            assertFalse(restored.profile.hasSavedPassword)
            assertFalse(pendingFile.readText().contains("secret"))
            assertTrue(store.load().isEmpty())
            store.clearPendingSetup()
            assertNull(store.loadPendingSetup())
        } finally { store.clearPendingSetup(); file.delete(); pendingFile.delete() }
    }

    @Test fun encryptedRoundTripFreshIvTamperingMissingKeyAndDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val alias = "v380.test.$id"
        val file = File(context.noBackupFilesDir, "test-$id.json")
        val secrets = SecretStore(alias)
        val store = ProfileStore(context, file, secrets)
        val profile = CameraProfile(name = "Fixture", host = "192.168.1.10", deviceId = "123", username = "synthetic")
        val password = UUID.randomUUID().toString().toCharArray()
        try {
            store.save(profile, password, true)
            assertTrue(store.load().single().hasSavedPassword)
            assertArrayEquals(password, store.password(profile.id))
            assertFalse(file.readText().contains(String(password)))
            val first = secrets.encrypt(id, byteArrayOf(1, 2, 3))
            val second = secrets.encrypt(id, byteArrayOf(1, 2, 3))
            assertFalse(first.iv.contentEquals(second.iv))
            assertThrows(Exception::class.java) { secrets.decrypt("wrong-id", first) }
            val corrupted = first.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
            assertThrows(Exception::class.java) { secrets.decrypt(id, EncryptedSecret(first.iv, corrupted)) }
            store.save(profile.copy(name = "Renamed"), null, true)
            assertArrayEquals(password, store.password(profile.id))
            store.save(profile, null, false)
            assertNull(store.password(profile.id))
            store.save(profile, charArrayOf(), true)
            assertArrayEquals(charArrayOf(), store.password(profile.id))
            KeyStore.getInstance("AndroidKeyStore").run { load(null); deleteEntry(alias) }
            assertThrows(Exception::class.java) { store.password(profile.id) }
            assertEquals(1, store.load().size)
            store.delete(profile.id)
            assertTrue(store.load().isEmpty())
            assertFalse(file.readText().contains("secret"))
        } finally {
            password.fill('\u0000')
            file.delete()
            KeyStore.getInstance("AndroidKeyStore").run { load(null); if (containsAlias(alias)) deleteEntry(alias) }
        }
    }
}
