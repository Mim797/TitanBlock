package com.titan.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class AppItem(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {

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
                val context = LocalContext.current
                var lockEndTime by remember { mutableStateOf(prefs.getLong("lock_end_time", 0L)) }
                var selectedMinutes by remember { mutableFloatStateOf(30f) }
                var searchQuery by remember { mutableStateOf("") }

                // Load all installed apps with launch intent (ignores internal background daemons)
                val installedApps by remember {
                    mutableStateOf(loadInstalledApps(context))
                }

                // Selected apps stored in SharedPreferences
                var selectedAppPackages by remember {
                    val saved = prefs.getString("blocked_apps", null)
                    val list: List<String> = if (saved != null) gson.fromJson(saved, object : TypeToken<List<String>>() {}.type) else emptyList()
                    mutableStateOf(list.toSet())
                }

                var protectSettings by remember { mutableStateOf(prefs.getBoolean("protect_settings", true)) }
                val isCurrentlyLocked = System.currentTimeMillis() < lockEndTime

                fun persistSelection(updated: Set<String>) {
                    selectedAppPackages = updated
                    prefs.edit().putString("blocked_apps", gson.toJson(updated)).apply()
                }

                val filteredApps = remember(searchQuery, installedApps) {
                    if (searchQuery.isBlank()) installedApps
                    else installedApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TITAN BLOCKER", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
                            Text("${selectedAppPackages.size} apps flagged for lockdown", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2430)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("SERVICE ➔", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Anti-Settings Shield
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141720), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Anti-Force-Stop Shield (Locks Settings)", fontSize = 12.sp, color = Color.White)
                        Switch(
                            checked = protectSettings,
                            enabled = !isCurrentlyLocked,
                            onCheckedChange = {
                                protectSettings = it
                                prefs.edit().putBoolean("protect_settings", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search and App Filter Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...", fontSize = 13.sp, color = Color.DarkGray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF222836)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Installed Apps List (Scrollable)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedAppPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color(0xFF152A1E) else Color(0xFF141720), RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isCurrentlyLocked) {
                                        val updated = if (isSelected) selectedAppPackages - app.packageName else selectedAppPackages + app.packageName
                                        persistSelection(updated)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF232A3B), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = app.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(app.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                        Text(app.packageName, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }

                                Checkbox(
                                    checked = isSelected,
                                    enabled = !isCurrentlyLocked,
                                    onCheckedChange = {
                                        val updated = if (it) selectedAppPackages + app.packageName else selectedAppPackages - app.packageName
                                        persistSelection(updated)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // -------------------------------------------------------------
                    // TIMER CONTROL PANEL (DEFAULT CHOICES + MANUAL SLIDER)
                    // -------------------------------------------------------------
                    if (isCurrentlyLocked) {
                        val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(lockEndTime - System.currentTimeMillis()).coerceAtLeast(1)
                        val finishDate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lockEndTime))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF261214)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("LOCKDOWN ACTIVE", color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("$minutesRemaining minutes left (Unlocks at $finishDate)", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            val durationInt = selectedMinutes.roundToInt()
                            val hours = durationInt / 60
                            val mins = durationInt % 60
                            val durationLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                            val futureTimestamp = System.currentTimeMillis() + (durationInt * 60 * 1000)
                            val endClockTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(futureTimestamp))

                            // 1. DEFAULT PRESET BUTTONS (15m, 30m, 1h, 2h)
                            Text("DEFAULT CHOICES:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(15f to "15 min", 30f to "30 min", 60f to "1 h", 120f to "2 h").forEach { (presetVal, label) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selectedMinutes == presetVal) MaterialTheme.colorScheme.primary else Color(0xFF222836))
                                            .clickable { selectedMinutes = presetVal }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedMinutes == presetVal) Color.Black else Color.White
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. MANUAL SLIDER (5 mins to 480 mins = 8 hours)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("MANUAL DURATION:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                                Text(
                                    "$durationLabel (Until $endClockTime)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = selectedMinutes,
                                onValueChange = { selectedMinutes = (it / 5).roundToInt() * 5f },
                                valueRange = 5f..480f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color(0xFF252B3B)
                                )
                            )

                            // 3. START BUTTON
                            Button(
                                onClick = {
                                    if (selectedAppPackages.isNotEmpty()) {
                                        val end = System.currentTimeMillis() + (durationInt * 60 * 1000)
                                        prefs.edit().putLong("lock_end_time", end).apply()
                                        lockEndTime = end
                                    }
                                },
                                enabled = selectedAppPackages.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Text(
                                    text = if (selectedAppPackages.isEmpty()) "SELECT AT LEAST 1 APP" else "LOCK ${selectedAppPackages.size} APPS FOR $durationLabel",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos
            .map {
                AppItem(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
            .filter { it.packageName != context.packageName } // exclude TitanBlock itself
            .distinctBy { it.packageName }
            .sortedBy { it.name.lowercase() }
    }
}
