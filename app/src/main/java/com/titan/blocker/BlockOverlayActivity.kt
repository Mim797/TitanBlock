package com.titan.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import java.util.concurrent.TimeUnit

class BlockOverlayActivity : Activity() {

    private val requiredTaxText = "I am consciously choosing to waste my time on digital distractions."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#090A0C"))
            setPadding(40, 60, 40, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "🚫 TITAN LOCK ACTIVE"
            textSize = 22f
            setTextColor(Color.parseColor("#FF5252"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val prefs = getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        val remainingMillis = prefs.getLong("lock_end_time", 0L) - System.currentTimeMillis()
        val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(remainingMillis).coerceAtLeast(1)

        val subtitle = TextView(this).apply {
            text = "Locked for $minutesLeft more minutes.\nStay focused on your real goals."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }
        layout.addView(subtitle)

        val btnHome = Button(this).apply {
            text = "RETURN TO WORK (HOME)"
            setBackgroundColor(Color.parseColor("#00E676"))
            setTextColor(Color.BLACK)
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
            text = "Type this exact sentence by hand (Copy-Paste Disabled) to earn a strict 5-minute unlock:\n\n\"$requiredTaxText\""
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(taxInstructions)

        val input = EditText(this).apply {
            hint = "Type sentence here exactly..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#151922"))
            setPadding(20, 20, 20, 20)

            // DISABLE COPY-PASTE TO PREVENT CHEATING
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
        }
        layout.addView(input)

        val btnUnlock = Button(this).apply {
            text = "CLAIM 5-MINUTE PASS"
            isEnabled = false
            setBackgroundColor(Color.parseColor("#2A3142"))
            setTextColor(Color.GRAY)
            setOnClickListener {
                prefs.edit().putLong("temp_pass_time", System.currentTimeMillis() + (5 * 60 * 1000)).apply()
                Toast.makeText(this@BlockOverlayActivity, "Unlocked for 5 minutes only!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        layout.addView(btnUnlock)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s?.toString()?.trim() == requiredTaxText) {
                    btnUnlock.isEnabled = true
                    btnUnlock.setBackgroundColor(Color.parseColor("#FF5252"))
                    btnUnlock.setTextColor(Color.WHITE)
                } else {
                    btnUnlock.isEnabled = false
                    btnUnlock.setBackgroundColor(Color.parseColor("#2A3142"))
                    btnUnlock.setTextColor(Color.GRAY)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        setContentView(layout)
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
