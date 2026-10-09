package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "items",
    indices = [Index(value = ["userId"])]
)
data class Item(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val name: String,
    val systemName: String? = null,
    val cost: Double = 0.0,
    val price: Double, // "qiimaha" = official selling price
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
