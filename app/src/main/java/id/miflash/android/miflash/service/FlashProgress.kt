package id.miflash.android.miflash.service

import id.miflash.android.common.UiText
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PartitionState {
    PENDING,
    IN_PROGRESS,
    DONE
}

data class PartitionProgress(
    val name: String,
    val state: PartitionState,
    val durationMs: Long? = null
)

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

object FlashProgress {
    private val _fraction = MutableStateFlow(0f)
    val fraction: StateFlow<Float> = _fraction.asStateFlow()

    private val _message = MutableStateFlow<UiText>(UiText.Raw(""))
    val message: StateFlow<UiText> = _message.asStateFlow()

    private val _partitions = MutableStateFlow<List<PartitionProgress>>(emptyList())
    val partitions: StateFlow<List<PartitionProgress>> = _partitions.asStateFlow()

    private val partitionStartedAt = mutableListOf<Long?>()

    @Volatile
    private var cancelRequested = false

    fun update(fraction: Float, message: UiText) {
        _fraction.value = fraction.coerceIn(0f, 1f)
        _message.value = message
    }

    fun setPartitions(names: List<String>) {
        partitionStartedAt.clear()
        partitionStartedAt.addAll(names.map { null })
        _partitions.value = names.map { PartitionProgress(it, PartitionState.PENDING) }
    }

    fun markPartitionStarted(index: Int) {
        val current = _partitions.value
        if (index !in current.indices) return
        partitionStartedAt[index] = System.currentTimeMillis()
        _partitions.value = current.mapIndexed { i, p ->
            if (i == index) p.copy(state = PartitionState.IN_PROGRESS) else p
        }
    }

    fun markPartitionDone(index: Int) {
        val current = _partitions.value
        if (index !in current.indices) return
        val started = partitionStartedAt[index]
        val duration = if (started != null) System.currentTimeMillis() - started else null
        _partitions.value = current.mapIndexed { i, p ->
            if (i == index) p.copy(state = PartitionState.DONE, durationMs = duration) else p
        }
    }

    fun requestCancel() {
        cancelRequested = true
    }

    fun isCancelRequested(): Boolean = cancelRequested

    fun checkCancelled() {
        if (cancelRequested) throw CancellationException("Flash dibatalkan oleh pengguna")
    }

    fun reset() {
        _fraction.value = 0f
        _message.value = UiText.Raw("")
        _partitions.value = emptyList()
        partitionStartedAt.clear()
        cancelRequested = false
    }
}
