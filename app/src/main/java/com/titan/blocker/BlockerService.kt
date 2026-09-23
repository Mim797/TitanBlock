package com.titan.blocker

import android.accessibilityservice.AccessibilityService
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val currentPackage = event.packageName?.toString() ?: return

            // CRITICAL: NEVER inspect or block TitanBlock itself
            if (currentPackage == packageName) return

            val prefs = getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
            val lockEndTime = prefs.getLong("lock_end_time", 0L)
            val isLocked = System.currentTimeMillis() < lockEndTime

            if (!isLocked) return

            // 1. Temporary emergency pass check (5 minutes)
            val tempPassUntil = prefs.getLong("temp_pass_time", 0L)
            if (System.currentTimeMillis() < tempPassUntil) return

            // 2. ANTI-FORCE-STOP SHIELD: Guard Android Settings & App Installers
            val protectSettings = prefs.getBoolean("protect_settings", true)
            if (protectSettings && (currentPackage == "com.android.settings" || currentPackage.contains("packageinstaller"))) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                handler.post {
                    Toast.makeText(applicationContext, "🛡️ Settings locked during Focus Session!", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // 3. TARGET APPS CHECK
            val blockedListJson = prefs.getString("blocked_apps", "[]") ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            val blockedApps: List<String> = try {
                gson.fromJson(blockedListJson, type) ?: emptyList()
            } catch (t: Throwable) {
                emptyList()
            }

            if (blockedApps.contains(currentPackage)) {
                val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("BLOCKED_PACKAGE", currentPackage)
                }
                startActivity(overlayIntent)
            }
        } catch (t: Throwable) {
            // Failsafe: Never allow the accessibility service to crash the process
            t.printStackTrace()
        }
    }

    override fun onInterrupt() {}
}
