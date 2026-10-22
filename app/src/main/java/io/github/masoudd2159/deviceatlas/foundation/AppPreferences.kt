package io.github.masoudd2159.deviceatlas.foundation

enum class ThemePreference(val storageKey: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemePreference = entries.firstOrNull {
            it.storageKey == value
        } ?: SYSTEM
    }
}

enum class AppLanguage(val languageTag: String) {
    SYSTEM(""), ENGLISH("en"), PERSIAN("fa");

    companion object {
        fun fromLanguageTag(tag: String): AppLanguage = entries.firstOrNull {
            it.languageTag == tag.substringBefore(',').substringBefore('-')
        } ?: SYSTEM
    }
}

/** Only theme is stored here. AppCompat/framework owns the application locale. */
interface AppPreferences {
    suspend fun readTheme(): ThemePreference
    suspend fun writeTheme(theme: ThemePreference): Boolean
}
