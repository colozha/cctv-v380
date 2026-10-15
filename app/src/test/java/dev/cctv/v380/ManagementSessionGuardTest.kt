package dev.cctv.v380

import dev.cctv.v380.protocol.*
import dev.cctv.v380.session.*
import dev.cctv.v380.transport.CameraFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** These tests never construct a socket or run a LAN session. */
class ManagementSessionGuardTest {
    @Test fun unauthenticatedAndGatedOperationsNeverReachTransport() = runTest {
        var opened = 0
        val session = CameraSession(channelFactory = { opened++; error("Transport must not be opened") })
        val snapshot = ManagementSnapshot("synthetic", 1, ManagementGroup.MOTOR, CameraSettings(emptyMap(), emptyMap()))
        suspend fun rejected(block: suspend () -> Unit) {
            try { block(); fail("Expected rejection") } catch (_: CameraFailure) { }
        }
        try {
            rejected { session.readManagement("synthetic", ManagementGroup.DEVICE) }
            rejected { session.saveManagement(snapshot, mapOf(CameraManagementProtocol.ALARM_MAIN to true)) }
            val area = CameraAlarmArea(1, 1, listOf(1), "{}")
            val forged = AreaReference(AreaContext("synthetic", "synthetic-device", 1, 0, 0), area, VideoGeometry(640, 480))
            assertFalse(session.canCaptureArea("synthetic")); assertFalse(session.canUseAreaReference(forged))
            rejected { session.saveManagement(snapshot.copy(group = ManagementGroup.ALARM, settings = CameraSettings(mapOf(CameraManagementProtocol.ALARM_AREA to area), emptyMap())),
                mapOf(CameraManagementProtocol.ALARM_AREA to area)) }
            rejected { session.presetAction(snapshot, 0, 1, "one") }
            rejected { session.calibrateCamera(snapshot, "two") { fail("No calibration marker before authentication") } }
            rejected { session.restartCamera("synthetic", "synthetic-device", "three") { fail("No pending marker for an unauthenticated request") } }
            rejected { session.sendSdFormat("synthetic", "synthetic-device", "format-op",
                beforeWrite = { fail("Gated format must not save a marker") },
                onWriteStarted = { fail("Gated format must not submit a write") }) }
            rejected { session.readSdFormatStatus("synthetic", "synthetic-device") }
            session.blockMaintenanceWrites(true)
            assertTrue(session.maintenancePending)
            rejected { session.saveSettings(mapOf(CameraSettingsProtocol.LIGHT_MODE to 1), CameraSettings(mapOf(CameraSettingsProtocol.LIGHT_MODE to 0), emptyMap())) }
            assertEquals(0, opened)
        } finally { session.dispose() }
    }
}
