package com.titan.blocker

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Optional system-level enforcement. This works only after the user provisions
 * TitanBlock as Device Owner with ADB. Accessibility remains the fallback.
 */
object DeviceOwnerProtection {

    private const val PREFS = "titan_prefs"
    private const val KEY_LOCK_END = "lock_end_time"
    private const val KEY_BLOCKED_APPS = "blocked_apps"

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    @Suppress("DEPRECATION")
    fun removeOwner(context: Context): Boolean {
        if (!isDeviceOwner(context)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
            )
            val json = prefs.getString(KEY_BLOCKED_APPS, "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            val packages: List<String> = try {
                Gson().fromJson<List<String>>(json, type) ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }
            val targets = packages.filter { it.isNotBlank() && it != context.packageName }.distinct().toTypedArray()
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(context, TitanDeviceAdminReceiver::class.java)
            return try {
                if (targets.isNotEmpty()) dpm.setPackagesSuspended(admin, targets, false)
                cancelExpiry(context)
                dpm.clearDeviceOwnerApp(context.packageName)
                !dpm.isDeviceOwnerApp(context.packageName)
            } catch (_: Throwable) {
                false
            }
        }
        return false
    }

    /**
     * Returns packages that Android refused to suspend, null if Device Owner
     * mode is not enabled or the API is unavailable.
     */
    fun refresh(context: Context): List<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isDeviceOwner(context)) {
            return null
        }

        val prefs = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )
        val endTime = prefs.getLong(KEY_LOCK_END, 0L)
        val json = prefs.getString(KEY_BLOCKED_APPS, "[]") ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        val packages: List<String> = try {
            Gson().fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }

        val targets = packages
            .filter { it.isNotBlank() && it != context.packageName }
            .distinct()
            .toTypedArray()

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, TitanDeviceAdminReceiver::class.java)

        return try {
            if (endTime > System.currentTimeMillis() && targets.isNotEmpty()) {
                val failed = dpm.setPackagesSuspended(admin, targets, true).toList()
                scheduleExpiry(context, endTime)
                failed
            } else {
                if (targets.isNotEmpty()) {
                    dpm.setPackagesSuspended(admin, targets, false)
                }
                cancelExpiry(context)
                emptyList()
            }
        } catch (_: SecurityException) {
            // Device Owner provisioning may have been removed or refused.
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun scheduleExpiry(context: Context, endTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = expiryPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Fallback may run a little after the selected end time, never early.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endTime, pendingIntent)
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            }
        }
    }

    private fun cancelExpiry(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(expiryPendingIntent(context))
    }

    private fun expiryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockExpiryReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, 7103, intent, flags)
    }
}
