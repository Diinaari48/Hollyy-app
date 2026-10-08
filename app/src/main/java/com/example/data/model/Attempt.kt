package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "attempts",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["userId"]),
        Index(value = ["questionType"])
    ]
)
data class Attempt(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val itemId: String,
    val questionType: String = QUESTION_TYPE_SELL_PRICE, // SELL_PRICE | COST
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // CHOICE | TYPED | EXAM | WORK_SESSION
    val result: String, // CORRECT | WRONG | SKIPPED
    val answerGiven: Double? = null,
    val responseTimeMs: Long = 0L,
    val updatedAt: Long = timestamp
) {
    companion object {
        const val QUESTION_TYPE_SELL_PRICE = "SELL_PRICE"
        const val QUESTION_TYPE_COST = "COST"
    }
}

