package id.miflash.android.common

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import id.miflash.android.ui.settings.LocaleHelper
import id.miflash.android.ui.settings.readSavedLanguageCode
import java.util.Locale

sealed interface UiText {
    data class Raw(val text: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any?> = emptyList(), val english: Boolean = false) : UiText

    val isEmpty: Boolean
        get() = this is Raw && text.isEmpty()

    companion object {
        fun res(@StringRes id: Int, vararg args: Any?): UiText = Res(id, args.toList())
        fun en(@StringRes id: Int, vararg args: Any?): UiText = Res(id, args.toList(), english = true)
        fun raw(text: String): UiText = Raw(text)
    }
}

private fun englishContext(context: Context): Context {
    val config = Configuration(context.resources.configuration)
    config.setLocale(Locale("en"))
    config.setLayoutDirection(Locale("en"))
    return context.createConfigurationContext(config)
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> if (english) {
        englishContext(LocalContext.current)
            .getString(id, *args.map { it ?: "" }.toTypedArray())
    } else {
        stringResource(id, *args.map { it ?: "" }.toTypedArray())
    }
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> (if (english) englishContext(context) else LocaleHelper.wrap(context, readSavedLanguageCode(context)))
        .getString(id, *args.map { it ?: "" }.toTypedArray())
}

fun UiText.resolveEnglish(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> englishContext(context)
        .getString(id, *args.map { it ?: "" }.toTypedArray())
}
