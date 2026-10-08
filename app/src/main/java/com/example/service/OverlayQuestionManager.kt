package com.example.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.repository.PharmacyRepository
import com.example.data.repository.QuizQuestion
import com.example.util.DistractorGenerator
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayQuestionManager(
    private val context: Context,
    private val repository: PharmacyRepository
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentOverlayView: View? = null
    private var currentQuestion: QuizQuestion? = null
    private var timeoutRunnable: Runnable? = null
    private var isAnswered = false
    private var overlayStartTime = 0L

    fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Shows a popup over other apps if permission is granted, otherwise falls back to High-Priority Notification.
     */
    fun showQuestionPopup(timeoutMinutes: Int, onComplete: () -> Unit = {}) {
        serviceScope.launch {
            val question = repository.getNextQuestion()
            if (question == null) {
                onComplete()
                return@launch
            }

            currentQuestion = question
            isAnswered = false

            if (canDrawOverlay()) {
                try {
                    displayOverlayWindow(question, timeoutMinutes, onComplete)
                    return@launch
                } catch (e: Exception) {
                    // In case adding overlay view fails on a sandbox environment, fall back to notification
                }
            }

            // Fallback: High priority notification with tap-to-open and timeout
            NotificationHelper.showQuizNotification(context, question.item)
            setupTimeout(question.item.id, timeoutMinutes, onComplete)
        }
    }

    private fun displayOverlayWindow(
        question: QuizQuestion,
        timeoutMinutes: Int,
        onComplete: () -> Unit
    ) {
        // Remove any previous view
        dismissOverlay(recordAsSkipped = false)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            width = (context.resources.displayMetrics.widthPixels * 0.92).toInt()
        }

        overlayStartTime = System.currentTimeMillis()
        val overlayLayout = buildOverlayView(question, timeoutMinutes, onComplete)
        currentOverlayView = overlayLayout

        windowManager.addView(overlayLayout, params)

        // Setup 5-minute auto-skip timeout
        setupTimeout(question.item.id, timeoutMinutes, onComplete)
    }

    private fun buildOverlayView(
        question: QuizQuestion,
        timeoutMinutes: Int,
        onComplete: () -> Unit
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(2), Color.parseColor("#006A60"))
            }
            elevation = dp(16).toFloat()
        }

        // Header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(context).apply {
            text = "⚡ Qiimo Quiz • Shaqo Socota"
            setTextColor(Color.parseColor("#006A60"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E0F2F1"))
                cornerRadius = dp(12).toFloat()
            }
        }
        headerRow.addView(badge)
        root.addView(headerRow)

        // Spacing
        root.addView(createSpacer(dp(12)))

        // Question Title
        val questionText = TextView(context).apply {
            text = "Qiimaha ${question.item.name} waa immisa marka macaamiil laga iibinayo?"
            setTextColor(Color.parseColor("#1C1B1F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(questionText)

        if (!question.item.systemName.isNullOrBlank()) {
            val systemText = TextView(context).apply {
                text = "System: ${question.item.systemName}"
                setTextColor(Color.parseColor("#49454F"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            root.addView(systemText)
        }

        root.addView(createSpacer(dp(16)))

        // Feedback Text Container
        val feedbackText = TextView(context).apply {
            visibility = View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(feedbackText)

        // Choices Grid or Buttons
        val optionsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val options = question.options.ifEmpty {
            DistractorGenerator.generateOptions(question.item.price)
        }

        // 2 rows of 2 options
        val optionRows = options.chunked(2)
        for (rowItems in optionRows) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
            }

            for (option in rowItems) {
                val btn = Button(context).apply {
                    text = "$${DistractorGenerator.formatPrice(option)}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#006A60"))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F4FAF7"))
                        cornerRadius = dp(14).toFloat()
                        setStroke(dp(1), Color.parseColor("#6F7976"))
                    }
                    val params = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                    layoutParams = params

                    setOnClickListener {
                        if (isAnswered) return@setOnClickListener
                        isAnswered = true
                        cancelTimeout()

                        val isCorrect = abs(option - question.item.price) < 0.009
                        val responseTime = System.currentTimeMillis() - overlayStartTime
                        val responseSec = (responseTime / 100) / 10.0

                        if (isCorrect) {
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#2E7D32"))
                                cornerRadius = dp(14).toFloat()
                            }
                            setTextColor(Color.WHITE)
                            feedbackText.visibility = View.VISIBLE

                            val speedMsg = when {
                                responseTime < 4000L -> "Fiican ✅ Si fiican u yaqaanaa ⚡ (${responseSec}s)"
                                responseTime > 8000L -> "Sax gaabis ah ⚠️ (${responseSec}s) — Dhakhso baa dib loogu soo celinayaa"
                                else -> "Fiican ✅ (${responseSec}s)"
                            }
                            feedbackText.text = speedMsg
                            feedbackText.setTextColor(Color.parseColor("#2E7D32"))

                            serviceScope.launch {
                                repository.recordAttempt(
                                    itemId = question.item.id,
                                    type = "WORK_SESSION",
                                    result = "CORRECT",
                                    answerGiven = option,
                                    responseTimeMs = responseTime
                                )
                            }
                        } else {
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#C62828"))
                                cornerRadius = dp(14).toFloat()
                            }
                            setTextColor(Color.WHITE)
                            feedbackText.visibility = View.VISIBLE
                            feedbackText.text = "Khalad ❌ Qiimaha rasmiga ah waa $${DistractorGenerator.formatPrice(question.item.price)}"
                            feedbackText.setTextColor(Color.parseColor("#C62828"))

                            serviceScope.launch {
                                repository.recordAttempt(
                                    itemId = question.item.id,
                                    type = "WORK_SESSION",
                                    result = "WRONG",
                                    answerGiven = option,
                                    responseTimeMs = responseTime
                                )
                            }
                        }

                        // Delay dismiss to let user see the feedback
                        mainHandler.postDelayed({
                            dismissOverlay(recordAsSkipped = false)
                            onComplete()
                        }, 1400)
                    }
                }
                row.addView(btn)
            }
            optionsContainer.addView(row)
        }
        root.addView(optionsContainer)

        root.addView(createSpacer(dp(14)))

        // Bottom Action Bar: "Dhaaf" (Skip) & Timeout indicator
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val timeoutLabel = TextView(context).apply {
            text = "⏳ Waxay xidhmi doontaa $timeoutMinutes daqiiqo gudahood"
            setTextColor(Color.parseColor("#79747E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        bottomBar.addView(timeoutLabel)

        val skipBtn = Button(context).apply {
            text = "Dhaaf"
            setTextColor(Color.parseColor("#C62828"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#C62828"))
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener {
                if (isAnswered) return@setOnClickListener
                isAnswered = true
                cancelTimeout()
                dismissOverlay(recordAsSkipped = true)
                onComplete()
            }
        }
        bottomBar.addView(skipBtn)

        root.addView(bottomBar)

        return root
    }

    private fun setupTimeout(itemId: String, timeoutMinutes: Int, onComplete: () -> Unit) {
        cancelTimeout()
        val timeoutMs = timeoutMinutes * 60 * 1000L

        timeoutRunnable = Runnable {
            if (!isAnswered) {
                isAnswered = true
                dismissOverlay(recordAsSkipped = true)
                onComplete()
            }
        }

        mainHandler.postDelayed(timeoutRunnable!!, timeoutMs)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
    }

    fun dismissOverlay(recordAsSkipped: Boolean = false) {
        cancelTimeout()

        if (recordAsSkipped && currentQuestion != null && !isAnswered) {
            isAnswered = true
            val id = currentQuestion!!.item.id
            serviceScope.launch {
                repository.recordItemSkipped(id)
            }
        }

        currentOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            currentOverlayView = null
        }
    }

    private fun dp(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun createSpacer(heightPx: Int): View {
        return View(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        }
    }
}
