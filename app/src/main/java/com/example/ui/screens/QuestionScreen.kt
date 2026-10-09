package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FeedbackType
import com.example.ui.MainViewModel
import com.example.ui.components.ConfettiCelebration
import com.example.ui.components.ExamProgressChart
import com.example.ui.components.NumericKeypad
import com.example.ui.navigation.Screen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PriceTagBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold
import com.example.util.DistractorGenerator
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val quizState by viewModel.quizState.collectAsState()
    val pastExams by viewModel.pastExams.collectAsState()

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (quizState.isExamMode) "Imtixaanka Qiimaha" else "Tababarka Qiimaha",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (quizState.isExamMode) {
                            Text(
                                text = "Su'aasha ${quizState.examCurrentIndex} ee ${quizState.examQuestionCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Guulo xiriir ah: ${quizState.consecutiveCorrect} 🔥",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("quiz_back_btn")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ka noqo")
                    }
                },
                actions = {
                    // Always visible "Dhaaf" (Skip) button
                    if (!quizState.isExamFinished && quizState.currentQuestion != null) {
                        OutlinedButton(
                            onClick = { viewModel.skipQuestion() },
                            enabled = !quizState.isEvaluating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("quiz_dhaaf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dhaaf", fontWeight = FontWeight.Bold)
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
                // Exam Finished Screen
                quizState.isExamFinished -> {
                    ExamResultsView(
                        correct = quizState.examCorrectCount,
                        wrong = quizState.examWrongCount,
                        skipped = quizState.examSkippedCount,
                        total = quizState.examQuestionCount,
                        averageTimeSeconds = quizState.examAverageTimeSeconds,
                        wrongItems = quizState.examWrongItems,
                        pastExams = pastExams,
                        onRetry = { viewModel.startExam(quizState.examQuestionCount, quizState.examTimeLimitSeconds) },
                        onHome = { viewModel.navigateBack() },
                        onPracticeItem = { itemId ->
                            viewModel.navigateTo(Screen.Quiz(mode = Screen.Quiz.MODE_DRILL, initialItemId = itemId))
                        }
                    )
                }

                // Question is loading / empty database
                quizState.currentQuestion == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Lama helin dawooyin ku jira diiwaanka.",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.seedSampleMedicines() }) {
                                Text("Soo geli dawooyin tusaale ah")
                            }
                        }
                    }
                }

                // Active Question View
                else -> {
                    val question = quizState.currentQuestion!!
                    val isLevel2 = question.isLevel2Typed

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Exam Timer Countdown Bar
                        if (quizState.isExamMode) {
                            val totalTime = quizState.examTimeLimitSeconds.coerceAtLeast(1)
                            val remaining = quizState.examTimeRemainingSeconds
                            val ratio = (remaining.toFloat() / totalTime).coerceIn(0f, 1f)
                            val isUrgent = remaining <= 4

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUrgent) ErrorRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Timer,
                                                contentDescription = null,
                                                tint = if (isUrgent) ErrorRed else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Waqtiga: ${remaining}s dhiman",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isUrgent) ErrorRed else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Text(
                                            text = "Su'aasha ${quizState.examCurrentIndex}/${quizState.examQuestionCount}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        color = if (isUrgent) ErrorRed else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            }
                        }

                        // Level & Streak Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isLevel2) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLevel2) "Heerka 2: Gacanta ku qor (Numpad)" else "Heerka 1: 4 Xulasho",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLevel2) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            val streak = question.stats?.correctStreak ?: 0
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (streak >= 5) SuccessGreen.copy(alpha = 0.2f) else if (streak >= 3) WarningGold.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (streak >= 5) "La Xafiday 🏆 5/5" else "Xiriir: $streak/5 ${if (streak >= 3) "🔥 Numpad" else ""}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (streak >= 5) SuccessGreen else if (streak >= 3) WarningGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Question Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "Su'aasha:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = question.questionText,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (!question.item.systemName.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Magaca systemka: ${question.item.systemName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Interactive Question Inputs
                        if (isLevel2) {
                            // LEVEL 2: TYPED INPUT
                            TypedAnswerSection(
                                typedInput = quizState.typedInput,
                                isEvaluating = quizState.isEvaluating,
                                onDigit = { viewModel.onTypedInputDigit(it) },
                                onBackspace = { viewModel.onTypedInputBackspace() },
                                onClear = { viewModel.onTypedInputClear() },
                                onSubmit = { viewModel.submitTyped() }
                            )
                        } else {
                            // LEVEL 1: 4 CHOICES
                            ChoiceAnswerSection(
                                options = question.options,
                                correctPrice = question.targetPrice,
                                selectedOption = quizState.selectedOption,
                                isEvaluating = quizState.isEvaluating,
                                onOptionSelected = { viewModel.submitChoice(it) }
                            )
                        }

                        // Feedback Banner / Result
                        if (quizState.feedback != FeedbackType.NONE) {
                            FeedbackSection(
                                feedback = quizState.feedback,
                                officialPrice = quizState.feedbackOfficialPrice ?: question.targetPrice,
                                cost = question.item.cost,
                                responseTimeMs = quizState.lastResponseTimeMs,
                                speedCategory = quizState.speedCategory,
                                streak = quizState.consecutiveCorrect,
                                isGrand = quizState.isGrandCelebration,
                                nextDueAt = quizState.nextDueAt,
                                onContinue = { viewModel.continueToNextQuestion() }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Confetti Celebration Overlay (with Grand Celebration for 5-streak mastery)
            if (quizState.feedback == FeedbackType.CORRECT) {
                ConfettiCelebration(
                    isGrandCelebration = quizState.isGrandCelebration,
                    particleCount = if (quizState.isGrandCelebration) 100 else 40
                )
            }
        }
    }
}

