package com.titan.blocker

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class AppItem(val name: String, val packageName: String, val icon: ImageBitmap?)

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
                var showFullListDialog by remember { mutableStateOf(false) }

                // Asynchronously load installed apps with real icons
                var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
                LaunchedEffect(Unit) {
                    installedApps = loadInstalledApps(context)
                }

                // Selected apps list
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

                val pinnedApps = remember(installedApps, selectedAppPackages) {
                    installedApps.filter { selectedAppPackages.contains(it.packageName) }
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
                            Text("${selectedAppPackages.size} apps targeted for lock", fontSize = 11.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2330)),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // ACTIVE BLOCKLIST (Clean Pinned View)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("YOUR ACTIVE BLOCKLIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                        Text(
                            text = "⚙️ MANAGE ALL APPS",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !isCurrentlyLocked) {
                                showFullListDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (pinnedApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                .clickable { showFullListDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Tap here to select apps from full phone list", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(pinnedApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF16241C), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (app.icon != null) {
                                            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(34.dp))
                                        } else {
                                            Box(modifier = Modifier.size(34.dp).background(Color(0xFF232A3B), CircleShape))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(app.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                            Text(app.packageName, fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }

                                    Text(
                                        text = "REMOVE",
                                        color = Color(0xFFFF5252),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(enabled = !isCurrentlyLocked) {
                                            persistSelection(selectedAppPackages - app.packageName)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // -------------------------------------------------------------
                    // TIMER PANEL (DEFAULT PRESETS + MANUAL SLIDER)
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

                            // 1. DEFAULT CHOICES
                            Text("DEFAULT PRESETS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
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

                            // 2. MANUAL DURATION SLIDER
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

                // -------------------------------------------------------------
                // ALL INSTALLED APPS DIALOG (WITH SEARCH & APP ICONS)
                // -------------------------------------------------------------
                if (showFullListDialog) {
                    var query by remember { mutableStateOf("") }
                    val filtered = remember(query, installedApps) {
                        if (query.isBlank()) installedApps
                        else installedApps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
                    }

                    AlertDialog(
                        onDismissRequest = { showFullListDialog = false },
                        confirmButton = {
                            Button(
                                onClick = { showFullListDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("DONE (${selectedAppPackages.size} SELECTED)", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF12151D),
                        title = { Text("All Installed Apps", fontWeight = FontWeight.Bold, color = Color.White) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = { Text("Search installed apps...", fontSize = 12.sp, color = Color.Gray) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(filtered, key = { it.packageName }) { app ->
                                        val isChecked = selectedAppPackages.contains(app.packageName)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isChecked) Color(0xFF16241C) else Color(0xFF181C26))
                                                .clickable {
                                                    val updated = if (isChecked) selectedAppPackages - app.packageName else selectedAppPackages + app.packageName
                                                    persistSelection(updated)
                                                }
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                if (app.icon != null) {
                                                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                                } else {
                                                    Box(modifier = Modifier.size(32.dp).background(Color.Gray, CircleShape))
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }

                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = {
                                                    val updated = if (it) selectedAppPackages + app.packageName else selectedAppPackages - app.packageName
                                                    persistSelection(updated)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun loadInstalledApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, 0).mapNotNull { resolveInfo ->
            try {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val name = resolveInfo.loadLabel(pm).toString()
                val iconDrawable: Drawable = resolveInfo.loadIcon(pm)
                val bitmap: ImageBitmap = iconDrawable.toBitmap(width = 80, height = 80).asImageBitmap()
                AppItem(name = name, packageName = pkg, icon = bitmap)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }
}
