package com.titan.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * A small, user-visible foreground service that helps Xiaomi/MIUI keep the
 * app process alive when its launcher task is dismissed from Recents.
 * It does not replace the AccessibilityService and cannot override Force stop.
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ask Android to recreate this service if the process is reclaimed.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TitanBlock protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the active focus protection service running."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("TitanBlock protection active")
                .setContentText("Focus protection is running.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("TitanBlock protection active")
                .setContentText("Focus protection is running.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "titanblock_protection"
        private const val NOTIFICATION_ID = 4107

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // The UI and AccessibilityService can still operate if startup is restricted.
            }
        }
    }
}