@Composable
private fun ChoiceAnswerSection(
    options: List<Double>,
    correctPrice: Double,
    selectedOption: Double?,
    isEvaluating: Boolean,
    onOptionSelected: (Double) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Render 2x2 grid or list of 4 choices
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { option ->
                    val isSelected = (selectedOption != null && abs(selectedOption - option) < 0.009)
                    val isCorrectOption = abs(option - correctPrice) < 0.009

                    val backgroundColor = when {
                        !isEvaluating -> MaterialTheme.colorScheme.surface
                        isCorrectOption -> SuccessGreen.copy(alpha = 0.2f)
                        isSelected -> ErrorRed.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val borderColor = when {
                        !isEvaluating && isSelected -> MaterialTheme.colorScheme.primary
                        isEvaluating && isCorrectOption -> SuccessGreen
                        isEvaluating && isSelected -> ErrorRed
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clickable(enabled = !isEvaluating) {
                                onOptionSelected(option)
                            }
                            .testTag("quiz_choice_option_${DistractorGenerator.formatPrice(option)}")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "$${DistractorGenerator.formatPrice(option)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isEvaluating && isCorrectOption -> SuccessGreen
                                        isEvaluating && isSelected -> ErrorRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isEvaluating && isCorrectOption) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Sax",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isEvaluating && isSelected && !isCorrectOption) {
                                    Spacer(modifier = Modifier.width(6.dp))
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
            }
        }
    }
}

@Composable
private fun TypedAnswerSection(
    typedInput: String,
    isEvaluating: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Large input display
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (typedInput.isEmpty()) "$0.00" else "$$typedInput",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (typedInput.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("quiz_typed_input_display")
                )
            }
        }

        if (!isEvaluating) {
            NumericKeypad(
                onDigitClick = onDigit,
                onBackspace = onBackspace,
                onClear = onClear,
                onSubmit = onSubmit,
                canSubmit = typedInput.isNotBlank() && (typedInput.toDoubleOrNull() ?: 0.0) > 0.0
            )
        }
    }
}

