package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "exam_results",
    indices = [Index(value = ["userId"])]
)
data class ExamResult(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val scorePercentage: Int,
    val averageTimeMs: Long,
    val updatedAt: Long = timestamp
)

data class ExamWrongItem(
    val item: Item,
    val answerGiven: String?,
    val correctPrice: Double,
    val responseTimeMs: Long
)

