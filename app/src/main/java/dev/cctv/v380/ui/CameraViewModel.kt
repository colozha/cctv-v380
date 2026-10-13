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

    init { viewModelScope.launch { reload() } }

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
                renderSurface?.takeIf { it.isValid }?.let { session.play(it, Quality.HD) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(CameraSession.reason(error).userMessage()) }
            finally { entered?.fill('\u0000') }
        }.also { job -> job.invokeOnCompletion { entered?.fill('\u0000') } }
    }

    fun surfaceReady(value: Surface, profileId: String) {
        renderSurface = value
        if (session.connectedProfile?.id == profileId && session.state.value is SessionState.Ready)
            connectionJob = viewModelScope.launch { session.play(value, Quality.HD) }
    }
    fun surfaceLost(value: Surface?) {
        if (value != null && renderSurface === value) {
            renderSurface = null
            connectionJob?.cancel()
            viewModelScope.launch { session.detachSurface() }
        }
    }
    fun changeQuality(quality: Quality) {
        val previous = connectionJob
        connectionJob = viewModelScope.launch { previous?.cancelAndJoin(); session.changeQuality(quality) }
    }
    fun disconnect() {
        connectionJob?.cancel()
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
    override fun onCleared() { provisioning.cancel(); wifi.releaseAp(); session.dispose(); super.onCleared() }
}