@Composable
private fun FeedbackSection(
    feedback: FeedbackType,
    officialPrice: Double,
    cost: Double,
    responseTimeMs: Long,
    speedCategory: com.example.data.model.SpeedCategory?,
    streak: Int,
    isGrand: Boolean,
    nextDueAt: Long?,
    onContinue: () -> Unit
) {
    val isCorrect = (feedback == FeedbackType.CORRECT)
    val isWrong = (feedback == FeedbackType.WRONG)
    val isSkipped = (feedback == FeedbackType.SKIPPED)

    val responseSeconds = (responseTimeMs / 100) / 10.0

    val containerColor = when {
        isGrand -> Color(0xFFE8F5E9)
        isCorrect && speedCategory == com.example.data.model.SpeedCategory.SLOW -> WarningGold.copy(alpha = 0.14f)
        isCorrect -> SuccessGreen.copy(alpha = 0.12f)
        isWrong -> ErrorRed.copy(alpha = 0.12f)
        else -> WarningGold.copy(alpha = 0.12f)
    }

    val iconColor = when {
        isGrand -> Color(0xFF2E7D32)
        isCorrect && speedCategory == com.example.data.model.SpeedCategory.SLOW -> WarningGold
        isCorrect -> SuccessGreen
        isWrong -> ErrorRed
        else -> WarningGold
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quiz_feedback_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = when {
                        isGrand -> Icons.Default.CheckCircle
                        isCorrect -> Icons.Default.CheckCircle
                        isWrong -> Icons.Default.Close
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = when {
                        isCorrect && speedCategory == com.example.data.model.SpeedCategory.FAST ->
                            "Si fiican u yaqaanaa ⚡ (${responseSeconds}s)"
                        isCorrect && speedCategory == com.example.data.model.SpeedCategory.SLOW ->
                            "Sax gaabis ah ⚠️ (${responseSeconds}s)"
                        isCorrect ->
                            "Sax! Waad heshay 🎉 (${responseSeconds}s)"
                        isWrong ->
                            "Khalad ❌ Qiimaha rasmiga ah waa $${DistractorGenerator.formatPrice(officialPrice)}"
                        else ->
                            "Waad dhaaftay ℹ️ Qiimaha waa $${DistractorGenerator.formatPrice(officialPrice)}"
                    }

                    Text(
                        text = titleText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val subtitleText = when {
                        isCorrect && speedCategory == com.example.data.model.SpeedCategory.FAST ->
                            "Jawaab degdeg ah (< 4s)! Muddo fog kaddib baa dib laguugu soo celin doonaa."
                        isCorrect && speedCategory == com.example.data.model.SpeedCategory.SLOW ->
                            "Weli waa daciif maadaama aad ka gaabisay (> 8s). Dhakhso (3 daqiiqo) baa dib loogu soo celinayaa."
                        isCorrect ->
                            "Jawaab sax ah oo habaysan."
                        isWrong ->
                            "Dawadan dib baa laguugu waydiin doonaa 3–5 su'aalood kaddib."
                        else ->
                            "Dawadan waxay mudnaan sare ka heli doontaa su'aalaha soo socda."
                    }

                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Grand Celebration Badge
            if (isGrand) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏆 5 GUULOOD OO XIRIIR AH! Dawadan si buuxda ayaa loo xafiday (LEARNED) — mar dhif ah bay soo bixi doontaa.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Extra context: Cost & Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cost > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "Cost: $${DistractorGenerator.formatPrice(cost)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isCorrect) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "Xiriir: $streak/5",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCorrect) SuccessGreen else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("quiz_continue_btn")
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
    }
}

@Composable
private fun ExamResultsView(
    correct: Int,
    wrong: Int,
    skipped: Int,
    total: Int,
    averageTimeSeconds: Double,
    wrongItems: List<com.example.data.model.ExamWrongItem>,
    pastExams: List<com.example.data.model.ExamResult>,
    onRetry: () -> Unit,
    onHome: () -> Unit,
    onPracticeItem: (String) -> Unit
) {
    val percentage = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0
    val isPassing = percentage >= 70

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Celebration Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isPassing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
            ),
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
                    color = if (isPassing) SuccessGreen else WarningGold,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPassing) Icons.Default.Celebration else Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Imtixaanku Wuu Dhamaaday!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Exact format: "17/20 sax (85%)"
                Text(
                    text = "$correct/$total sax ($percentage%)",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPassing) SuccessGreen else WarningGold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Average time
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Celceliska waqtiga: ${String.format(Locale.US, "%.1f", averageTimeSeconds)}s su'aal kasta",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Stats summary row
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultRow(label = "Sax (Correct)", count = "$correct", color = SuccessGreen)
                ResultRow(label = "Khalad (Wrong)", count = "$wrong", color = ErrorRed)
                ResultRow(label = "La Dhaafay (Skipped)", count = "$skipped", color = WarningGold)
                ResultRow(label = "Isugeyn Su'aalo", count = "$total", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // LIST OF WRONG ITEMS WITH THE CORRECT PRICE
        if (wrongItems.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dawooyinka aad khaladday (${wrongItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                wrongItems.forEach { wrongItem ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wrongItem.item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (!wrongItem.item.systemName.isNullOrBlank()) {
                                    Text(
                                        text = "System: ${wrongItem.item.systemName}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Correct price in green
                                Text(
                                    text = "Qiimaha rasmiga ah: $${DistractorGenerator.formatPrice(wrongItem.correctPrice)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SuccessGreen
                                )

                                // Answer given
                                val given = wrongItem.answerGiven ?: "Waqtigu wuu dhacay ⏱️"
                                Text(
                                    text = "Jawaabtaadii: $given",
                                    fontSize = 12.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Button(
                                onClick = { onPracticeItem(wrongItem.item.id) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("exam_wrong_practice_${wrongItem.item.id}")
                            ) {
                                Text("Tababar", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Hambalyo! Dhammaan su'aalaha imtixaanka waad wada saxday (100%)!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // PROGRESS CHART OF PAST EXAMS
        if (pastExams.isNotEmpty()) {
            ExamProgressChart(pastExams = pastExams)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("exam_retry_btn")
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dib u Imtixaan", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onHome,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("exam_home_btn")
            ) {
                Text("Bogga Hore", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ResultRow(label: String, count: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
