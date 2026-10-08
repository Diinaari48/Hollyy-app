package com.example.data.model

import java.util.Calendar
import java.util.Locale

data class WorkSessionConfig(
    val intervalMinutes: Int = 5,
    val timeoutMinutes: Int = 5,
    val quietHoursEnabled: Boolean = true,
    val quietStartHour: Int = 22,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 7,
    val quietEndMinute: Int = 0,
    val reminderHour: Int = 18,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val questionMode: String = QUESTION_MODE_BOTH // SELL_PRICE | COST | BOTH
) {
    companion object {
        const val QUESTION_MODE_SELL_PRICE = "SELL_PRICE"
        const val QUESTION_MODE_COST = "COST"
        const val QUESTION_MODE_BOTH = "BOTH"
    }
    fun formatReminderTime(): String {
        return String.format(Locale.US, "%02d:%02d", reminderHour, reminderMinute)
    }

    fun formatQuietEnd(): String {
        return String.format(Locale.US, "%02d:%02d", quietEndHour, quietEndMinute)
    }

    fun formatQuietStart(): String {
        return String.format(Locale.US, "%02d:%02d", quietStartHour, quietStartMinute)
    }

    fun formatQuietRange(): String {
        return "${formatQuietStart()} – ${formatQuietEnd()}"
    }

    /**
     * Checks if given calendar time is currently inside quiet hours.
     * Handles overnight span (e.g. 22:00 to 07:00).
     */
    fun isInsideQuietHours(now: Calendar = Calendar.getInstance()): Boolean {
        if (!quietHoursEnabled) return false

        val currentMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinuteOfDay = quietStartHour * 60 + quietStartMinute
        val endMinuteOfDay = quietEndHour * 60 + quietEndMinute

        return if (startMinuteOfDay < endMinuteOfDay) {
            // Same day span (e.g. 13:00 to 16:00)
            currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else {
            // Overnight span (e.g. 22:00 to 07:00)
            currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < endMinuteOfDay
        }
    }

    /**
     * Calculates milliseconds until quiet hours end.
     */
    fun millisUntilQuietHoursEnd(now: Calendar = Calendar.getInstance()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, quietEndHour)
            set(Calendar.MINUTE, quietEndMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
