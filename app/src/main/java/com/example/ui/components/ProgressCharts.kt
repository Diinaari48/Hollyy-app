package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChartBarData
import com.example.data.model.ExamResult
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PriceTagBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun ActivityBarChart(
    data: List<ChartBarData>,
    modifier: Modifier = Modifier
) {
    val maxTotal = max(1, data.maxOfOrNull { it.totalCount } ?: 1)
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Chart Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shaxda Isku-dayada & Saxnaanta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendItem(color = SuccessGreen, label = "Sax")
                    LegendItem(color = ErrorRed, label = "Khalad/Dhaaf")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bars container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEach { bar ->
                    val barHeightRatio = (bar.totalCount.toFloat() / maxTotal).coerceIn(0f, 1f) * animProgress.value
                    val correctRatio = if (bar.totalCount > 0) bar.correctCount.toFloat() / bar.totalCount else 0f

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Accuracy % label on top
                        if (bar.totalCount > 0) {
                            Text(
                                text = "${bar.accuracyPercent}%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bar.accuracyPercent >= 70) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // The vertical bar
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((100 * max(0.06f, barHeightRatio)).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (bar.totalCount > 0) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Wrong / skipped (top part)
                                    val wrongRatio = 1f - correctRatio
                                    if (wrongRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(wrongRatio.coerceAtLeast(0.01f))
                                                .background(ErrorRed.copy(alpha = 0.85f))
                                        )
                                    }
                                    // Correct (bottom part)
                                    if (correctRatio > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(correctRatio.coerceAtLeast(0.01f))
                                                .background(SuccessGreen)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // X-axis label
                        Text(
                            text = bar.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        if (bar.subLabel.isNotEmpty()) {
                            Text(
                                text = bar.subLabel,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamProgressChart(
    pastExams: List<ExamResult>,
    modifier: Modifier = Modifier
) {
    if (pastExams.isEmpty()) return

    val sortedExams = remember(pastExams) {
        pastExams.take(10).reversed()
    }

    val avgScore = remember(sortedExams) {
        (sortedExams.sumOf { it.scorePercentage }.toDouble() / sortedExams.size).toInt()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Horumarka Imtixaannadii Hore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${pastExams.size} imtixaan baa la galay",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (avgScore >= 75) SuccessGreen.copy(alpha = 0.15f) else WarningGold.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Celcelis: $avgScore%",
                        color = if (avgScore >= 75) SuccessGreen else WarningGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Progress Columns
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val dateFormat = SimpleDateFormat("d/M", Locale.US)

                sortedExams.forEachIndexed { index, exam ->
                    val ratio = (exam.scorePercentage / 100f).coerceIn(0.1f, 1f)
                    val barColor = when {
                        exam.scorePercentage >= 80 -> SuccessGreen
                        exam.scorePercentage >= 60 -> PriceTagBlue
                        else -> WarningGold
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "${exam.scorePercentage}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height((80 * ratio).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(barColor)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "#${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateFormat.format(Date(exam.timestamp)),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
