package com.example.data.repository

import com.example.data.db.ItemDao
import com.example.data.model.Attempt
import com.example.data.model.ExamResult
import com.example.data.model.Item
import com.example.data.model.ItemStats
import com.example.data.model.ItemWithStats
import com.example.data.model.SkippedItemEntry
import com.example.data.model.SpacedRepetitionConfig
import com.example.data.model.SpeedCategory
import com.example.util.DistractorGenerator
import com.example.util.SampleData
import kotlinx.coroutines.flow.Flow
import kotlin.math.max
import kotlin.random.Random

data class QuizQuestion(
    val item: Item,
    val stats: ItemStats?,
    val isLevel2Typed: Boolean, // true if correctStreak >= 3
    val options: List<Double>,  // 4 options if Level 1 Choice
    val questionIndex: Int,
    val questionType: String = ItemStats.QUESTION_TYPE_SELL_PRICE // SELL_PRICE | COST
) {
    val questionText: String
        get() = if (questionType == ItemStats.QUESTION_TYPE_COST) {
            "Kharashka ${item.name} (cost) waa immisa?"
        } else {
            "Qiimaha ${item.name} waa immisa marka macaamiil laga iibinayo?"
        }

    val targetPrice: Double
        get() = if (questionType == ItemStats.QUESTION_TYPE_COST) {
            item.cost
        } else {
            item.price
        }
}

