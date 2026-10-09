package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FeedbackType
import com.example.ui.MainViewModel
import com.example.ui.components.ConfettiCelebration
import com.example.ui.components.ExamProgressChart
import com.example.ui.components.NumericKeypad
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PriceTagBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold
import com.example.util.DistractorGenerator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.allItems.collectAsState()
    val quizState by viewModel.quizState.collectAsState()
    val pastExams by viewModel.pastExams.collectAsState()

    var selectedQuestionCount by remember { mutableIntStateOf(20) } // Default 20
    var customCountInput by remember { mutableStateOf("") }
    var selectedTimerSeconds by remember { mutableIntStateOf(15) } // Default 15s

    val totalItems = items.size
    val effectiveCount = if (customCountInput.isNotBlank()) {
        (customCountInput.toIntOrNull() ?: selectedQuestionCount).coerceAtLeast(1)
    } else {
        selectedQuestionCount
    }
    val cappedCount = min(effectiveCount, totalItems)
    val isCapped = totalItems > 0 && effectiveCount > totalItems
    val hasEnoughItems = totalItems >= 4

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (quizState.isExamMode && quizState.currentQuestion != null) {
                            "Imtixaanka (${quizState.examCurrentIndex}/${quizState.examQuestionCount})"
                        } else {
                            "Imtixaan (Exam)"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (quizState.isExamMode && (quizState.currentQuestion != null || quizState.isExamFinished)) {
                        IconButton(
                            onClick = { viewModel.resetExamToSetup() },
                            modifier = Modifier.testTag("exam_reset_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Ka noqo imtixaanka")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. EXAM FINISHED RESULTS VIEW
                quizState.isExamMode && quizState.isExamFinished -> {
                    ExamFinishedView(
                        viewModel = viewModel,
                        quizState = quizState,
                        onRetry = {
                            viewModel.startExam(
                                questionCount = quizState.examQuestionCount,
                                timeLimitSeconds = quizState.examTimeLimitSeconds
                            )
                        },
                        onNewExam = {
                            viewModel.resetExamToSetup()
                        }
                    )
                }

                // 2. ACTIVE EXAM QUESTION VIEW
                quizState.isExamMode && quizState.currentQuestion != null -> {
                    ActiveExamQuestionView(
                        viewModel = viewModel,
                        quizState = quizState
                    )
                }

                // 3. EXAM SETUP & PAST RESULTS VIEW
                else -> {
                    ExamSetupView(
                        totalItems = totalItems,
                        hasEnoughItems = hasEnoughItems,
                        selectedQuestionCount = selectedQuestionCount,
                        customCountInput = customCountInput,
                        selectedTimerSeconds = selectedTimerSeconds,
                        cappedCount = cappedCount,
                        isCapped = isCapped,
                        pastExams = pastExams,
                        onSelectCount = { count ->
                            selectedQuestionCount = count
                            customCountInput = ""
                        },
                        onCustomCountChange = { customCountInput = it },
                        onSelectTimer = { selectedTimerSeconds = it },
                        onStartExam = {
                            val finalCount = if (cappedCount > 0) cappedCount else 20
                            viewModel.startExam(
                                questionCount = finalCount,
                                timeLimitSeconds = selectedTimerSeconds
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExamSetupView(
    totalItems: Int,
    hasEnoughItems: Boolean,
    selectedQuestionCount: Int,
    customCountInput: String,
    selectedTimerSeconds: Int,
    cappedCount: Int,
    isCapped: Boolean,
    pastExams: List<com.example.data.model.ExamResult>,
    onSelectCount: (Int) -> Unit,
    onCustomCountChange: (String) -> Unit,
    onSelectTimer: (Int) -> Unit,
    onStartExam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Description Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tijaabi Xifdintaada (Exam)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Su'aalo xulasho ah oo laga soo qaatay dawooyinkaaga",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Question Count Selection: 10 / 15 / 20 / 30 / 50 + custom
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
                Text(
                    text = "Dooro Tirada Su'aalaha:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 15, 20, 30, 50).forEach { count ->
                        val isSelected = selectedQuestionCount == count && customCountInput.isBlank()
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectCount(count) },
                            label = { Text("$count", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exam_chip_$count")
                        )
                    }
                }

                // Custom count input
                OutlinedTextField(
                    value = customCountInput,
                    onValueChange = onCustomCountChange,
                    label = { Text("Ama qor tiro gaar ah (Custom)") },
                    placeholder = { Text("Tusaale: 25") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_custom_count_input")
                )

                // Capped note if fewer items
                if (isCapped) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WarningGold.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = WarningGold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waxa aad haysataa $totalItems dawo oo keliya, markaa tirada imtixaanka waxaa lagu koobay $cappedCount su'aalood.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Timer selection per question (10s, 15s, 20s, 30s)
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
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Waqtiga Su'aal kasta (Seconds):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 15, 20, 30).forEach { sec ->
                        val isSelected = selectedTimerSeconds == sec
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTimer(sec) },
                            label = { Text("${sec}s", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exam_timer_chip_$sec")
                        )
                    }
                }
            }
        }

        // Not enough items warning
        if (!hasEnoughItems) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ErrorRed.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fadlan ku dar ugu yaraan 4 dawo si aad imtixaan u bilowdo (Hadda: $totalItems dawo).",
                        fontSize = 12.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Start Exam Button
        Button(
            onClick = onStartExam,
            enabled = hasEnoughItems,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("exam_start_btn")
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Bilow Imtixaanka ($cappedCount Su'aalood)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Past Exams Progress Chart
        if (pastExams.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Natiijooyinkii Imtixaannadii Hore:",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            ExamProgressChart(pastExams = pastExams)
        }
    }
}

@Composable
private fun ActiveExamQuestionView(
    viewModel: MainViewModel,
    quizState: com.example.ui.QuizUiState
) {
    val question = quizState.currentQuestion ?: return
    var typedAnswer by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val progress = if (quizState.examTimeLimitSeconds > 0) {
        quizState.examTimeRemainingSeconds.toFloat() / quizState.examTimeLimitSeconds.toFloat()
    } else 1f

    val progressColor = when {
        quizState.examTimeRemainingSeconds <= 3 -> ErrorRed
        quizState.examTimeRemainingSeconds <= 7 -> WarningGold
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Question Header Card with Timer & Progress
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Su'aasha ${quizState.examCurrentIndex} ee ${quizState.examQuestionCount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = progressColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${quizState.examTimeRemainingSeconds}s",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = progressColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Question Prompt Card: "Qiimaha {item.name} waa immisa marka macaamiil laga iibinayo?"
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    if (!question.item.systemName.isNullOrBlank()) {
                        Text(
                            text = "System: ${question.item.systemName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = question.questionText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Feedback Banner
        if (quizState.isEvaluating && quizState.feedback != null) {
            val isCorrect = quizState.feedback == FeedbackType.CORRECT
            val formattedOfficial = DistractorGenerator.formatPrice(question.targetPrice)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isCorrect) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (isCorrect) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCorrect) "Sax! Waad heshay 🎉" else "Khalad ❌ Qiimaha rasmiga ah waa $$formattedOfficial",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isCorrect) SuccessGreen else ErrorRed
                        )
                    }
                }
            }
        }

        // Choices or Keypad
        if (!question.isLevel2Typed && question.options.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { idx, optionPrice ->
                    val isCorrectOption = abs(optionPrice - question.targetPrice) < 0.009
                    val isChosen = quizState.selectedOption != null && abs(quizState.selectedOption!! - optionPrice) < 0.009

                    val (bgColor, borderColor, textColor) = when {
                        quizState.isEvaluating && isCorrectOption -> Triple(SuccessGreen.copy(alpha = 0.15f), SuccessGreen, SuccessGreen)
                        quizState.isEvaluating && isChosen && !isCorrectOption -> Triple(ErrorRed.copy(alpha = 0.15f), ErrorRed, ErrorRed)
                        else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.onSurface)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                            .clickable(enabled = !quizState.isEvaluating) {
                                viewModel.submitChoice(optionPrice)
                            }
                            .testTag("exam_option_$idx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$${DistractorGenerator.formatPrice(optionPrice)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            if (quizState.isEvaluating && isCorrectOption) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Sax",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (quizState.isEvaluating && isChosen && !isCorrectOption) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Khalad",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Level 2 Numeric input display & keypad
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (quizState.typedInput.isEmpty()) "$0.00" else "$${quizState.typedInput}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (quizState.typedInput.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!quizState.isEvaluating) {
                NumericKeypad(
                    onDigitClick = { num -> viewModel.onTypedInputDigit(num) },
                    onBackspace = { viewModel.onTypedInputBackspace() },
                    onClear = { viewModel.onTypedInputClear() },
                    onSubmit = { viewModel.submitTyped() },
                    canSubmit = quizState.typedInput.isNotBlank() && (quizState.typedInput.toDoubleOrNull() ?: 0.0) > 0.0
                )
            }
        }

        // Continue Button when evaluating
        if (quizState.isEvaluating) {
            Button(
                onClick = { viewModel.continueToNextQuestion() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (quizState.feedback == FeedbackType.CORRECT) SuccessGreen else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("exam_continue_btn")
            ) {
                Text(
                    text = "Su'aasha Xigta",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(imageVector = Icons.Default.NavigateNext, contentDescription = null)
            }
        }

        // Skip button (if not evaluating)
        if (!quizState.isEvaluating) {
            OutlinedButton(
                onClick = { viewModel.skipQuestion() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exam_skip_btn")
            ) {
                Icon(imageVector = Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dhaaf Su'aashan")
            }
        }
    }
}

@Composable
private fun ExamFinishedView(
    viewModel: MainViewModel,
    quizState: com.example.ui.QuizUiState,
    onRetry: () -> Unit,
    onNewExam: () -> Unit
) {
    val total = quizState.examQuestionCount
    val correct = quizState.examCorrectCount
    val wrong = quizState.examWrongCount
    val scorePercent = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

    val avgTimeSeconds = if (total > 0) {
        String.format(Locale.US, "%.1f", (quizState.examTotalResponseTimeMs / total.toDouble()) / 1000.0)
    } else "0.0"

    val pastExams by viewModel.pastExams.collectAsState()

    if (scorePercent >= 70) {
        ConfettiCelebration(isGrandCelebration = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Result Score Card: "17/20 sax (85%)"
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (scorePercent >= 70) SuccessGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (scorePercent >= 70) Icons.Default.Celebration else Icons.Default.Quiz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Imtixaankii Wuu Dhamaaday!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$correct/$total sax ($scorePercent%)",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (scorePercent >= 70) SuccessGreen else MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Celceliska waqtiga: $avgTimeSeconds ilbiriqsi su'aashiiba",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Action Buttons: Retry / New Exam
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("exam_retry_btn")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ku celi Imtixaanka", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onNewExam,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("exam_new_setup_btn")
                ) {
                    Text("Imtixaan Cusub", fontSize = 13.sp)
                }
            }
        }

        // List of Wrong Items with Correct Prices
        if (quizState.examWrongItems.isNotEmpty()) {
            item {
                Text(
                    text = "Dawooyinka aad khaladday (${quizState.examWrongItems.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = ErrorRed
                )
            }

            items(quizState.examWrongItems) { wrongItem ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wrongItem.item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Jawaabtaadii: ${wrongItem.answerGiven}",
                                fontSize = 12.sp,
                                color = ErrorRed
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SuccessGreen,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "Qiimaha Saxda ah: $${DistractorGenerator.formatPrice(wrongItem.correctPrice)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Progress Chart of Past Exams
        if (pastExams.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Horumarka Imtixaannada Hore:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ExamProgressChart(pastExams = pastExams)
            }
        }
    }
}
