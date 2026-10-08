package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ItemStats
import com.example.service.WorkSessionManager
import com.example.ui.MainViewModel
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PriceTagBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by viewModel.allItems.collectAsState()
    val totalAttempts by viewModel.totalAttempts.collectAsState()
    val correctAttempts by viewModel.correctAttempts.collectAsState()

    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val isWaitingForQuietHours by viewModel.isWaitingForQuietHours.collectAsState()
    val quietNoticeMessage by viewModel.quietNoticeMessage.collectAsState()
    val sessionConfig by viewModel.sessionConfig.collectAsState()

    var showOverlayPermissionPrompt by remember { mutableStateOf(false) }

    val learnedCount = items.count { it.stats?.status == ItemStats.STATUS_LEARNED }
    val learningCount = items.count { it.stats?.status == ItemStats.STATUS_LEARNING }

    val accuracy = if (totalAttempts > 0) {
        ((correctAttempts.toDouble() / totalAttempts) * 100).toInt()
    } else 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Compact Hero Header with Stats
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocalPharmacy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Qiimo Quiz",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Xifdinta Qiimaha Farmashiyaha",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Stats row: Dhammaan / La Xafiday / Baranaya / Saxnaan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickStat(label = "Dhammaan", value = "${items.size}", color = MaterialTheme.colorScheme.primary)
                    QuickStat(label = "La Xafiday", value = "$learnedCount", color = SuccessGreen)
                    QuickStat(label = "Baranaya", value = "$learningCount", color = WarningGold)
                    QuickStat(label = "Saxnaan", value = "$accuracy%", color = PriceTagBlue)
                }
            }
        }

        // Quiet Hours Notice Banner (if triggered)
        AnimatedVisibility(visible = quietNoticeMessage != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NightlightRound,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saacadaha Deggan (Quiet Hours)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = quietNoticeMessage ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    IconButton(onClick = { viewModel.clearQuietNotice() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xidh",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Active Session Running Status Banner (Status line)
        if (isSessionActive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isWaitingForQuietHours) WarningGold.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isWaitingForQuietHours) WarningGold else SuccessGreen,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWaitingForQuietHours) "Shaqadu waxay ku jirtaa Heegan (Quiet Hours)" else "Shaqadu way socotaa (Foreground Service)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isWaitingForQuietHours) WarningGold else SuccessGreen
                        )
                        Text(
                            text = if (isWaitingForQuietHours) {
                                "Pop-up wuxuu soo bixi doonaa ${sessionConfig.formatQuietEnd()}"
                            } else {
                                "Pop-up su'aal ah ${sessionConfig.intervalMinutes} daqiiqo kasta"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // PRIMARY ACTION BUTTON: Large "Bilow shaqada" / "Dhamee" Button
        val buttonColor by animateColorAsState(
            targetValue = if (isSessionActive) ErrorRed else MaterialTheme.colorScheme.primary,
            label = "session_btn_color"
        )

        Button(
            onClick = {
                if (!isSessionActive) {
                    if (!WorkSessionManager.canDrawOverlays(context)) {
                        showOverlayPermissionPrompt = true
                    } else {
                        viewModel.toggleWorkSession()
                    }
                } else {
                    viewModel.toggleWorkSession()
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .testTag("home_bilow_shaqada_btn")
        ) {
            Icon(
                imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isSessionActive) "Dhamee" else "Bilow shaqada",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isSessionActive) {
                        "Taabo halkan si aad u joojiso shaqada"
                    } else {
                        "${sessionConfig.intervalMinutes} daqiiqo kasta pop-up ayaa soo bixi doona"
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }

    // Permission Prompt Dialog for Overlays
    if (showOverlayPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionPrompt = false },
            title = {
                Text("Ogolaanshaha Pop-up-ka (Draw Over Other Apps)", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Si su'aalaha qiimaha dawooyinku kuugu soo baxaan adigoo isticmaalaya apps-ka kale, fadlan bixi ogolaanshaha 'Display over other apps'.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionPrompt = false
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Fur Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayPermissionPrompt = false
                    viewModel.toggleWorkSession()
                }) {
                    Text("Bilow la'aantiis (Ogeysiis kaliya)")
                }
            }
        )
    }
}

@Composable
private fun QuickStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            fontSize = 11.sp
        )
    }
}
