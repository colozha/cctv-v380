package dev.cctv.v380.ui

import android.app.Application
import android.net.Uri
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.provisioning.ProvisioningManager
import dev.cctv.v380.provisioning.ProvisioningPhase
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.NearbyWifi
import dev.cctv.v380.transport.WifiNetwork
import dev.cctv.v380.transport.CameraFailure
import dev.cctv.v380.protocol.CameraSettingsProtocol
import dev.cctv.v380.protocol.IoTProtocolException
import dev.cctv.v380.protocol.IoTFailure
import dev.cctv.v380.media.RecordingExporter
import android.provider.DocumentsContract
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.BindException

data class CameraUiState(
    val profiles: List<CameraProfile> = emptyList(), val discovered: List<DiscoveredCamera> = emptyList(),
    val scanning: Boolean = false, val scanCompleted: Boolean = false, val loading: Boolean = true,
    val saving: Boolean = false, val error: String? = null, val wifiScanning: Boolean = false,
    val nearbyWifi: List<NearbyWifi> = emptyList(),
)

enum class DownloadPhase { IDLE, AWAITING_ACCESS, DOWNLOADING, SAVING, COMPLETE, CANCELLED, FAILED }
enum class SdFormatPhase { IDLE, SENDING, CHECKING, UNKNOWN, REJECTED }
data class CameraFeatureState(
    val profileId: String? = null, val tab: CameraTab = CameraTab.LIVE,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val settings: CameraSettings? = null, val draft: Map<Int, Any> = emptyMap(),
    val date: LocalDate = LocalDate.now(), val eventsOnly: Boolean = false,
    val recordings: List<RecordingSegment> = emptyList(), val recordingsLoaded: Boolean = false,
    val selected: RecordingSegment? = null, val paused: Boolean = false,
    val playRequest: Int = 0, val requestedPositionMs: Long = 0,
    val download: DownloadPhase = DownloadPhase.IDLE, val downloadBytes: Long = 0,
    val pendingFormat: PendingSdFormat? = null, val pendingLoaded: Boolean = false,
    val format: SdFormatPhase = SdFormatPhase.IDLE,
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ProfileStore(application)
    private val wifi = WifiNetwork(application)
    private val discovery = LanDiscovery(wifi)
    val diagnostics = Diagnostics()
    val session = CameraSession(wifi, discovery, diagnostics)
    val provisioning = ProvisioningManager(wifi, discovery, session, store, viewModelScope)
    private val mutableUi = MutableStateFlow(CameraUiState())
    val ui = mutableUi.asStateFlow()
    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var handoffExpiry: Job? = null
    private var expectedHandoff = false
    private var renderSurface: Surface? = null
    private val mutableFeatures = MutableStateFlow(CameraFeatureState())
    val features = mutableFeatures.asStateFlow()
    private var featureJob: Job? = null
    private var featureVersion = 0
    private var pendingExport: Pair<Uri, RecordingSegment>? = null

    init {
        viewModelScope.launch { reload() }
        viewModelScope.launch(Dispatchers.IO) {
            // Only our temporary files; never delete user-selected documents on startup.
            application.cacheDir.listFiles()?.filter { it.name.startsWith("v380-recording-") && it.extension == "mp4" }?.forEach { it.delete() }
        }
    }

    private suspend fun reload() {
        try {
            val profiles = withContext(Dispatchers.IO) { store.load() }
            mutableUi.update { it.copy(profiles = profiles, loading = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutableUi.update { it.copy(loading = false, error = "Profil tidak dapat dibaca. Data lama tidak dihapus. Periksa ruang penyimpanan lalu buka ulang aplikasi.") }
        }
    }

    fun scan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            mutableUi.update { it.copy(scanning = true, scanCompleted = false, discovered = emptyList(), error = null) }
            diagnostics.record(DiagnosticStage.DISCOVERY, DiagnosticStatus.STARTED)
            try {
                val link = wifi.resolve()
                diagnostics.selectNetwork(link.localAddress, link.gateway)
                val cameras = discovery.scan(link) { camera ->
                    mutableUi.update { state -> state.copy(discovered = state.discovered.filterNot { it.deviceId == camera.deviceId } + camera) }
                }
                diagnostics.record(DiagnosticStage.DISCOVERY, bytes = cameras.size)
                mutableUi.update { it.copy(scanCompleted = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: BindException) { message("Port pencarian sedang dipakai aplikasi lain. Tutup aplikasi tersebut atau tambahkan kamera manual.") }
            catch (_: SecurityException) { message(SessionError.PermissionDenied.userMessage()) }
            catch (_: Exception) { message("Pencarian gagal. Hubungkan ke Wi-Fi kamera lalu coba lagi.") }
            finally { mutableUi.update { it.copy(scanning = false) } }
        }
    }
    fun cancelScan() { scanJob?.cancel() }

    fun scanWifi() = viewModelScope.launch {
        mutableUi.update { it.copy(wifiScanning = true, error = null) }
        try { mutableUi.update { it.copy(nearbyWifi = wifi.scanSsids()) } }
        catch (cancelled: CancellationException) { if (cancelled !is TimeoutCancellationException) throw cancelled; message("Pemindaian Wi-Fi timeout. Masukkan SSID secara manual.") }
        catch (_: Exception) { message("Daftar Wi-Fi tidak tersedia. Aktifkan lokasi/izin pemindaian atau masukkan SSID manual.") }
        finally { mutableUi.update { it.copy(wifiScanning = false) } }
    }

    fun save(profile: CameraProfile, password: CharArray?, remember: Boolean, onSaved: () -> Unit) {
        viewModelScope.launch {
            mutableUi.update { it.copy(saving = true) }
            try {
                withContext(Dispatchers.IO) { store.save(profile, password, remember) }
                reload(); onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("Profil gagal disimpan. Periksa isian dan ruang penyimpanan lalu coba lagi.") }
            finally { password?.fill('\u0000'); mutableUi.update { it.copy(saving = false) } }
        }
    }

    fun delete(profile: CameraProfile, onDeleted: () -> Unit) = viewModelScope.launch {
        try {
            if (session.connectedProfile?.id == profile.id) session.close()
            withContext(Dispatchers.IO) { store.delete(profile.id) }
            reload(); onDeleted()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message("Profil gagal dihapus. Coba lagi.") }
    }

    fun connect(profile: CameraProfile, entered: CharArray?) {
        dismissError()
        val previous = connectionJob
        connectionJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            cancelScan()
            try {
                val password = entered ?: withContext(Dispatchers.IO) { store.password(profile.id) }
                if (password == null) { message("Masukkan password lokal kamera untuk menyambungkan."); return@launch }
                session.connect(profile, password)
                val resolved = session.connectedProfile ?: return@launch
                if (resolved != profile) withContext(Dispatchers.IO) { store.save(resolved, null, profile.hasSavedPassword) }
                if (features.value.tab == CameraTab.LIVE)
                    renderSurface?.takeIf { it.isValid }?.let { session.play(it, Quality.HD) }
                else if (pendingExport != null) startExport()
                else if (features.value.tab == CameraTab.SETTINGS) loadSettings()
                else loadRecordings()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(CameraSession.reason(error).userMessage()) }
            finally { entered?.fill('\u0000') }
        }.also { job -> job.invokeOnCompletion { entered?.fill('\u0000') } }
    }

    fun surfaceReady(value: Surface, profileId: String) {
        renderSurface = value
        if (features.value.tab == CameraTab.LIVE && session.connectedProfile?.id == profileId && session.state.value is SessionState.Ready)
            connectionJob = viewModelScope.launch { session.play(value, Quality.HD) }
    }
    fun surfaceLost(value: Surface?) {
        if (value != null && renderSurface === value) {
            renderSurface = null
            connectionJob?.cancel()
            if (features.value.tab == CameraTab.LIVE) viewModelScope.launch { session.detachSurface() }
        }
    }
    fun changeQuality(quality: Quality) {
        val previous = connectionJob
        connectionJob = viewModelScope.launch { previous?.cancelAndJoin(); session.changeQuality(quality) }
    }
    fun disconnect() {
        connectionJob?.cancel()
        cancelFeature()
        viewModelScope.launch { session.close() }
    }
    suspend fun move(direction: dev.cctv.v380.control.Direction) = session.move(direction)
    suspend fun renewPtz() = session.renewPtz()
    suspend fun stopPtz() = withContext(NonCancellable) { withTimeoutOrNull(600) { session.stopPtz() }; Unit }

    fun beginSetup() { cancelScan(); disconnect(); provisioning.cancel(); provisioning.resumePending(); dismissError() }
    fun connectAp(ssid: String, password: CharArray) {
        cancelScan(); expectWifiHandoff(); provisioning.connectAp(ssid, password)
    }
    fun expectWifiHandoff() { expectedHandoff = true }
    fun onForeground() { expectedHandoff = false; handoffExpiry?.cancel(); handoffExpiry = null }
    fun onBackground() {
        cancelScan(); connectionJob?.cancel()
        cancelFeature()
        val expected = expectedHandoff
        provisioning.onBackground(expected)
        viewModelScope.launch { session.close(); if (!expected) wifi.releaseAp() }
        if (expected) {
            handoffExpiry?.cancel()
            handoffExpiry = viewModelScope.launch { delay(30_000); expectedHandoff = false; provisioning.onBackground(false); wifi.releaseAp() }
        }
    }
    fun cancelSetup() { provisioning.cancel(); disconnect() }
    fun reloadAfterSetup() = viewModelScope.launch { reload() }

    fun prepareFeatures(profile: CameraProfile) {
        if (features.value.profileId == profile.id) return
        cancelFeature(); pendingExport = null
        mutableFeatures.value = CameraFeatureState(profileId = profile.id)
        viewModelScope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { store.loadPendingFormat() }
                if (features.value.profileId == profile.id) mutableFeatures.update {
                    it.copy(pendingLoaded = true, pendingFormat = pending,
                        format = if (pending?.profileId == profile.id) SdFormatPhase.UNKNOWN else SdFormatPhase.IDLE)
                }
            } catch (_: Exception) {
                mutableFeatures.update { it.copy(error = "Status format sebelumnya tidak dapat dibaca. Format SD dinonaktifkan.") }
            }
        }
    }

    fun selectTab(tab: CameraTab) {
        if (tab == features.value.tab) return
        cancelFeature()
        mutableFeatures.update { it.copy(tab = tab, error = null, notice = null, draft = emptyMap(), paused = true) }
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            session.stopMedia()
            if (tab == CameraTab.LIVE && session.connectedProfile != null)
                renderSurface?.takeIf { it.isValid }?.let { session.play(it, Quality.HD) }
        }
    }

    private fun launchFeature(stage: DiagnosticStage, requiresCamera: Boolean = true, block: suspend () -> Unit) {
        val previous = featureJob
        val version = ++featureVersion
        val expectedProfile = features.value.profileId
        featureJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            mutableFeatures.update { it.copy(busy = true, error = null, notice = null) }
            diagnostics.record(stage, DiagnosticStatus.STARTED)
            try {
                if (requiresCamera && (expectedProfile == null || session.connectedProfile?.id != expectedProfile || features.value.profileId != expectedProfile))
                    throw CameraFailure(SessionError.AuthenticationFailed)
                block()
            }
            catch (cancelled: CancellationException) {
                diagnostics.record(stage, DiagnosticStatus.CANCELLED)
                if (version == featureVersion) mutableFeatures.update { it.copy(
                    download = if (it.download in setOf(DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) DownloadPhase.CANCELLED else it.download,
                    format = if (it.pendingFormat != null) SdFormatPhase.UNKNOWN else it.format) }
                throw cancelled
            } catch (error: Exception) {
                val reason = CameraSession.reason(error)
                diagnostics.record(stage, DiagnosticStatus.FAILED, reason = reason,
                    code = (error as? IoTProtocolException)?.deviceCode)
                if (version == featureVersion) mutableFeatures.update { it.copy(error = reason.userMessage(),
                    download = if (stage == DiagnosticStage.DOWNLOAD) DownloadPhase.FAILED else it.download,
                    format = if (it.pendingFormat != null) SdFormatPhase.UNKNOWN else it.format) }
            } finally { if (version == featureVersion) mutableFeatures.update { it.copy(busy = false) } }
        }
    }

    fun cancelFeature() {
        featureVersion++
        featureJob?.cancel(); featureJob = null
        val abandoned = pendingExport
        pendingExport = null
        if (abandoned != null) viewModelScope.launch(Dispatchers.IO) {
            runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, abandoned.first) }
        }
        mutableFeatures.update { it.copy(busy = false, paused = true, draft = emptyMap(),
            download = if (it.download in setOf(DownloadPhase.AWAITING_ACCESS, DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) DownloadPhase.CANCELLED else it.download,
            format = if (it.pendingFormat != null) SdFormatPhase.UNKNOWN else it.format) }
    }

    fun loadSettings() = launchFeature(DiagnosticStage.SETTINGS) {
        val settings = session.readSettings()
        mutableFeatures.update { it.copy(settings = settings, draft = emptyMap()) }
        if (features.value.pendingFormat?.profileId == features.value.profileId) mutableFeatures.update {
            it.copy(format = SdFormatPhase.UNKNOWN,
                notice = "Ada perintah format sebelumnya. Status kartu diperbarui; perintah format tidak dikirim ulang.")
        }
    }

    fun draftSetting(id: Int, value: Any) {
        if (features.value.busy) return
        mutableFeatures.update { state ->
            val draft = state.draft.toMutableMap()
            if (state.settings?.values?.get(id) == value) draft.remove(id) else draft[id] = value
            state.copy(draft = draft.toMap())
        }
    }

    fun saveSettings() {
        val before = features.value.settings ?: return
        val changes = features.value.draft.toMap()
        if (changes.isEmpty()) return
        launchFeature(DiagnosticStage.SETTINGS) {
            try {
                val settings = session.saveSettings(changes, before)
                mutableFeatures.update { it.copy(settings = settings, draft = emptyMap(), notice = "Pengaturan tersimpan dan telah diperiksa kembali.") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Reconcile a potentially applied write; never send it a second time.
                val refreshed = try { session.readSettings() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                refreshed?.let { settings ->
                    mutableFeatures.update { it.copy(settings = settings, draft = emptyMap()) }
                }
                mutableFeatures.update { it.copy(notice = "Hasil penyimpanan belum dapat dipastikan. Periksa nilai terbaru sebelum mencoba lagi.") }
                throw error
            }
        }
    }

    fun reverseImage() {
        val before = features.value.settings ?: return
        launchFeature(DiagnosticStage.SETTINGS) {
            try {
                val after = session.reverseImage(before)
                mutableFeatures.update { it.copy(settings = after, draft = emptyMap(), notice = "Orientasi kamera telah diubah.") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val refreshed = try { session.readSettings() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                mutableFeatures.update { it.copy(settings = refreshed ?: it.settings,
                    notice = "Hasil perubahan orientasi belum dapat dipastikan. Periksa gambar sebelum mencoba lagi.") }
                throw error
            }
        }
    }

    fun setRecordingDate(date: LocalDate) {
        cancelFeature()
        mutableFeatures.update { it.copy(date = date, recordings = emptyList(), recordingsLoaded = false, selected = null) }
        viewModelScope.launch { session.stopMedia() }
    }
    fun setEventsOnly(value: Boolean) {
        cancelFeature()
        mutableFeatures.update { it.copy(eventsOnly = value, recordings = emptyList(), recordingsLoaded = false, selected = null) }
        viewModelScope.launch { session.stopMedia() }
    }
    fun loadRecordings() {
        val date = features.value.date
        val events = features.value.eventsOnly
        val zone = ZoneId.systemDefault()
        launchFeature(DiagnosticStage.RECORDINGS) {
            session.stopMedia()
            val rows = session.listRecordings(date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), events)
            mutableFeatures.update { it.copy(recordings = rows, recordingsLoaded = true) }
        }
    }
    fun requestPlayback(recording: RecordingSegment, positionMs: Long = recording.startMs) {
        mutableFeatures.update { it.copy(selected = recording, paused = false, playRequest = it.playRequest + 1,
            requestedPositionMs = positionMs.coerceIn(recording.startMs, recording.endMs - 1), error = null) }
    }
    fun startPlayback(surface: Surface) {
        val state = features.value
        val recording = state.selected ?: return
        if (state.tab != CameraTab.RECORDINGS || state.paused || state.busy) return
        launchFeature(DiagnosticStage.PLAYBACK) { session.playRecording(surface, recording, state.requestedPositionMs) }
    }
    fun pausePlayback() {
        if (features.value.download in setOf(DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) return
        mutableFeatures.update { it.copy(paused = true) }
        viewModelScope.launch { session.stopMedia() }
    }

    fun exportRecording(uri: Uri, recording: RecordingSegment) {
        pendingExport = uri to recording
        mutableFeatures.update { it.copy(download = DownloadPhase.AWAITING_ACCESS, paused = true,
            notice = if (session.connectedProfile == null) "Lokasi penyimpanan sudah dipilih. Sambungkan kamera untuk melanjutkan." else null) }
        if (session.connectedProfile?.id == features.value.profileId) startExport()
    }

    private fun startExport() {
        val (uri, recording) = pendingExport ?: return
        val profileId = features.value.profileId
        val version = featureVersion + 1
        launchFeature(DiagnosticStage.DOWNLOAD, requiresCamera = false) {
            val app = getApplication<Application>()
            pendingExport = null
            try {
                if (session.connectedProfile?.id != profileId || features.value.profileId != profileId)
                    throw CameraFailure(SessionError.AuthenticationFailed)
                mutableFeatures.update { it.copy(download = DownloadPhase.DOWNLOADING, downloadBytes = 0) }
                withContext(Dispatchers.IO) {
                    RecordingExporter(app.cacheDir).use { exporter ->
                        session.downloadRecording(recording) { frame, width, height ->
                            currentCoroutineContext().ensureActive()
                            exporter.write(frame, width, height)
                            if (version == featureVersion) mutableFeatures.update { it.copy(downloadBytes = exporter.bytesWritten) }
                        }
                        val file = exporter.finish()
                        currentCoroutineContext().ensureActive()
                        if (version == featureVersion) mutableFeatures.update { it.copy(download = DownloadPhase.SAVING) }
                        app.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            file.inputStream().use { input ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer); if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.flush()
                            }
                        } ?: throw java.io.IOException("No destination")
                    }
                }
                currentCoroutineContext().ensureActive()
                mutableFeatures.update { it.copy(download = DownloadPhase.COMPLETE, notice = "Rekaman video tersimpan tanpa audio.") }
            } catch (error: Exception) {
                val removed = withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }.getOrDefault(false)
                }
                // Cancellation changes the feature generation; the storage warning must still reach the user.
                if (!removed) message("File tujuan penyalinan mungkin belum lengkap. Hapus file tersebut sebelum mencoba lagi.")
                if (error is CancellationException) throw error
                throw CameraFailure(SessionError.ExportFailed)
            }
        }
    }

    fun formatSdCard(profile: CameraProfile) {
        val state = features.value
        val settings = state.settings ?: return
        if (!CameraSettingsProtocol.FORMAT_STATUS_VERIFIED || state.busy || !state.pendingLoaded || state.pendingFormat != null || state.draft.isNotEmpty() ||
            settings.int(CameraSettingsProtocol.SD_STATUS) !in setOf(1, 2, 3, 8) ||
            settings.int(CameraSettingsProtocol.SD_FORMAT_STATE) == null || session.connectedProfile?.id != profile.id) return
        launchFeature(DiagnosticStage.SD_FORMAT) {
            val pending = withContext(NonCancellable + Dispatchers.IO) { store.savePendingFormat(profile); store.loadPendingFormat() }
            mutableFeatures.update { it.copy(pendingFormat = pending, format = SdFormatPhase.SENDING, recordingsLoaded = false, recordings = emptyList()) }
            currentCoroutineContext().ensureActive()
            try { session.sendSdFormat() }
            catch (error: Exception) {
                if (error is IoTProtocolException && error.kind in setOf(IoTFailure.DeviceRejected, IoTFailure.PermissionDenied, IoTFailure.AuthenticationFailed)) {
                    withContext(NonCancellable + Dispatchers.IO) { store.clearPendingFormat() }
                    mutableFeatures.update { it.copy(pendingFormat = null, format = SdFormatPhase.REJECTED) }
                } else mutableFeatures.update { it.copy(format = SdFormatPhase.UNKNOWN) }
                throw error
            }
            mutableFeatures.update { it.copy(format = SdFormatPhase.CHECKING) }
            repeat(15) {
                delay(2000)
                val current = session.readSettings()
                mutableFeatures.update { it.copy(settings = current) }
            }
            // APK's Java parser ignores DPID_SD_FORMAT_STATE. Its terminal values are not proven.
            // An ACK or normal card alone must never be promoted to "format complete".
            mutableFeatures.update { it.copy(format = SdFormatPhase.UNKNOWN,
                notice = "Perintah format diterima. Status akhir belum dapat dipastikan; periksa kartu sebelum menutup status ini.") }
        }
    }

    fun acknowledgeFormat() {
        if (features.value.busy || features.value.pendingFormat?.profileId != features.value.profileId) return
        launchFeature(DiagnosticStage.SD_FORMAT, requiresCamera = false) {
            withContext(Dispatchers.IO) { store.clearPendingFormat() }
            mutableFeatures.update { it.copy(pendingFormat = null, format = SdFormatPhase.IDLE,
                notice = "Status ditutup. Tidak ada perintah format baru yang dikirim.") }
        }
    }

    fun exportDiagnostics(uri: Uri) = viewModelScope.launch {
        try {
            val json = diagnostics.exportJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: throw java.io.IOException("No export destination")
            }
        } catch (_: Exception) { message("Diagnostik gagal diekspor. Pilih lokasi lain lalu coba lagi.") }
    }
    fun message(value: String) { mutableUi.update { it.copy(error = value) } }
    fun dismissError() { mutableUi.update { it.copy(error = null) } }
    override fun onCleared() { featureJob?.cancel(); provisioning.cancel(); wifi.releaseAp(); session.dispose(); super.onCleared() }
}
