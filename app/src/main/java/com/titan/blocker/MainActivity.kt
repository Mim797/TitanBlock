package com.titan.blocker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class AppItem(val name: String, val packageName: String)

object FastIconCache {
    private val memoryCache = LruCache<String, ImageBitmap>(80)
    fun get(pkg: String): ImageBitmap? = memoryCache.get(pkg)
    fun put(pkg: String, bmp: ImageBitmap) { memoryCache.put(pkg, bmp) }
}

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

                // LIVE TICKER: Updates every second
                var currentClockTime by remember { mutableStateOf(System.currentTimeMillis()) }
                val isCurrentlyLocked = currentClockTime < lockEndTime

                LaunchedEffect(lockEndTime) {
                    while (currentClockTime < lockEndTime) {
                        delay(1000L)
                        currentClockTime = System.currentTimeMillis()
                    }
                }

                // Selected apps
                var selectedAppPackages by remember {
                    val saved = prefs.getString("blocked_apps", null)
                    val list: List<String> = if (saved != null) {
                        try { gson.fromJson(saved, object : TypeToken<List<String>>() {}.type) } catch (t: Throwable) { emptyList() }
                    } else emptyList()
                    mutableStateOf(list.toSet())
                }

                var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
                var isScanningApps by remember { mutableStateOf(false) }

                LaunchedEffect(showFullListDialog) {
                    if (showFullListDialog && installedApps.isEmpty()) {
                        isScanningApps = true
                        withContext(Dispatchers.IO) {
                            installedApps = scanLaunchableApps(context)
                            isScanningApps = false
                        }
                    }
                }

                var protectSettings by remember { mutableStateOf(prefs.getBoolean("protect_settings", true)) }

                fun persistSelection(updated: Set<String>) {
                    selectedAppPackages = updated
                    prefs.edit().putString("blocked_apps", gson.toJson(updated)).apply()
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

                    // Blocklist Header (MANAGE BUTTON REMAINS ENABLED DURING LOCKDOWN)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("YOUR ACTIVE BLOCKLIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                        Text(
                            text = if (isCurrentlyLocked) "+ ADD MORE APPS" else "⚙️ MANAGE ALL APPS",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                showFullListDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedAppPackages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color(0xFF141720), RoundedCornerShape(12.dp))
                                .clickable { showFullListDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Tap to select apps to block", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        val pinnedList = remember(selectedAppPackages) { selectedAppPackages.toList() }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(pinnedList, key = { it }) { pkg ->
                                val appName = resolveAppNameQuick(context, pkg)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF16241C), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        CachedAppIcon(pkg, appName)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                            Text(pkg, fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }

                                    // During lockdown, unchecking/removing is prevented
                                    if (isCurrentlyLocked) {
                                        Text("🔒 LOCKED", color = Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    } else {
                                        Text(
                                            text = "REMOVE",
                                            color = Color(0xFFFF5252),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                persistSelection(selectedAppPackages - pkg)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // TIMER DASHBOARD (LIVE SECOND-BY-SECOND TICKING COUNTDOWN)
                    if (isCurrentlyLocked) {
                        val remainingMillis = (lockEndTime - currentClockTime).coerceAtLeast(0L)
                        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60
                        val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
                        val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        val finishDate = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lockEndTime))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF261214)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("LOCKDOWN ACTIVE", color = Color(0xFFFF5252), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = timeFormatted,
                                    color = Color(0xFF00E676),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text("Unlocks at $finishDate", color = Color.LightGray, fontSize = 11.sp)
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

                // DIALOG: ALLOWS CHECKING NEW APPS DURING LOCKDOWN (UNCHECKING IS PREVENTED)
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
                                Text("DONE (${selectedAppPackages.size})", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF12151D),
                        title = {
                            Column {
                                Text("Manage Blocked Apps", fontWeight = FontWeight.Bold, color = Color.White)
                                if (isCurrentlyLocked) {
                                    Text("🔒 Session Active: You can add new apps, but cannot unblock.", fontSize = 11.sp, color = Color(0xFFFF9100))
                                }
                            }
                        },
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

                                if (isScanningApps) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(filtered, key = { it.packageName }) { app ->
                                            val isChecked = selectedAppPackages.contains(app.packageName)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isChecked) Color(0xFF16241C) else Color(0xFF181C26))
                                                .clickable {
                                                    if (isChecked) {
                                                        if (isCurrentlyLocked) {
                                                            Toast.makeText(context, "🛡️ Cannot unblock apps while lock is active!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            persistSelection(selectedAppPackages - app.packageName)
                                                        }
                                                    } else {
                                                        // Adding is ALWAYS allowed!
                                                        persistSelection(selectedAppPackages + app.packageName)
                                                    }
                                                }
                                                .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    CachedAppIcon(app.packageName, app.name)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(app.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                }

                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checkState ->
                                                        if (!checkState && isCurrentlyLocked) {
                                                            Toast.makeText(context, "🛡️ Cannot unblock apps during session!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            val updated = if (checkState) selectedAppPackages + app.packageName else selectedAppPackages - app.packageName
                                                            persistSelection(updated)
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                                )
                                            }
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

    @Composable
    private fun CachedAppIcon(packageName: String, appName: String) {
        val context = LocalContext.current
        var bitmap by remember(packageName) { mutableStateOf(FastIconCache.get(packageName)) }

        if (bitmap == null) {
            LaunchedEffect(packageName) {
                withContext(Dispatchers.IO) {
                    try {
                        val drawable = context.packageManager.getApplicationIcon(packageName)
                        val decoded = drawableToTinyBitmap(drawable)?.asImageBitmap()
                        if (decoded != null) {
                            FastIconCache.put(packageName, decoded)
                            bitmap = decoded
                        }
                    } catch (t: Throwable) {
                        // ignore and use letter fallback
                    }
                }
            }
        }

        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0xFF232A3B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(appName.take(1).uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    private fun resolveAppNameQuick(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (t: Throwable) {
            pkg.substringAfterLast('.')
        }
    }

    private fun scanLaunchableApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            try {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val name = resolveInfo.loadLabel(pm).toString()
                AppItem(name = name, packageName = pkg)
            } catch (t: Throwable) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun drawableToTinyBitmap(drawable: Drawable): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, 48, 48, false)
            } else {
                val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, 48, 48)
                drawable.draw(canvas)
                bitmap
            }
        } catch (t: Throwable) {
            null
        }
    }
}
