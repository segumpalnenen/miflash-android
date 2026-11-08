package id.miflash.android.miflash.fastboot

import id.miflash.android.R
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.UiText
import id.miflash.android.common.LogKind
import id.miflash.android.common.SeekableSource
import java.util.Locale

class FastbootException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed class FastbootResult {
    data class Ok(val payload: String) : FastbootResult()
    data class Fail(val message: String) : FastbootResult()
    data class Data(val size: Long) : FastbootResult()
}

class FastbootSession(
    private val transport: FastbootTransport,
    private val logger: FlashLogger? = null
) {

    companion object {
        const val DEFAULT_MAX_DOWNLOAD_SIZE = 512L * 1024 * 1024
        const val MAX_DOWNLOAD_SIZE_CAP = 1024L * 1024 * 1024
        const val RESPONSE_BUFFER_SIZE = 256
        const val COMMAND_IDLE_TIMEOUT_MS = 60_000
        const val FLASH_IDLE_TIMEOUT_MS = 600_000
        const val DATA_CHUNK_SIZE = 16384

        fun parseFastbootOutput(output: String?): String? {
            if (output.isNullOrEmpty()) return null
            val cleaned = output
                .replace(Regex("(?i)\\(bootloader\\)\\s*|token:\\s*|product:\\s*|INFO\\s*|OKAY\\s*|FAIL\\s*"), "")
                .trim()
            return cleaned.takeIf { it.isNotBlank() }
        }
    }

    private var cachedMaxDownloadSize: Long? = null

    fun open(): Boolean = transport.open()

    fun close() = transport.close()

    fun getvar(name: String): String? {
        return when (val result = sendCommand("getvar:$name")) {
            is FastbootResult.Ok -> result.payload
            else -> null
        }
    }

    fun getvarInfo(name: String): String? {
        val bytes = "getvar:$name".toByteArray(Charsets.US_ASCII)
        if (!transport.write(bytes)) throw FastbootException("Gagal mengirim perintah fastboot: getvar:$name")
        val raw = readRawResponse(COMMAND_IDLE_TIMEOUT_MS)
        if (raw.contains("FAIL", ignoreCase = true)) return null
        return parseFastbootOutput(raw)
    }

    fun oemInfo(args: String): String? {
        val bytes = "oem $args".toByteArray(Charsets.US_ASCII)
        if (!transport.write(bytes)) throw FastbootException("Gagal mengirim perintah fastboot: oem $args")
        val raw = readRawResponse(COMMAND_IDLE_TIMEOUT_MS)
        if (raw.contains("FAIL", ignoreCase = true)) return null
        return parseFastbootOutput(raw)
    }

    fun maxDownloadSize(): Long {
        cachedMaxDownloadSize?.let { return it }
        val raw = getvar("max-download-size")
        val parsed = raw?.trim()?.toLongOrNull(16)
        val size = when {
            parsed == null || parsed <= 0 -> DEFAULT_MAX_DOWNLOAD_SIZE
            parsed > MAX_DOWNLOAD_SIZE_CAP -> MAX_DOWNLOAD_SIZE_CAP
            else -> parsed
        }
        cachedMaxDownloadSize = size
        logger?.logRaw("max-download-size: $size byte")
        return size
    }

    fun download(source: SeekableSource, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val size = source.size
        if (size > Int.MAX_VALUE) throw FastbootException("File terlalu besar untuk satu download: $size byte")
        val command = String.format(Locale.US, "download:%08x", size.toInt())
        when (val result = sendCommand(command)) {
            is FastbootResult.Data -> {
                if (result.size != size) throw FastbootException("Device meminta ukuran data berbeda: ${result.size} != $size")
            }
            is FastbootResult.Fail -> throw FastbootException("download ditolak device: ${result.message}")
            is FastbootResult.Ok -> throw FastbootException("Jawaban download tidak terduga dari device")
        }
        source.seek(0)
        val buffer = ByteArray(DATA_CHUNK_SIZE)
        var sent = 0L
        while (sent < size) {
            val want = minOf(buffer.size.toLong(), size - sent).toInt()
            val n = source.read(buffer, 0, want)
            if (n <= 0) throw FastbootException("File terpotong saat dibaca")
            if (!transport.write(buffer, 0, n)) throw FastbootException("Gagal mengirim data ke device")
            sent += n
            onProgress(sent, size)
        }
        when (val done = readResponse(FLASH_IDLE_TIMEOUT_MS)) {
            is FastbootResult.Ok -> return
            is FastbootResult.Fail -> throw FastbootException("download gagal: ${done.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban download tidak terduga dari device")
        }
    }

    fun flash(partition: String, source: SeekableSource, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val maxSize = maxDownloadSize()
        val pieces = SparseSplit.split(source, maxSize)
        var sentTotal = 0L
        val total = pieces.sumOf { it.size }
        for (piece in pieces) {
            download(piece) { sent, _ -> onProgress(sentTotal + sent, total) }
            when (val result = sendCommand("flash:$partition", FLASH_IDLE_TIMEOUT_MS)) {
                is FastbootResult.Ok -> Unit
                is FastbootResult.Fail -> throw FastbootException("flash $partition gagal: ${result.message}")
                is FastbootResult.Data -> throw FastbootException("Jawaban flash tidak terduga dari device")
            }
            sentTotal += piece.size
            onProgress(sentTotal, total)
            if (piece !== source) piece.close()
        }
    }

    fun erase(partition: String) {
        when (val result = sendCommand("erase:$partition", FLASH_IDLE_TIMEOUT_MS)) {
            is FastbootResult.Ok -> Unit
            is FastbootResult.Fail -> throw FastbootException("erase $partition gagal: ${result.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban erase tidak terduga dari device")
        }
    }

    fun reboot(target: String? = null) {
        val command = when (target) {
            null, "" -> "reboot"
            "bootloader" -> "reboot-bootloader"
            "fastboot" -> "reboot-fastboot"
            "recovery" -> "reboot-recovery"
            else -> "reboot"
        }
        sendCommandTolerant(command)
    }

    fun oem(args: String) {
        when (val result = sendCommand("oem $args")) {
            is FastbootResult.Ok -> Unit
            is FastbootResult.Fail -> throw FastbootException("oem $args gagal: ${result.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban oem tidak terduga dari device")
        }
    }

    fun flashingLock() {
        when (val result = sendCommand("flashing lock", COMMAND_IDLE_TIMEOUT_MS)) {
            is FastbootResult.Ok -> Unit
            is FastbootResult.Fail -> throw FastbootException("flashing lock gagal: ${result.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban flashing lock tidak terduga dari device")
        }
    }

    fun flashingUnlock() {
        when (val result = sendCommand("flashing unlock", COMMAND_IDLE_TIMEOUT_MS)) {
            is FastbootResult.Ok -> Unit
            is FastbootResult.Fail -> throw FastbootException("flashing unlock gagal: ${result.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban flashing unlock tidak terduga dari device")
        }
    }

    fun setActive(slot: String) {
        when (val result = sendCommand("set_active:$slot")) {
            is FastbootResult.Ok -> Unit
            is FastbootResult.Fail -> throw FastbootException("set_active $slot gagal: ${result.message}")
            is FastbootResult.Data -> throw FastbootException("Jawaban set_active tidak terduga dari device")
        }
    }

    fun sendCommand(command: String, idleTimeoutMs: Int = COMMAND_IDLE_TIMEOUT_MS): FastbootResult {
        val bytes = command.toByteArray(Charsets.US_ASCII)
        if (!transport.write(bytes)) throw FastbootException("Gagal mengirim perintah fastboot: $command")
        return readResponse(idleTimeoutMs)
    }

    private fun sendCommandTolerant(command: String) {
        val bytes = command.toByteArray(Charsets.US_ASCII)
        if (!transport.write(bytes)) {
            logger?.log(R.string.log_reboot_missed, command, kind = LogKind.WARNING)
            return
        }
        try {
            readResponse(10_000)
        } catch (e: FastbootException) {
            logger?.log(R.string.log_reboot_no_reply, kind = LogKind.WARNING)
        }
    }

    private fun readResponse(idleTimeoutMs: Int): FastbootResult {
        var deadline = System.currentTimeMillis() + idleTimeoutMs
        val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) throw FastbootException("Timeout menunggu jawaban device")
            val n = transport.read(buffer, remaining)
            if (n < 0) throw FastbootException("Koneksi USB putus saat menunggu jawaban")
            if (n == 0) {
                Thread.sleep(50)
                continue
            }
            deadline = System.currentTimeMillis() + idleTimeoutMs
            val text = String(buffer, 0, n, Charsets.US_ASCII)
            when {
                text.startsWith("OKAY") -> return FastbootResult.Ok(text.substring(4))
                text.startsWith("FAIL") -> return FastbootResult.Fail(text.substring(4))
                text.startsWith("DATA") -> {
                    val size = text.substring(4).trim().toLongOrNull(16)
                        ?: throw FastbootException("Jawaban DATA tidak valid dari device")
                    return FastbootResult.Data(size)
                }
                text.startsWith("INFO") -> logger?.logRaw("(bootloader) ${text.substring(4)}")
                text.startsWith("TEXT") -> logger?.logRaw(text.substring(4))
                else -> logger?.log(R.string.log_unknown_packet, text, kind = LogKind.WARNING)
            }
        }
    }

    private fun readRawResponse(idleTimeoutMs: Int): String {
        var deadline = System.currentTimeMillis() + idleTimeoutMs
        val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
        val builder = StringBuilder()
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) throw FastbootException("Timeout menunggu jawaban device")
            val n = transport.read(buffer, remaining)
            if (n < 0) throw FastbootException("Koneksi USB putus saat menunggu jawaban")
            if (n == 0) {
                Thread.sleep(50)
                continue
            }
            deadline = System.currentTimeMillis() + idleTimeoutMs
            val text = String(buffer, 0, n, Charsets.US_ASCII)
            builder.append(text)
            val current = builder.toString()
            if (current.contains("OKAY", ignoreCase = true) || current.contains("FAIL", ignoreCase = true)) {
                return current
            }
        }
    }
}
