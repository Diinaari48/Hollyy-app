package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ItemWithStats(
    @Embedded
    val item: Item,

    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val stats: ItemStats? = null
) {
    val effectiveStreak: Int
        get() = stats?.correctStreak ?: 0

    val effectiveStatus: String
        get() = stats?.status ?: ItemStats.STATUS_NEW

    val effectiveWeakness: Double
        get() = stats?.weaknessScore ?: 1.0
}
