package id.miflash.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import id.miflash.android.miflash.MiFlashScreen
import id.miflash.android.miflash.MiFlashViewModel
import id.miflash.android.miflash.terminal.FastbootTerminalScreen
import id.miflash.android.miflash.terminal.FastbootTerminalViewModel
import id.miflash.android.miunlock.MiUnlockScreen
import id.miflash.android.miunlock.MiUnlockViewModel
import id.miflash.android.ui.AppScreen
import id.miflash.android.ui.HomeScreen
import id.miflash.android.ui.components.AppSidebar
import id.miflash.android.ui.settings.LocaleHelper
import id.miflash.android.ui.settings.SettingsScreen
import id.miflash.android.ui.settings.SettingsStore
import id.miflash.android.ui.settings.readSavedLanguageCode
import id.miflash.android.ui.theme.MiFlashBackground
import id.miflash.android.ui.theme.MiFlashTheme
import id.miflash.android.ui.theme.ThemeMode
import id.miflash.android.ui.theme.paletteFor
import id.miflash.android.ui.theme.themePalettes
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val flashViewModel: MiFlashViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MiFlashViewModel(application as Application) as T
            }
        }
    }

    private val unlockViewModel: MiUnlockViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MiUnlockViewModel(application as Application) as T
            }
        }
    }

    private val terminalViewModel: FastbootTerminalViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FastbootTerminalViewModel(application as Application) as T
            }
        }
    }

    private val settingsStore by lazy { SettingsStore(applicationContext) }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MiFlashViewModel.ACTION_USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        flashViewModel.onPermissionGranted()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> flashViewModel.refreshDevices()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, readSavedLanguageCode(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(MiFlashViewModel.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
        flashViewModel.refreshDevices()
        setContent {
            val settings by settingsStore.settings.collectAsState()
            val palettes = themePalettes()
            val palette = paletteFor(palettes, settings.paletteKey)
            val dark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MiFlashTheme(dark = dark, palette = palette) {
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                var screen by remember { mutableStateOf(AppScreen.HOME) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                BackHandler(enabled = screen != AppScreen.HOME) {
                    screen = AppScreen.HOME
                }
                Box(modifier = Modifier.fillMaxSize().background(MiFlashBackground)) {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                        AppSidebar(
                            selected = screen,
                            onSelect = {
                                screen = it
                                scope.launch { drawerState.close() }
                            },
                            onClose = {
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(
                            onSelect = { screen = it },
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        AppScreen.MIFLASH -> MiFlashScreen(
                            flashViewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        AppScreen.MIUNLOCK -> MiUnlockScreen(
                            unlockViewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        AppScreen.FASTBOOT_TERMINAL -> FastbootTerminalScreen(
                            terminalViewModel,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            settings = settings,
                            palettes = palettes,
                            onThemeMode = { settingsStore.setThemeMode(it) },
                            onPalette = { settingsStore.setPalette(it) },
                            onLanguage = {
                                settingsStore.setLanguage(it)
                                recreate()
                            },
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                }
                }
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbReceiver) }
        super.onDestroy()
    }
}
