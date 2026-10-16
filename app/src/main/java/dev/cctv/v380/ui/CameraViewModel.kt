package dev.cctv.v380.ui

import android.app.Application
import android.net.Uri
import android.view.Surface
import android.view.SurfaceView
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.cctv.v380.discovery.DiscoveredCamera
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.provisioning.ProvisioningManager
import dev.cctv.v380.provisioning.ProvisioningPhase
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.NearbyWifi
import dev.cctv.v380.transport.WifiNetwork
import dev.cctv.v380.transport.CameraFailure
import dev.cctv.v380.protocol.*
import dev.cctv.v380.protocol.IoTProtocolException
import dev.cctv.v380.protocol.IoTFailure
import dev.cctv.v380.media.RecordingExporter
import dev.cctv.v380.media.RecordingExportException
import dev.cctv.v380.media.RecordingStorageFullException
import dev.cctv.v380.media.copyRecording
import dev.cctv.v380.media.verifyRecordingCopy
import dev.cctv.v380.media.VideoFrameCapture
import dev.cctv.v380.media.TalkbackAudioSession
import dev.cctv.v380.BuildConfig
import android.provider.DocumentsContract
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import java.net.BindException
import dev.cctv.v380.protocol.ApHotspotProtocol
import android.os.Build
import android.os.Bundle
import android.system.ErrnoException
import android.system.OsConstants

data class CameraUiState(
    val profiles: List<CameraProfile> = emptyList(), val discovered: List<DiscoveredCamera> = emptyList(),
    val scanning: Boolean = false, val scanCompleted: Boolean = false, val loading: Boolean = true,
    val saving: Boolean = false, val error: String? = null, val wifiScanning: Boolean = false,
    val nearbyWifi: List<NearbyWifi> = emptyList(),
)

enum class RestartPhase { IDLE, SENDING, WAITING, RECONNECTED, NOT_SENT, REJECTED, UNKNOWN }
enum class AreaCapturePhase { IDLE, CAPTURING, EDITING, STAGED, ERROR }
data class CameraAreaImage(val reference: AreaReference, val bitmap: Bitmap)

enum class DownloadPhase { IDLE, CHOOSING_DESTINATION, AWAITING_ACCESS, DOWNLOADING, SAVING, COMPLETE, CANCELLED, FAILED }
enum class SdFormatPhase { IDLE, SENDING, CHECKING, FORMATTING, UNKNOWN, NOT_SENT, REJECTED }
enum class ApEditPhase { IDLE, CHECKING, SENDING, RECONNECTING, WAITING_WIFI, VERIFYING, VERIFIED, NOT_SENT, REJECTED, UNKNOWN }
data class CameraFeatureState(
    val profileId: String? = null, val tab: CameraTab = CameraTab.LIVE,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val settings: CameraSettings? = null, val draft: Map<Int, Any> = emptyMap(),
    val date: LocalDate = LocalDate.now(), val eventsOnly: Boolean = false,
    val recordings: List<RecordingSegment> = emptyList(), val recordingsLoaded: Boolean = false,
    val recordingOrder: RecordingOrder = RecordingOrder.NEWEST_FIRST,
    val recordingStorage: CameraSettings? = null, val storageLoading: Boolean = false,
    val storageError: String? = null,
    val selected: RecordingSegment? = null, val paused: Boolean = false,
    val playRequest: Int = 0, val requestedPositionMs: Long = 0,
    val download: DownloadPhase = DownloadPhase.IDLE, val downloadBytes: Long = 0,
    val pendingFormat: PendingSdFormat? = null, val pendingLoaded: Boolean = false,
    val format: SdFormatPhase = SdFormatPhase.IDLE,
    val pendingAp: PendingApEdit? = null, val apPendingLoaded: Boolean = false, val apStorageFailed: Boolean = false,
    val apPhase: ApEditPhase = ApEditPhase.IDLE, val apNotice: String? = null,
    val apBusy: Boolean = false,
    val management: Map<ManagementGroup, ManagementSnapshot> = emptyMap(),
    val managementDraft: Map<Int, Any> = emptyMap(),
    val managementErrors: Map<ManagementGroup, String> = emptyMap(),
    val managementNotice: String? = null, val managementLoading: ManagementGroup? = null,
    val pendingRestart: PendingCameraRestart? = null, val restartLoaded: Boolean = false,
    val managementStorageFailed: Boolean = false, val restart: RestartPhase = RestartPhase.IDLE,
    val calibrationNeedsCheck: Boolean = false, val pendingCalibration: PendingCameraCalibration? = null,
    val areaPhase: AreaCapturePhase = AreaCapturePhase.IDLE, val areaImage: CameraAreaImage? = null,
    val areaError: String? = null, val areaRequestId: Int = 0,
)

