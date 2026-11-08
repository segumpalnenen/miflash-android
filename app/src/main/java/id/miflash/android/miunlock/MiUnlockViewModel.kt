package id.miflash.android.miunlock

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.miflash.android.common.FileSeekableSource
import id.miflash.android.R
import id.miflash.android.common.FlashLogger
import id.miflash.android.common.UiText
import id.miflash.android.common.resolve
import id.miflash.android.common.resolveEnglish
import id.miflash.android.ui.settings.localizedString
import id.miflash.android.common.LogKind
import id.miflash.android.common.usb.DeviceDiscovery
import id.miflash.android.common.usb.DeviceMode
import id.miflash.android.miflash.fastboot.FastbootException
import id.miflash.android.miflash.fastboot.FastbootSession
import id.miflash.android.miflash.fastboot.FastbootTransport
import id.miflash.android.miunlock.auth.MiAccountAuth
import id.miflash.android.miunlock.auth.MiAccountCredentials
import id.miflash.android.miunlock.auth.MiAccountSessionStore
import id.miflash.android.miunlock.auth.MiServiceSession
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

sealed interface MiLoginState {
    data object LoggedOut : MiLoginState
    data object LoggingIn : MiLoginState
    data class LoggedIn(val userId: String) : MiLoginState
}

sealed interface MiUnlockState {
    data object Idle : MiUnlockState
    data object CheckingDevice : MiUnlockState
    data object DeviceReady : MiUnlockState
    data object FetchingClear : MiUnlockState
    data object AwaitingConfirm : MiUnlockState
    data object Unlocking : MiUnlockState
    data object Done : MiUnlockState
    data class Failed(val message: UiText) : MiUnlockState
}

class MiUnlockViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val ACTION_USB_PERMISSION = "id.miflash.android.MIUNLOCK_USB_PERMISSION"
        private const val PERMISSION_TIMEOUT_MS = 30_000L
    }

    val logger = FlashLogger()

    private val app = getApplication<Application>()
    private val usbManager = app.getSystemService(UsbManager::class.java)
    private val miAuth = MiAccountAuth()
    private val miSessionStore = runCatching { MiAccountSessionStore(getApplication()) }.getOrNull()
    private val miUnlockApi = MiUnlockApi()

    private val _loginState = MutableStateFlow<MiLoginState>(MiLoginState.LoggedOut)
    val loginState: StateFlow<MiLoginState> = _loginState.asStateFlow()

    private val _loginStatus = MutableStateFlow<UiText>(UiText.res(R.string.not_logged_in))
    val loginStatus: StateFlow<UiText> = _loginStatus.asStateFlow()

    private val _unlockState = MutableStateFlow<MiUnlockState>(MiUnlockState.Idle)
    val unlockState: StateFlow<MiUnlockState> = _unlockState.asStateFlow()

    private val _unlockStatus = MutableStateFlow<UiText>(UiText.res(R.string.not_checked))
    val unlockStatus: StateFlow<UiText> = _unlockStatus.asStateFlow()

    private val _unlockProgress = MutableStateFlow(0f)
    val unlockProgress: StateFlow<Float> = _unlockProgress.asStateFlow()

    private val _unlockProduct = MutableStateFlow<String?>(null)
    val unlockProduct: StateFlow<String?> = _unlockProduct.asStateFlow()

    private val _unlockToken = MutableStateFlow<String?>(null)
    val unlockToken: StateFlow<String?> = _unlockToken.asStateFlow()

    private val _unlockNotice = MutableStateFlow("")
    val unlockNotice: StateFlow<String> = _unlockNotice.asStateFlow()

    private val _unlockWipesData = MutableStateFlow(false)
    val unlockWipesData: StateFlow<Boolean> = _unlockWipesData.asStateFlow()

    private var serviceJob: Job? = null
    private var unlockJob: Job? = null
    private var miCredentials: MiAccountCredentials? = null
    private var miServiceSession: MiServiceSession? = null

    private fun str(resId: Int, vararg args: Any): String =
        localizedString(app, resId, *args)

    private var unlockDevice: UsbDevice? = null
    private var unlockHst: MiUnlockHst? = null
    private var unlockNonce: String? = null
    private var unlockProductValue: String? = null
    private var unlockTokenValue: String? = null
    private var permissionDeferred: CompletableDeferred<Boolean>? = null

    val miAccount: MiAccountCredentials?
        get() = miCredentials

    val miSession: MiServiceSession?
        get() = miServiceSession

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            permissionDeferred?.complete(granted)
            permissionDeferred = null
        }
    }

    init {
        restoreMiSession()
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(permissionReceiver, filter)
        }
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(permissionReceiver) }
        super.onCleared()
    }

    fun beginLogin() {
        if (_loginState.value is MiLoginState.LoggingIn) return
        _loginState.value = MiLoginState.LoggingIn
        _loginStatus.value = UiText.res(R.string.login_waiting)
    }

    fun completeLogin(credentials: MiAccountCredentials) {
        runCatching { miSessionStore?.save(credentials) }
        miCredentials = credentials
        _loginState.value = MiLoginState.LoggedIn(credentials.userId)
        _loginStatus.value = UiText.res(R.string.logged_in_as, credentials.userId)
        logger.log(R.string.log_login_success, kind = LogKind.SUCCESS)
        refreshServiceSession()
    }

    fun onLoginCancelled() {
        if (_loginState.value !is MiLoginState.LoggingIn) return
        _loginState.value = MiLoginState.LoggedOut
        _loginStatus.value = UiText.res(R.string.not_logged_in)
    }

    fun logout() {
        serviceJob?.cancel()
        unlockJob?.cancel()
        runCatching { miSessionStore?.clear() }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        miCredentials = null
        miServiceSession = null
        _loginState.value = MiLoginState.LoggedOut
        _loginStatus.value = UiText.res(R.string.not_logged_in)
        resetUnlock()
        logger.log(R.string.log_logout)
    }

    fun checkDevice() {
        if (miCredentials == null) {
            failUnlock(UiText.res(R.string.login_first_err))
            return
        }
        val state = _unlockState.value
        if (state is MiUnlockState.CheckingDevice || state is MiUnlockState.FetchingClear || state is MiUnlockState.Unlocking) return
        unlockJob?.cancel()
        unlockJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                resetUnlockData()
                setUnlock(MiUnlockState.CheckingDevice, UiText.res(R.string.searching_device), 0.1f)
                val device = findFastbootDevice() ?: throw UnlockStepException(str(R.string.no_fastboot_device))
                unlockDevice = device
                if (!ensurePermission(device)) throw UnlockStepException(str(R.string.usb_permission_denied))
                val product = withFastbootSession(device) { session ->
                    session.getvarInfo("product")?.replace("\\s".toRegex(), "")
                }.orEmpty()
                if (product.isEmpty()) throw UnlockStepException(str(R.string.read_product_failed))
                val token = withFastbootSession(device) { session ->
                    val raw = session.getvarInfo("token") ?: session.oemInfo("get_token")
                    raw?.replace("\\s".toRegex(), "")
                }.orEmpty()
                if (token.isEmpty()) throw UnlockStepException(str(R.string.read_token_failed))
                unlockProductValue = product
                unlockTokenValue = token
                _unlockProduct.value = product
                _unlockToken.value = token
                logger.log(R.string.log_unlock_device, product)
                setUnlock(MiUnlockState.DeviceReady, UiText.res(R.string.device_found), 0.3f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failUnlock(e)
            }
        }
    }

    fun continueUnlock() {
        val credentials = miCredentials ?: run { failUnlock(UiText.res(R.string.login_first_err)); return }
        val product = unlockProductValue ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        if (_unlockState.value !is MiUnlockState.DeviceReady) return
        unlockJob?.cancel()
        unlockJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                setUnlock(MiUnlockState.FetchingClear, UiText.res(R.string.contacting_server), 0.5f)
                val hst = miUnlockApi.fetchHst(credentials)
                unlockHst = hst
                val nonceJson = miUnlockApi.nonce(hst.host, hst.ssecurity, hst.serviceToken)
                val nonce = nonceJson.optString("nonce", "")
                if (nonce.isEmpty()) throw UnlockStepException(serverMessage(nonceJson))
                unlockNonce = nonce
                val clearJson = miUnlockApi.deviceClear(hst.host, hst.ssecurity, hst.serviceToken, product, nonce)
                val clearError = serverError(clearJson)
                if (clearError != null) throw UnlockStepException(clearError)
                val notice = clearJson.optString("notice", "")
                val cleanOrNot = clearJson.optInt("cleanOrNot", -1)
                _unlockNotice.value = notice
                _unlockWipesData.value = cleanOrNot == 1
                logger.log(R.string.log_unlock_notice, notice)
                if (cleanOrNot == 1) logger.log(R.string.log_unlock_wipes, kind = LogKind.WARNING)
                setUnlock(MiUnlockState.AwaitingConfirm, UiText.res(R.string.unlock_ready), 0.8f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failUnlock(e)
            }
        }
    }

    fun confirmUnlock() {
        val credentials = miCredentials ?: run { failUnlock(UiText.res(R.string.login_first_err)); return }
        val hst = unlockHst ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        val nonce = unlockNonce ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        val product = unlockProductValue ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        val token = unlockTokenValue ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        val device = unlockDevice ?: run { failUnlock(UiText.res(R.string.check_device_first)); return }
        if (_unlockState.value !is MiUnlockState.AwaitingConfirm) return
        unlockJob?.cancel()
        unlockJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                setUnlock(MiUnlockState.Unlocking, UiText.res(R.string.requesting_unlock), 0.85f)
                val pcId = miUnlockApi.pcId(credentials.deviceId)
                val result = miUnlockApi.ahaUnlock(hst.host, hst.ssecurity, hst.serviceToken, token, product, pcId, credentials.userId, nonce)
                val ahaError = serverError(result)
                if (ahaError != null) throw UnlockStepException(ahaError)
                val encryptData = result.optString("encryptData", "")
                if (encryptData.isEmpty()) throw UnlockStepException(str(R.string.encryptdata_empty))
                val bytes = hexToBytes(encryptData)
                val file = File(app.filesDir, "encryptData")
                file.outputStream().use { it.write(bytes) }
                logger.log(R.string.log_encryptdata_expiry, kind = LogKind.WARNING)
                setUnlock(MiUnlockState.Unlocking, UiText.res(R.string.sending_unlock), 0.95f)
                withFastbootSession(device) { session ->
                    FileSeekableSource(file).use { source ->
                        session.download(source)
                    }
                    session.oem("unlock")
                }
                setUnlock(MiUnlockState.Done, UiText.res(R.string.unlock_success), 1f)
                logger.log(R.string.log_unlock_success, kind = LogKind.SUCCESS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failUnlock(e)
            }
        }
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

    private fun <T> withFastbootSession(device: UsbDevice, block: (FastbootSession) -> T): T {
        val transport = FastbootTransport(usbManager, device)
        if (!transport.open()) throw FastbootException(str(R.string.usb_open_failed))
        val session = FastbootSession(transport, logger)
        try {
            return block(session)
        } finally {
            transport.close()
        }
    }

    private fun setUnlock(state: MiUnlockState, status: UiText, progress: Float) {
        _unlockState.value = state
        _unlockStatus.value = status
        _unlockProgress.value = progress
    }

    private fun failUnlock(e: Exception) {
        failUnlock(UiText.Raw(e.message ?: str(R.string.failed_generic)))
    }

    private fun failUnlock(message: UiText) {
        _unlockState.value = MiUnlockState.Failed(message)
        _unlockStatus.value = message
        _unlockProgress.value = 0f
        logger.log(R.string.log_unlock_failed, message.resolveEnglish(app), kind = LogKind.ERROR)
    }

    private fun resetUnlock() {
        resetUnlockData()
        _unlockState.value = MiUnlockState.Idle
        _unlockStatus.value = UiText.res(R.string.not_checked)
        _unlockProgress.value = 0f
    }

    private fun resetUnlockData() {
        unlockDevice = null
        unlockHst = null
        unlockNonce = null
        unlockProductValue = null
        unlockTokenValue = null
        _unlockProduct.value = null
        _unlockToken.value = null
        _unlockNotice.value = ""
        _unlockWipesData.value = false
    }

    private fun serverError(json: JSONObject): String? {
        if (json.has("error")) return json.optString("error")
        val code = json.optInt("code", 0)
        if (code != 0) {
            val desc = when {
                json.has("descEN") -> json.optString("descEN")
                json.has("description") -> json.optString("description")
                else -> str(R.string.error_code, code)
            }
            return "$desc ${str(R.string.code_suffix, code)}"
        }
        return null
    }

    private fun serverMessage(json: JSONObject): String {
        return serverError(json) ?: json.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { str(R.string.hex_invalid) }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun refreshServiceSession() {
        val credentials = miCredentials ?: return
        serviceJob?.cancel()
        serviceJob = viewModelScope.launch(Dispatchers.IO) {
            miServiceSession = runCatching { miAuth.getService(credentials) }.getOrNull()
            if (miServiceSession == null) logger.log(R.string.log_service_session_failed, kind = LogKind.WARNING)
        }
    }

    private fun restoreMiSession() {
        val credentials = runCatching { miSessionStore?.load() }.getOrNull() ?: return
        miCredentials = credentials
        _loginState.value = MiLoginState.LoggedIn(credentials.userId)
        _loginStatus.value = UiText.res(R.string.logged_in_as, credentials.userId)
        viewModelScope.launch(Dispatchers.IO) {
            miServiceSession = runCatching { miAuth.getService(credentials) }.getOrNull()
            if (miServiceSession == null) logger.log(R.string.log_service_session_expired, kind = LogKind.WARNING)
        }
    }
}

private class UnlockStepException(message: String) : Exception(message)
