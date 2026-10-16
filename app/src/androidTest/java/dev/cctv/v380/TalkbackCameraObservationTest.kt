package dev.cctv.v380

import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.TcpChannel
import dev.cctv.v380.transport.WifiNetwork
import dev.cctv.v380.ui.ProfileStore
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Explicit opt-in only. Reads a saved camera's Live metadata; never starts a microphone or PTZ. */
class TalkbackCameraObservationTest {
    @Test fun observeSavedCameraLiveMetadata() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Real camera observation requires explicit opt-in", args.getString("observeCamera") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ProfileStore(context)
        val target = store.load().singleOrNull { it.host == args.getString("cameraHost") }
        requireNotNull(target) { "Select exactly one existing saved camera" }
        require(target.protocolProfileId == "iot-lan" || target.platformType == 2) { "Expected saved IoT profile" }
        val password = requireNotNull(store.password(target.id)) { "Saved credential unavailable; reconnect in the app" }
        val wifi = WifiNetwork(context)
        val session = CameraSession(channelFactory = { TcpChannel(wifi.resolve().socket()) })
        try {
            withTimeout(30000) {
                session.connect(target, password)
                // Exercise the production reader without a display/decoder or saving any video.
                suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                    session.javaClass.declaredMethods.single { it.name == "startStream" }.apply { isAccessible = true }
                        .invoke(session, Quality.HD, continuation)
                }
                while (session.diagnostics.events.value.none { it.talkbackMetadata != null }) delay(50)
                delay(1000) // Permit a following partial notification; no retry loop.
                val observations = session.diagnostics.events.value.mapNotNull { it.talkbackMetadata }.distinct()
                val report = JSONObject().put("microphoneStarted", false).put("settingsChanged", false)
                    .put("capability", session.capabilities.value?.talkback ?: JSONObject.NULL)
                    .put("renderingTested", false)
                    .put("metadataAccepted", session.talkbackAudio.value != null)
                    .put("talkbackBlock", session.talkbackBlock(target.id)?.name ?: JSONObject.NULL)
                    .put("metadataBlock", session.talkbackMetadataIssue.value?.name
                        ?: if (session.talkbackAudio.value?.supported != true) "UNSUPPORTED_AUDIO" else JSONObject.NULL)
                    .put("observations", org.json.JSONArray().apply { observations.forEach { put(it.toJson()) } })
                File(context.cacheDir, "talkback-envelope-observation.json").writeText(report.toString(2))
                assertTrue(observations.isNotEmpty())
                if (args.getString("expectMetadataAccepted") == "true") assertNotNull(session.talkbackAudio.value)
                assertEquals(TalkbackState.Idle, session.talkback.value)
            }
        } finally {
            password.fill('\u0000')
            // No PTZ watchdog STOP command or reconnect on observational teardown.
            session.dispose()
        }
    }
}