class CameraViewModel @JvmOverloads constructor(application: Application,
    private val savedState: SavedStateHandle = SavedStateHandle(), sessionOverride: CameraSession? = null) : AndroidViewModel(application) {
    private val store = ProfileStore(application)
    private val wifi = WifiNetwork(application)
    private val discovery = LanDiscovery(wifi)
    val diagnostics = Diagnostics()
    val session = sessionOverride ?: CameraSession(wifi, discovery, diagnostics, talkbackAudioFactory = {
        if (BuildConfig.TALKBACK_MIC_FRONTEND_V2) TalkbackAudioSession.android(getApplication())
        else TalkbackAudioSession.NO_OP
    })
    @Volatile private var resumed = false
    fun microphoneGranted(): Boolean = getApplication<Application>().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    fun startTalkback(profileId: String) {
        session.startTalkback(profileId) {
            resumed && microphoneGranted() && features.value.tab == CameraTab.LIVE && features.value.profileId == profileId &&
                !features.value.busy && !managementBlocked()
        }
    }
    /** Debug-only bounded probe: no microphone permission needed and no capture is opened. */
    fun startTalkbackProbe(profileId: String) {
        session.startTalkbackProbe(profileId) {
            resumed && features.value.tab == CameraTab.LIVE && features.value.profileId == profileId &&
                !features.value.busy && !managementBlocked()
        }
    }
    /** Debug-only bounded voice test: captures the microphone for at most 2 seconds. */
    fun startTalkbackVoiceTest(profileId: String) {
        session.startTalkbackVoiceTest(profileId) {
            resumed && microphoneGranted() && features.value.tab == CameraTab.LIVE && features.value.profileId == profileId &&
                !features.value.busy && !managementBlocked()
        }
    }
    fun stopTalkback() { session.requestStopTalkback() }
    fun onPaused() { resumed = false; stopTalkback() }
    val provisioning = ProvisioningManager(wifi, discovery, session, store, viewModelScope)
    private val mutableUi = MutableStateFlow(CameraUiState())
    val ui = mutableUi.asStateFlow()
    private var scanJob: Job? = null
    private var connectionJob: Job? = null
    private var handoffExpiry: Job? = null
    private var expectedHandoff = false
    private var renderSurface: Surface? = null
    private val mutableFeatures = MutableStateFlow(restoredExportState())
    val features = mutableFeatures.asStateFlow()
    private var featureJob: Job? = null
    private var featureVersion = 0
    private var storageJob: Job? = null
    private var storageVersion = 0
    private var exportRequest: Bundle?
        get() = savedState["recordingExport"]
        set(value) { savedState["recordingExport"] = value }
    private val pendingExport: Pair<Uri, RecordingSegment>?
        get() = exportRequest?.let { request ->
            request.getString("uri")?.let { Uri.parse(it) to exportSegment(request) }
        }
    private var apWorkRunning = false
    private var managementWorkRunning = false
    private var areaSurface: CompletableDeferred<SurfaceView>? = null
    private var recoveryLoadVersion = 0
    private var apPassword: CharArray? = null
    private var apCameraPassword: CharArray? = null
    private var apHandoffUntil = 0L
    private var automaticMotorAttempt = 0L

    private fun clearApSecrets() {
        apPassword?.fill('\u0000'); apPassword = null
        apCameraPassword?.fill('\u0000'); apCameraPassword = null
        apHandoffUntil = 0L
    }

    init {
        loadRecovery()
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SessionState.Connecting || state is SessionState.Authenticating ||
                    state is SessionState.Reconnecting || state is SessionState.Idle || state is SessionState.Error) {
                    clearAreaReference()
                    clearRecordingStorage()
                    mutableFeatures.update {
                        it.copy(settings = null, draft = emptyMap(), management = emptyMap(), managementDraft = emptyMap(), managementErrors = emptyMap(),
                            managementNotice = if (it.management.isNotEmpty() || it.managementDraft.isNotEmpty())
                                "Sesi kamera berubah. Muat ulang nilai sebelum mengubah pengaturan." else it.managementNotice)
                    }
                }
            }
        }
        viewModelScope.launch {
            session.areaReference.collect { reference ->
                if (reference == null && features.value.areaPhase in setOf(AreaCapturePhase.EDITING, AreaCapturePhase.STAGED)) clearAreaReference()
            }
        }
        viewModelScope.launch {
            combine(session.state, session.authenticationRevision, features) { state, revision, feature ->
                Triple(state, revision, feature)
            }.collect { (state, revision, feature) ->
                val caps = session.capabilities.value
                if (state is SessionState.Streaming && feature.tab == CameraTab.LIVE && revision > 0 &&
                    revision != automaticMotorAttempt && feature.profileId == session.connectedProfile?.id &&
                    caps?.ptz == true && caps.management.patrol == true && !feature.busy &&
                    !managementWorkRunning && !apWorkRunning && feature.pendingAp == null) {
                    automaticMotorAttempt = revision
                    if (feature.management[ManagementGroup.MOTOR]?.revision != revision) loadManagement(ManagementGroup.MOTOR, automatic = true)
                }
            }
        }
        viewModelScope.launch { reload() }
        viewModelScope.launch { wifi.revision.collect { if (it > 0L) {
            session.abort()
            mutableUi.update { state -> state.copy(discovered = emptyList(), scanCompleted = false) }
        } } }
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
        if (apWorkRunning || managementWorkRunning) { entered?.fill('\u0000'); return }
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
                if (features.value.pendingRestart != null) {
                    mutableFeatures.update { it.copy(managementNotice = "Kamera tersambung. Pilih Periksa kamera untuk memulihkan catatan restart.") }
                } else if (features.value.tab == CameraTab.LIVE)
                    renderSurface?.takeIf { it.isValid }?.let { session.play(it, Quality.HD) }
                else if (pendingExport != null) startExport()
                else if (features.value.tab == CameraTab.SETTINGS) loadSettings()
                // The recordings screen owns automatic loading after authentication.
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(CameraSession.reason(error).userMessage()) }
            finally { entered?.fill('\u0000') }
        }.also { job -> job.invokeOnCompletion { entered?.fill('\u0000') } }
    }

    fun surfaceReady(value: Surface, profileId: String) {
        renderSurface = value
        if (!managementWorkRunning && features.value.pendingRestart == null && features.value.tab == CameraTab.LIVE && session.connectedProfile?.id == profileId && session.state.value is SessionState.Ready)
            connectionJob = viewModelScope.launch { session.play(value, Quality.HD) }
    }
    fun surfaceLost(value: Surface?) {
        if (value != null && renderSurface === value) {
            stopTalkback()
            renderSurface = null
            if (features.value.tab == CameraTab.LIVE) {
                connectionJob?.cancel()
                viewModelScope.launch { session.detachSurface() }
            }
        }
    }
    fun changeQuality(quality: Quality) {
        stopTalkback()
        val previous = connectionJob
        connectionJob = viewModelScope.launch { previous?.cancelAndJoin(); session.changeQuality(quality) }
    }
    fun disconnect() {
        stopTalkback()
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
    fun onForeground() { resumed = true; if (!managementWorkRunning && !apWorkRunning) loadRecovery(); expectedHandoff = false; if (apHandoffUntil == 0L) { handoffExpiry?.cancel(); handoffExpiry = null } }
    fun onBackground() {
        onPaused()
        if (features.value.areaPhase == AreaCapturePhase.CAPTURING) featureJob?.cancel()
        clearAreaReference()
        // The document picker owns the foreground until its result callback, including recreation.
        // Stop media before launching it, but retain local authentication and the Wi-Fi binding.
        if (features.value.download == DownloadPhase.CHOOSING_DESTINATION && exportRequest != null) return
        if (apHandoffUntil > android.os.SystemClock.elapsedRealtime()) return
        cancelScan(); connectionJob?.cancel()
        cancelFeature(preserveExport = features.value.download == DownloadPhase.AWAITING_ACCESS)
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

    private data class Recovery(val restart: PendingCameraRestart?, val format: PendingSdFormat?,
        val ap: PendingApEdit?, val calibration: PendingCameraCalibration?)

    /** Read once before any writes, including provisioning, with no stale callback overwrites. */
    private fun loadRecovery() {
        val version = ++recoveryLoadVersion
        session.blockMaintenanceWrites(true)
        mutableFeatures.update { it.copy(restartLoaded = false, pendingLoaded = false, apPendingLoaded = false) }
        viewModelScope.launch {
            try {
                val pending = withContext(Dispatchers.IO) { Recovery(store.loadPendingRestart(), store.loadPendingFormat(), store.loadPendingApEdit(), store.loadPendingCalibration()) }
                if (version != recoveryLoadVersion) return@launch
                mutableFeatures.update { it.copy(pendingRestart = pending.restart, pendingFormat = pending.format,
                    pendingAp = pending.ap, pendingCalibration = pending.calibration, calibrationNeedsCheck = pending.calibration != null,
                    restartLoaded = true, pendingLoaded = true, apPendingLoaded = true, managementStorageFailed = false,
                    apStorageFailed = false, restart = if (pending.restart != null) RestartPhase.UNKNOWN else it.restart,
                    format = if (pending.format != null) SdFormatPhase.UNKNOWN else it.format,
                    apPhase = if (pending.ap != null) ApEditPhase.UNKNOWN else it.apPhase) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == recoveryLoadVersion) mutableFeatures.update { it.copy(managementStorageFailed = true,
                    managementNotice = "Catatan operasi kamera tidak dapat dibaca. Perubahan kamera dinonaktifkan; periksa penyimpanan aplikasi.") }
            } finally { if (version == recoveryLoadVersion) refreshMaintenanceBlock() }
        }
    }

    fun prepareFeatures(profile: CameraProfile) {
        if (features.value.profileId == profile.id) return
        stopTalkback()
        cancelFeature(); clearRecordingStorage()
        mutableFeatures.value = CameraFeatureState(profileId = profile.id)
        loadRecovery()
    }

    fun selectTab(tab: CameraTab): Job? {
        if (tab == features.value.tab) return null
        val previousTab = features.value.tab
        stopTalkback()
        cancelFeature()
        clearRecordingStorage()
        mutableFeatures.update { it.copy(tab = tab, error = null, notice = null, draft = emptyMap(), paused = true) }
        val profileId = features.value.profileId
        connectionJob?.cancel()
        return viewModelScope.launch {
            if (previousTab == CameraTab.LIVE || previousTab == CameraTab.RECORDINGS) session.close()
            else session.stopMedia()
            if (tab == CameraTab.LIVE && session.connectedProfile != null)
                renderSurface?.takeIf { it.isValid }?.let { session.play(it, Quality.HD) }
            else if (tab == CameraTab.SETTINGS && features.value.tab == tab && profileId != null &&
                features.value.profileId == profileId && session.isAuthenticated(profileId)) loadSettings()
        }.also { connectionJob = it }
    }

    private fun launchFeature(stage: DiagnosticStage, requiresCamera: Boolean = true, mutating: Boolean = false, block: suspend () -> Unit) {
        if (managementWorkRunning || apWorkRunning || features.value.pendingAp != null) return
        if (mutating && (features.value.busy || managementBlocked())) return
        if (mutating) {
            managementWorkRunning = true
            mutableFeatures.update { it.copy(busy = true) }
        }
        val previous = featureJob
        val version = ++featureVersion
        val expectedProfile = features.value.profileId
        featureJob = viewModelScope.launch {
            diagnostics.record(stage, DiagnosticStatus.STARTED)
            try {
                previous?.cancelAndJoin()
                currentCoroutineContext().ensureActive()
                mutableFeatures.update { it.copy(busy = true, error = null, notice = null) }
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
                    detail = (error as? RecordingExportException)?.detail,
                    code = generateSequence(error as Throwable) { it.cause }.filterIsInstance<IoTProtocolException>().firstOrNull()?.deviceCode)
                if (version == featureVersion) mutableFeatures.update { it.copy(error = (error as? RecordingExportException)?.userMessage ?: reason.userMessage(),
                    download = if (stage == DiagnosticStage.DOWNLOAD) DownloadPhase.FAILED else it.download,
                    format = if (it.pendingFormat != null) SdFormatPhase.UNKNOWN else it.format) }
            } finally {
                if (mutating) managementWorkRunning = false
                if (version == featureVersion || mutating) mutableFeatures.update { it.copy(busy = apWorkRunning) }
                refreshMaintenanceBlock()
            }
        }
    }

    fun cancelFeature() = cancelFeature(preserveExport = false)

    private fun cancelFeature(preserveExport: Boolean) {
        clearAreaReference()
        featureVersion++
        featureJob?.cancel(); featureJob = null
        clearApSecrets()
        session.invalidateApAccess()
        val abandoned = if (preserveExport) null else pendingExport
        if (!preserveExport) exportRequest = null
        if (abandoned != null) viewModelScope.launch { removeIncompleteExport(abandoned.first) }
        mutableFeatures.update { it.copy(busy = apWorkRunning || managementWorkRunning, apBusy = apWorkRunning, paused = true, draft = emptyMap(), managementDraft = emptyMap(),
            download = if (!preserveExport && it.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS, DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) DownloadPhase.CANCELLED else it.download,
            format = if (it.pendingFormat != null) SdFormatPhase.UNKNOWN else it.format,
            restart = if (it.pendingRestart != null) RestartPhase.UNKNOWN else it.restart,
            apPhase = if (it.pendingAp != null) ApEditPhase.UNKNOWN else it.apPhase) }
    }

    fun loadApHotspot() = launchApWork {
        mutableFeatures.update { it.copy(apPhase = ApEditPhase.CHECKING) }
        val id = features.value.profileId ?: throw CameraFailure(SessionError.AuthenticationFailed)
        session.readApHotspot(id)
        mutableFeatures.update { it.copy(apPhase = ApEditPhase.IDLE, apNotice = "Hotspot aktif dan identitas kamera telah diperiksa.") }
    }

    private fun launchApWork(onFinished: () -> Unit = {}, block: suspend () -> Unit) {
        if (features.value.busy || apWorkRunning || managementWorkRunning || managementBlocked()) return
        apWorkRunning = true
        val version = ++featureVersion
        val profileId = features.value.profileId
        mutableFeatures.update { it.copy(busy = true, apBusy = true, apNotice = null) }
        featureJob = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (version == featureVersion && profileId == features.value.profileId) mutableFeatures.update {
                    it.copy(apPhase = if (it.pendingAp != null) ApEditPhase.UNKNOWN else ApEditPhase.NOT_SENT,
                        apNotice = if (it.pendingAp != null) "Hasil perubahan belum diketahui. Sambungkan dan periksa hotspot; perintah tidak dikirim ulang."
                            else CameraSession.reason(error).userMessage())
                }
            } finally {
                if (version != featureVersion) withContext(NonCancellable) {
                    try {
                        val pending = withContext(Dispatchers.IO) { store.loadPendingApEdit() }
                        if (features.value.profileId == profileId) mutableFeatures.update {
                            it.copy(pendingAp = pending, apPhase = if (pending != null) ApEditPhase.UNKNOWN else ApEditPhase.NOT_SENT)
                        }
                    } catch (_: Exception) { mutableFeatures.update { it.copy(apStorageFailed = true) } }
                }
                apWorkRunning = false
                mutableFeatures.update { it.copy(busy = false, apBusy = false) }
                if (version == featureVersion) {
                    mutableFeatures.update { it.copy(busy = false,
                        apPhase = if (it.pendingAp != null && it.apPhase !in setOf(ApEditPhase.WAITING_WIFI, ApEditPhase.VERIFIED)) ApEditPhase.UNKNOWN else it.apPhase) }
                    if (features.value.apPhase != ApEditPhase.WAITING_WIFI) clearApSecrets()
                }
                refreshMaintenanceBlock()
            }
        }.also { it.invokeOnCompletion { onFinished() } }
    }

    fun saveApHotspot(profile: CameraProfile, ssid: String, password: CharArray) {
        val state = features.value
        val access = session.apAccess.value
        if (state.busy || apWorkRunning || !state.apPendingLoaded || state.apStorageFailed || state.pendingAp != null ||
            state.pendingFormat != null || state.draft.isNotEmpty() || state.profileId != profile.id ||
            !session.canEditAp(profile.id) || access == null || ApHotspotProtocol.inputError(ssid, password, access.ssid) != null) {
            password.fill('\u0000'); return
        }
        clearApSecrets()
        apPassword = password.copyOf()
        apCameraPassword = try { session.apReconnectPassword(profile.id) }
        catch (_: Exception) { clearApSecrets(); password.fill('\u0000'); return }
        val pending = PendingApEdit(java.util.UUID.randomUUID().toString(), profile.id, profile.deviceId, access.ssid, ssid, System.currentTimeMillis())
        launchApWork(onFinished = { password.fill('\u0000') }) {
            val operationVersion = featureVersion
            var started = false
            try {
                mutableFeatures.update { it.copy(apPhase = ApEditPhase.CHECKING) }
                val result = session.configureApHotspot(profile.id, profile.deviceId, ssid, password,
                    beforeWrite = {
                        withContext(NonCancellable + Dispatchers.IO) { store.savePendingApEdit(pending) }
                        if (operationVersion == featureVersion && features.value.profileId == profile.id) mutableFeatures.update { it.copy(pendingAp = pending) }
                        currentCoroutineContext().ensureActive()
                    }, onWriteStarted = {
                        started = true
                        mutableFeatures.update { it.copy(apPhase = ApEditPhase.SENDING) }
                    })
                if (result == ApWriteResult.REJECTED) {
                    withContext(NonCancellable + Dispatchers.IO) { store.clearPendingApEdit(pending.operationId) }
                    mutableFeatures.update { it.copy(pendingAp = null, apPhase = ApEditPhase.REJECTED,
                        apNotice = "Kamera menolak perubahan hotspot. Sambungkan kembali untuk memeriksa akses dan isian.") }
                    return@launchApWork
                }
                mutableFeatures.update { it.copy(apNotice = if (result == ApWriteResult.ACCEPTED)
                    "Perintah diterima kamera. Menunggu verifikasi hotspot." else "Respons terputus. Memeriksa apakah hotspot sudah berubah.") }
                startApHandoff()
                if (Build.VERSION.SDK_INT < 29) mutableFeatures.update { it.copy(apPhase = ApEditPhase.WAITING_WIFI) }
                else reconnectAndVerifyAp(pending)
            } catch (error: Exception) {
                if (!started) withContext(NonCancellable + Dispatchers.IO) {
                    if (store.loadPendingApEdit()?.operationId == pending.operationId) store.clearPendingApEdit(pending.operationId)
                    if (operationVersion == featureVersion && features.value.profileId == profile.id) mutableFeatures.update { it.copy(pendingAp = null, apPhase = ApEditPhase.NOT_SENT) }
                }
                throw error
            } finally { password.fill('\u0000') }
        }
    }

    private fun startApHandoff() {
        apHandoffUntil = android.os.SystemClock.elapsedRealtime() + 120_000
        handoffExpiry?.cancel()
        handoffExpiry = viewModelScope.launch {
            delay(120_000)
            if (apHandoffUntil != 0L) { cancelFeature(); wifi.releaseAp() }
        }
    }

    fun verifyPendingAp(password: CharArray, localPassword: CharArray?) {
        val pending = features.value.pendingAp
        if (features.value.busy || apWorkRunning || pending == null || pending.profileId != features.value.profileId || features.value.apStorageFailed) {
            password.fill('\u0000'); localPassword?.fill('\u0000'); return
        }
        clearApSecrets()
        apPassword = password
        apCameraPassword = localPassword
        launchApWork {
            if (apCameraPassword == null) apCameraPassword = withContext(Dispatchers.IO) { store.password(pending.profileId) }
            if (apCameraPassword == null) throw CameraFailure(SessionError.AuthenticationFailed)
            startApHandoff()
            if (Build.VERSION.SDK_INT < 29) mutableFeatures.update { it.copy(apPhase = ApEditPhase.WAITING_WIFI) }
            else reconnectAndVerifyAp(pending)
        }
    }

    fun resumeManualApVerification() {
        val pending = features.value.pendingAp ?: return
        if (features.value.apPhase != ApEditPhase.WAITING_WIFI || apPassword == null || apCameraPassword == null) return
        launchApWork { reconnectAndVerifyAp(pending) }
    }

    private suspend fun reconnectAndVerifyAp(pending: PendingApEdit) = withTimeout(
        if (apHandoffUntil > 0L) (apHandoffUntil - android.os.SystemClock.elapsedRealtime()).coerceIn(1L, 120_000L) else 120_000L
    ) {
        val cameraPassword = apCameraPassword?.copyOf() ?: throw CameraFailure(SessionError.AuthenticationFailed)
        val hotspotPassword = apPassword?.copyOf() ?: throw CameraFailure(SessionError.InvalidInput)
        try {
            session.close()
            mutableFeatures.update { it.copy(apPhase = ApEditPhase.RECONNECTING) }
            val link = if (Build.VERSION.SDK_INT >= 29) wifi.connectAp(pending.targetSsid, hotspotPassword.copyOf()) else wifi.resolve()
            apHandoffUntil = 0L
            if (wifi.currentSsid(link) != pending.targetSsid) throw CameraFailure(SessionError.NetworkUnavailable)
            mutableFeatures.update { it.copy(apPhase = ApEditPhase.VERIFYING) }
            val found = discovery.scan(link).firstOrNull { it.deviceId == pending.deviceId && it.platformType == 2 }
                ?: throw CameraFailure(SessionError.IdentityMismatch)
            val original = ui.value.profiles.firstOrNull { it.id == pending.profileId && it.deviceId == pending.deviceId }
                ?: throw CameraFailure(SessionError.IdentityMismatch)
            if (features.value.profileId != pending.profileId) throw CancellationException()
            val resolved = original.copy(host = found.host, port = found.port, platformType = found.platformType, discoveryGeneration = found.generation)
            session.connect(resolved, cameraPassword.copyOf())
            val access = session.readApHotspot(original.id)
            if (access.ssid != pending.targetSsid || !session.verifyApReadback(original.id, pending.targetSsid, hotspotPassword))
                throw CameraFailure(SessionError.WifiVerificationTimeout)
            currentCoroutineContext().ensureActive()
            mutableFeatures.update { it.copy(apPhase = ApEditPhase.VERIFIED, apNotice = "Hotspot terverifikasi. Kamera yang sama berhasil tersambung kembali.") }
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    store.updateVerifiedAddress(original.id, original.deviceId, resolved.host, resolved.port)
                    store.clearPendingApEdit(pending.operationId)
                }
                mutableFeatures.update { it.copy(pendingAp = null) }
                reload()
            } catch (_: Exception) {
                mutableFeatures.update { it.copy(apNotice = "Hotspot terverifikasi, tetapi pembaruan profil lokal gagal. Periksa penyimpanan; jangan kirim perubahan lagi.") }
            }
            diagnostics.record(DiagnosticStage.AP_VERIFY)
        } finally { cameraPassword.fill('\u0000'); hotspotPassword.fill('\u0000') }
    }

    fun closeApStatus() {
        val pending = features.value.pendingAp ?: return
        if (features.value.busy || pending.profileId != features.value.profileId) return
        launchApWork {
            withContext(Dispatchers.IO) { store.clearPendingApEdit(pending.operationId) }
            session.invalidateApAccess()
            mutableFeatures.update { it.copy(pendingAp = null, apPhase = ApEditPhase.IDLE,
                apNotice = "Catatan lokal ditutup. Hotspot kamera tidak diubah; sambungkan dan periksa sebelum menyimpan lagi.") }
        }
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
        if (changes.isEmpty() || managementBlocked() || managementWorkRunning) return
        launchFeature(DiagnosticStage.SETTINGS, mutating = true) {
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
        if (managementBlocked() || managementWorkRunning) return
        val before = features.value.settings ?: return
        launchFeature(DiagnosticStage.SETTINGS, mutating = true) {
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
        if (date == features.value.date) return
        cancelFeature()
        mutableFeatures.update { it.copy(date = date, recordings = emptyList(), recordingsLoaded = false, selected = null) }
        viewModelScope.launch { session.stopMedia() }
    }
    fun setEventsOnly(value: Boolean) {
        if (value == features.value.eventsOnly) return
        cancelFeature()
        mutableFeatures.update { it.copy(eventsOnly = value, recordings = emptyList(), recordingsLoaded = false, selected = null) }
        viewModelScope.launch { session.stopMedia() }
    }

    fun setRecordingOrder(order: RecordingOrder) {
        mutableFeatures.update { it.copy(recordingOrder = order) }
    }

    private fun clearRecordingStorage() {
        storageVersion++
        storageJob?.cancel(); storageJob = null
        mutableFeatures.update { it.copy(recordingStorage = null, storageLoading = false, storageError = null) }
    }

    fun loadRecordingStorage() {
        val profileId = features.value.profileId ?: return
        if (features.value.tab != CameraTab.RECORDINGS || !session.isAuthenticated(profileId) || features.value.pendingAp != null) return
        storageJob?.cancel()
        val version = ++storageVersion
        mutableFeatures.update { it.copy(recordingStorage = null, storageLoading = true, storageError = null) }
        storageJob = viewModelScope.launch {
            try {
                val storage = session.readRecordingStorage()
                if (version == storageVersion && features.value.profileId == profileId && session.isAuthenticated(profileId))
                    mutableFeatures.update { it.copy(recordingStorage = storage) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == storageVersion) mutableFeatures.update {
                    it.copy(storageError = "Kapasitas kartu SD belum tersedia. Muat ulang untuk memeriksa kembali.")
                }
            } finally {
                if (version == storageVersion) mutableFeatures.update { it.copy(storageLoading = false) }
            }
        }
    }

    fun loadRecordings() {
        val date = features.value.date
        val events = features.value.eventsOnly
        val zone = ZoneId.systemDefault()
        val profileId = features.value.profileId
        val version = featureVersion + 1
        loadRecordingStorage()
        launchFeature(DiagnosticStage.RECORDINGS) {
            session.stopMedia()
            val rows = session.listRecordings(date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), events)
            currentCoroutineContext().ensureActive()
            if (version == featureVersion && features.value.profileId == profileId &&
                features.value.date == date && features.value.eventsOnly == events)
                mutableFeatures.update { it.copy(recordings = rows, recordingsLoaded = true) }
        }
    }
    fun returnToRecordingList() {
        cancelFeature()
        mutableFeatures.update { it.copy(selected = null, paused = true) }
        viewModelScope.launch { session.stopMedia() }
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
        if (features.value.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS,
                DownloadPhase.DOWNLOADING, DownloadPhase.SAVING)) return
        mutableFeatures.update { it.copy(paused = true) }
        viewModelScope.launch { session.stopMedia() }
    }

    private fun exportSegment(request: Bundle) = RecordingSegment(request.getLong("start"), request.getLong("end"),
        request.getInt("kind"), request.getLong("source"))

    private fun restoredExportState(): CameraFeatureState {
        val request = exportRequest ?: return CameraFeatureState()
        val profileId = request.getString("profileId") ?: return CameraFeatureState()
        val segment = exportSegment(request)
        if (profileId.isBlank() || segment.startMs < 0 || segment.endMs <= segment.startMs ||
            segment.durationMs !in 1..3600000 || segment.kind !in setOf(1, 2, 8) ||
            segment.sourceStartMs !in 0..segment.startMs) {
            exportRequest = null
            return CameraFeatureState()
        }
        return CameraFeatureState(profileId = profileId, tab = CameraTab.RECORDINGS,
            selected = if (!request.containsKey("showPlayer") || request.getBoolean("showPlayer")) segment else null, paused = true,
            date = java.time.Instant.ofEpochMilli(segment.startMs).atZone(ZoneId.systemDefault()).toLocalDate(),
            download = if (request.getString("uri") == null) DownloadPhase.CHOOSING_DESTINATION else DownloadPhase.AWAITING_ACCESS,
            notice = if (request.getString("uri") == null) null else "Lokasi penyimpanan sudah dipilih. Sambungkan kamera untuk melanjutkan.")
    }

    suspend fun beginRecordingExport(recording: RecordingSegment): Boolean {
        val state = features.value
        val profileId = state.profileId ?: return false
        if (state.busy || exportRequest != null || state.pendingAp != null || state.pendingFormat != null ||
            (state.selected != recording && recording !in state.recordings)) return false
        storageJob?.cancel()
        mutableFeatures.update { it.copy(busy = true, paused = true, error = null) }
        try {
            session.stopMedia()
            currentCoroutineContext().ensureActive()
            if (features.value.profileId != profileId || features.value.tab != CameraTab.RECORDINGS) return false
            exportRequest = Bundle().apply {
                putString("profileId", profileId); putLong("start", recording.startMs); putLong("end", recording.endMs)
                putInt("kind", recording.kind); putLong("source", recording.sourceStartMs)
                putBoolean("showPlayer", state.selected != null)
            }
            mutableFeatures.update { it.copy(download = DownloadPhase.CHOOSING_DESTINATION, notice = null) }
            return true
        } finally { mutableFeatures.update { it.copy(busy = false) } }
    }

    fun completeRecordingExport(uri: Uri?) {
        val request = exportRequest
        if (request == null) {
            // A result can arrive after the user changed profiles or cancelled the pending request.
            if (uri != null) viewModelScope.launch { removeIncompleteExport(uri) }
            return
        }
        if (uri == null) {
            exportRequest = null
            mutableFeatures.update { it.copy(download = DownloadPhase.IDLE) }
            return
        }
        if (request.getString("profileId") != features.value.profileId || features.value.tab != CameraTab.RECORDINGS) {
            exportRequest = null
            viewModelScope.launch { removeIncompleteExport(uri) }
            return
        }
        exportRequest = Bundle(request).apply { putString("uri", uri.toString()) }
        mutableFeatures.update { it.copy(download = DownloadPhase.AWAITING_ACCESS, paused = true,
            notice = if (!session.isAuthenticated(features.value.profileId!!)) "Lokasi penyimpanan sudah dipilih. Sambungkan kamera untuk melanjutkan." else null) }
        if (session.isAuthenticated(features.value.profileId!!)) startExport()
    }

    private suspend fun removeIncompleteExport(uri: Uri) {
        val removed = withContext(NonCancellable + Dispatchers.IO) {
            runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }.getOrDefault(false)
        }
        if (!removed) message("File tujuan penyalinan mungkin belum lengkap. Hapus file tersebut sebelum mencoba lagi.")
    }

    private fun storageFull(error: Throwable): Boolean = generateSequence(error) { it.cause }.any {
        it is RecordingStorageFullException || it is ErrnoException && it.errno == OsConstants.ENOSPC
    }

    private fun exportFailure(error: Exception, destination: Boolean): RecordingExportException {
        if (error is CancellationException) throw error
        if (error is RecordingExportException) return error
        val message = when {
            storageFull(error) -> "Ruang penyimpanan lokal tidak cukup. Kosongkan ruang lalu coba lagi."
            destination -> "File tujuan tidak dapat ditulis. Periksa akses dan ruang penyimpanan, lalu pilih lokasi lain."
            else -> "Video tidak dapat dibuat menjadi MP4. Rekaman mungkin rusak atau formatnya tidak didukung."
        }
        return RecordingExportException(SessionError.ExportFailed,
            if (destination) DiagnosticDetail.DESTINATION_WRITE else DiagnosticDetail.MP4_SAMPLE, message, error)
    }

    private fun startExport() {
        val (uri, recording) = pendingExport ?: return
        val profileId = features.value.profileId
        val version = featureVersion + 1
        launchFeature(DiagnosticStage.DOWNLOAD, requiresCamera = false) {
            val app = getApplication<Application>()
            exportRequest = null
            try {
                if (session.connectedProfile?.id != profileId || features.value.profileId != profileId)
                    throw CameraFailure(SessionError.AuthenticationFailed)
                mutableFeatures.update { it.copy(download = DownloadPhase.DOWNLOADING, downloadBytes = 0) }
                withContext(Dispatchers.IO) {
                    val writer = try { RecordingExporter(app.cacheDir) } catch (error: Exception) { throw exportFailure(error, false) }
                    writer.use { exporter ->
                        try {
                            session.downloadRecording(recording) { frame, width, height ->
                                currentCoroutineContext().ensureActive()
                                try { exporter.write(frame, width, height) } catch (error: Exception) { throw exportFailure(error, false) }
                                if (version == featureVersion) mutableFeatures.update { it.copy(downloadBytes = exporter.bytesWritten) }
                            }
                        } catch (error: Exception) {
                            if (error is CancellationException || error is RecordingExportException) throw error
                            throw RecordingExportException(CameraSession.reason(error),
                                diagnostics.events.value.lastOrNull { it.stage == DiagnosticStage.DOWNLOAD && it.status == DiagnosticStatus.FAILED }?.detail
                                    ?: DiagnosticDetail.REPLAY_FRAME, recordingDownloadMessage(error), error)
                        }
                        diagnostics.record(DiagnosticStage.DOWNLOAD, DiagnosticStatus.STARTED, detail = DiagnosticDetail.MP4_FINALIZE)
                        val file = try { exporter.finish() } catch (error: Exception) {
                            val failure = exportFailure(error, false)
                            throw RecordingExportException(failure.reason, DiagnosticDetail.MP4_FINALIZE, failure.userMessage, error)
                        }
                        currentCoroutineContext().ensureActive()
                        if (version == featureVersion) mutableFeatures.update { it.copy(download = DownloadPhase.SAVING) }
                        diagnostics.record(DiagnosticStage.DOWNLOAD, DiagnosticStatus.STARTED, detail = DiagnosticDetail.DESTINATION_WRITE)
                        try {
                            copyRecording(file, app.contentResolver.openOutputStream(uri, "wt")
                                ?: throw java.io.IOException("No destination"))
                        } catch (error: Exception) { throw exportFailure(error, true) }
                        diagnostics.record(DiagnosticStage.DOWNLOAD, DiagnosticStatus.STARTED, detail = DiagnosticDetail.DESTINATION_VERIFY)
                        try {
                            verifyRecordingCopy(file, app.contentResolver.openInputStream(uri)
                                ?: throw java.io.IOException("Destination cannot be read"))
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            throw RecordingExportException(SessionError.ExportFailed, DiagnosticDetail.DESTINATION_VERIFY,
                                "File tujuan belum dapat dibaca kembali secara utuh. Pilih lokasi penyimpanan lain lalu coba lagi.", error)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                diagnostics.record(DiagnosticStage.DOWNLOAD, detail = DiagnosticDetail.DESTINATION_VERIFY)
                mutableFeatures.update { it.copy(download = DownloadPhase.COMPLETE, notice = "Rekaman video tersimpan tanpa audio.") }
            } catch (error: Exception) {
                // Cancellation changes the feature generation; the storage warning must still reach the user.
                removeIncompleteExport(uri)
                if (error is CancellationException) throw error
                throw error
            }
        }
    }

    fun formatSdCard(profile: CameraProfile) {
        val state = features.value
        val settings = state.settings ?: return
        if (!CameraSettingsProtocol.FORMAT_STATUS_VERIFIED || state.busy || !state.pendingLoaded || state.pendingFormat != null || state.draft.isNotEmpty() ||
            state.download in setOf(DownloadPhase.CHOOSING_DESTINATION, DownloadPhase.AWAITING_ACCESS, DownloadPhase.DOWNLOADING, DownloadPhase.SAVING) ||
            settings.int(CameraSettingsProtocol.SD_STATUS) !in setOf(1, 2, 3, 8) ||
            settings.int(CameraSettingsProtocol.SD_FORMAT_STATE) == null || state.profileId != profile.id ||
            !session.isAuthenticated(profile.id) || session.connectedProfile?.deviceId != profile.deviceId) return
        launchFeature(DiagnosticStage.SD_FORMAT, mutating = true) {
            val version = featureVersion
            val operationId = java.util.UUID.randomUUID().toString()
            var pending: PendingSdFormat? = null
            var started = false
            var rejected = false
            try {
                try {
                    session.sendSdFormat(profile.id, profile.deviceId, operationId, beforeWrite = {
                        withContext(NonCancellable + Dispatchers.IO) { pending = store.savePendingFormat(profile, operationId) }
                        if (version == featureVersion && features.value.profileId == profile.id)
                            mutableFeatures.update { it.copy(pendingFormat = pending, format = SdFormatPhase.SENDING) }
                    }, onWriteStarted = {
                        started = true
                        if (version == featureVersion && features.value.profileId == profile.id) {
                            clearRecordingStorage()
                            mutableFeatures.update { it.copy(recordingsLoaded = false, recordings = emptyList(), selected = null, paused = true) }
                        }
                    })
                } catch (error: Exception) {
                    // Only a correlated response to the action can prove rejection, not a later GET error.
                    rejected = started && error is IoTProtocolException && error.kind in setOf(IoTFailure.DeviceRejected,
                        IoTFailure.PermissionDenied, IoTFailure.AuthenticationFailed, IoTFailure.SessionExpired)
                    throw error
                }
                currentCoroutineContext().ensureActive()
                if (version != featureVersion || features.value.profileId != profile.id) throw CancellationException()
                mutableFeatures.update { it.copy(format = SdFormatPhase.CHECKING) }
                withTimeoutOrNull(120_000) {
                    while (true) {
                        delay(2000)
                        val marker = pending ?: throw CameraFailure(SessionError.StorageUnavailable)
                        updateFormatReadback(marker, session.readSdFormatStatus(marker.profileId, marker.deviceId), version)
                    }
                }
                mutableFeatures.update { it.copy(format = SdFormatPhase.UNKNOWN,
                    notice = "Pemantauan format berakhir. Hasil akhir belum diketahui. Pilih Periksa status kartu; perintah tidak dikirim ulang.") }
            } finally {
                withContext(NonCancellable) {
                    try {
                        val restored = withContext(Dispatchers.IO) {
                            if (pending != null && (!started || rejected)) store.clearPendingFormat(operationId)
                            store.loadPendingFormat()
                        }
                        mutableFeatures.update {
                            val current = version == featureVersion && it.profileId == profile.id
                            it.copy(pendingFormat = restored, pendingLoaded = true, format = when {
                                restored != null -> SdFormatPhase.UNKNOWN
                                current && rejected -> SdFormatPhase.REJECTED
                                current && !started -> SdFormatPhase.NOT_SENT
                                else -> it.format
                            }, notice = if (current && !started) "Perintah format tidak dikirim." else it.notice)
                        }
                    } catch (_: Exception) {
                        mutableFeatures.update { it.copy(managementStorageFailed = true, pendingLoaded = false,
                            notice = "Catatan format tidak dapat diperbarui. Perubahan kamera dinonaktifkan; periksa penyimpanan aplikasi.") }
                    }
                    refreshMaintenanceBlock()
                }
            }
        }
    }

    private fun updateFormatReadback(pending: PendingSdFormat, status: CameraSettings, version: Int) {
        if (version != featureVersion || features.value.profileId != pending.profileId ||
            features.value.pendingFormat?.operationId != pending.operationId) return
        val ids = CameraSettingsProtocol.sdFormatProperties.toSet()
        val formatting = CameraSettingsProtocol.formatInProgress(status)
        mutableFeatures.update { it.copy(settings = CameraSettings(
            (it.settings?.values.orEmpty() - ids) + status.values,
            (it.settings?.rejected.orEmpty() - ids) + status.rejected),
            format = if (formatting) SdFormatPhase.FORMATTING else SdFormatPhase.UNKNOWN,
            notice = if (formatting) "Kamera melaporkan kartu sedang diformat. Hasil akhir belum terverifikasi." else
                "Status kartu diperbarui. Hasil format sebelumnya belum dapat dipastikan; perintah tidak dikirim ulang.") }
    }

    fun checkPendingFormat() {
        val pending = features.value.pendingFormat ?: return
        if (features.value.busy || pending.profileId != features.value.profileId ||
            !session.isAuthenticated(pending.profileId) || session.connectedProfile?.deviceId != pending.deviceId) return
        launchFeature(DiagnosticStage.SD_FORMAT) {
            val version = featureVersion
            mutableFeatures.update { it.copy(format = SdFormatPhase.CHECKING) }
            updateFormatReadback(pending, session.readSdFormatStatus(pending.profileId, pending.deviceId), version)
        }
    }

    fun acknowledgeFormat(operationId: String) {
        val pending = features.value.pendingFormat ?: return
        if (features.value.busy || pending.profileId != features.value.profileId || pending.operationId != operationId) return
        launchFeature(DiagnosticStage.SD_FORMAT, requiresCamera = false) {
            val version = featureVersion
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) { store.clearPendingFormat(pending.operationId) }
                if (version == featureVersion && features.value.pendingFormat?.operationId == pending.operationId)
                    mutableFeatures.update { it.copy(pendingFormat = null, format = SdFormatPhase.UNKNOWN,
                        notice = "Catatan lokal ditutup. Hasil format tetap belum diketahui; proses di kamera tidak dibatalkan.") }
                else loadRecovery()
            }
        }
    }

    fun managementBlocked(): Boolean = features.value.let {
        !it.restartLoaded || !it.pendingLoaded || it.managementStorageFailed || it.pendingRestart != null ||
            it.pendingFormat != null || it.calibrationNeedsCheck
    }
    private fun refreshMaintenanceBlock() { session.blockMaintenanceWrites(managementBlocked() || !features.value.apPendingLoaded || features.value.pendingAp != null || features.value.apStorageFailed) }

    private fun launchManagement(group: ManagementGroup, mutating: Boolean = false, block: suspend () -> Unit) {
        val state = features.value
        if (managementWorkRunning || apWorkRunning || state.busy || state.pendingAp != null || mutating && managementBlocked()) return
        val profileId = state.profileId ?: return
        val version = ++featureVersion
        managementWorkRunning = true
        mutableFeatures.update { it.copy(busy = true, managementLoading = group, managementNotice = null,
            managementErrors = it.managementErrors - group) }
        val stage = when (group) {
            ManagementGroup.ALARM -> DiagnosticStage.ALARM
            ManagementGroup.LIGHT -> DiagnosticStage.LIGHT_SCHEDULE
            ManagementGroup.MOTOR -> DiagnosticStage.PTZ_CRUISE
            ManagementGroup.DEVICE -> DiagnosticStage.DEVICE_INFO
        }
        featureJob = viewModelScope.launch {
            diagnostics.record(stage, DiagnosticStatus.STARTED)
            try {
                if (!session.isAuthenticated(profileId)) throw CameraFailure(SessionError.AuthenticationFailed)
                block()
                diagnostics.record(stage)
            } catch (cancelled: CancellationException) { diagnostics.record(stage, DiagnosticStatus.CANCELLED); throw cancelled }
            catch (error: Exception) {
                diagnostics.record(stage, DiagnosticStatus.FAILED, reason = CameraSession.reason(error), code = (error as? IoTProtocolException)?.deviceCode)
                if (version == featureVersion && features.value.profileId == profileId) mutableFeatures.update {
                    it.copy(managementErrors = it.managementErrors + (group to CameraSession.reason(error).userMessage()))
                }
            } finally {
                managementWorkRunning = false
                if (version == featureVersion && features.value.profileId == profileId) mutableFeatures.update {
                    it.copy(busy = false, managementLoading = null)
                }
                else mutableFeatures.update { it.copy(busy = apWorkRunning, managementLoading = null) }
                refreshMaintenanceBlock()
            }
        }
    }

    fun loadManagement(group: ManagementGroup, automatic: Boolean = false) = launchManagement(group) {
        val selected = ui.value.profiles.firstOrNull { it.id == features.value.profileId } ?: throw CameraFailure(SessionError.IdentityMismatch)
        if (session.connectedProfile?.deviceId != selected.deviceId) throw CameraFailure(SessionError.IdentityMismatch)
        val snapshot = try { session.readManagement(selected.id, group) }
        catch (cancelled: CancellationException) {
            if (automatic) automaticMotorAttempt = 0L
            throw cancelled
        }
        currentCoroutineContext().ensureActive()
        if (features.value.profileId != selected.id || session.authenticationRevision.value != snapshot.revision ||
            !session.isAuthenticated(selected.id)) throw CameraFailure(SessionError.AuthenticationFailed)
        mutableFeatures.update { it.copy(management = it.management + (group to snapshot)) }
        if (group == ManagementGroup.ALARM && features.value.areaImage != null && !areaDraftReady()) clearAreaReference()
    }

    fun draftManagement(group: ManagementGroup, id: Int, value: Any) {
        if (features.value.busy || managementBlocked()) return
        val snapshot = features.value.management[group] ?: return
        if (id !in CameraManagementProtocol.ids(group) || id !in snapshot.settings.values) return
        mutableFeatures.update { state ->
            val draft = state.managementDraft.toMutableMap()
            if (CameraManagementProtocol.same(id, snapshot.settings.values[id], value)) draft.remove(id) else draft[id] = value
            state.copy(managementDraft = draft.toMap())
        }
    }

    fun saveManagement(group: ManagementGroup) {
        val before = features.value.management[group] ?: return
        val changes = features.value.managementDraft.filterKeys { it in CameraManagementProtocol.ids(group) }
        if (changes.isEmpty()) return
        launchManagement(group, mutating = true) {
            try {
                val after = session.saveManagement(before, changes)
                mutableFeatures.update { it.copy(management = it.management + (group to after),
                    managementDraft = it.managementDraft - changes.keys, managementNotice = "Pengaturan tersimpan dan telah dibaca kembali.") }
                if (CameraManagementProtocol.ALARM_AREA in changes) clearAreaReference()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val after = try { session.readManagement(before.profileId, group) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
                val matched = changes.filter { (id, value) -> CameraManagementProtocol.same(id, value, after?.settings?.values?.get(id)) }.keys
                mutableFeatures.update { it.copy(management = if (after != null) it.management + (group to after) else it.management,
                    managementDraft = it.managementDraft - matched,
                    managementNotice = "Hasil penyimpanan diperiksa kembali. Perubahan yang belum cocok tetap ditampilkan; tidak ada pengiriman ulang.") }
                if (CameraManagementProtocol.ALARM_AREA in matched) clearAreaReference()
                throw error
            }
        }
    }

    fun discardManagement(group: ManagementGroup) {
        if (features.value.busy) return
        mutableFeatures.update { it.copy(managementDraft = it.managementDraft - CameraManagementProtocol.ids(group).toSet(),
            managementErrors = it.managementErrors - group, managementNotice = null) }
        if (group == ManagementGroup.ALARM) clearAreaReference()
    }

    private fun clearAreaReference() {
        session.invalidateAreaReference()
        areaSurface?.cancel(); areaSurface = null
        mutableFeatures.update { it.copy(areaImage = null,
            areaPhase = if (CameraManagementProtocol.ALARM_AREA in it.managementDraft) AreaCapturePhase.ERROR else AreaCapturePhase.IDLE,
            areaError = if (CameraManagementProtocol.ALARM_AREA in it.managementDraft) "Referensi area tidak berlaku lagi. Ambil gambar ulang sebelum menyimpan." else null) }
        // Published bitmaps are released by dropping ownership; recycling a bitmap still in a draw can crash Android.
    }

    fun areaDraftReady(): Boolean {
        val reference = session.areaReference.value ?: return false
        val baseline = features.value.management[ManagementGroup.ALARM]?.settings?.values?.get(CameraManagementProtocol.ALARM_AREA) as? CameraAlarmArea
            ?: return false
        return session.canUseAreaReference(reference) && CameraAreaProtocol.sameBaseline(reference.baseline, baseline)
    }

    fun requestAreaReference() {
        val state = features.value
        val before = state.management[ManagementGroup.ALARM] ?: return
        if (managementWorkRunning || apWorkRunning || state.busy || state.pendingAp != null || managementBlocked() || state.profileId != before.profileId) return
        if (!session.canCaptureArea(before.profileId)) {
            mutableFeatures.update { it.copy(areaError = "Editor memerlukan satu tampilan datar yang terkonfirmasi dan akses kamera tersambung.") }
            return
        }
        val waiting = CompletableDeferred<SurfaceView>()
        areaSurface = waiting
        val requestId = featureVersion + 1
        mutableFeatures.update { it.copy(areaPhase = AreaCapturePhase.CAPTURING, areaImage = null, areaError = null, areaRequestId = requestId) }
        launchManagement(ManagementGroup.ALARM, mutating = true) {
            var bitmap: Bitmap? = null
            try {
                val view = withTimeout(3000) { waiting.await() }
                val reference = session.captureAlarmArea(before, view.holder.surface) { geometry ->
                    bitmap = VideoFrameCapture.copy(view, geometry, session::awaitAreaFrame)
                }
                currentCoroutineContext().ensureActive()
                if (featureVersion != requestId || features.value.profileId != before.profileId || !session.canUseAreaReference(reference))
                    throw CameraFailure(SessionError.AuthenticationFailed)
                mutableFeatures.update { it.copy(areaPhase = AreaCapturePhase.EDITING, areaImage = CameraAreaImage(reference, bitmap!!)) }
                bitmap = null
            } catch (error: Exception) {
                val cancelled = error is CancellationException && error !is TimeoutCancellationException
                if (featureVersion == requestId) mutableFeatures.update { it.copy(areaImage = null,
                    areaPhase = if (cancelled) AreaCapturePhase.IDLE else AreaCapturePhase.ERROR,
                    areaError = if (cancelled) null else "Gambar referensi gagal atau pemetaannya belum pasti. Ambil ulang setelah kamera siap.") }
                if (error is TimeoutCancellationException) throw CameraFailure(SessionError.Timeout)
                throw error
            } finally {
                if (areaSurface === waiting) areaSurface = null
                bitmap?.recycle()
            }
        }
    }

    fun areaSurfaceReady(requestId: Int, view: SurfaceView) {
        if (features.value.areaRequestId == requestId && features.value.areaPhase == AreaCapturePhase.CAPTURING) areaSurface?.complete(view)
    }

    fun cancelAreaCapture(requestId: Int) {
        if (features.value.areaRequestId != requestId || features.value.areaPhase != AreaCapturePhase.CAPTURING) return
        featureJob?.cancel()
        clearAreaReference()
    }

    fun useArea(reference: AreaReference, area: CameraAlarmArea) {
        if (features.value.busy || !areaDraftReady() || session.areaReference.value !== reference || !CameraAreaProtocol.valid(area) ||
            area.rows != reference.baseline.rows || area.columns != reference.baseline.columns) return
        draftManagement(ManagementGroup.ALARM, CameraManagementProtocol.ALARM_AREA, area.copy(source = reference.baseline.source))
        mutableFeatures.update { it.copy(areaImage = null, areaPhase = AreaCapturePhase.STAGED) }
        if (CameraManagementProtocol.ALARM_AREA !in features.value.managementDraft) clearAreaReference()
    }

    fun cancelAreaEdit() {
        clearAreaReference()
    }

    fun presetAction(slot: Int, operation: Int) {
        val before = features.value.management[ManagementGroup.MOTOR] ?: return
        if (features.value.managementDraft.isNotEmpty()) return
        launchManagement(ManagementGroup.MOTOR, mutating = true) {
            val after = session.presetAction(before, slot, operation, java.util.UUID.randomUUID().toString())
            mutableFeatures.update { it.copy(management = it.management + (ManagementGroup.MOTOR to after),
                managementNotice = if (operation == 2) "Perintah ke posisi diterima. Posisi motor belum dapat diverifikasi." else if (operation == 1) "Perintah simpan diterima dan slot terisi dibaca kembali. Posisi tersimpan belum diverifikasi." else "Preset tidak lagi tercantum pada kamera.") }
        }
    }

    fun calibrateCamera() {
        val before = features.value.management[ManagementGroup.MOTOR] ?: return
        if (features.value.managementDraft.isNotEmpty() || features.value.draft.isNotEmpty()) return
        val pending = PendingCameraCalibration(java.util.UUID.randomUUID().toString(), before.profileId, before.deviceId, System.currentTimeMillis())
        launchManagement(ManagementGroup.MOTOR, mutating = true) {
            try {
                session.calibrateCamera(before, pending.operationId) {
                    try { withContext(NonCancellable + Dispatchers.IO) { store.savePendingCalibration(pending) } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { throw CameraFailure(SessionError.StorageUnavailable) }
                    mutableFeatures.update { it.copy(pendingCalibration = pending, calibrationNeedsCheck = true) }
                }
                mutableFeatures.update { it.copy(managementNotice = "Perintah kalibrasi diterima. Status selesai belum dapat dibaca; periksa bahwa gerak kamera sudah berhenti.") }
            } catch (error: IoTProtocolException) {
                if (error.kind in setOf(IoTFailure.DeviceRejected, IoTFailure.PermissionDenied, IoTFailure.AuthenticationFailed)) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (store.loadPendingCalibration()?.operationId == pending.operationId) store.clearPendingCalibration(pending.operationId)
                    }
                }
                throw error
            } finally {
                // Also recover a marker written while cancellation was pending, before a UI update ran.
                withContext(NonCancellable) {
                    val restored = try { withContext(Dispatchers.IO) { store.loadPendingCalibration() } }
                        catch (_: Exception) { mutableFeatures.update { it.copy(managementStorageFailed = true) }; pending }
                    mutableFeatures.update { it.copy(pendingCalibration = restored, calibrationNeedsCheck = restored != null) }
                }
            }
        }
    }
    fun acknowledgeCalibration() {
        val pending = features.value.pendingCalibration ?: return
        if (features.value.busy || pending.profileId != features.value.profileId || managementWorkRunning || apWorkRunning) return
        managementWorkRunning = true
        mutableFeatures.update { it.copy(busy = true) }
        featureJob = viewModelScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) { store.clearPendingCalibration(pending.operationId) }
                mutableFeatures.update { it.copy(pendingCalibration = null, calibrationNeedsCheck = false,
                    management = it.management - ManagementGroup.MOTOR,
                    managementNotice = "Pemeriksaan gerak ditutup oleh pengguna. Muat ulang status sebelum aksi motor berikutnya.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFeatures.update { it.copy(managementNotice = "Catatan kalibrasi tidak dapat ditutup. Periksa penyimpanan lalu coba lagi.") } }
            finally {
                managementWorkRunning = false
                mutableFeatures.update { it.copy(busy = false) }
                refreshMaintenanceBlock()
            }
        }
    }

    fun restartCamera(profile: CameraProfile) {
        val state = features.value
        if (state.draft.isNotEmpty() || state.managementDraft.isNotEmpty()) return
        launchManagement(ManagementGroup.DEVICE, mutating = true) {
            val password = session.restartPassword(profile.id)
            val pending = PendingCameraRestart(java.util.UUID.randomUUID().toString(), profile.id, profile.deviceId, System.currentTimeMillis())
            try {
                try {
                    session.restartCamera(profile.id, profile.deviceId, pending.operationId) {
                        try { withContext(NonCancellable + Dispatchers.IO) { store.savePendingRestart(pending) } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { throw CameraFailure(SessionError.StorageUnavailable) }
                        mutableFeatures.update { it.copy(pendingRestart = pending, restart = RestartPhase.SENDING) }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (features.value.pendingRestart == null) {
                        mutableFeatures.update { it.copy(restart = RestartPhase.NOT_SENT) }; throw error
                    }
                    if (error is IoTProtocolException && error.kind in setOf(IoTFailure.DeviceRejected, IoTFailure.PermissionDenied, IoTFailure.AuthenticationFailed)) {
                        withContext(NonCancellable + Dispatchers.IO) { store.clearPendingRestart(pending.operationId) }
                        mutableFeatures.update { it.copy(pendingRestart = null, restart = RestartPhase.REJECTED) }; throw error
                    }
                    // Partial write, EOF, malformed response and timeout are ambiguous, not a retry signal.
                }
                mutableFeatures.update { it.copy(restart = RestartPhase.WAITING, management = emptyMap()) }
                withTimeout(120_000) {
                    while (isActive) {
                        delay(2000)
                        val found = discovery.scan().firstOrNull { it.deviceId == pending.deviceId && it.platformType == 2 } ?: continue
                        currentCoroutineContext().ensureActive()
                        if (features.value.profileId != pending.profileId) throw CancellationException()
                        val resolved = profile.copy(host = found.host, port = found.port, platformType = found.platformType, discoveryGeneration = found.generation)
                        try { session.connect(resolved, password.copyOf()) }
                        catch (error: CameraFailure) { if (error.reason == SessionError.AuthenticationFailed) throw error else continue }
                        withContext(NonCancellable + Dispatchers.IO) {
                            store.updateVerifiedAddress(profile.id, profile.deviceId, found.host, found.port)
                            store.clearPendingRestart(pending.operationId)
                        }
                        mutableFeatures.update { it.copy(pendingRestart = null, restart = RestartPhase.RECONNECTED,
                            managementNotice = "Kamera yang sama tersambung kembali. Bukti reboot tidak tersedia; restart belum dapat dipastikan.") }
                        break
                    }
                }
            } finally {
                password.fill('\u0000')
                withContext(NonCancellable) {
                    val restored = try { withContext(Dispatchers.IO) { store.loadPendingRestart() } }
                        catch (_: Exception) { mutableFeatures.update { it.copy(managementStorageFailed = true) }; pending }
                    mutableFeatures.update { it.copy(pendingRestart = restored,
                        restart = if (restored != null) RestartPhase.UNKNOWN else it.restart,
                        managementNotice = if (restored != null) "Hasil restart belum diketahui. Sambungkan kamera lalu pilih Periksa kamera; perintah tidak dikirim ulang." else it.managementNotice) }
                }
            }
        }
    }

    fun verifyPendingRestart() {
        val pending = features.value.pendingRestart ?: return
        if (pending.profileId != features.value.profileId || session.connectedProfile?.deviceId != pending.deviceId) return
        launchManagement(ManagementGroup.DEVICE) {
            val snapshot = session.readManagement(pending.profileId, ManagementGroup.DEVICE)
            withContext(NonCancellable + Dispatchers.IO) { store.clearPendingRestart(pending.operationId) }
            mutableFeatures.update { it.copy(pendingRestart = null, restart = RestartPhase.RECONNECTED,
                management = it.management + (ManagementGroup.DEVICE to snapshot),
                managementNotice = "Kamera yang sama merespons. Restart belum terverifikasi; tidak ada perintah baru yang dikirim.") }
        }
    }

    fun closePendingRestart() {
        val pending = features.value.pendingRestart ?: return
        if (features.value.busy || pending.profileId != features.value.profileId) return
        if (managementWorkRunning || apWorkRunning) return
        managementWorkRunning = true
        mutableFeatures.update { it.copy(busy = true) }
        featureJob = viewModelScope.launch {
            try {
                withContext(NonCancellable + Dispatchers.IO) { store.clearPendingRestart(pending.operationId) }
                mutableFeatures.update { it.copy(pendingRestart = null, restart = RestartPhase.UNKNOWN,
                    managementNotice = "Catatan lokal ditutup. Hasil restart kamera tetap belum diketahui; tidak ada perintah baru yang dikirim.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableFeatures.update { it.copy(managementNotice = "Catatan restart tidak dapat ditutup. Periksa penyimpanan lalu coba lagi.") } }
            finally {
                managementWorkRunning = false
                mutableFeatures.update { it.copy(busy = false) }
                refreshMaintenanceBlock()
            }
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
    override fun onCleared() { featureJob?.cancel(); clearAreaReference(); clearApSecrets(); provisioning.cancel(); wifi.dispose(); session.dispose(); super.onCleared() }
}
