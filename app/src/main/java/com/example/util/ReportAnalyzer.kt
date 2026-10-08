package com.example.util

import com.example.data.model.Attempt
import com.example.data.model.ChartBarData
import com.example.data.model.Item
import com.example.data.model.ItemStats
import com.example.data.model.ItemWithStats
import com.example.data.model.TodayReport
import com.example.data.model.WeakItemReport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReportAnalyzer {

    fun calculateTodayReport(attempts: List<Attempt>): TodayReport {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayAttempts = attempts.filter { it.timestamp >= midnight }
        val correct = todayAttempts.count { it.result == "CORRECT" }
        val wrong = todayAttempts.count { it.result == "WRONG" }
        val skipped = todayAttempts.count { it.result == "SKIPPED" }
        val total = correct + wrong + skipped
        val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

        val summary = "Maanta $correct sax, $wrong khalad, $skipped la dhaafay"

        return TodayReport(
            correct = correct,
            wrong = wrong,
            skipped = skipped,
            total = total,
            accuracyPercent = accuracy,
            summaryText = summary
        )
    }

    fun calculateTop10WeakestItems(
        items: List<ItemWithStats>,
        attempts: List<Attempt>,
        slowThresholdMs: Long = 8000L
    ): List<WeakItemReport> {
        val attemptsByItem = attempts.groupBy { it.itemId }

        val reports = items.map { itemWithStats ->
            val itemAttempts = attemptsByItem[itemWithStats.item.id] ?: emptyList()
            val wrongCount = itemAttempts.count { it.result == "WRONG" }
            val skippedCount = itemAttempts.count { it.result == "SKIPPED" }
            val slowCount = itemAttempts.count { it.result == "CORRECT" && it.responseTimeMs > slowThresholdMs }

            // User requirement: Top 10 weakest items (wrong + skipped + slow)
            val score = (wrongCount * 2.5) + (skippedCount * 1.8) + (slowCount * 1.2) + ((itemWithStats.stats?.weaknessScore ?: 0.0) * 0.5)

            WeakItemReport(
                item = itemWithStats.item,
                stats = itemWithStats.stats,
                wrongCount = wrongCount,
                skippedCount = skippedCount,
                slowCount = slowCount,
                weaknessScore = score
            )
        }

        return reports
            .filter { it.wrongCount > 0 || it.skippedCount > 0 || it.slowCount > 0 || it.weaknessScore > 1.0 }
            .sortedByDescending { it.weaknessScore }
            .take(10)
    }

    fun calculateTop10WeakestItemsForType(
        items: List<ItemWithStats>,
        attempts: List<Attempt>,
        questionType: String,
        slowThresholdMs: Long = 8000L
    ): List<WeakItemReport> {
        val filteredAttempts = attempts.filter { it.questionType == questionType }
        val attemptsByItem = filteredAttempts.groupBy { it.itemId }

        val eligibleItems = if (questionType == ItemStats.QUESTION_TYPE_COST) {
            items.filter { it.item.cost > 0.0 }
        } else {
            items
        }

        val reports = eligibleItems.map { itemWithStats ->
            val itemAttempts = attemptsByItem[itemWithStats.item.id] ?: emptyList()
            val wrongCount = itemAttempts.count { it.result == "WRONG" }
            val skippedCount = itemAttempts.count { it.result == "SKIPPED" }
            val slowCount = itemAttempts.count { it.result == "CORRECT" && it.responseTimeMs > slowThresholdMs }

            val score = (wrongCount * 2.5) + (skippedCount * 1.8) + (slowCount * 1.2)

            WeakItemReport(
                item = itemWithStats.item,
                stats = itemWithStats.stats,
                wrongCount = wrongCount,
                skippedCount = skippedCount,
                slowCount = slowCount,
                weaknessScore = score
            )
        }

        return reports
            .filter { it.wrongCount > 0 || it.skippedCount > 0 || it.slowCount > 0 }
            .sortedByDescending { it.weaknessScore }
            .take(10)
    }

    fun calculateAccuracyForType(attempts: List<Attempt>, questionType: String): Pair<Int, Int> {
        val filtered = attempts.filter { it.questionType == questionType }
        val correct = filtered.count { it.result == "CORRECT" }
        val total = filtered.size
        val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0
        return Pair(accuracy, total)
    }

    fun calculateConsecutiveDaysStreak(attempts: List<Attempt>): Int {
        if (attempts.isEmpty()) return 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val activeDays = attempts.map { dateFormat.format(Date(it.timestamp)) }.toSet()

        val cal = Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(cal.time)

        // Check if user answered today, or if not, check if streak can continue from yesterday
        var currentCheckCal = Calendar.getInstance()
        var streak = 0

        val hasToday = activeDays.contains(todayStr)
        val hasYesterday = activeDays.contains(yesterdayStr)

        if (!hasToday && !hasYesterday) {
            return 0
        }

        if (hasToday) {
            streak++
            currentCheckCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // User hasn't answered yet today, check consecutive streak leading up to yesterday
            currentCheckCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = dateFormat.format(currentCheckCal.time)
            if (activeDays.contains(dateStr)) {
                streak++
                currentCheckCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }

    fun buildDailyChartData(attempts: List<Attempt>): List<ChartBarData> {
        val result = mutableListOf<ChartBarData>()
        val cal = Calendar.getInstance()

        // Somali short day names
        val somaliDays = mapOf(
            Calendar.MONDAY to "Isn",
            Calendar.TUESDAY to "Tla",
            Calendar.WEDNESDAY to "Arb",
            Calendar.THURSDAY to "Khm",
            Calendar.FRIDAY to "Jmc",
            Calendar.SATURDAY to "Sbt",
            Calendar.SUNDAY to "Axd"
        )

        val dayFormat = SimpleDateFormat("d/M", Locale.US)

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = dayCal.timeInMillis
            val endMs = startMs + (24 * 60 * 60 * 1000L) - 1

            val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
            val dayName = somaliDays[dayOfWeek] ?: "Maa"
            val dateLabel = dayFormat.format(dayCal.time)

            val dayAttempts = attempts.filter { it.timestamp in startMs..endMs }
            val correct = dayAttempts.count { it.result == "CORRECT" }
            val wrong = dayAttempts.count { it.result == "WRONG" }
            val skipped = dayAttempts.count { it.result == "SKIPPED" }
            val total = correct + wrong + skipped
            val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

            result.add(
                ChartBarData(
                    label = dayName,
                    subLabel = dateLabel,
                    correctCount = correct,
                    wrongCount = wrong,
                    skippedCount = skipped,
                    totalCount = total,
                    accuracyPercent = accuracy
                )
            )
        }

        return result
    }

    fun buildWeeklyChartData(attempts: List<Attempt>): List<ChartBarData> {
        val result = mutableListOf<ChartBarData>()

        for (w in 3 downTo 0) {
            val startCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(w * 7 + 6))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(w * 7))
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val weekLabel = when (w) {
                0 -> "Todd.kan"
                1 -> "Todd.hore"
                else -> "-${w} Todd."
            }

            val weekAttempts = attempts.filter { it.timestamp in startCal.timeInMillis..endCal.timeInMillis }
            val correct = weekAttempts.count { it.result == "CORRECT" }
            val wrong = weekAttempts.count { it.result == "WRONG" }
            val skipped = weekAttempts.count { it.result == "SKIPPED" }
            val total = correct + wrong + skipped
            val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

            result.add(
                ChartBarData(
                    label = weekLabel,
                    subLabel = "W$w",
                    correctCount = correct,
                    wrongCount = wrong,
                    skippedCount = skipped,
                    totalCount = total,
                    accuracyPercent = accuracy
                )
            )
        }

        return result
    }

    fun buildMonthlyChartData(attempts: List<Attempt>): List<ChartBarData> {
        val result = mutableListOf<ChartBarData>()
        val somaliMonths = arrayOf("Jan", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ogs", "Seb", "Okt", "Noof", "Dis")

        for (m in 5 downTo 0) {
            val monthCal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -m)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = monthCal.timeInMillis

            val maxDay = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val endCal = (monthCal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, maxDay)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endMs = endCal.timeInMillis

            val monthIndex = monthCal.get(Calendar.MONTH)
            val monthName = somaliMonths.getOrElse(monthIndex) { "Bil" }

            val monthAttempts = attempts.filter { it.timestamp in startMs..endMs }
            val correct = monthAttempts.count { it.result == "CORRECT" }
            val wrong = monthAttempts.count { it.result == "WRONG" }
            val skipped = monthAttempts.count { it.result == "SKIPPED" }
            val total = correct + wrong + skipped
            val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

            result.add(
                ChartBarData(
                    label = monthName,
                    subLabel = "'${monthCal.get(Calendar.YEAR).toString().takeLast(2)}",
                    correctCount = correct,
                    wrongCount = wrong,
                    skippedCount = skipped,
                    totalCount = total,
                    accuracyPercent = accuracy
                )
            )
        }

        return result
    }
}
