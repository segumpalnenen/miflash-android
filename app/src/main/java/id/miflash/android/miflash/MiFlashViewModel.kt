package id.miflash.android.miflash

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.miflash.android.miflash.edl.SlaSigner
import id.miflash.android.miflash.edl.UnimplementedSlaSigner
import id.miflash.android.common.DeviceState
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.UiText
import id.miflash.android.ui.settings.englishString
import id.miflash.android.ui.settings.localizedString
import id.miflash.android.common.LogKind
import id.miflash.android.miflash.service.FlashProgress
import id.miflash.android.miflash.service.FlashService
import id.miflash.android.miflash.service.PartitionProgress
import id.miflash.android.miflash.fastboot.FastbootException
import id.miflash.android.miflash.fastboot.FastbootFlasher
import id.miflash.android.miflash.fastboot.FastbootRomScript
import id.miflash.android.miflash.fastboot.FastbootRomSource
import id.miflash.android.miflash.fastboot.FastbootScriptOption
import id.miflash.android.miflash.fastboot.FastbootSession
import id.miflash.android.miflash.fastboot.FastbootTransport
import id.miflash.android.miflash.edl.AuthRequiredException
import id.miflash.android.miflash.edl.EdlFlasher
import id.miflash.android.miflash.edl.FirmwareException
import id.miflash.android.miflash.edl.FirmwareSource
import id.miflash.android.R
import id.miflash.android.common.usb.DeviceDiscovery
import id.miflash.android.common.usb.DeviceMode
import id.miflash.android.common.usb.UsbHostTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MiFlashViewModel(
    application: Application,
    val signer: SlaSigner = UnimplementedSlaSigner()
) : AndroidViewModel(application) {

    companion object {
        const val ACTION_USB_PERMISSION = "id.miflash.android.USB_PERMISSION"
        private const val PREFS_NAME = "miflash"
        private const val KEY_FIRMWARE_TREE_URI = "firmware_tree_uri"
        private const val KEY_PENDING_LOCK = "pending_lock"
    }

    val logger = FlashLogger()

    private val usbManager = application.getSystemService(UsbManager::class.java)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _devices = MutableStateFlow<List<DeviceState>>(emptyList())
    val devices: StateFlow<List<DeviceState>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DeviceState?>(null)
    val selectedDevice: StateFlow<DeviceState?> = _selectedDevice.asStateFlow()

    private val _firmwareSource = MutableStateFlow<FirmwareSource?>(null)

    private val _firmwareLabel = MutableStateFlow<String?>(null)
    val firmwareLabel: StateFlow<String?> = _firmwareLabel.asStateFlow()

    private val _fastbootInfo = MutableStateFlow<FastbootFlasher.DeviceInfo?>(null)
    val fastbootInfo: StateFlow<FastbootFlasher.DeviceInfo?> = _fastbootInfo.asStateFlow()

    private val _fastbootScripts = MutableStateFlow<List<FastbootScriptOption>>(emptyList())
    val fastbootScripts: StateFlow<List<FastbootScriptOption>> = _fastbootScripts.asStateFlow()

    private val _availableOptions = MutableStateFlow(FlashOption.values().toList())
    val availableOptions: StateFlow<List<FlashOption>> = _availableOptions.asStateFlow()

    private val _selectedOption = MutableStateFlow(FlashOption.CLEAN_ALL)
    val selectedOption: StateFlow<FlashOption> = _selectedOption.asStateFlow()

    private val _pendingLock = MutableStateFlow(prefs.getBoolean(KEY_PENDING_LOCK, false))
    val pendingLock: StateFlow<Boolean> = _pendingLock.asStateFlow()

    private val _flashing = MutableStateFlow(false)
    val flashing: StateFlow<Boolean> = _flashing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _status = MutableStateFlow<UiText>(UiText.Raw(""))
    val status: StateFlow<UiText> = _status.asStateFlow()

    val partitions: StateFlow<List<PartitionProgress>> = FlashProgress.partitions

    private val _currentStep = MutableStateFlow<UiText>(UiText.Raw(""))
    val currentStep: StateFlow<UiText> = _currentStep.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _cancelling = MutableStateFlow(false)
    val cancelling: StateFlow<Boolean> = _cancelling.asStateFlow()

    private var flashJob: Job? = null
    private var tickerJob: Job? = null

    val signerProvided: Boolean
        get() = signer !is UnimplementedSlaSigner

    private fun str(resId: Int, vararg args: Any): String =
        localizedString(getApplication(), resId, *args)

    private fun strEn(resId: Int, vararg args: Any): String =
        englishString(getApplication(), resId, *args)

    val isFastbootDevice: Boolean
        get() = _selectedDevice.value?.discovered?.mode == DeviceMode.FASTBOOT

    init {
        restoreFirmwareTree()
    }

    fun refreshDevices() {
        val found = DeviceDiscovery.listDevices(usbManager).map { discovered ->
            DeviceState(discovered, usbManager.hasPermission(discovered.usbDevice))
        }
        _devices.value = found
        logger.log(R.string.log_devices_found, found.size)
        val selected = _selectedDevice.value
        if (selected != null && found.none { it.discovered.usbDevice == selected.discovered.usbDevice }) {
            _selectedDevice.value = null
            _fastbootInfo.value = null
            refreshOptions()
        }
    }

    fun selectDevice(device: DeviceState) {
        _selectedDevice.value = device
        _fastbootInfo.value = null
        refreshOptions()
        if (!device.hasPermission) {
            val intent = PendingIntent.getBroadcast(
                getApplication(),
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device.discovered.usbDevice, intent)
        } else if (device.discovered.mode == DeviceMode.FASTBOOT) {
            queryFastbootInfo(device)
        }
    }

    fun onPermissionGranted() {
        refreshDevices()
        val selected = _selectedDevice.value
        if (selected != null && selected.discovered.mode == DeviceMode.FASTBOOT) {
            queryFastbootInfo(selected)
        }
    }

    fun selectOption(option: FlashOption) {
        if (option in _availableOptions.value) {
            _selectedOption.value = option
        }
    }

    private fun refreshOptions() {
        val device = _selectedDevice.value
        val options = if (device?.discovered?.mode == DeviceMode.FASTBOOT) {
            _fastbootScripts.value.map { it.option }
        } else {
            FlashOption.values().toList()
        }
        _availableOptions.value = options
        val selected = _selectedOption.value
        _selectedOption.value = when {
            selected in options -> selected
            FlashOption.CLEAN_ALL in options -> FlashOption.CLEAN_ALL
            else -> options.firstOrNull() ?: FlashOption.CLEAN_ALL
        }
    }

    private fun queryFastbootInfo(device: DeviceState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flasher = FastbootFlasher(usbManager, device.discovered.usbDevice, logger)
                val info = flasher.queryInfo()
                withContext(Dispatchers.Main) {
                    _fastbootInfo.value = info
                    logger.log(R.string.log_fastboot_info, info.product ?: "?", info.version ?: "?")
                }
            } catch (e: Exception) {
                logger.log(R.string.log_fastboot_no_response, e.message ?: "", kind = LogKind.WARNING)
            }
        }
    }

    private fun refreshFastbootScripts() {
        val source = _firmwareSource.value
        if (source == null) {
            _fastbootScripts.value = emptyList()
            refreshOptions()
            return
        }
        val scripts = runCatching {
            FastbootRomSource(source).availableScripts()
        }.getOrDefault(emptyList())
        _fastbootScripts.value = scripts
        refreshOptions()
    }

    private fun restoreFirmwareTree() {
        val saved = prefs.getString(KEY_FIRMWARE_TREE_URI, null) ?: return
        val uri = try {
            Uri.parse(saved)
        } catch (e: Exception) {
            prefs.edit().remove(KEY_FIRMWARE_TREE_URI).apply()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = FirmwareSource.open(getApplication(), uri, logger)
                _firmwareSource.value = source
                _firmwareLabel.value = source.label
                logger.log(R.string.log_firmware_restored, source.label)
                _status.value = UiText.res(R.string.firmware_ready)
                withContext(Dispatchers.Main) { refreshFastbootScripts() }
            } catch (e: Exception) {
                prefs.edit().remove(KEY_FIRMWARE_TREE_URI).apply()
                _firmwareSource.value = null
                _firmwareLabel.value = null
                _status.value = UiText.res(R.string.folder_lost)
                logger.log(R.string.log_firmware_saved_unreadable, e.message ?: "", kind = LogKind.ERROR)
            }
        }
    }

    fun setFirmwareTree(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    throw FirmwareException("Izin folder tidak bisa disimpan — pilih ulang folder firmware", e)
                }
                prefs.edit().putString(KEY_FIRMWARE_TREE_URI, uri.toString()).apply()
                logger.log(R.string.log_opening_firmware)
                val source = FirmwareSource.open(getApplication(), uri, logger)
                _firmwareSource.value = source
                _firmwareLabel.value = source.label
                _status.value = UiText.res(R.string.firmware_ready)
                withContext(Dispatchers.Main) { refreshFastbootScripts() }
            } catch (e: Exception) {
                _status.value = UiText.res(R.string.firmware_read_failed, e.message ?: "")
                logger.log(R.string.log_firmware_read_failed, e.message ?: "", kind = LogKind.ERROR)
            }
        }
    }

    fun flash() {
        val device = _selectedDevice.value
        if (device == null) {
            _status.value = UiText.res(R.string.select_device)
            return
        }
        if (!device.hasPermission) {
            _status.value = UiText.res(R.string.usb_permission_missing)
            return
        }
        val firmware = _firmwareSource.value
        if (firmware == null) {
            _status.value = UiText.res(R.string.select_firmware)
            return
        }
        val option = _selectedOption.value
        when (device.discovered.mode) {
            DeviceMode.EDL_9008 -> {
                if (!signerProvided) {
                    _status.value = UiText.res(R.string.signer_missing_flash)
                    logger.log(R.string.log_flash_denied_signer, kind = LogKind.ERROR)
                    return
                }
                runFlash(UiText.Raw("EDL"), onSuccess = {
                    if (option == FlashOption.CLEAN_ALL_AND_LOCK) {
                        prefs.edit().putBoolean(KEY_PENDING_LOCK, true).apply()
                        _pendingLock.value = true
                        _status.value = UiText.res(R.string.lock_done_status)
                        logger.log(R.string.log_edl_done_lock_pending, kind = LogKind.SUCCESS)
                    }
                }) { onProgress ->
                    val transport = UsbHostTransport(usbManager, device.discovered.usbDevice)
                    val flasher = EdlFlasher(transport, signer)
                    flasher.flash(firmware, option, logger) { fraction, message ->
                        onProgress(fraction, message)
                    }
                }
            }
            DeviceMode.FASTBOOT -> {
                val rom = FastbootRomSource(firmware)
                val script = rom.scriptFor(option)
                if (script == null) {
                    _status.value = UiText.res(R.string.script_missing, str(option.labelRes))
                    return
                }
                runFlash(UiText.Raw("Fastboot")) { onProgress ->
                    val text = rom.readScript(script.fileName)
                    val steps = FastbootRomScript.parse(text, script.fileName.lowercase().endsWith(".bat"))
                    if (steps.isEmpty()) throw FastbootException(str(R.string.script_empty, script.fileName))
                    logger.log(R.string.log_script, strEn(script.labelRes), script.fileName, steps.size, kind = LogKind.STAGE)
                    val flasher = FastbootFlasher(usbManager, device.discovered.usbDevice, logger)
                    flasher.flash(rom, steps) { fraction, message ->
                        onProgress(fraction, message)
                    }
                }
            }
            DeviceMode.UNKNOWN -> {
                _status.value = UiText.res(R.string.not_edl_fastboot)
            }
        }
    }

    fun lockBootloader() {
        val device = _selectedDevice.value
        if (device == null || device.discovered.mode != DeviceMode.FASTBOOT) {
            _status.value = UiText.res(R.string.lock_needs_fastboot)
            return
        }
        if (!device.hasPermission) {
            _status.value = UiText.res(R.string.usb_permission_missing)
            return
        }
        runFlash(UiText.en(R.string.lock_bootloader)) { onProgress ->
            onProgress(0.1f, UiText.Raw("Fastboot: flashing lock"))
            val transport = FastbootTransport(usbManager, device.discovered.usbDevice)
            val session = FastbootSession(transport, logger)
            try {
                if (!session.open()) throw FastbootException("Tidak bisa membuka koneksi fastboot")
                session.flashingLock()
            } finally {
                session.close()
            }
            onProgress(1f, UiText.en(R.string.bootloader_locked))
            prefs.edit().putBoolean(KEY_PENDING_LOCK, false).apply()
            _pendingLock.value = false
            logger.log(R.string.log_bootloader_locked, kind = LogKind.SUCCESS)
        }
    }

    fun cancelFlash() {
        if (!_flashing.value || _cancelling.value) return
        _cancelling.value = true
        _status.value = UiText.res(R.string.cancelling_flash)
        logger.log(R.string.log_flash_cancelled_warn, kind = LogKind.WARNING)
        FlashProgress.requestCancel()
        flashJob?.cancel()
    }

    private fun runFlash(
        modeLabel: UiText,
        onSuccess: (() -> Unit)? = null,
        work: (onProgress: (Float, UiText) -> Unit) -> Unit
    ) {
        if (_flashing.value) return
        _flashing.value = true
        _cancelling.value = false
        _progress.value = 0f
        _currentStep.value = UiText.Raw("")
        _elapsedMs.value = 0L
        FlashProgress.reset()
        _status.value = UiText.res(R.string.flashing_status)
        val app = getApplication<Application>()
        try {
            FlashService.start(app, modeLabel)
        } catch (e: Exception) {
            _flashing.value = false
            _status.value = UiText.res(R.string.fgs_failed, e.message ?: "")
            logger.log(R.string.log_fgs_failed, e.message ?: "", kind = LogKind.ERROR)
            return
        }
        val startMs = System.currentTimeMillis()
        tickerJob = viewModelScope.launch {
            while (_flashing.value) {
                _elapsedMs.value = System.currentTimeMillis() - startMs
                delay(500)
            }
        }
        flashJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                work { fraction, message ->
                    _progress.value = fraction
                    _currentStep.value = message
                    FlashProgress.update(fraction, message)
                    logger.log(message, LogKind.STAGE)
                }
                withContext(Dispatchers.Main) {
                    if (onSuccess == null) _status.value = UiText.res(R.string.flash_done)
                    onSuccess?.invoke()
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.Main) {
                    _status.value = UiText.res(R.string.flash_cancelled)
                    logger.log(R.string.log_flash_cancelled, kind = LogKind.WARNING)
                }
                throw e
            } catch (e: AuthRequiredException) {
                withContext(Dispatchers.Main) {
                    _status.value = UiText.res(R.string.auth_failed, e.message ?: "")
                    logger.log(R.string.log_auth_failed, e.message ?: "", kind = LogKind.ERROR)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _status.value = UiText.res(R.string.flash_failed, e.message ?: "")
                    logger.log(R.string.log_flash_failed, e.message ?: "", kind = LogKind.ERROR)
                }
            } finally {
                FlashService.stop(app)
                tickerJob?.cancel()
                withContext(NonCancellable + Dispatchers.Main) {
                    _flashing.value = false
                    _cancelling.value = false
                }
            }
        }
    }
}
