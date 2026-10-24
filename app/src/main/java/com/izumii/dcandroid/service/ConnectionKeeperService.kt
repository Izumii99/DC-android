package com.izumii.dcandroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.izumii.dcandroid.MainActivity
import com.izumii.dcandroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service that prevents Android and OEM battery managers (MIUI/HyperOS, OneUI)
 * from freezing CPU threads or closing active Discord voice/chat sessions.
 */
class ConnectionKeeperService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var pulseJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val CHANNEL_ID = "dc_connection_keeper"
        private const val CHAT_CHANNEL_ID = "dc_chat_notifications"
        private const val NOTIFICATION_ID = 2001

        var onPulseListener: (() -> Unit)? = null

        fun buildNotification(context: Context, unreadCount: Int = 0): Notification {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = if (unreadCount > 0) "DC ($unreadCount)" else context.getString(R.string.service_running_title)
            val text = if (unreadCount > 0) {
                "$unreadCount new mention${if (unreadCount > 1) "s" else ""}"
            } else {
                context.getString(R.string.service_running_desc)
            }
            val icon = R.drawable.ic_discord_logo

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setOngoing(true)
                .setPriority(if (unreadCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .apply {
                    if (unreadCount > 0) setNumber(unreadCount)
                }
                .build()
        }

        fun updateBadgeCount(context: Context, count: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            try {
                manager.notify(NOTIFICATION_ID, buildNotification(context, count))
            } catch (ignored: Exception) {}
        }

        fun showChatNotification(context: Context, title: String, message: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val notif = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_discord_logo)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build()

            try {
                manager.notify((System.currentTimeMillis() % 10000).toInt() + 3000, notif)
            } catch (ignored: Exception) {}
        }

        fun start(context: Context) {
            val intent = Intent(context, ConnectionKeeperService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (ignored: Exception) {}
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ConnectionKeeperService::class.java))
            } catch (ignored: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DCAndroid:ConnectionKeeperWakeLock")?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Periodic keep-alive pulse to maintain WebSocket and Anti-AFK activity
        // 1.5s Pulse Guard: briefly acquires wake lock only during pulse, keeping CPU asleep 96.5% of the time
        pulseJob?.cancel()
        pulseJob = serviceScope.launch {
            while (isActive) {
                delay(42_000L)
                try {
                    wakeLock?.acquire(1_500L)
                    onPulseListener?.invoke()
                } catch (ignored: Exception) {}
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pulseJob?.cancel()
        pulseJob = null
        onPulseListener = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {}
        wakeLock = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val keeperChannel = NotificationChannel(
                CHANNEL_ID,
                "Discord Connection Keeper",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains Discord WebSocket and background audio/voice sessions"
                setShowBadge(false)
            }
            manager.createNotificationChannel(keeperChannel)

            val chatChannel = NotificationChannel(
                CHAT_CHANNEL_ID,
                "Discord Messages & Mentions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for Discord DMs and mentions"
                enableVibration(true)
            }
            manager.createNotificationChannel(chatChannel)
        }
    }
}
