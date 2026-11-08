package id.miflash.android.miflash.terminal

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.miflash.android.R
import id.miflash.android.common.UiText
import id.miflash.android.ui.settings.englishString
import id.miflash.android.common.usb.DeviceDiscovery
import id.miflash.android.common.usb.DeviceMode
import id.miflash.android.miflash.edl.ParcelFdSeekableSource
import id.miflash.android.miflash.fastboot.FastbootException
import id.miflash.android.miflash.fastboot.FastbootSession
import id.miflash.android.miflash.fastboot.FastbootTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AttachedFile(val name: String, val uri: Uri)

enum class TerminalKind {
    COMMAND,
    OUTPUT,
    OK,
    ERROR,
    INFO
}

data class TerminalLine(val text: UiText, val kind: TerminalKind)

class FastbootTerminalViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_USB_PERMISSION = "id.miflash.android.FASTBOOT_TERMINAL_USB_PERMISSION"
        private const val PERMISSION_TIMEOUT_MS = 30_000L
        private const val MAX_LINES = 500
    }

    private val app = getApplication<Application>()
    private val usbManager = app.getSystemService(UsbManager::class.java)

    private val _lines = MutableStateFlow<List<TerminalLine>>(
        listOf(TerminalLine(UiText.en(R.string.terminal_help_hint), TerminalKind.INFO))
    )
    val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _deviceLabel = MutableStateFlow<UiText>(UiText.en(R.string.terminal_no_device))
    val deviceLabel: StateFlow<UiText> = _deviceLabel.asStateFlow()

    private fun str(resId: Int, vararg args: Any): String =
        englishString(app, resId, *args)

    private var commandJob: Job? = null
    private var permissionDeferred: CompletableDeferred<Boolean>? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            permissionDeferred?.complete(granted)
            permissionDeferred = null
        }
    }

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(permissionReceiver, filter)
        }
        refreshDevice()
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(permissionReceiver) }
        super.onCleared()
    }

    fun refreshDevice() {
        val device = findFastbootDevice()
        _deviceLabel.value = if (device == null) {
            UiText.en(R.string.terminal_no_device)
        } else {
            UiText.en(R.string.terminal_fastboot_connected)
        }
    }

    fun attachFile(uri: Uri) {
        val name = displayName(uri) ?: uri.lastPathSegment ?: "file"
        val current = _attachedFiles.value
        if (current.any { it.uri == uri }) return
        _attachedFiles.value = current + AttachedFile(name, uri)
        printLine(UiText.en(R.string.terminal_file_attached, name), TerminalKind.INFO)
    }

    fun detachFile(name: String) {
        _attachedFiles.value = _attachedFiles.value.filterNot { it.name == name }
    }

    fun execute(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        if (_busy.value) {
            printLine(UiText.en(R.string.terminal_wait), TerminalKind.INFO)
            return
        }
        printLine(UiText.Raw("$ $trimmed"), TerminalKind.COMMAND)
        val tokens = trimmed.split("\\s+".toRegex())
        when (tokens[0].lowercase()) {
            "help" -> printHelp()
            "clear" -> _lines.value = emptyList()
            "devices" -> listDevices()
            else -> runDeviceCommand(tokens)
        }
    }

    private fun printHelp() {
        printLine(UiText.en(R.string.terminal_help_title))
        printLine(UiText.en(R.string.terminal_help_section_device))
        printLine(UiText.en(R.string.terminal_help_devices))
        printLine(UiText.en(R.string.terminal_help_getvar))
        printLine(UiText.raw(""))
        printLine(UiText.en(R.string.terminal_help_section_flash))
        printLine(UiText.en(R.string.terminal_help_flash))
        printLine(UiText.en(R.string.terminal_help_erase))
        printLine(UiText.en(R.string.terminal_help_stage))
        printLine(UiText.en(R.string.terminal_help_set_active))
        printLine(UiText.raw(""))
        printLine(UiText.en(R.string.terminal_help_section_system))
        printLine(UiText.en(R.string.terminal_help_reboot))
        printLine(UiText.en(R.string.terminal_help_oem))
        printLine(UiText.en(R.string.terminal_help_flashing))
        printLine(UiText.raw(""))
        printLine(UiText.en(R.string.terminal_help_section_other))
        printLine(UiText.en(R.string.terminal_help_clear))
        printLine(UiText.en(R.string.terminal_help_help))
        printLine(UiText.raw(""))
        printLine(UiText.en(R.string.terminal_help_files))
    }

    private fun listDevices() {
        val devices = DeviceDiscovery.listDevices(usbManager)
        if (devices.isEmpty()) {
            printLine(UiText.en(R.string.terminal_no_usb_device), TerminalKind.ERROR)
            return
        }
        devices.forEach { printLine(UiText.Raw(it.label)) }
    }

    private fun runDeviceCommand(tokens: List<String>) {
        _busy.value = true
        commandJob?.cancel()
        commandJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = findFastbootDevice() ?: throw FastbootException(str(R.string.no_fastboot_device))
                if (!ensurePermission(device)) throw FastbootException(str(R.string.usb_permission_denied))
                withSession(device) { session -> runCommand(session, tokens) }
                refreshDevice()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printLine(UiText.en(R.string.terminal_fail, e.message ?: str(R.string.failed_generic)), TerminalKind.ERROR)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun runCommand(session: FastbootSession, tokens: List<String>) {
        when (tokens[0].lowercase()) {
            "getvar" -> {
                val name = tokens.getOrNull(1) ?: throw FastbootException(str(R.string.terminal_getvar_usage))
                val value = session.getvarInfo(name)
                if (value.isNullOrEmpty()) throw FastbootException(str(R.string.terminal_read_failed, name))
                printLine(UiText.Raw("$name: $value"))
            }
            "flash" -> {
                val partition = tokens.getOrNull(1) ?: throw FastbootException(str(R.string.terminal_flash_usage))
                val file = resolveFile(tokens.getOrNull(2))
                file.source.use { source ->
                    printLine(UiText.en(R.string.terminal_sending, file.name, formatSize(source.size)), TerminalKind.INFO)
                    session.flash(partition, source)
                }
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            "erase" -> {
                val partition = tokens.getOrNull(1) ?: throw FastbootException(str(R.string.terminal_erase_usage))
                session.erase(partition)
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            "reboot" -> {
                session.reboot(tokens.getOrNull(1))
                printLine(UiText.en(R.string.terminal_device_reboot), TerminalKind.OK)
            }
            "oem" -> {
                val args = tokens.drop(1).joinToString(" ")
                if (args.isEmpty()) throw FastbootException(str(R.string.terminal_oem_usage))
                session.oem(args)
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            "flashing" -> {
                when (tokens.getOrNull(1)?.lowercase()) {
                    "lock" -> session.flashingLock()
                    "unlock" -> session.flashingUnlock()
                    else -> throw FastbootException(str(R.string.terminal_flashing_usage))
                }
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            "set_active" -> {
                val slot = tokens.getOrNull(1) ?: throw FastbootException(str(R.string.terminal_set_active_usage))
                session.setActive(slot)
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            "stage" -> {
                val file = resolveFile(tokens.getOrNull(1))
                file.source.use { source ->
                    printLine(UiText.en(R.string.terminal_sending, file.name, formatSize(source.size)), TerminalKind.INFO)
                    session.download(source)
                }
                printLine(UiText.Raw("OKAY"), TerminalKind.OK)
            }
            else -> printLine(UiText.en(R.string.terminal_unknown_command, tokens[0]), TerminalKind.ERROR)
        }
    }

    private data class ResolvedFile(val name: String, val source: ParcelFdSeekableSource)

    private fun resolveFile(arg: String?): ResolvedFile {
        val files = _attachedFiles.value
        if (arg != null) {
            files.find { it.name.equals(arg, ignoreCase = true) }?.let {
                return ResolvedFile(it.name, openAttached(it))
            }
            return openByPath(arg)
        }
        return when (files.size) {
            0 -> throw FastbootException(str(R.string.terminal_no_file))
            1 -> files[0].let { ResolvedFile(it.name, openAttached(it)) }
            else -> throw FastbootException(str(R.string.terminal_name_file, files.joinToString(", ") { it.name }))
        }
    }

    private fun openByPath(arg: String): ResolvedFile {
        for (file in pathCandidates(arg)) {
            try {
                if (!file.isFile) continue
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                return ResolvedFile(file.name, ParcelFdSeekableSource(pfd))
            } catch (e: Exception) {
                continue
            }
        }
        throw FastbootException(str(R.string.terminal_path_unreadable, arg))
    }

    private fun pathCandidates(arg: String): List<File> {
        val direct = File(arg)
        if (direct.isAbsolute) return listOf(direct)
        val external = Environment.getExternalStorageDirectory()
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return listOf(File(external, arg), File(download, arg), direct)
    }

    private fun openAttached(file: AttachedFile): ParcelFdSeekableSource {
        val pfd = app.contentResolver.openFileDescriptor(file.uri, "r")
            ?: throw FastbootException(str(R.string.terminal_open_failed, file.name))
        return ParcelFdSeekableSource(pfd)
    }

    private fun findFastbootDevice(): UsbDevice? {
        return DeviceDiscovery.listDevices(usbManager)
            .map { it.usbDevice }
            .firstOrNull { device ->
                DeviceDiscovery.classify(device) == DeviceMode.FASTBOOT ||
                    device.vendorId == 0x0BB4 || device.vendorId == 0x0E8D
            }
    }

    private suspend fun ensurePermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        val deferred = CompletableDeferred<Boolean>()
        permissionDeferred = deferred
        val intent = PendingIntent.getBroadcast(
            app, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, intent)
        return withTimeoutOrNull(PERMISSION_TIMEOUT_MS) { deferred.await() } ?: false
    }

    private fun <T> withSession(device: UsbDevice, block: (FastbootSession) -> T): T {
        val transport = FastbootTransport(usbManager, device)
        if (!transport.open()) throw FastbootException(str(R.string.usb_open_failed))
        val session = FastbootSession(transport)
        try {
            return block(session)
        } finally {
            transport.close()
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun printLine(line: UiText, kind: TerminalKind = TerminalKind.OUTPUT) {
        _lines.value = (_lines.value + TerminalLine(line, kind)).takeLast(MAX_LINES)
    }
}
