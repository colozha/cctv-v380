package dev.cctv.v380

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import dev.cctv.v380.ui.MainActivity
import org.junit.Rule
import org.junit.Test

class UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun apDraftSurvivesRecreationButHotspotPasswordDoesNot() {
        compose.onNodeWithText("Setup lewat AP kamera").performClick()
        compose.onNodeWithText("SSID hotspot kamera").performTextInput("Synthetic-camera-AP")
        compose.onNodeWithText("Password hotspot kamera").performTextInput(java.util.UUID.randomUUID().toString())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Setup Wi-Fi kamera").assertIsDisplayed()
        compose.onNodeWithText("SSID hotspot kamera").assertTextContains("Synthetic-camera-AP")
        val secret = compose.onNodeWithText("Password hotspot kamera").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        org.junit.Assert.assertTrue(secret.isEmpty())
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithContentDescription("Diagnostik LAN").performClick()
        compose.onNodeWithText("Status pemeriksaan").assertIsDisplayed()
    }

    @Test fun addEditAndDeleteProfileWithoutStoringPassword() {
        val name = "Uji UI ${java.util.UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithContentDescription("Tambah kamera manual").performClick()
        compose.onNodeWithText("Nama kamera").performTextInput(name)
        compose.onNodeWithText("Alamat IP").performTextInput("192.168.1.10")
        compose.onNodeWithText("Device ID").performTextInput("12345678")
        compose.onNodeWithText("Username kamera").performTextInput("synthetic")
        compose.onNodeWithText("Simpan kamera").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit $name").performClick()
        compose.onNodeWithContentDescription("Hapus kamera").performClick()
        compose.onNodeWithText("Hapus", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(name).assertDoesNotExist()
    }

    @Test fun rotationPreservesNonsecretDraftAndClearsPassword() {
        compose.onNodeWithContentDescription("Tambah kamera manual").performClick()
        compose.onNodeWithText("Nama kamera").performTextInput("Draft rotasi")
        compose.onNodeWithText("Alamat IP").performTextInput("192.168.1.10")
        compose.onNodeWithText("Device ID").performTextInput("12345678")
        compose.onNodeWithText("Username kamera").performTextInput("synthetic")
        compose.onNodeWithText("Ingat password").performScrollTo().performClick()
        compose.onNodeWithText("Password kamera").performScrollTo().performTextInput(java.util.UUID.randomUUID().toString())
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Tambah kamera").assertIsDisplayed()
        compose.onNodeWithText("Nama kamera").assertTextContains("Draft rotasi")
        compose.onNodeWithText("Alamat IP").assertTextContains("192.168.1.10")
        compose.onNodeWithText("Device ID").assertTextContains("12345678")
        compose.onNodeWithText("Username kamera").assertTextContains("synthetic")
        val password = compose.onNodeWithText("Password kamera").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        org.junit.Assert.assertTrue(password.isEmpty())
    }
}
