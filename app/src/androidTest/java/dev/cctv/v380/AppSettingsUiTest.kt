package dev.cctv.v380

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.cctv.v380.ui.AppLanguage
import dev.cctv.v380.ui.AppPreferences
import dev.cctv.v380.ui.AppSettingsScreen
import dev.cctv.v380.ui.AppTheme
import dev.cctv.v380.ui.MainActivity
import dev.cctv.v380.ui.V380Theme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageAndThemeChoicesAreAccessibleAndPersisted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppPreferences(context)
        val previousLanguage = preferences.language
        val previousTheme = preferences.theme
        try {
            preferences.language = AppLanguage.INDONESIAN
            preferences.theme = AppTheme.LIGHT
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    var language by remember { mutableStateOf(preferences.language) }
                    var theme by remember { mutableStateOf(preferences.theme ?: AppTheme.LIGHT) }
                    V380Theme(darkTheme = theme == AppTheme.DARK) {
                        AppSettingsScreen(
                            language = language,
                            theme = theme,
                            onLanguageSelected = { language = it; preferences.language = it },
                            onThemeSelected = { theme = it; preferences.theme = it },
                            onBack = {},
                        )
                    }
                }
            }

            compose.onNodeWithText("ID · Indonesia").assertIsSelected()
            compose.onNodeWithText("EN · English").assertIsNotSelected().performClick().assertIsSelected()
            assertEquals(AppLanguage.ENGLISH, preferences.language)

            compose.onNodeWithText("Light").assertIsSelected()
            compose.onNodeWithText("Dark").assertIsNotSelected().performClick().assertIsSelected()
            assertEquals(AppTheme.DARK, preferences.theme)
        } finally {
            preferences.language = previousLanguage
            preferences.theme = previousTheme
        }
    }
}
