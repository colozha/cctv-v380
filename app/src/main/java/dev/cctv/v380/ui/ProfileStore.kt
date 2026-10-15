package dev.cctv.v380.ui

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import dev.cctv.v380.session.CameraProfile
import dev.cctv.v380.session.AuthenticationMode
import dev.cctv.v380.session.validationError
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer

data class PendingSetup(val profile: CameraProfile, val targetSsid: String)
data class PendingSdFormat(val profileId: String, val deviceId: String, val createdAtMs: Long,
    val operationId: String = "legacy:$profileId:$createdAtMs")
data class PendingCameraCalibration(val operationId: String, val profileId: String, val deviceId: String, val createdAtMs: Long)
data class PendingCameraRestart(val operationId: String, val profileId: String, val deviceId: String, val createdAtMs: Long)
class PendingApEdit(val operationId: String, val profileId: String, val deviceId: String,
    val oldSsid: String, val targetSsid: String, val createdAtMs: Long) {
    override fun toString() = "PendingApEdit(redacted)"
}

/** One atomic, non-backed-up file: metadata and optional ciphertext, never plaintext. */
class ProfileStore(
    context: Context,
    file: File = File(context.noBackupFilesDir, "camera-profiles.json"),
    private val secrets: SecretStore = SecretStore(),
) {
    private val storage = AtomicFile(file)
    private val pending = AtomicFile(File(file.parentFile, "pending-${file.name}"))
    // ponytail: one pending format per app; use per-profile files only if simultaneous camera work is introduced.
    private val pendingFormat = AtomicFile(File(file.parentFile, "pending-sd-format.json"))
    private val pendingRestart = AtomicFile(File(file.parentFile, "pending-camera-restart.json"))
    private val pendingCalibration = AtomicFile(File(file.parentFile, "pending-ptz-calibration.json"))
    // ponytail: one pending AP edit; split per profile if concurrent camera sessions are introduced.
    private val pendingAp = AtomicFile(File(file.parentFile, "pending-ap-edit.json"))
    private var migrationNeeded = false

    private fun records(): MutableList<JSONObject> {
        migrationNeeded = false
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) return mutableListOf()
        val json = JSONObject(storage.openRead().use {
            require(it.channel.size() <= 1024 * 1024) { "Profile storage exceeds the size bound" }
            it.readBytes().toString(Charsets.UTF_8)
        })
        val version = json.getInt("version")
        require(version in 1..2) { "Unsupported profile storage version" }
        migrationNeeded = version == 1
        val records = json.getJSONArray("profiles")
        return MutableList(records.length()) { records.getJSONObject(it) }
    }

    @Synchronized
    fun load(): List<CameraProfile> {
        val rows = records()
        val profiles = rows.map { row ->
            CameraProfile(row.getString("id"), row.getString("name"), row.getString("host"), row.getInt("port"),
                row.get("deviceId").toString(), row.optString("username"),
                row.optString("protocolProfileId", "auto").let { if (it == "unverified") "auto" else it }, row.has("secret"),
                AuthenticationMode.valueOf(row.optString("authenticationMode", "AUTO")),
                if (row.has("platformType")) row.getInt("platformType") else null,
                if (row.has("discoveryGeneration")) row.getInt("discoveryGeneration") else null)
                .also { require(it.validationError() == null) { "Invalid saved profile" } }
        }
        if (migrationNeeded) {
            rows.forEachIndexed { index, row ->
                val profile = profiles[index]
                row.put("deviceId", profile.deviceId).put("protocolProfileId", profile.protocolProfileId)
                    .put("authenticationMode", profile.authenticationMode.name)
            }
            write(rows)
        }
        return profiles
    }

    @Synchronized
    fun save(profile: CameraProfile, password: CharArray?, remember: Boolean) {
        require(profile.validationError() == null)
        val rows = records()
        val old = rows.firstOrNull { it.getString("id") == profile.id }
        val row = JSONObject().put("id", profile.id).put("name", profile.name).put("host", profile.host)
            .put("port", profile.port).put("deviceId", profile.deviceId).put("username", profile.username)
            .put("protocolProfileId", profile.protocolProfileId)
            .put("authenticationMode", profile.authenticationMode.name)
        profile.platformType?.let { row.put("platformType", it) }
        profile.discoveryGeneration?.let { row.put("discoveryGeneration", it) }
        if (remember) {
            if (password != null) {
                val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                try {
                    val secret = secrets.encrypt(profile.id, bytes)
                    row.put("secret", JSONObject().put("iv", encode(secret.iv)).put("data", encode(secret.ciphertext)))
                } finally { bytes.fill(0); if (buffer.hasArray()) buffer.array().fill(0) }
            } else if (old?.has("secret") == true) row.put("secret", old.getJSONObject("secret"))
            else throw IllegalArgumentException("Password required to enable storage")
        }
        rows.removeAll { it.getString("id") == profile.id }
        rows += row
        write(rows)
    }

    @Synchronized
    fun password(id: String): CharArray? {
        val secret = records().firstOrNull { it.getString("id") == id }?.optJSONObject("secret") ?: return null
        val bytes = secrets.decrypt(id, EncryptedSecret(decode(secret.getString("iv")), decode(secret.getString("data"))))
        try {
            val chars = Charsets.UTF_8.decode(ByteBuffer.wrap(bytes))
            return CharArray(chars.remaining()).also { chars.get(it); if (chars.hasArray()) chars.array().fill('\u0000') }
        } finally { bytes.fill(0) }
    }

    @Synchronized
    fun delete(id: String) {
        require(loadPendingCalibration()?.profileId != id) { "Resolve the pending calibration before deleting this profile" }
        require(loadPendingRestart()?.profileId != id) { "Resolve the pending restart status before deleting this profile" }
        require(loadPendingFormat()?.profileId != id) { "Resolve the pending format status before deleting this profile" }
        require(loadPendingApEdit()?.profileId != id) { "Resolve the pending hotspot status before deleting this profile" }
        write(records().filterNot { it.getString("id") == id })
    }

    @Synchronized
    fun updateVerifiedAddress(id: String, deviceId: String, host: String, port: Int) {
        require(dev.cctv.v380.session.isIpv4(host) && port in 1..65535)
        val rows = records()
        val row = rows.firstOrNull { it.getString("id") == id && it.get("deviceId").toString() == deviceId }
            ?: throw IllegalArgumentException("Profile identity changed")
        row.put("host", host).put("port", port)
        write(rows)
    }

    @Synchronized
    fun savePendingSetup(profile: CameraProfile, targetSsid: String) {
        require(profile.validationError() == null && targetSsid.toByteArray(Charsets.UTF_8).size in 1..32)
        val row = JSONObject().put("id", profile.id).put("name", profile.name).put("host", profile.host)
            .put("port", profile.port).put("deviceId", profile.deviceId).put("username", profile.username)
            .put("protocolProfileId", profile.protocolProfileId).put("authenticationMode", profile.authenticationMode.name)
        profile.platformType?.let { row.put("platformType", it) }
        profile.discoveryGeneration?.let { row.put("discoveryGeneration", it) }
        val bytes = JSONObject().put("version", 1).put("targetSsid", targetSsid).put("profile", row)
            .toString().toByteArray(Charsets.UTF_8)
        val output = pending.startWrite()
        try { output.write(bytes); pending.finishWrite(output) }
        catch (error: Throwable) { pending.failWrite(output); throw error }
    }

    @Synchronized
    fun loadPendingSetup(): PendingSetup? {
        if (!pending.baseFile.exists() && !File(pending.baseFile.path + ".bak").exists()) return null
        val json = pending.openRead().use { require(it.channel.size() <= 65536); JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1)
        val row = json.getJSONObject("profile")
        val profile = CameraProfile(id = row.getString("id"), name = row.getString("name"), host = row.getString("host"),
            port = row.getInt("port"), deviceId = row.getString("deviceId"), username = row.getString("username"),
            protocolProfileId = row.getString("protocolProfileId"), authenticationMode = AuthenticationMode.valueOf(row.getString("authenticationMode")),
            platformType = if (row.has("platformType")) row.getInt("platformType") else null,
            discoveryGeneration = if (row.has("discoveryGeneration")) row.getInt("discoveryGeneration") else null)
        val ssid = json.getString("targetSsid")
        require(profile.validationError() == null && ssid.toByteArray(Charsets.UTF_8).size in 1..32)
        return PendingSetup(profile, ssid)
    }
    @Synchronized
    fun clearPendingSetup() { pending.delete() }

    @Synchronized
    fun savePendingFormat(profile: CameraProfile, operationId: String = java.util.UUID.randomUUID().toString()): PendingSdFormat {
        require(profile.validationError() == null && loadPendingFormat() == null)
        require(operationId.isNotBlank() && operationId.length <= 256)
        val value = PendingSdFormat(profile.id, profile.deviceId, System.currentTimeMillis(), operationId)
        val bytes = JSONObject().put("version", 2).put("operationId", value.operationId).put("profileId", value.profileId)
            .put("deviceId", value.deviceId).put("createdAtMs", value.createdAtMs).toString().toByteArray()
        val output = pendingFormat.startWrite()
        try { output.write(bytes); output.fd.sync(); pendingFormat.finishWrite(output) }
        catch (error: Throwable) { pendingFormat.failWrite(output); throw error }
        // AtomicFile.finishWrite can log a rename failure without throwing; verify before authorizing a write.
        if (loadPendingFormat() != value) throw java.io.IOException("Pending format marker was not persisted")
        return value
    }

    @Synchronized
    fun loadPendingFormat(): PendingSdFormat? {
        if (!pendingFormat.baseFile.exists() && !File(pendingFormat.baseFile.path + ".bak").exists()) return null
        val json = pendingFormat.openRead().use { require(it.channel.size() <= 65536); JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") in 1..2)
        val operationId = if (json.getInt("version") == 2) json.getString("operationId") else "legacy:${json.getString("profileId")}:${json.getLong("createdAtMs")}"
        return PendingSdFormat(json.getString("profileId"), json.getString("deviceId"), json.getLong("createdAtMs"), operationId).also {
            require(it.operationId.isNotBlank() && it.operationId.length <= 256)
            require(it.profileId.length in 1..128 && it.deviceId.length in 1..128 && it.createdAtMs >= 0)
        }
    }

    @Synchronized
    fun clearPendingFormat(operationId: String) {
        require(loadPendingFormat()?.operationId == operationId)
        pendingFormat.delete()
        if (loadPendingFormat() != null) throw java.io.IOException("Pending format marker was not removed")
    }

    @Synchronized
    fun savePendingRestart(value: PendingCameraRestart) {
        require(loadPendingRestart() == null)
        require(value.operationId.length in 1..128 && value.profileId.length in 1..128 && value.deviceId.length in 1..128 && value.createdAtMs >= 0)
        val bytes = JSONObject().put("version", 1).put("operationId", value.operationId).put("profileId", value.profileId)
            .put("deviceId", value.deviceId).put("createdAtMs", value.createdAtMs).toString().toByteArray(Charsets.UTF_8)
        val output = pendingRestart.startWrite()
        try { output.write(bytes); pendingRestart.finishWrite(output) }
        catch (error: Throwable) { pendingRestart.failWrite(output); throw error }
    }

    @Synchronized
    fun loadPendingRestart(): PendingCameraRestart? {
        if (!pendingRestart.baseFile.exists() && !File(pendingRestart.baseFile.path + ".bak").exists()) return null
        val json = pendingRestart.openRead().use { require(it.channel.size() <= 4096); JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1)
        return PendingCameraRestart(json.getString("operationId"), json.getString("profileId"), json.getString("deviceId"), json.getLong("createdAtMs")).also {
            require(it.operationId.length in 1..128 && it.profileId.length in 1..128 && it.deviceId.length in 1..128 && it.createdAtMs >= 0)
        }
    }

    @Synchronized
    fun clearPendingRestart(operationId: String) {
        require(loadPendingRestart()?.operationId == operationId)
        pendingRestart.delete()
    }

    @Synchronized
    fun savePendingCalibration(value: PendingCameraCalibration) {
        require(loadPendingCalibration() == null)
        require(value.operationId.length in 1..128 && value.profileId.length in 1..128 && value.deviceId.length in 1..128 && value.createdAtMs >= 0)
        val bytes = JSONObject().put("version", 1).put("operationId", value.operationId).put("profileId", value.profileId)
            .put("deviceId", value.deviceId).put("createdAtMs", value.createdAtMs).toString().toByteArray(Charsets.UTF_8)
        val output = pendingCalibration.startWrite()
        try { output.write(bytes); pendingCalibration.finishWrite(output) }
        catch (error: Throwable) { pendingCalibration.failWrite(output); throw error }
    }

    @Synchronized
    fun loadPendingCalibration(): PendingCameraCalibration? {
        if (!pendingCalibration.baseFile.exists() && !File(pendingCalibration.baseFile.path + ".bak").exists()) return null
        val json = pendingCalibration.openRead().use { require(it.channel.size() <= 4096); JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1)
        return PendingCameraCalibration(json.getString("operationId"), json.getString("profileId"), json.getString("deviceId"), json.getLong("createdAtMs")).also {
            require(it.operationId.length in 1..128 && it.profileId.length in 1..128 && it.deviceId.length in 1..128 && it.createdAtMs >= 0)
        }
    }

    @Synchronized
    fun clearPendingCalibration(operationId: String) {
        require(loadPendingCalibration()?.operationId == operationId)
        pendingCalibration.delete()
    }

    @Synchronized
    fun savePendingApEdit(value: PendingApEdit) {
        require(loadPendingApEdit() == null)
        val bytes = JSONObject().put("version", 1).put("operationId", value.operationId)
            .put("profileId", value.profileId).put("deviceId", value.deviceId)
            .put("oldSsid", value.oldSsid).put("targetSsid", value.targetSsid).put("createdAtMs", value.createdAtMs)
            .toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 4096)
        val output = pendingAp.startWrite()
        try { output.write(bytes); pendingAp.finishWrite(output) }
        catch (error: Throwable) { pendingAp.failWrite(output); throw error }
    }

    @Synchronized
    fun loadPendingApEdit(): PendingApEdit? {
        if (!pendingAp.baseFile.exists() && !File(pendingAp.baseFile.path + ".bak").exists()) return null
        val json = pendingAp.openRead().use { require(it.channel.size() <= 4096); JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(json.getInt("version") == 1)
        return PendingApEdit(json.getString("operationId"), json.getString("profileId"), json.getString("deviceId"),
            json.getString("oldSsid"), json.getString("targetSsid"), json.getLong("createdAtMs")).also {
            require(it.operationId.length in 1..128 && it.profileId.length in 1..128 && it.deviceId.length in 1..128 && it.createdAtMs >= 0)
            require(listOf(it.oldSsid, it.targetSsid).all { s -> s.isNotBlank() && s.toByteArray(Charsets.UTF_8).size <= 32 && s.none(Char::isISOControl) })
        }
    }

    @Synchronized
    fun clearPendingApEdit(operationId: String) {
        require(loadPendingApEdit()?.operationId == operationId)
        pendingAp.delete()
    }

    private fun write(rows: List<JSONObject>) {
        val bytes = JSONObject().put("version", 2).put("profiles", JSONArray(rows)).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1024 * 1024) { "Profile storage exceeds the size bound" }
        val output = storage.startWrite()
        try { output.write(bytes); storage.finishWrite(output) }
        catch (error: Throwable) { storage.failWrite(output); throw error }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}
