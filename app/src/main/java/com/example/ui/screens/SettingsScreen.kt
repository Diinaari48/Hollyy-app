package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.sync.SupabaseSchema
import com.example.service.WorkSessionManager
import com.example.ui.MainViewModel
import com.example.ui.theme.AppFontSize
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold
import com.example.util.DailyReminderManager
import com.example.util.SomaliPhoneAuthValidator

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val currentFontSize by viewModel.fontScale.collectAsState()
    val sessionConfig by viewModel.sessionConfig.collectAsState()
    val spacedConfig by viewModel.spacedRepetitionConfig.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncResult by viewModel.lastSyncResult.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var testReminderSentMsg by remember { mutableStateOf(false) }
    var testHighPrioritySentMsg by remember { mutableStateOf(false) }

    val hasOverlay = WorkSessionManager.canDrawOverlays(context)
    val hasBatteryExemption = WorkSessionManager.isIgnoringBatteryOptimizations(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendTestNotification()
            testHighPrioritySentMsg = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hagaajinta (Settings)", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. USER ACCOUNT & LOGOUT CARD
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PrimaryBlue,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentUser?.fullName ?: "Akoonka Farmashiyaha",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                val displayPhone = currentUser?.phone?.let {
                                    SomaliPhoneAuthValidator.formatPhoneDisplay(it)
                                } ?: "Lama helin"
                                Text(
                                    text = displayPhone,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Ku xiran yahay ✅",
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Button(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("settings_logout_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ka bax (Logout)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. DAILY REMINDER NOTIFICATION SETTINGS
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ogeysiiska Xusuusinta Maalinlaha ah",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Haddii aadan maanta weli wax jawaab ah bixin, ogeysiis ayaa laguugu soo dirayaa: 'Maanta weli ma jawaabin, 20 suaal ayaa kuu sugaya.' (Tapping wuxuu toos u furayaa Imtixaanka).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Ogeysiiska waa shidan yahay", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Switch(
                            checked = sessionConfig.reminderEnabled,
                            onCheckedChange = {
                                viewModel.updateSessionConfig(sessionConfig.copy(reminderEnabled = it))
                            },
                            modifier = Modifier.testTag("reminder_toggle_switch")
                        )
                    }

                    if (sessionConfig.reminderEnabled) {
                        Text(
                            text = "Waqtiga Xusuusinta (Default: 18:00 / 6:00 Galabnimo):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair(16, 0) to "16:00",
                                Pair(18, 0) to "18:00",
                                Pair(20, 0) to "20:00",
                                Pair(21, 0) to "21:00"
                            ).forEach { (time, label) ->
                                val selected = sessionConfig.reminderHour == time.first && sessionConfig.reminderMinute == time.second
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.updateSessionConfig(
                                            sessionConfig.copy(reminderHour = time.first, reminderMinute = time.second)
                                        )
                                    },
                                    label = { Text(label, fontSize = 12.sp) },
                                    modifier = Modifier.testTag("reminder_time_${label}_chip")
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.sendTestDailyReminder()
                                testReminderSentMsg = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_test_daily_reminder_btn")
                        ) {
                            Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tijaabi Ogeysiiska Xusuusinta Hadda")
                        }

                        if (testReminderSentMsg) {
                            Text(
                                text = "Ogeysiiska xusuusinta waa la diray! Taabo si aad u furto Imtixaanka 20-ka su'aalood.",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 4. WORK SESSION SETTINGS (Interval, Timeout, Quiet Hours)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Xilliga Shaqada (Work Session)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Popup Interval
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Muddada u dhaxaysa Su'aalaha (Interval):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(3, 5, 10, 15, 30).forEach { mins ->
                                FilterChip(
                                    selected = sessionConfig.intervalMinutes == mins,
                                    onClick = {
                                        viewModel.updateSessionConfig(sessionConfig.copy(intervalMinutes = mins))
                                    },
                                    label = { Text("${mins}m") },
                                    modifier = Modifier.testTag("interval_${mins}m_chip")
                                )
                            }
                        }
                    }

                    // Timeout
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Waqtiga Sugitaanka Su'aasha (Timeout):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 3, 5, 10).forEach { mins ->
                                FilterChip(
                                    selected = sessionConfig.timeoutMinutes == mins,
                                    onClick = {
                                        viewModel.updateSessionConfig(sessionConfig.copy(timeoutMinutes = mins))
                                    },
                                    label = { Text("${mins}m") },
                                    modifier = Modifier.testTag("timeout_${mins}m_chip")
                                )
                            }
                        }
                    }

                    // Quiet Hours
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Saacadaha Nasashada (Quiet Hours)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Switch(
                                checked = sessionConfig.quietHoursEnabled,
                                onCheckedChange = {
                                    viewModel.updateSessionConfig(sessionConfig.copy(quietHoursEnabled = it))
                                },
                                modifier = Modifier.testTag("quiet_hours_switch")
                            )
                        }

                        if (sessionConfig.quietHoursEnabled) {
                            Text(
                                text = "Waqtiga: ${sessionConfig.formatQuietRange()} (Su'aal popup ah ma soo baxeyso)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. SPACED REPETITION & SPEED THRESHOLDS
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Xawaaraha Jawaabta (Speed Thresholds)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Fast Threshold
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Xawaare Sare ('Si fiican u yaqaanaa') < ${spacedConfig.fastThresholdSeconds}s:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(2, 3, 4, 5).forEach { sec ->
                                FilterChip(
                                    selected = spacedConfig.fastThresholdSeconds == sec,
                                    onClick = {
                                        viewModel.updateSpacedRepetitionConfig(spacedConfig.copy(fastThresholdSeconds = sec))
                                    },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier.testTag("fast_thresh_${sec}s_chip")
                                )
                            }
                        }
                    }

                    // Slow Threshold
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Xawaare Gaabis ah ('Sax gaabis ah') > ${spacedConfig.slowThresholdSeconds}s:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(6, 7, 8, 10, 12).forEach { sec ->
                                FilterChip(
                                    selected = spacedConfig.slowThresholdSeconds == sec,
                                    onClick = {
                                        viewModel.updateSpacedRepetitionConfig(spacedConfig.copy(slowThresholdSeconds = sec))
                                    },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier.testTag("slow_thresh_${sec}s_chip")
                                )
                            }
                        }
                    }
                }
            }

            // 6. CSV BACKUP & EXPORT
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Kaabidda Xogta (CSV Backup)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Dhoofi dhammaan dawooyinkaaga qaab fayl CSV ah si aad ugu kaydsato meel ammaan ah ama ugu wareejiso taleefan kale.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.shareCsvBackup(context)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_export_csv_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dhoofi CSV", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.getCsvExportText()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Qiimo Quiz CSV Backup", csv)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "CSV-ga waa la koobiyeeyay! ✅", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("settings_copy_csv_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Koobiyeey", fontSize = 13.sp)
                        }
                    }
                }
            }

            // 7. THEME & FONT
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Muuqaalka & Qoraalka",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Theme
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentTheme == AppThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                            label = { Text("Nidaamka") },
                            modifier = Modifier.testTag("theme_system_chip")
                        )
                        FilterChip(
                            selected = currentTheme == AppThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                            label = { Text("Iftiin") },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = currentTheme == AppThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                            label = { Text("Madow") },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                    }

                    // Font Size
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppFontSize.values().forEach { size ->
                            FilterChip(
                                selected = currentFontSize == size,
                                onClick = { viewModel.setFontScale(size) },
                                label = { Text(size.labelSo) },
                                modifier = Modifier.testTag("font_size_${size.name.lowercase()}_chip")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // LOGOUT CONFIRMATION DIALOG
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Ka bax akoonka?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Ma hubtaa inaad ka baxayso akoonkaaga? Xogtaada maxalliga ah waa la xafidi doonaa markaad dib u gasho.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_logout_btn")
                ) {
                    Text("Haa, Ka bax")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Maya")
                }
            }
        )
    }
}
