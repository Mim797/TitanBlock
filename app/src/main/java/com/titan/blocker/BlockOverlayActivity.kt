package com.titan.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import java.util.concurrent.TimeUnit

class BlockOverlayActivity : Activity() {

    private val requiredTaxText = "I acknowledge that my attention is my most valuable finite resource, yet I am deliberately choosing to surrender my discipline, break my promise to myself, and consume meaningless digital stimulation."
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#090A0C"))
            isFillViewport = true
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(layout)

        val title = TextView(this).apply {
            text = "🚫 TITAN LOCKDOWN ACTIVE"
            textSize = 18f
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        // LIVE REAL-TIME TICKING DIGITAL CLOCK (HH:MM:SS)
        val timerDisplay = TextView(this).apply {
            text = "--:--:--"
            textSize = 42f
            setTextColor(Color.parseColor("#00E676"))
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        layout.addView(timerDisplay)

        val prefs = getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        val remainingMillis = prefs.getLong("lock_end_time", 0L) - System.currentTimeMillis()

        if (remainingMillis <= 0) {
            finish()
            return
        }

        // Real countdown timer ticking every 1 second
        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                timerDisplay.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }

            override fun onFinish() {
                timerDisplay.text = "00:00:00"
                finish() // Automatically dismiss block screen when time expires
            }
        }.start()

        val subtitle = TextView(this).apply {
            text = "Time remaining until unlocked.\nClose this app and return to your work."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(subtitle)

        val btnHome = Button(this).apply {
            text = "RETURN TO FOCUS (HOME)"
            setBackgroundColor(Color.parseColor("#00E676"))
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        }
        layout.addView(btnHome)

        val spacer = TextView(this).apply {
            text = "\n— OR PAY THE DOPAMINE TAX —\n"
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        layout.addView(spacer)

        val taxInstructions = TextView(this).apply {
            text = "Type this exact passage manually (Copy-Paste Disabled):\n\n\"$requiredTaxText\""
            textSize = 11f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, 0, 0, 10)
        }
        layout.addView(taxInstructions)

        val progressText = TextView(this).apply {
            text = "Progress: 0 / ${requiredTaxText.length} characters"
            textSize = 12f
            setTextColor(Color.parseColor("#FF9100"))
            setPadding(0, 0, 0, 8)
        }
        layout.addView(progressText)

        val input = EditText(this).apply {
            hint = "Begin typing here..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#141822"))
            setPadding(18, 18, 18, 18)
            textSize = 13f

            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        }
        layout.addView(input)

        val btnUnlock = Button(this).apply {
            text = "CLAIM 5-MINUTE EMERGENCY PASS"
            isEnabled = false
            setBackgroundColor(Color.parseColor("#1F2430"))
            setTextColor(Color.DKGRAY)
            setOnClickListener {
                prefs.edit().putLong("temp_pass_time", System.currentTimeMillis() + (5 * 60 * 1000)).apply()
                Toast.makeText(this@BlockOverlayActivity, "Unlocked for 5 minutes only!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        layout.addView(btnUnlock)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val typed = normalize(s?.toString() ?: "")
                val target = normalize(requiredTaxText)
                val matchedCount = typed.zip(target).takeWhile { (a, b) -> a == b }.count()

                if (typed == target) {
                    btnUnlock.isEnabled = true
                    btnUnlock.setBackgroundColor(Color.parseColor("#FF5252"))
                    btnUnlock.setTextColor(Color.WHITE)
                    progressText.text = "PASSED (100% MATCH). You may claim 5 min."
                    progressText.setTextColor(Color.parseColor("#00E676"))
                } else {
                    btnUnlock.isEnabled = false
                    btnUnlock.setBackgroundColor(Color.parseColor("#1F2430"))
                    btnUnlock.setTextColor(Color.DKGRAY)
                    progressText.text = "Progress: $matchedCount / ${target.length} characters correct"
                    progressText.setTextColor(if (typed.length > matchedCount) Color.parseColor("#FF5252") else Color.parseColor("#FF9100"))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setContentView(scroll)
    }

    private fun normalize(str: String): String {
        return str.trim()
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("\\s+".toRegex(), " ")
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
