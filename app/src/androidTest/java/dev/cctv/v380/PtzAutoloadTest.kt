package dev.cctv.v380

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.control.Direction
import dev.cctv.v380.control.PtzWatchdog
import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.*
import dev.cctv.v380.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Post-authentication fixtures and an encrypted loopback property peer. No camera credentials or footage. */
class PtzAutoloadTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun automaticReadUnlocksDirectionsWithoutPresetUiAndRespectsAxesAndLifecycle(): Unit = runBlocking {
        MotorPeer().use { peer ->
            withModel(peer) { model, session, profile ->
                val sent = mutableListOf<Direction?>()
                val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                try {
                    main { set(session, "watchdog", PtzWatchdog(watchdogScope, { synchronized(sent) { sent += it } })) }
                    // A busy feature queues the automatic read rather than dropping it.
                    main { features(model).value = model.features.value.copy(busy = true); authenticate(session, profile, 1) }
                    delay(150); assertEquals(0, peer.reads.get())
                    main { features(model).value = model.features.value.copy(busy = false) }
                    await { peer.reads.get() == 1 && !model.features.value.busy && session.manualPtzAllowed }
                    assertNotNull(model.features.value.management[ManagementGroup.MOTOR])
                    assertEquals(CameraTab.LIVE, model.features.value.tab)
                    session.move(Direction.LEFT); session.stopPtz()
                    assertTrue(synchronized(sent) { Direction.LEFT in sent && null in sent })
                    main { flow<CameraCapabilities?>(session, "mutableCapabilities").value = session.capabilities.value!!.copy(verticalPtz = false) }
                    session.move(Direction.UP)
                    assertFalse(synchronized(sent) { Direction.UP in sent })
                    // Quality changes, returning to Live, and feature updates do not reread the same session.
                    main { flow<SessionState>(session, "mutableState").value = SessionState.Negotiating(Quality.SD) }
                    main { flow<SessionState>(session, "mutableState").value = SessionState.Streaming(Quality.SD) }
                    main { features(model).value = model.features.value.copy(tab = CameraTab.SETTINGS) }
                    main { features(model).value = model.features.value.copy(tab = CameraTab.LIVE) }
                    delay(150); assertEquals(1, peer.reads.get())
                    session.blockMaintenanceWrites(true)
                    assertFalse(session.manualPtzAllowed)
                    session.move(Direction.RIGHT)
                    assertFalse(synchronized(sent) { Direction.RIGHT in sent })
                    session.blockMaintenanceWrites(false)
                    main { flow<SessionState>(session, "mutableState").value = SessionState.Reconnecting(1) }
                    await { model.features.value.management.isEmpty() }
                    peer.active = true
                    main { authenticate(session, profile, 2) }
                    await { peer.reads.get() == 2 && !model.features.value.busy }
                    assertFalse("Active patrol must keep manual movement blocked", session.manualPtzAllowed)
                    peer.active = false; peer.reject = true
                    main { model.loadManagement(ManagementGroup.MOTOR) }
                    await { peer.reads.get() == 3 && !model.features.value.busy }
                    assertFalse(session.manualPtzAllowed)
                    assertNotNull(model.features.value.managementErrors[ManagementGroup.MOTOR])
                    delay(150); assertEquals("A failed read must not create an automatic retry loop", 3, peer.reads.get())
                    peer.reject = false
                    main { model.loadManagement(ManagementGroup.MOTOR) }
                    await { peer.reads.get() == 4 && !model.features.value.busy && session.manualPtzAllowed }
                } finally { watchdogScope.cancel() }
            }
            peer.failure.get()?.let { throw AssertionError("Loopback peer failed", it) }
        }
    }

    @Test fun staleReadIsRejectedAndUnsupportedCamerasDoNotLoadMotorProperties(): Unit = runBlocking {
        MotorPeer().use { peer ->
            withModel(peer) { model, session, profile ->
                val gate = CountDownLatch(1)
                peer.gate = gate
                main { authenticate(session, profile, 1) }
                await { peer.reads.get() == 1 }
                main {
                    set(session, "managementRevision", 2L)
                    flow<Long>(session, "mutableAuthenticationRevision").value = 2
                    flow<SessionState>(session, "mutableState").value = SessionState.Ready(session.capabilities.value!!)
                }
                gate.countDown()
                await { !model.features.value.busy }
                assertNull(model.features.value.management[ManagementGroup.MOTOR])
                main { authenticate(session, profile, 3, patrolSupported = false) }
                delay(150); assertEquals(1, peer.reads.get())
                assertTrue(session.manualPtzAllowed)
                main { authenticate(session, profile, 4, ptz = false) }
                delay(150); assertEquals(1, peer.reads.get())
            }
        }
    }

    private suspend fun withModel(peer: MotorPeer, test: suspend (CameraViewModel, CameraSession, CameraProfile) -> Unit) {
        val session = CameraSession(channelFactory = { TcpChannel(Socket()) })
        val profile = CameraProfile(name = "PTZ sintetis", host = "127.0.0.1", port = peer.server.localPort,
            deviceId = "synthetic-ptz", protocolProfileId = "iot-lan")
        val store = ViewModelStore()
        lateinit var model: CameraViewModel
        main {
            model = CameraViewModel(instrumentation.targetContext.applicationContext as Application, sessionOverride = session)
            store.put("ptz-test", model)
        }
        try {
            await { !model.ui.value.loading && model.features.value.pendingLoaded && model.features.value.apPendingLoaded }
            main {
                flow<CameraUiState>(model, "mutableUi").value = CameraUiState(profiles = listOf(profile), loading = false)
                features(model).value = CameraFeatureState(profileId = profile.id, pendingLoaded = true, restartLoaded = true, apPendingLoaded = true)
                session.blockMaintenanceWrites(false)
            }
            test(model, session, profile)
        } finally { main { store.clear() } }
    }

    private fun authenticate(session: CameraSession, profile: CameraProfile, revision: Long,
        patrolSupported: Boolean = true, ptz: Boolean = true) {
        val iot = IoTSession(MotorPeer.KEY.copyOf(), 17, 3, 0)
        set(session, "profile", profile); set(session, "iot", iot); set(session, "authenticatedIoT", iot)
        set(session, "accessRevoked", false); set(session, "managementRevision", revision)
        set(session, "patrolActive", if (patrolSupported) null else false)
        flow<CameraCapabilities?>(session, "mutableCapabilities").value = CameraCapabilities(emptySet(), Quality.entries.toSet(), ptz,
            management = ManagementAbilities(presets = 6, patrol = patrolSupported))
        flow<Long>(session, "mutableAuthenticationRevision").value = revision
        flow<SessionState>(session, "mutableState").value = SessionState.Streaming(Quality.HD)
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(5000) { while (!condition()) delay(10) }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun features(model: CameraViewModel) = flow<CameraFeatureState>(model, "mutableFeatures")
    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as MutableStateFlow<T>
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)

    private class MotorPeer : AutoCloseable {
        val server = ServerSocket(0)
        val reads = AtomicInteger()
        val failure = AtomicReference<Throwable?>()
        @Volatile var active = false
        @Volatile var reject = false
        @Volatile var gate: CountDownLatch? = null
        private val thread = Thread {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = ExactReader(socket.getInputStream())
                    val header = IoTCommonProtocol.parseHeader(reader.read(16))
                    val request = IoTCommonProtocol.decodeJson(header, reader.read(header.bodySize), KEY)
                    assertEquals(CameraSettingsProtocol.GET, request.getInt("method_id"))
                    assertEquals(JSONArray(CameraManagementProtocol.ids(ManagementGroup.MOTOR)).toString(),
                        request.getJSONObject("params").getJSONArray("dplist").toString())
                    reads.incrementAndGet()
                    gate?.await(3, TimeUnit.SECONDS); gate = null
                    val patrol = JSONObject().put("ptz_auto_enable", active).put("ptz_timer_enable", false)
                        .put("ptz_cruise_route_total_cnt", 2).put("ptz_cruise_route_position_max", 3)
                        .put("ptz_auto_route_cnt", 0).put("ptz_auto_route_info", JSONArray())
                    val rows = JSONArray().put(JSONObject().put("dpid", CameraManagementProtocol.PATROL)
                        .put("code", 1000).put("value", patrol)).put(JSONObject().put("dpid", CameraManagementProtocol.PRESETS)
                        .put("code", 1000).put("value", JSONObject().put("pos_mark_cnt", 1).put("pos_mark_details", JSONArray(listOf(0)))))
                    val response = JSONObject().put("id", request.getInt("id")).put("chn", 1)
                        .put("method_id", CameraSettingsProtocol.GET_RESPONSE).put("result", JSONObject().put("code", if (reject) 1109 else 1000))
                        .put("data", JSONObject().put("dplist_result", rows))
                    val body = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                        init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(KEY.copyOfRange(0, 16), "AES"),
                            javax.crypto.spec.IvParameterSpec(KEY.copyOfRange(16, 32))); doFinal(response.toString().toByteArray())
                    }
                    val packet = java.nio.ByteBuffer.allocate(16 + body.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .putInt(IoTCommonProtocol.COMMAND_IOT).put(1).put(2).put(17).put(0).putInt(0).putInt(body.size).put(body).array()
                    socket.getOutputStream().write(packet)
                }
            } catch (error: Throwable) { if (!server.isClosed) failure.set(error) }
        }.apply { isDaemon = true; start() }
        override fun close() { gate?.countDown(); server.close(); thread.join(1000) }
        companion object { val KEY = ByteArray(32) { (it + 1).toByte() } }
    }
}
