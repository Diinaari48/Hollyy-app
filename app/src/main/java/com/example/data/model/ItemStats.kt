package com.example.data.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "item_stats",
    primaryKeys = ["itemId", "questionType"],
    indices = [Index(value = ["userId"])]
)
data class ItemStats(
    val itemId: String,
    val questionType: String = QUESTION_TYPE_SELL_PRICE, // SELL_PRICE | COST
    val userId: String = "",
    val correctStreak: Int = 0,
    val wrongCount: Int = 0,
    val skippedCount: Int = 0,
    val weaknessScore: Double = 1.0,
    val nextDueAt: Long = 0L,
    val status: String = STATUS_NEW, // NEW | LEARNING | LEARNED
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val QUESTION_TYPE_SELL_PRICE = "SELL_PRICE"
        const val QUESTION_TYPE_COST = "COST"
        const val STATUS_NEW = "NEW"
        const val STATUS_LEARNING = "LEARNING"
        const val STATUS_LEARNED = "LEARNED"
    }
}

