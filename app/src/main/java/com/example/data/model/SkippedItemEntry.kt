package com.example.data.model

data class SkippedItemEntry(
    val attemptId: String,
    val itemId: String,
    val name: String,
    val systemName: String?,
    val price: Double,
    val wholesalePrice: Double,
    val cost: Double,
    val timestamp: Long
)

