package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.db.AppDatabase
import com.example.data.model.WorkSessionConfig
import com.example.service.WorkSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailyReminderManager {

    private const val PREFS_NAME = "qiimo_reminder_prefs"
    private const val KEY_LAST_DHAMEE_DATE = "last_dhamee_date"
    private const val KEY_LAST_REMINDER_DATE = "last_reminder_date"
    private const val REQUEST_CODE_REMINDER = 5001

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    /**
     * Records that the user actively tapped "Dhamee" to finish their session today.
     * When this is recorded, no reminder notification will be sent today.
     */
    fun recordSessionEndedWithDhamee(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_DHAMEE_DATE, getTodayDateString()).apply()
    }

    fun wasSessionEndedWithDhameeToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DHAMEE_DATE, null)
        return lastDate == getTodayDateString()
    }

    private fun wasReminderAlreadySentToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_REMINDER_DATE, null)
        return lastDate == getTodayDateString()
    }

    private fun markReminderSentToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_REMINDER_DATE, getTodayDateString()).apply()
    }

    /**
     * Schedules the daily alarm at the configured reminder time (default 18:00).
     */
    fun scheduleDailyReminder(context: Context, config: WorkSessionConfig = WorkSessionManager.sessionConfig.value) {
        if (!config.reminderEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.reminderHour)
            set(Calendar.MINUTE, config.reminderMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target time has already passed today, schedule for tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, DailyReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_REMINDER, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // Alarm scheduling fallback
            alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
        }
    }

    /**
     * Checks all conditions and sends reminder if eligible:
     * 1. Reminder enabled
     * 2. Not already sent today
     * 3. Session was NOT ended with "Dhamee"
     * 4. Nothing was answered today (0 attempts today)
     * 5. NOT during quiet hours ("None during quiet hours")
     */
    fun checkAndSendReminderIfNeeded(context: Context) {
        val config = WorkSessionManager.sessionConfig.value
        if (!config.reminderEnabled) return

        if (wasReminderAlreadySentToday(context)) return

        if (wasSessionEndedWithDhameeToday(context)) return

        val now = Calendar.getInstance()
        if (config.isInsideQuietHours(now)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val attempts = db.itemDao().getAllAttemptsOverallList()

                val midnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val answeredToday = attempts.any { it.timestamp >= midnight }

                if (!answeredToday) {
                    NotificationHelper.showDailyReminderNotification(context)
                    markReminderSentToday(context)
                }

                // Reschedule for next day
                scheduleDailyReminder(context, config)
            } catch (_: Exception) {}
        }
    }
}
