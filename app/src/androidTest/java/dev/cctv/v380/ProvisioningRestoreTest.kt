package dev.cctv.v380

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.discovery.LanDiscovery
import dev.cctv.v380.provisioning.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.*
import dev.cctv.v380.ui.ProfileStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ProvisioningRestoreTest {
    @Test fun processRestartRestoresVerificationWithoutRepeatingWifiConfigurationOrLogin() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "restore-${UUID.randomUUID()}.json")
        val store = ProfileStore(context, file)
        val attempts = AtomicInteger()
        val session = CameraSession(channelFactory = { attempts.incrementAndGet(); TcpChannel(Socket()) })
        val wifi = WifiNetwork(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val profile = CameraProfile(name = "Synthetic restore", host = "192.168.1.10", deviceId = "12345678", username = "synthetic")
        store.savePendingSetup(profile, "synthetic-home")
        val manager = ProvisioningManager(wifi, LanDiscovery(wifi), session, store, scope)
        try {
            withTimeout(3000) { while (manager.state.value.phase != ProvisioningPhase.WAITING_LAN) delay(10) }
            assertEquals(profile.id, manager.state.value.profile?.id)
            assertEquals("synthetic-home", manager.state.value.targetSsid)
            assertTrue(manager.state.value.mayHaveConfigured)
            assertEquals(0, attempts.get()); assertEquals(SessionState.Idle, session.state.value)
            manager.cancel()
            assertNotNull("Leaving setup must preserve verification metadata", store.loadPendingSetup())
        } finally { manager.cancel(); scope.cancel(); session.dispose(); store.clearPendingSetup(); file.delete() }
    }
}
