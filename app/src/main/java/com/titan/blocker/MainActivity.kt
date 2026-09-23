package com.titan.blocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val commonTargets = listOf(
        "YouTube" to "com.google.android.youtube",
        "Instagram" to "com.instagram.android",
        "TikTok" to "com.zhiliaoapp.musically",
        "Facebook" to "com.facebook.katana",
        "Chrome Browser" to "com.android.chrome"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("titan_prefs", Context.MODE_PRIVATE)
        val gson = Gson()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF090A0C),
                surface = Color(0xFF141720),
                primary = Color(0xFF00E676),
                error = Color(0xFFFF5252)
            )) {
                var lockEndTime by remember { mutableStateOf(prefs.getLong("lock_end_time", 0L)) }
                var selectedApps by remember {
                    val saved = prefs.getString("blocked_apps", null)
                    val list: List<String> = if (saved != null) gson.fromJson(saved, object : TypeToken<List<String>>() {}.type) else listOf("com.google.android.youtube")
                    mutableStateOf(list.toSet())
                }
                var protectSettings by remember { mutableStateOf(prefs.getBoolean("protect_settings", true)) }

                val isCurrentlyLocked = System.currentTimeMillis() < lockEndTime

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp)
                ) {
                    Text("TITAN BLOCKER", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
                    Text("Unbreakable Anti-Cheat Focus Guard", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222836)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. ENABLE ACCESSIBILITY PERMISSION ➔", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720), RoundedCornerShape(10.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Anti-Force-Stop Shield", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text("Locks Android Settings to prevent cheating", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = protectSettings,
                            enabled = !isCurrentlyLocked,
                            onCheckedChange = {
                                protectSettings = it
                                prefs.edit().putBoolean("protect_settings", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("TARGET DISTRACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(commonTargets) { (name, pkg) ->
                            val isSelected = selectedApps.contains(pkg)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFF1B2A22) else Color(0xFF141720), RoundedCornerShape(10.dp))
                                    .clickable(enabled = !isCurrentlyLocked) {
                                        val updated = if (isSelected) selectedApps - pkg else selectedApps + pkg
                                        selectedApps = updated
                                        prefs.edit().putString("blocked_apps", gson.toJson(updated)).apply()
                                    }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(if (isSelected) "LOCKED ✓" else "UNLOCKED", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color(0xFF00E676) else Color.DarkGray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isCurrentlyLocked) {
                        val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(lockEndTime - System.currentTimeMillis()).coerceAtLeast(1)
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF261214)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("SESSION ACTIVE: $minutesRemaining MIN REMAINING", color = Color(0xFFFF5252), fontWeight = FontWeight.Black)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val end = System.currentTimeMillis() + (30 * 60 * 1000)
                                    prefs.edit().putLong("lock_end_time", end).apply()
                                    lockEndTime = end
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("LOCK 30M", color = Color.Black, fontWeight = FontWeight.Black)
                            }

                            Button(
                                onClick = {
                                    val end = System.currentTimeMillis() + (2 * 60 * 60 * 1000)
                                    prefs.edit().putLong("lock_end_time", end).apply()
                                    lockEndTime = end
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("LOCK 2H", color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
