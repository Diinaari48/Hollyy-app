package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Attempt
import com.example.data.model.ChartBarData
import com.example.data.model.ExamResult
import com.example.data.model.ExamWrongItem
import com.example.data.model.Item
import com.example.data.model.ItemStats
import com.example.data.model.ItemWithStats
import com.example.data.model.TodayReport
import com.example.data.model.WeakItemReport
import com.example.data.repository.PharmacyRepository
import com.example.data.repository.QuizQuestion
import com.example.data.sync.SupabaseClient
import com.example.data.sync.SupabaseSyncManager
import com.example.data.sync.SupabaseUser
import com.example.data.sync.SyncResult
import com.example.ui.navigation.NavTab
import com.example.ui.navigation.Screen
import com.example.ui.theme.AppFontSize
import com.example.ui.theme.AppThemeMode
import com.example.util.BulkImportPreviewResult
import com.example.util.CsvExporter
import com.example.util.CsvImporter
import com.example.util.DailyReminderManager
import com.example.util.DistractorGenerator
import com.example.util.NotificationHelper
import com.example.util.ReportAnalyzer
import com.example.util.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class FeedbackType {
    NONE,
    CORRECT,
    WRONG,
    SKIPPED
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: SupabaseUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

data class QuizUiState(
    val currentQuestion: QuizQuestion? = null,
    val feedback: FeedbackType = FeedbackType.NONE,
    val selectedOption: Double? = null,
    val typedInput: String = "",
    val feedbackOfficialPrice: Double? = null,
    val isEvaluating: Boolean = false,
    val questionStartTime: Long = 0L,
    val consecutiveCorrect: Int = 0,
    val lastResponseTimeMs: Long = 0L,
    val speedCategory: com.example.data.model.SpeedCategory? = null,
    val isGrandCelebration: Boolean = false,
    val nextDueAt: Long? = null,
    // Exam mode state
    val isExamMode: Boolean = false,
    val examQuestionCount: Int = 20,
    val examTimeLimitSeconds: Int = 15,
    val examTimeRemainingSeconds: Int = 15,
    val examCurrentIndex: Int = 0,
    val examCorrectCount: Int = 0,
    val examWrongCount: Int = 0,
    val examSkippedCount: Int = 0,
    val isExamFinished: Boolean = false,
    val examQuestionsList: List<QuizQuestion> = emptyList(),
    val examWrongItems: List<ExamWrongItem> = emptyList(),
    val examTotalResponseTimeMs: Long = 0L,
    val examAverageTimeSeconds: Double = 0.0
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val supabaseClient = SupabaseClient(application)
    private val db = AppDatabase.getDatabase(application)
    val supabaseSyncManager = SupabaseSyncManager(application, supabaseClient, db.itemDao())

    val repository = PharmacyRepository(
        itemDao = db.itemDao(),
        getUserId = { supabaseClient.currentUser.value?.id ?: "" }
    )

    private var examTimerJob: Job? = null

    // Auth State
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: StateFlow<SupabaseUser?> = supabaseClient.currentUser

    private val _isItemsLoading = MutableStateFlow(false)
    val isItemsLoading: StateFlow<Boolean> = _isItemsLoading.asStateFlow()

    init {
        // Initial auth check
        val initialUser = supabaseClient.currentUser.value
        if (initialUser != null) {
            _authState.value = AuthState.Authenticated(initialUser)
            // Schedule reminder for active user
            DailyReminderManager.scheduleDailyReminder(application, com.example.service.WorkSessionManager.sessionConfig.value)
            loadItems()
            triggerSilentSync()
        }
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    viewModelScope.launch {
                        if (currentUser.value != null) {
                            loadItems()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed registering network callback", e)
        }
    }

    fun loadItems() {
        viewModelScope.launch {
            if (_isItemsLoading.value) return@launch
            _isItemsLoading.value = true
            try {
                supabaseSyncManager.loadItems(showToastOnError = true)
            } finally {
                _isItemsLoading.value = false
            }
        }
    }

    fun triggerSilentSync() {
        viewModelScope.launch {
            if (supabaseSyncManager.isOnline()) {
                supabaseSyncManager.performSync()
            }
        }
    }

    fun hasSignedUpOnce(): Boolean = supabaseClient.hasSignedUpOnce()

    fun clearAuthError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }

    fun signUp(normalizedPhone: String, password: String, fullName: String?) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val prevUid = supabaseClient.currentUser.value?.id
            val result = supabaseClient.signUpWithPhone(normalizedPhone, password, fullName)
            result.onSuccess { user ->
                if (!prevUid.isNullOrEmpty() && prevUid != user.id) {
                    repository.clearCurrentUserData(prevUid)
                }
                _authState.value = AuthState.Authenticated(user)
                DailyReminderManager.scheduleDailyReminder(getApplication(), sessionConfig.value)
                loadItems()
            }.onFailure { err ->
                _authState.value = AuthState.Error(err.message ?: "Diiwaangelintu waa fashilantay")
            }
        }
    }

    fun signIn(normalizedPhone: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val prevUid = supabaseClient.currentUser.value?.id
            val result = supabaseClient.signInWithPhone(normalizedPhone, password)
            result.onSuccess { user ->
                if (!prevUid.isNullOrEmpty() && prevUid != user.id) {
                    repository.clearCurrentUserData(prevUid)
                }
                _authState.value = AuthState.Authenticated(user)
                DailyReminderManager.scheduleDailyReminder(getApplication(), sessionConfig.value)
                loadItems()
                // Optional auto-sync on login
                if (supabaseSyncManager.isOnline()) {
                    supabaseSyncManager.performSync()
                }
            }.onFailure { err ->
                _authState.value = AuthState.Error(err.message ?: "Galitaanku waa fashilmay")
            }
        }
    }

    fun logout() {
        examTimerJob?.cancel()
        val currentUid = supabaseClient.currentUser.value?.id
        viewModelScope.launch {
            if (!currentUid.isNullOrEmpty()) {
                repository.clearCurrentUserData(currentUid)
            }
            supabaseClient.signOut()
            _authState.value = AuthState.Idle
            _selectedTab.value = NavTab.HOME
            _screenStack.value = listOf(Screen.Main)
            _quizState.value = QuizUiState()
        }
    }

    // Active Tab for persistent Bottom Navigation Bar (Default is NavTab.HOME)
    private val _selectedTab = MutableStateFlow(NavTab.HOME)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
        // Pop any sub-screens back to Main
        if (_screenStack.value.size > 1) {
            _screenStack.value = listOf(Screen.Main)
        }
        if (tab == NavTab.ITEMS) {
            loadItems()
        }
    }

    // Navigation Stack
    private val _screenStack = MutableStateFlow<List<Screen>>(listOf(Screen.Main))
    val screenStack: StateFlow<List<Screen>> = _screenStack.asStateFlow()
    val currentScreen: Screen get() = _screenStack.value.lastOrNull() ?: Screen.Main

    fun navigateTo(screen: Screen) {
        val current = _screenStack.value.toMutableList()
        current.add(screen)
        _screenStack.value = current

        if (screen is Screen.Quiz) {
            if (screen.mode == Screen.Quiz.MODE_EXAM) {
                startExam(questionCount = 20, timeLimitSeconds = 15)
            } else {
                startQuiz(screen.mode, screen.initialItemId)
            }
        }
    }

    fun navigateBack(): Boolean {
        examTimerJob?.cancel()
        val current = _screenStack.value.toMutableList()
        return if (current.size > 1) {
            current.removeAt(current.size - 1)
            _screenStack.value = current
            true
        } else {
            false
        }
    }

    // Theme & Font Settings
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _fontScale = MutableStateFlow(AppFontSize.NORMAL)
    val fontScale: StateFlow<AppFontSize> = _fontScale.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
    }

    fun setFontScale(scale: AppFontSize) {
        _fontScale.value = scale
    }

    // Dynamic User-Scoped Data Flows
    val allItems: StateFlow<List<ItemWithStats>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getAllItemsWithStats(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAttempts: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getTotalAttempts(user.id) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val correctAttempts: StateFlow<Int> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getCorrectAttempts(user.id) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allAttempts: StateFlow<List<Attempt>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getAllAttempts(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pastExams: StateFlow<List<ExamResult>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getAllExamResults(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val skippedItems: StateFlow<List<com.example.data.model.SkippedItemEntry>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repository.getSkippedItems(user.id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Work Session states
    val isSessionActive = com.example.service.WorkSessionManager.isSessionActive
    val isWaitingForQuietHours = com.example.service.WorkSessionManager.isWaitingForQuietHours
    val quietNoticeMessage = com.example.service.WorkSessionManager.quietNoticeMessage
    val sessionConfig = com.example.service.WorkSessionManager.sessionConfig

    // Spaced Repetition & Response Time Config
    private val _spacedRepetitionConfig = MutableStateFlow(com.example.data.model.SpacedRepetitionConfig())
    val spacedRepetitionConfig: StateFlow<com.example.data.model.SpacedRepetitionConfig> = _spacedRepetitionConfig.asStateFlow()

    fun updateSpacedRepetitionConfig(config: com.example.data.model.SpacedRepetitionConfig) {
        _spacedRepetitionConfig.value = config
    }

    fun toggleWorkSession() {
        val app = getApplication<Application>()
        if (isSessionActive.value) {
            DailyReminderManager.recordSessionEndedWithDhamee(app)
            val stopIntent = Intent(app, com.example.service.WorkSessionService::class.java).apply {
                action = com.example.service.WorkSessionService.ACTION_STOP
            }
            app.startService(stopIntent)
        } else {
            val config = sessionConfig.value
            val startIntent = Intent(app, com.example.service.WorkSessionService::class.java).apply {
                action = com.example.service.WorkSessionService.ACTION_START
                putExtra(com.example.service.WorkSessionService.EXTRA_INTERVAL_MINUTES, config.intervalMinutes)
                putExtra(com.example.service.WorkSessionService.EXTRA_TIMEOUT_MINUTES, config.timeoutMinutes)
                putExtra(com.example.service.WorkSessionService.EXTRA_QUIET_ENABLED, config.quietHoursEnabled)
                putExtra(com.example.service.WorkSessionService.EXTRA_QUIET_START_H, config.quietStartHour)
                putExtra(com.example.service.WorkSessionService.EXTRA_QUIET_START_M, config.quietStartMinute)
                putExtra(com.example.service.WorkSessionService.EXTRA_QUIET_END_H, config.quietEndHour)
                putExtra(com.example.service.WorkSessionService.EXTRA_QUIET_END_M, config.quietEndMinute)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                app.startForegroundService(startIntent)
            } else {
                app.startService(startIntent)
            }
        }
    }

    fun updateSessionConfig(config: com.example.data.model.WorkSessionConfig) {
        com.example.service.WorkSessionManager.updateConfig(config)
        DailyReminderManager.scheduleDailyReminder(getApplication(), config)
    }

    fun clearQuietNotice() {
        com.example.service.WorkSessionManager.clearNoticeMessage()
    }

    fun deleteSkippedAttempt(attemptId: String) {
        viewModelScope.launch {
            repository.deleteAttempt(attemptId)
        }
    }

    // Search query & results
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<ItemWithStats>> = _searchQuery
        .combine(allItems) { query, items ->
            if (query.isBlank()) {
                items
            } else {
                items.filter {
                    it.item.name.contains(query, ignoreCase = true) ||
                        (it.item.systemName != null && it.item.systemName.contains(query, ignoreCase = true))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // CRUD Item operations
    fun addItem(name: String, systemName: String?, cost: Double, price: Double) {
        val uid = supabaseClient.currentUser.value?.id ?: return
        viewModelScope.launch {
            val item = Item(
                userId = uid,
                name = name,
                systemName = systemName?.ifBlank { null },
                cost = cost,
                price = price
            )
            repository.insertItem(item)
            triggerSilentSync()
        }
    }

    fun updateItem(item: Item) {
        viewModelScope.launch {
            repository.updateItem(item)
            triggerSilentSync()
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            repository.deleteItem(item)
            triggerSilentSync()
        }
    }

    // Bulk Import
    private val _bulkImportPreview = MutableStateFlow<BulkImportPreviewResult?>(null)
    val bulkImportPreview: StateFlow<BulkImportPreviewResult?> = _bulkImportPreview.asStateFlow()

    private val _bulkImportSuccessMessage = MutableStateFlow<String?>(null)
    val bulkImportSuccessMessage: StateFlow<String?> = _bulkImportSuccessMessage.asStateFlow()

    fun previewBulkCsv(text: String) {
        val result = CsvImporter.parse(text)
        _bulkImportPreview.value = result
    }

    fun clearBulkPreview() {
        _bulkImportPreview.value = null
        _bulkImportSuccessMessage.value = null
    }

    fun commitBulkImport() {
        val preview = _bulkImportPreview.value ?: return
        viewModelScope.launch {
            if (preview.validItems.isNotEmpty()) {
                repository.insertItems(preview.validItems)
                _bulkImportSuccessMessage.value = "Waxaa si guul leh loo geliyey ${preview.validItems.size} dawo!"
                _bulkImportPreview.value = null
                triggerSilentSync()
            }
        }
    }

    // Quiz State
    private val _quizState = MutableStateFlow(QuizUiState())
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    fun startQuiz(mode: String, initialItemId: String? = null, questionMode: String? = null) {
        examTimerJob?.cancel()
        val isExam = (mode == Screen.Quiz.MODE_EXAM)
        if (isExam) {
            startExam(questionCount = 20, timeLimitSeconds = 15, questionMode = questionMode)
            return
        }

        repository.resetQuizSession()
        _quizState.value = QuizUiState(
            isExamMode = false,
            examQuestionCount = 20,
            examCurrentIndex = 0,
            examCorrectCount = 0,
            examWrongCount = 0,
            examSkippedCount = 0,
            isExamFinished = false
        )
        loadNextQuestion(initialItemId, questionMode)
    }

    // EXAM MODE
    fun resetExamToSetup() {
        examTimerJob?.cancel()
        _quizState.value = QuizUiState(
            isExamMode = false,
            currentQuestion = null,
            isExamFinished = false
        )
    }

    fun startExam(questionCount: Int = 20, timeLimitSeconds: Int = 15, questionMode: String? = null) {
        examTimerJob?.cancel()
        viewModelScope.launch {
            repository.resetQuizSession()
            val mode = questionMode ?: sessionConfig.value.questionMode
            val questions = repository.generateExamQuestions(questionCount, mode)

            _quizState.value = QuizUiState(
                isExamMode = true,
                examQuestionCount = if (questions.isNotEmpty()) questions.size else questionCount,
                examTimeLimitSeconds = timeLimitSeconds,
                examTimeRemainingSeconds = timeLimitSeconds,
                examCurrentIndex = 1,
                examCorrectCount = 0,
                examWrongCount = 0,
                examSkippedCount = 0,
                isExamFinished = false,
                examQuestionsList = questions,
                examWrongItems = emptyList(),
                examTotalResponseTimeMs = 0L,
                currentQuestion = questions.firstOrNull(),
                questionStartTime = System.currentTimeMillis()
            )

            if (questions.isNotEmpty()) {
                startExamQuestionTimer(timeLimitSeconds)
            }
        }
    }

    private fun startExamQuestionTimer(timeLimitSeconds: Int) {
        examTimerJob?.cancel()
        _quizState.value = _quizState.value.copy(examTimeRemainingSeconds = timeLimitSeconds)

        examTimerJob = viewModelScope.launch {
            var remaining = timeLimitSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _quizState.value = _quizState.value.copy(examTimeRemainingSeconds = remaining)
            }
            onExamQuestionTimedOut()
        }
    }

    private fun onExamQuestionTimedOut() {
        val state = _quizState.value
        val question = state.currentQuestion ?: return
        if (state.isEvaluating || state.isExamFinished) return

        val responseTimeMs = state.examTimeLimitSeconds * 1000L
        val wrongList = state.examWrongItems.toMutableList()
        wrongList.add(
            ExamWrongItem(
                item = question.item,
                answerGiven = "Waqtigu wuu dhacay ⏱️",
                correctPrice = question.targetPrice,
                responseTimeMs = responseTimeMs
            )
        )

        _quizState.value = state.copy(
            isEvaluating = true,
            feedback = FeedbackType.WRONG,
            feedbackOfficialPrice = question.targetPrice,
            lastResponseTimeMs = responseTimeMs,
            examWrongCount = state.examWrongCount + 1,
            examTotalResponseTimeMs = state.examTotalResponseTimeMs + responseTimeMs,
            examWrongItems = wrongList
        )

        viewModelScope.launch {
            repository.recordAttempt(
                itemId = question.item.id,
                questionType = question.questionType,
                type = "EXAM",
                result = "WRONG",
                answerGiven = null,
                responseTimeMs = responseTimeMs,
                config = _spacedRepetitionConfig.value
            )
            triggerSilentSync()
        }
    }

    private fun loadNextQuestion(preferredItemId: String? = null, overrideQuestionMode: String? = null) {
        viewModelScope.launch {
            val state = _quizState.value

            if (state.isExamMode) {
                val nextIndex = state.examCurrentIndex + 1
                if (nextIndex > state.examQuestionCount || nextIndex > state.examQuestionsList.size) {
                    finishExam()
                    return@launch
                }

                val nextQuestion = state.examQuestionsList.getOrNull(nextIndex - 1)
                _quizState.value = state.copy(
                    currentQuestion = nextQuestion,
                    feedback = FeedbackType.NONE,
                    selectedOption = null,
                    typedInput = "",
                    feedbackOfficialPrice = null,
                    isEvaluating = false,
                    lastResponseTimeMs = 0L,
                    speedCategory = null,
                    isGrandCelebration = false,
                    nextDueAt = null,
                    questionStartTime = System.currentTimeMillis(),
                    examCurrentIndex = nextIndex
                )

                startExamQuestionTimer(state.examTimeLimitSeconds)
                return@launch
            }

            // Drill Mode
            val mode = overrideQuestionMode ?: sessionConfig.value.questionMode
            val question = repository.getNextQuestion(preferredItemId, mode)
            _quizState.value = state.copy(
                currentQuestion = question,
                feedback = FeedbackType.NONE,
                selectedOption = null,
                typedInput = "",
                feedbackOfficialPrice = null,
                isEvaluating = false,
                lastResponseTimeMs = 0L,
                speedCategory = null,
                isGrandCelebration = false,
                nextDueAt = null,
                questionStartTime = System.currentTimeMillis()
            )
        }
    }

    private fun finishExam() {
        examTimerJob?.cancel()
        val state = _quizState.value
        val total = state.examQuestionCount.coerceAtLeast(1)
        val correct = state.examCorrectCount
        val percentage = ((correct.toDouble() / total) * 100).toInt()
        val avgMs = if (total > 0) state.examTotalResponseTimeMs / total else 0L
        val avgSec = avgMs / 1000.0

        _quizState.value = state.copy(
            isExamFinished = true,
            currentQuestion = null,
            feedback = FeedbackType.NONE,
            isEvaluating = false,
            examAverageTimeSeconds = avgSec
        )

        viewModelScope.launch {
            val examRecord = ExamResult(
                totalQuestions = total,
                correctCount = correct,
                wrongCount = state.examWrongCount,
                skippedCount = state.examSkippedCount,
                scorePercentage = percentage,
                averageTimeMs = avgMs
            )
            repository.saveExamResult(examRecord)
            triggerSilentSync()
        }
    }

    fun onTypedInputDigit(digit: String) {
        val current = _quizState.value.typedInput
        if (digit == ".") {
            if (!current.contains(".")) {
                _quizState.value = _quizState.value.copy(typedInput = if (current.isEmpty()) "0." else "$current.")
            }
        } else {
            if (current.length < 8) {
                _quizState.value = _quizState.value.copy(typedInput = current + digit)
            }
        }
    }

    fun onTypedInputBackspace() {
        val current = _quizState.value.typedInput
        if (current.isNotEmpty()) {
            _quizState.value = _quizState.value.copy(typedInput = current.dropLast(1))
        }
    }

    fun onTypedInputClear() {
        _quizState.value = _quizState.value.copy(typedInput = "")
    }

    fun submitChoice(option: Double) {
        if (_quizState.value.isEvaluating) return
        evaluateAnswer(option, isTyped = false)
    }

    fun submitTyped() {
        if (_quizState.value.isEvaluating) return
        val entered = _quizState.value.typedInput.toDoubleOrNull() ?: return
        evaluateAnswer(entered, isTyped = true)
    }

    private fun evaluateAnswer(givenAnswer: Double, isTyped: Boolean) {
        examTimerJob?.cancel()
        val question = _quizState.value.currentQuestion ?: return
        val responseTime = System.currentTimeMillis() - _quizState.value.questionStartTime
        val isCorrect = abs(givenAnswer - question.targetPrice) < 0.01

        val currentState = _quizState.value
        val isExam = currentState.isExamMode

        val wrongList = currentState.examWrongItems.toMutableList()
        if (isExam && !isCorrect) {
            wrongList.add(
                ExamWrongItem(
                    item = question.item,
                    answerGiven = "$${DistractorGenerator.formatPrice(givenAnswer)}",
                    correctPrice = question.targetPrice,
                    responseTimeMs = responseTime
                )
            )
        }

        _quizState.value = currentState.copy(
            isEvaluating = true,
            selectedOption = givenAnswer,
            feedbackOfficialPrice = question.targetPrice,
            examWrongItems = wrongList
        )

        viewModelScope.launch {
            val attemptType = if (isExam) "EXAM" else if (isTyped) "TYPED" else "CHOICE"
            val attemptResult = if (isCorrect) "CORRECT" else "WRONG"

            val (updatedStats, speedCat) = repository.recordAttempt(
                itemId = question.item.id,
                questionType = question.questionType,
                type = attemptType,
                result = attemptResult,
                answerGiven = givenAnswer,
                responseTimeMs = responseTime,
                config = _spacedRepetitionConfig.value
            )
            triggerSilentSync()

            val isGrand = isCorrect && (updatedStats.correctStreak == 5)
            val updatedState = _quizState.value

            _quizState.value = updatedState.copy(
                feedback = if (isCorrect) FeedbackType.CORRECT else FeedbackType.WRONG,
                consecutiveCorrect = if (isCorrect) updatedState.consecutiveCorrect + 1 else 0,
                lastResponseTimeMs = responseTime,
                speedCategory = if (isCorrect) speedCat else null,
                isGrandCelebration = isGrand,
                nextDueAt = updatedStats.nextDueAt,
                examCorrectCount = if (isCorrect && isExam) updatedState.examCorrectCount + 1 else updatedState.examCorrectCount,
                examWrongCount = if (!isCorrect && isExam) updatedState.examWrongCount + 1 else updatedState.examWrongCount,
                examTotalResponseTimeMs = if (isExam) updatedState.examTotalResponseTimeMs + responseTime else updatedState.examTotalResponseTimeMs
            )
        }
    }

    fun skipQuestion() {
        examTimerJob?.cancel()
        val question = _quizState.value.currentQuestion ?: return
        if (_quizState.value.isEvaluating) return

        val responseTime = System.currentTimeMillis() - _quizState.value.questionStartTime
        val currentState = _quizState.value
        val isExam = currentState.isExamMode

        val wrongList = currentState.examWrongItems.toMutableList()
        if (isExam) {
            wrongList.add(
                ExamWrongItem(
                    item = question.item,
                    answerGiven = "Waad dhaaftay ⏩",
                    correctPrice = question.targetPrice,
                    responseTimeMs = responseTime
                )
            )
        }

        _quizState.value = currentState.copy(
            isEvaluating = true,
            feedback = FeedbackType.SKIPPED,
            feedbackOfficialPrice = question.targetPrice,
            examWrongItems = wrongList
        )

        viewModelScope.launch {
            val attemptType = if (isExam) "EXAM" else if (question.isLevel2Typed) "TYPED" else "CHOICE"
            repository.recordAttempt(
                itemId = question.item.id,
                questionType = question.questionType,
                type = attemptType,
                result = "SKIPPED",
                answerGiven = null,
                responseTimeMs = responseTime,
                config = _spacedRepetitionConfig.value
            )
            triggerSilentSync()

            val updatedState = _quizState.value
            _quizState.value = updatedState.copy(
                consecutiveCorrect = 0,
                lastResponseTimeMs = responseTime,
                speedCategory = null,
                isGrandCelebration = false,
                examSkippedCount = if (isExam) updatedState.examSkippedCount + 1 else updatedState.examSkippedCount,
                examTotalResponseTimeMs = if (isExam) updatedState.examTotalResponseTimeMs + responseTime else updatedState.examTotalResponseTimeMs
            )
        }
    }

    fun continueToNextQuestion() {
        loadNextQuestion()
    }

    // REPORT STATEFLOWS
    val todayReport: StateFlow<TodayReport> = allAttempts
        .map { ReportAnalyzer.calculateTodayReport(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayReport())

    val topWeakestItems: StateFlow<List<WeakItemReport>> = combine(allItems, allAttempts) { items, attempts ->
        ReportAnalyzer.calculateTop10WeakestItems(items, attempts, slowThresholdMs = spacedRepetitionConfig.value.slowThresholdSeconds * 1000L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topWeakestSellPriceItems: StateFlow<List<WeakItemReport>> = combine(allItems, allAttempts) { items, attempts ->
        ReportAnalyzer.calculateTop10WeakestItemsForType(items, attempts, ItemStats.QUESTION_TYPE_SELL_PRICE, slowThresholdMs = spacedRepetitionConfig.value.slowThresholdSeconds * 1000L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topWeakestCostItems: StateFlow<List<WeakItemReport>> = combine(allItems, allAttempts) { items, attempts ->
        ReportAnalyzer.calculateTop10WeakestItemsForType(items, attempts, ItemStats.QUESTION_TYPE_COST, slowThresholdMs = spacedRepetitionConfig.value.slowThresholdSeconds * 1000L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sellPriceAccuracy: StateFlow<Pair<Int, Int>> = allAttempts.map { attempts ->
        ReportAnalyzer.calculateAccuracyForType(attempts, ItemStats.QUESTION_TYPE_SELL_PRICE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    val costAccuracy: StateFlow<Pair<Int, Int>> = allAttempts.map { attempts ->
        ReportAnalyzer.calculateAccuracyForType(attempts, ItemStats.QUESTION_TYPE_COST)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    val streakDays: StateFlow<Int> = allAttempts
        .map { ReportAnalyzer.calculateConsecutiveDaysStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailyChartData: StateFlow<List<ChartBarData>> = allAttempts
        .map { ReportAnalyzer.buildDailyChartData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyChartData: StateFlow<List<ChartBarData>> = allAttempts
        .map { ReportAnalyzer.buildWeeklyChartData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyChartData: StateFlow<List<ChartBarData>> = allAttempts
        .map { ReportAnalyzer.buildMonthlyChartData(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications & Reminders
    fun sendTestNotification() {
        val items = allItems.value
        val itemToNotify = items.randomOrNull()?.item ?: SampleData.sampleMedicines.first()
        NotificationHelper.showQuizNotification(getApplication(), itemToNotify)
    }

    fun sendTestDailyReminder() {
        NotificationHelper.showDailyReminderNotification(getApplication())
    }

    // CSV Export & Backup
    fun getCsvExportText(): String {
        val items = allItems.value.map { it.item }
        return CsvExporter.exportToCsv(items)
    }

    fun shareCsvBackup(context: Context) {
        val csvText = getCsvExportText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Qiimo Quiz — Kaabta Dawooyinka (Medicines Backup)")
            putExtra(Intent.EXTRA_TEXT, csvText)
        }
        val chooser = Intent.createChooser(intent, "Dhoofi Kaabta CSV")
        chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooser)
    }

    // Cloud Sync
    val isSyncing: StateFlow<Boolean> = supabaseSyncManager.isSyncing
    val lastSyncResult: StateFlow<SyncResult?> = supabaseSyncManager.lastSyncResult

    fun performCloudSync() {
        viewModelScope.launch {
            loadItems()
            supabaseSyncManager.performSync()
        }
    }

    suspend fun getDebugInfo(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // a) backend URL host only (never keys) and whether URL and anon key are non-empty
        val url = supabaseClient.getBaseUrl()
        val anonKey = supabaseClient.getAnonKey()
        val host = try { java.net.URI(url).host ?: "unknown" } catch (_: Exception) { "invalid_url" }
        sb.append("=== A) BACKEND CONFIG ===\n")
        sb.append("Host: $host\n")
        sb.append("URL Non-Empty: ${url.isNotBlank()}\n")
        sb.append("Anon Key Non-Empty: ${anonKey.isNotBlank()}\n\n")

        // b) session: user id, phone, whether an access token exists, token expiry
        val user = supabaseClient.currentUser.value
        sb.append("=== B) SESSION ===\n")
        sb.append("User ID: ${user?.id ?: "(none)"}\n")
        sb.append("Phone: ${user?.phone ?: "(none)"}\n")
        val hasToken = !user?.accessToken.isNullOrBlank()
        sb.append("Access Token Exists: $hasToken\n")
        val exp = if (user != null) {
            if (user.expiresAt > 0L) user.expiresAt else supabaseClient.getJwtExpiry(user.accessToken)
        } else 0L
        val isExp = supabaseClient.isTokenExpired()
        val expDateStr = if (exp > 0L) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.format(java.util.Date(exp * 1000))
        } else "unknown"
        sb.append("Token Expiry: $expDateStr (Expired: $isExp)\n\n")

        // c) a RAW request made right now
        sb.append("=== C) RAW REQUEST (LIVE) ===\n")
        val rawResult = supabaseClient.fetchItemsRaw(retryOnAuthError = false)
        sb.append("HTTP Status: ${rawResult.httpStatus}\n")
        sb.append("Row Count: ${rawResult.rowCount}\n")
        sb.append("Body Snippet (first 300 chars):\n")
        sb.append(rawResult.bodySnippet)
        sb.append("\n\n")

        // d) number of items in the local database for the current user id
        val localCount = if (user != null) {
            db.itemDao().getItemsCountForUser(user.id)
        } else 0
        sb.append("=== D) LOCAL DATABASE ===\n")
        sb.append("Items in Local DB: $localCount\n\n")

        // e) the last load result: parsed N, saved N, skipped K, and every exception
        val lastLoad = supabaseSyncManager.lastLoadResult.value
        sb.append("=== E) LAST LOAD RESULT ===\n")
        if (lastLoad != null) {
            sb.append("Parsed: ${lastLoad.parsedCount}\n")
            sb.append("Saved: ${lastLoad.savedCount}\n")
            sb.append("Skipped: ${lastLoad.skippedCount}\n")
            if (lastLoad.error != null || lastLoad.exceptionDetails != null) {
                sb.append("Error: ${lastLoad.error}\n")
                sb.append("Exception: ${lastLoad.exceptionDetails}\n")
            } else {
                sb.append("Exceptions: None\n")
            }
        } else {
            sb.append("No loadItems() executed yet.\n")
        }

        sb.toString()
    }

    fun seedSampleMedicines() {
        viewModelScope.launch {
            repository.seedSampleMedicines()
        }
    }
}