class PharmacyRepository(
    val itemDao: ItemDao,
    private val getUserId: () -> String = { "" }
) {

    val currentUserId: String
        get() = getUserId()

    fun getAllItemsWithStats(userId: String = currentUserId): Flow<List<ItemWithStats>> {
        return itemDao.getAllItemsWithStats(userId)
    }

    fun getTotalAttempts(userId: String = currentUserId): Flow<Int> {
        return itemDao.getTotalAttemptsCount(userId)
    }

    fun getCorrectAttempts(userId: String = currentUserId): Flow<Int> {
        return itemDao.getCorrectAttemptsCount(userId)
    }

    fun getSkippedItems(userId: String = currentUserId): Flow<List<SkippedItemEntry>> {
        return itemDao.getSkippedItems(userId)
    }

    // Re-ask queue: maps (targetQuestionIndex -> (itemId, questionType))
    private val reAskQueue = mutableListOf<Triple<Int, String, String>>()
    private var totalQuestionsServed = 0

    fun decideQuestionType(item: Item, questionMode: String): String {
        val hasCost = item.cost > 0.0
        return when (questionMode) {
            com.example.data.model.WorkSessionConfig.QUESTION_MODE_COST -> {
                if (hasCost) ItemStats.QUESTION_TYPE_COST else ItemStats.QUESTION_TYPE_SELL_PRICE
            }
            com.example.data.model.WorkSessionConfig.QUESTION_MODE_BOTH -> {
                if (hasCost && Random.nextDouble() < 0.3) ItemStats.QUESTION_TYPE_COST else ItemStats.QUESTION_TYPE_SELL_PRICE
            }
            else -> ItemStats.QUESTION_TYPE_SELL_PRICE
        }
    }

    fun searchItems(query: String, userId: String = currentUserId): Flow<List<ItemWithStats>> {
        return itemDao.searchItemsWithStats(userId, query)
    }

    suspend fun getItemWithStats(id: String, userId: String = currentUserId): ItemWithStats? {
        return itemDao.getItemWithStatsById(userId, id)
    }

    suspend fun insertItem(item: Item, userId: String = currentUserId): String {
        val itemWithUser = item.copy(
            userId = userId,
            createdAt = if (item.createdAt == 0L) System.currentTimeMillis() else item.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        itemDao.insertItem(itemWithUser)
        itemDao.upsertStats(ItemStats(itemId = itemWithUser.id, questionType = ItemStats.QUESTION_TYPE_SELL_PRICE, userId = userId, updatedAt = System.currentTimeMillis()))
        if (itemWithUser.cost > 0.0) {
            itemDao.upsertStats(ItemStats(itemId = itemWithUser.id, questionType = ItemStats.QUESTION_TYPE_COST, userId = userId, updatedAt = System.currentTimeMillis()))
        }
        return itemWithUser.id
    }

    suspend fun insertItems(items: List<Item>, userId: String = currentUserId): List<String> {
        val now = System.currentTimeMillis()
        val itemsWithUser = items.map {
            it.copy(
                userId = userId,
                createdAt = if (it.createdAt == 0L) now else it.createdAt,
                updatedAt = now
            )
        }
        itemDao.insertItems(itemsWithUser)
        val statsList = mutableListOf<ItemStats>()
        itemsWithUser.forEach { item ->
            statsList.add(ItemStats(itemId = item.id, questionType = ItemStats.QUESTION_TYPE_SELL_PRICE, userId = userId, updatedAt = now))
            if (item.cost > 0.0) {
                statsList.add(ItemStats(itemId = item.id, questionType = ItemStats.QUESTION_TYPE_COST, userId = userId, updatedAt = now))
            }
        }
        itemDao.upsertItemStatsList(statsList)
        return itemsWithUser.map { it.id }
    }

    suspend fun updateItem(item: Item, userId: String = currentUserId) {
        val updated = item.copy(
            userId = userId,
            updatedAt = System.currentTimeMillis()
        )
        itemDao.updateItem(updated)
    }

    suspend fun deleteItem(item: Item, userId: String = currentUserId) {
        itemDao.deleteItem(item)
        itemDao.deleteStatsForItem(userId, item.id)
        itemDao.deleteAttemptsForItem(userId, item.id)
    }

    suspend fun clearCurrentUserData(userId: String = currentUserId) {
        itemDao.clearUserData(userId)
        reAskQueue.clear()
        totalQuestionsServed = 0
    }

    suspend fun clearAllData() {
        itemDao.clearAllData()
        reAskQueue.clear()
        totalQuestionsServed = 0
    }

    suspend fun seedSampleMedicines(userId: String = currentUserId) {
        val sampleWithUser = SampleData.sampleMedicines.map { it.copy(userId = userId) }
        insertItems(sampleWithUser, userId)
    }

    suspend fun recordAttempt(
        itemId: String,
        questionType: String = ItemStats.QUESTION_TYPE_SELL_PRICE,
        type: String, // CHOICE | TYPED | EXAM | WORK_SESSION
        result: String, // CORRECT | WRONG | SKIPPED
        answerGiven: Double?,
        responseTimeMs: Long,
        config: SpacedRepetitionConfig = SpacedRepetitionConfig(),
        userId: String = currentUserId
    ): Pair<ItemStats, SpeedCategory> {
        val now = System.currentTimeMillis()
        val attempt = Attempt(
            userId = userId,
            itemId = itemId,
            questionType = questionType,
            timestamp = now,
            type = type,
            result = result,
            answerGiven = answerGiven,
            responseTimeMs = responseTimeMs,
            updatedAt = now
        )
        itemDao.insertAttempt(attempt)

        val currentStats = itemDao.getStatsForItem(userId, itemId, questionType)
            ?: ItemStats(itemId = itemId, questionType = questionType, userId = userId)
        val speedCategory = config.categorize(responseTimeMs)

        val updatedStats = when (result) {
            "CORRECT" -> {
                val newStreak = currentStats.correctStreak + 1
                val newStatus = if (newStreak >= 5) ItemStats.STATUS_LEARNED else ItemStats.STATUS_LEARNING

                val newWeakness: Double
                val intervalMs: Long

                when (speedCategory) {
                    SpeedCategory.SLOW -> {
                        newWeakness = max(currentStats.weaknessScore + 0.2, 1.25)
                        intervalMs = config.calculateNextDueIntervalMs(newStreak, speedCategory)
                    }
                    SpeedCategory.FAST -> {
                        newWeakness = max(0.05, currentStats.weaknessScore * 0.45)
                        intervalMs = config.calculateNextDueIntervalMs(newStreak, speedCategory)
                    }
                    else -> {
                        newWeakness = max(0.1, currentStats.weaknessScore * 0.65)
                        intervalMs = config.calculateNextDueIntervalMs(newStreak, speedCategory)
                    }
                }

                val nextDue = now + intervalMs

                currentStats.copy(
                    userId = userId,
                    correctStreak = newStreak,
                    status = newStatus,
                    weaknessScore = newWeakness,
                    nextDueAt = nextDue,
                    updatedAt = now
                )
            }
            "WRONG" -> {
                val newWrongCount = currentStats.wrongCount + 1
                val newWeakness = currentStats.weaknessScore + 1.8
                val delayQuestions = Random.nextInt(3, 6)
                val targetIndex = totalQuestionsServed + delayQuestions
                reAskQueue.add(Triple(targetIndex, itemId, questionType))

                currentStats.copy(
                    userId = userId,
                    correctStreak = 0,
                    wrongCount = newWrongCount,
                    status = ItemStats.STATUS_LEARNING,
                    weaknessScore = newWeakness,
                    nextDueAt = now,
                    updatedAt = now
                )
            }
            else -> { // SKIPPED
                val newSkipped = currentStats.skippedCount + 1
                val newWeakness = currentStats.weaknessScore + 0.8
                currentStats.copy(
                    userId = userId,
                    skippedCount = newSkipped,
                    weaknessScore = newWeakness,
                    nextDueAt = now,
                    updatedAt = now
                )
            }
        }

        itemDao.upsertStats(updatedStats)
        return Pair(updatedStats, speedCategory)
    }

    suspend fun getNextQuestion(
        preferredItemId: String? = null,
        questionMode: String = com.example.data.model.WorkSessionConfig.QUESTION_MODE_BOTH,
        userId: String = currentUserId
    ): QuizQuestion? {
        val allItems = itemDao.getAllItemsList(userId)
        if (allItems.isEmpty()) return null

        totalQuestionsServed++

        var chosenItem: Item? = null
        var chosenType: String? = null

        // 1. Explicit preferred item
        if (preferredItemId != null) {
            chosenItem = allItems.find { it.id == preferredItemId }
            if (chosenItem != null) {
                chosenType = decideQuestionType(chosenItem, questionMode)
            }
        }

        // 2. Check re-ask queue
        if (chosenItem == null && reAskQueue.isNotEmpty()) {
            val dueIndex = reAskQueue.indexOfFirst { it.first <= totalQuestionsServed }
            if (dueIndex != -1) {
                val reAskTriple = reAskQueue.removeAt(dueIndex)
                chosenItem = allItems.find { it.id == reAskTriple.second }
                if (chosenItem != null) {
                    chosenType = reAskTriple.third
                }
            }
        }

        // 3. Weighted random selection based on weaknessScore
        if (chosenItem == null) {
            val recentlySkippedIds = itemDao.getRecentlySkippedItemIds(userId).toSet()
            val now = System.currentTimeMillis()

            val weightedItems = allItems.map { item ->
                val qType = decideQuestionType(item, questionMode)
                val stats = itemDao.getStatsForItem(userId, item.id, qType)
                val isSkippedRecently = recentlySkippedIds.contains(item.id)
                val isDue = stats != null && stats.nextDueAt > 0L && stats.nextDueAt <= now
                val baseWeight = when {
                    stats == null || stats.status == ItemStats.STATUS_NEW -> 3.5
                    stats.status == ItemStats.STATUS_LEARNED -> {
                        if (isDue) 0.8 else 0.08
                    }
                    else -> {
                        val dueBoost = if (isDue) 2.0 else 1.0
                        max(1.0, stats.weaknessScore * 1.5) * dueBoost
                    }
                }
                val finalWeight = if (isSkippedRecently) baseWeight * 3.5 else baseWeight
                Triple(item, qType, finalWeight)
            }

            val totalWeight = weightedItems.sumOf { it.third }
            var randomValue = Random.nextDouble(totalWeight)
            for (triple in weightedItems) {
                randomValue -= triple.third
                if (randomValue <= 0.0) {
                    chosenItem = triple.first
                    chosenType = triple.second
                    break
                }
            }
            if (chosenItem == null) {
                chosenItem = allItems.random()
                chosenType = decideQuestionType(chosenItem, questionMode)
            }
        }

        val qType = chosenType ?: decideQuestionType(chosenItem, questionMode)
        val stats = itemDao.getStatsForItem(userId, chosenItem.id, qType)
        val isLevel2 = (stats?.correctStreak ?: 0) >= 3

        val targetPrice = if (qType == ItemStats.QUESTION_TYPE_COST) chosenItem.cost else chosenItem.price
        val otherPrices = if (qType == ItemStats.QUESTION_TYPE_COST) {
            allItems.filter { it.id != chosenItem.id && it.cost > 0.0 }.map { it.cost }
        } else {
            allItems.filter { it.id != chosenItem.id }.map { it.price }
        }

        val options = if (!isLevel2) {
            DistractorGenerator.buildOptions(targetPrice, otherPrices)
        } else {
            emptyList()
        }

        return QuizQuestion(
            item = chosenItem,
            stats = stats,
            isLevel2Typed = isLevel2,
            options = options,
            questionIndex = totalQuestionsServed,
            questionType = qType
        )
    }

    suspend fun generateExamQuestions(
        requestedCount: Int,
        questionMode: String = com.example.data.model.WorkSessionConfig.QUESTION_MODE_BOTH,
        userId: String = currentUserId
    ): List<QuizQuestion> {
        val allItems = itemDao.getAllItemsList(userId)
        if (allItems.isEmpty()) return emptyList()

        val pool = allItems.toMutableList()
        val chosenItems = mutableListOf<Item>()
        val targetCount = requestedCount.coerceAtLeast(1)

        while (chosenItems.size < targetCount && pool.isNotEmpty()) {
            val weightedPool = pool.map { item ->
                val qType = decideQuestionType(item, questionMode)
                val s = itemDao.getStatsForItem(userId, item.id, qType)
                val base = when {
                    s == null || s.status == ItemStats.STATUS_NEW -> 3.0
                    s.status == ItemStats.STATUS_LEARNED -> 0.2
                    else -> max(1.0, s.weaknessScore * 1.6 + (s.wrongCount * 1.8) + (s.skippedCount * 1.2))
                }
                Pair(item, base)
            }

            val totalWeight = weightedPool.sumOf { it.second }
            var r = Random.nextDouble(totalWeight)
            var selected: Item = pool.first()
            for (p in weightedPool) {
                r -= p.second
                if (r <= 0.0) {
                    selected = p.first
                    break
                }
            }

            chosenItems.add(selected)
            pool.remove(selected)
        }

        while (chosenItems.size < targetCount && allItems.isNotEmpty()) {
            chosenItems.add(allItems.random())
        }

        return chosenItems.mapIndexed { index, item ->
            val qType = decideQuestionType(item, questionMode)
            val stats = itemDao.getStatsForItem(userId, item.id, qType)
            val isLevel2 = (stats?.correctStreak ?: 0) >= 3
            val targetPrice = if (qType == ItemStats.QUESTION_TYPE_COST) item.cost else item.price
            val otherPrices = if (qType == ItemStats.QUESTION_TYPE_COST) {
                allItems.filter { it.id != item.id && it.cost > 0.0 }.map { it.cost }
            } else {
                allItems.filter { it.id != item.id }.map { it.price }
            }

            val options = if (!isLevel2) {
                DistractorGenerator.buildOptions(targetPrice, otherPrices)
            } else {
                emptyList()
            }

            QuizQuestion(
                item = item,
                stats = stats,
                isLevel2Typed = isLevel2,
                options = options,
                questionIndex = index + 1,
                questionType = qType
            )
        }
    }

    suspend fun saveExamResult(result: ExamResult, userId: String = currentUserId): String {
        val itemWithUser = result.copy(userId = userId, updatedAt = System.currentTimeMillis())
        itemDao.insertExamResult(itemWithUser)
        return itemWithUser.id
    }

    fun getAllExamResults(userId: String = currentUserId): Flow<List<ExamResult>> {
        return itemDao.getAllExamResults(userId)
    }

    fun getAllAttempts(userId: String = currentUserId): Flow<List<Attempt>> {
        return itemDao.getAllAttempts(userId)
    }

    fun resetQuizSession() {
        reAskQueue.clear()
        totalQuestionsServed = 0
    }

    suspend fun recordItemSkipped(
        itemId: String,
        questionType: String = ItemStats.QUESTION_TYPE_SELL_PRICE,
        responseTimeMs: Long = 0L,
        userId: String = currentUserId
    ): ItemStats {
        return recordAttempt(
            itemId = itemId,
            questionType = questionType,
            type = "WORK_SESSION",
            result = "SKIPPED",
            answerGiven = null,
            responseTimeMs = responseTimeMs,
            userId = userId
        ).first
    }

    suspend fun deleteAttempt(attemptId: String, userId: String = currentUserId) {
        itemDao.deleteAttempt(userId, attemptId)
    }
}
