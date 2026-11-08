package id.miflash.android.miflash.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import id.miflash.android.R
import id.miflash.android.common.UiText
import id.miflash.android.common.resolve
import id.miflash.android.ui.settings.englishString
import kotlinx.coroutines.launch

class FlashService : Service() {

    companion object {
        const val ACTION_START = "id.miflash.android.START_FLASH"
        const val EXTRA_MODE = "mode"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "flash"
        private const val CHANNEL_NAME = "Flash berjalan"

        fun start(context: Context, modeLabel: UiText) {
            val intent = Intent(context, FlashService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, modeLabel.resolve(context))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlashService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var modeLabel = "Flash"
    private var lastNotifiedAt = 0L
    private var lastFraction = -1f

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            modeLabel = intent.getStringExtra(EXTRA_MODE) ?: "Flash"
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MiFlashAndroid::flash"
            )?.apply { acquire() }
            startForegroundWithType(buildNotification(0f, UiText.en(R.string.flashing_status)))
            scope.launch {
                FlashProgress.fraction.collect { fraction ->
                    maybeNotify(fraction, FlashProgress.message.value)
                }
            }
            scope.launch {
                FlashProgress.message.collect { message ->
                    maybeNotify(FlashProgress.fraction.value, message)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun maybeNotify(fraction: Float, message: UiText) {
        val now = System.currentTimeMillis()
        val percent = (fraction * 100).toInt()
        val lastPercent = (lastFraction * 100).toInt()
        if (fraction >= 1f || percent != lastPercent || now - lastNotifiedAt >= 500) {
            lastNotifiedAt = now
            lastFraction = fraction
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, buildNotification(fraction, message))
        }
    }

    private fun buildNotification(fraction: Float, message: UiText): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("MiTool — $modeLabel")
            .setContentText(if (message.isEmpty) englishString(this, R.string.flashing_status) else message.resolve(this))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (fraction * 100).toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
