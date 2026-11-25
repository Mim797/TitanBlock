package com.titan.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class BlockerService : AccessibilityService() {

    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    // Prevent repeated overlay launches while the same blocked app remains foreground.
    private var lastBlockedPackage: String? = null
    private var lastBlockAttemptAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Keep the user-visible guardian alive independently of the launcher task.
        KeepAliveService.start(applicationContext)

        // Apply the event configuration here as well as in XML. This helps keep
        // the service configuration consistent after Android reconnects it.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            notificationTimeout = 100
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val relevantEvents =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
        if ((event.eventType and relevantEvents) == 0) return

        val currentPackage = event.packageName?.toString() ?: return

        // Never inspect or block TitanBlock itself.
        if (currentPackage == packageName) return

        try {
            val prefs = getSharedPreferences("titan_prefs", Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
            val now = System.currentTimeMillis()
            val lockEndTime = prefs.getLong("lock_end_time", 0L)
            val isLocked = now < lockEndTime

            if (!isLocked) {
                return
            }

            // Temporary emergency pass.
            val tempPassUntil = prefs.getLong("temp_pass_time", 0L)
            if (now < tempPassUntil) {
                return
            }

            // Guard Android's main Settings app when the setting is enabled.
            val protectSettings = prefs.getBoolean("protect_settings", true)
            if (protectSettings && currentPackage == "com.android.settings") {
                if (lastBlockedPackage != currentPackage || now - lastBlockAttemptAt > 900L) {
                    lastBlockedPackage = currentPackage
                    lastBlockAttemptAt = now
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    handler.post {
                        Toast.makeText(
                            applicationContext,
                            "Settings are protected during this focus session.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return
            }

            val blockedListJson = prefs.getString("blocked_apps", "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            val blockedApps: List<String> = try {
                gson.fromJson(blockedListJson, type) ?: emptyList()
            } catch (_: Throwable) {
                emptyList()
            }

            if (!blockedApps.contains(currentPackage)) {
                return
            }

            // Do not repeatedly launch the lock screen for every accessibility event.
            if (lastBlockedPackage == currentPackage && now - lastBlockAttemptAt < 900L) return
            lastBlockedPackage = currentPackage
            lastBlockAttemptAt = now

            val launchOverlay = Runnable {
                try {
                    val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("BLOCKED_PACKAGE", currentPackage)
                    }
                    startActivity(overlayIntent)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }

            // Move away from the blocked app first, then show the lock screen.
            // Fall back to launching the overlay directly if HOME is unavailable.
            val movedHome = performGlobalAction(GLOBAL_ACTION_HOME)
            if (movedHome) {
                handler.postDelayed(launchOverlay, 120)
            } else {
                handler.post(launchOverlay)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onInterrupt() {
        // Android may interrupt accessibility callbacks temporarily.
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
