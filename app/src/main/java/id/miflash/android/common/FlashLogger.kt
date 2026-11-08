package id.miflash.android.common

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogKind {
    INFO,
    STAGE,
    SUCCESS,
    WARNING,
    ERROR,
    COMMAND
}

data class LogLine(val text: UiText, val kind: LogKind, val time: String)

class FlashLogger {
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(text: UiText, kind: LogKind = LogKind.INFO) {
        _lines.value = (_lines.value + LogLine(text, kind, timeFormat.format(Date()))).takeLast(500)
    }

    fun log(@StringRes resId: Int, vararg args: Any?, kind: LogKind = LogKind.INFO) {
        log(UiText.en(resId, *args), kind)
    }

    fun logRaw(text: String, kind: LogKind = LogKind.INFO) {
        log(UiText.Raw(text), kind)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
