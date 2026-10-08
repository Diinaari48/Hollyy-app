package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.model.WorkSessionConfig
import com.example.data.repository.PharmacyRepository
import java.util.Calendar

class WorkSessionService : Service() {

    companion object {
        const val ACTION_START = "action_start_work_session"
        const val ACTION_STOP = "action_stop_work_session"

        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        const val EXTRA_TIMEOUT_MINUTES = "extra_timeout_minutes"
        const val EXTRA_QUIET_ENABLED = "extra_quiet_enabled"
        const val EXTRA_QUIET_START_H = "extra_quiet_start_h"
        const val EXTRA_QUIET_START_M = "extra_quiet_start_m"
        const val EXTRA_QUIET_END_H = "extra_quiet_end_h"
        const val EXTRA_QUIET_END_M = "extra_quiet_end_m"

        private const val NOTIFICATION_CHANNEL_ID = "qiimo_quiz_session_service"
        private const val NOTIFICATION_ID = 2002
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var overlayManager: OverlayQuestionManager
    private var config = WorkSessionConfig()
    private var scheduleRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = PharmacyRepository(db.itemDao())
        overlayManager = OverlayQuestionManager(applicationContext, repo)

        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QiimoQuiz:SessionWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // safe 10-min safety acquire

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START -> {
                extractConfig(intent)
                startForegroundNotification()
                WorkSessionManager.setSessionActive(true)
                handleNextScheduledStep(isInitialStart = true)
            }
            ACTION_STOP -> {
                stopWorkSession()
            }
        }

        return START_STICKY
    }

    private fun extractConfig(intent: Intent) {
        val interval = intent.getIntExtra(EXTRA_INTERVAL_MINUTES, 5)
        val timeout = intent.getIntExtra(EXTRA_TIMEOUT_MINUTES, 5)
        val quietEnabled = intent.getBooleanExtra(EXTRA_QUIET_ENABLED, true)
        val qStartH = intent.getIntExtra(EXTRA_QUIET_START_H, 22)
        val qStartM = intent.getIntExtra(EXTRA_QUIET_START_M, 0)
        val qEndH = intent.getIntExtra(EXTRA_QUIET_END_H, 7)
        val qEndM = intent.getIntExtra(EXTRA_QUIET_END_M, 0)

        config = WorkSessionConfig(
            intervalMinutes = interval,
            timeoutMinutes = timeout,
            quietHoursEnabled = quietEnabled,
            quietStartHour = qStartH,
            quietStartMinute = qStartM,
            quietEndHour = qEndH,
            quietEndMinute = qEndM
        )
        WorkSessionManager.updateConfig(config)
    }

    private fun handleNextScheduledStep(isInitialStart: Boolean = false) {
        cancelScheduled()

        val now = Calendar.getInstance()
        val isQuiet = config.isInsideQuietHours(now)

        if (isQuiet) {
            val waitMillis = config.millisUntilQuietHoursEnd(now)
            val quietNotice = "Pop-up wuxuu soo bixi doonaa ${config.formatQuietEnd()}"

            WorkSessionManager.setWaitingQuietHours(true, quietNotice)
            updateNotificationContent("🌙 Heegan: $quietNotice")

            // Schedule when quiet hours end
            scheduleRunnable = Runnable {
                handleNextScheduledStep(isInitialStart = false)
            }
            scheduleRunnable?.let { handler.postDelayed(it, waitMillis) }
        } else {
            WorkSessionManager.setWaitingQuietHours(false, null)
            updateNotificationContent("Shaqadu way socotaa • ${config.intervalMinutes} daqiiqo kasta")

            if (isInitialStart) {
                // First popup shows after the interval (or immediate popup)
                scheduleNextPopup(config.intervalMinutes * 60 * 1000L)
            } else {
                // Show popup now!
                overlayManager.showQuestionPopup(config.timeoutMinutes) {
                    // Once answered or timed out, schedule next one
                    scheduleNextPopup(config.intervalMinutes * 60 * 1000L)
                }
            }
        }
    }

    private fun scheduleNextPopup(delayMillis: Long) {
        cancelScheduled()
        scheduleRunnable = Runnable {
            handleNextScheduledStep(isInitialStart = false)
        }
        scheduleRunnable?.let { handler.postDelayed(it, delayMillis) }
    }

    private fun cancelScheduled() {
        scheduleRunnable?.let {
            handler.removeCallbacks(it)
            scheduleRunnable = null
        }
    }

    private fun stopWorkSession() {
        cancelScheduled()
        overlayManager.dismissOverlay(recordAsSkipped = false)
        WorkSessionManager.setSessionActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Qiimo Quiz Shaqada Socota",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Xaaladda shaqada socota ee tababarka qiimaha"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, WorkSessionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Qiimo Quiz — Shaqadu Way Socotaa")
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dhamee",
                stopPendingIntent
            )
            .build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification("Shaqadu way bilaabatay...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotificationContent(contentText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onDestroy() {
        cancelScheduled()
        overlayManager.dismissOverlay(recordAsSkipped = false)
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
