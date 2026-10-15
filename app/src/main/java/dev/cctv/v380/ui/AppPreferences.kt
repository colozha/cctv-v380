package dev.cctv.v380.ui

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal enum class AppLanguage(val tag: String) {
    INDONESIAN("id"), ENGLISH("en");

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: INDONESIAN
    }
}

internal enum class AppTheme { LIGHT, DARK }

internal class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null))
        set(value) { preferences.edit().putString(KEY_LANGUAGE, value.tag).apply() }

    var theme: AppTheme?
        get() = preferences.getString(KEY_THEME, null)?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_THEME) else putString(KEY_THEME, value.name)
            }.apply()
        }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"

        fun localizedContext(base: Context): Context {
            val locale = Locale.forLanguageTag(AppPreferences(base).language.tag)
            Locale.setDefault(locale)
            return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                setLocale(locale)
            })
        }
    }
}
