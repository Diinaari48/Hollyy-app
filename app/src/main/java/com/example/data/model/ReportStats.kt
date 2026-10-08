package com.example.data.model

data class TodayReport(
    val correct: Int = 0,
    val wrong: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val accuracyPercent: Int = 0,
    val summaryText: String = "Maanta 0 sax, 0 khalad, 0 la dhaafay"
)

data class WeakItemReport(
    val item: Item,
    val stats: ItemStats?,
    val wrongCount: Int,
    val skippedCount: Int,
    val slowCount: Int,
    val weaknessScore: Double
)

data class ChartBarData(
    val label: String,
    val subLabel: String = "",
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val totalCount: Int,
    val accuracyPercent: Int
)

enum class ChartPeriod(val titleSo: String) {
    DAILY("Maalinle (7 Maalmood)"),
    WEEKLY("Toddobaadle (4 Toddobaad)"),
    MONTHLY("Bille (6 Bilood)")
}
