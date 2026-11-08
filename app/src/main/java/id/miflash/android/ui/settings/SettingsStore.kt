package id.miflash.android.ui.settings

import android.content.Context
import android.content.res.Configuration
import id.miflash.android.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val code: String, val nativeName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    MALAY("ms", "Bahasa Melayu"),
    ENGLISH("en", "English"),
    THAI("th", "ไทย"),
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    TAMIL("ta", "தமிழ்");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: INDONESIAN
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val paletteKey: String = "yellow",
    val language: AppLanguage = AppLanguage.INDONESIAN
)

object LocaleHelper {
    fun wrap(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

fun readSavedLanguageCode(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_LANGUAGE, AppLanguage.INDONESIAN.code)
        ?: AppLanguage.INDONESIAN.code
}

fun localizedString(context: Context, resId: Int, vararg args: Any?): String {
    return LocaleHelper.wrap(context, readSavedLanguageCode(context))
        .getString(resId, *args.map { it ?: "" }.toTypedArray())
}

fun englishString(context: Context, resId: Int, vararg args: Any?): String {
    return LocaleHelper.wrap(context, "en")
        .getString(resId, *args.map { it ?: "" }.toTypedArray())
}

private const val PREFS_NAME = "mitool_settings"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_PALETTE = "palette"
private const val KEY_LANGUAGE = "language"

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val mode = when (prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
        return AppSettings(
            themeMode = mode,
            paletteKey = prefs.getString(KEY_PALETTE, "yellow") ?: "yellow",
            language = AppLanguage.fromCode(
                prefs.getString(KEY_LANGUAGE, AppLanguage.INDONESIAN.code)
                    ?: AppLanguage.INDONESIAN.code
            )
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setPalette(key: String) {
        prefs.edit().putString(KEY_PALETTE, key).apply()
        _settings.value = _settings.value.copy(paletteKey = key)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        _settings.value = _settings.value.copy(language = language)
    }
}
