package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.Attempt
import com.example.data.model.ExamResult
import com.example.data.model.Item
import com.example.data.model.ItemStats
import com.example.data.model.ItemWithStats
import com.example.data.model.SkippedItemEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY name ASC")
    fun getAllItems(userId: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllItemsList(userId: String): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE userId = :userId")
    suspend fun getItemsCountForUser(userId: String): Int

    @Transaction
    @Query("SELECT * FROM items WHERE userId = :userId ORDER BY name ASC")
    fun getAllItemsWithStats(userId: String): Flow<List<ItemWithStats>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    suspend fun getItemWithStatsById(userId: String, id: String): ItemWithStats?

    @Query("SELECT * FROM items WHERE id = :id AND userId = :userId")
    suspend fun getItemById(userId: String, id: String): Item?

    @Transaction
    @Query("""
        SELECT * FROM items 
        WHERE userId = :userId 
          AND (name LIKE '%' || :query || '%' OR (systemName IS NOT NULL AND systemName LIKE '%' || :query || '%'))
        ORDER BY name ASC
    """)
    fun searchItemsWithStats(userId: String, query: String): Flow<List<ItemWithStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<Item>)

    @Query("DELETE FROM items WHERE userId = :userId AND id NOT IN (:keepIds)")
    suspend fun deleteItemsNotInList(userId: String, keepIds: List<String>)

    @Transaction
    suspend fun syncUserItems(userId: String, items: List<Item>) {
        if (items.isEmpty()) return
        insertItems(items)
        val keepIds = items.map { it.id }
        deleteItemsNotInList(userId, keepIds)
    }

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("DELETE FROM items WHERE id = :id AND userId = :userId")
    suspend fun deleteItemById(userId: String, id: String)

    @Query("DELETE FROM attempts WHERE itemId = :itemId AND userId = :userId")
    suspend fun deleteAttemptsForItem(userId: String, itemId: String)

    @Query("DELETE FROM item_stats WHERE itemId = :itemId AND userId = :userId")
    suspend fun deleteStatsForItem(userId: String, itemId: String)

    @Query("DELETE FROM items WHERE userId = :userId")
    suspend fun deleteAllItemsForUser(userId: String)

    @Query("DELETE FROM attempts WHERE userId = :userId")
    suspend fun deleteAllAttemptsForUser(userId: String)

    @Query("DELETE FROM item_stats WHERE userId = :userId")
    suspend fun deleteAllStatsForUser(userId: String)

    @Query("DELETE FROM exam_results WHERE userId = :userId")
    suspend fun deleteAllExamResultsForUser(userId: String)

    @Transaction
    suspend fun clearUserData(userId: String) {
        deleteAllItemsForUser(userId)
        deleteAllAttemptsForUser(userId)
        deleteAllStatsForUser(userId)
        deleteAllExamResultsForUser(userId)
    }

    @Query("DELETE FROM items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM item_stats")
    suspend fun deleteAllStats()

    @Query("DELETE FROM exam_results")
    suspend fun deleteAllExamResults()

    @Transaction
    suspend fun clearAllData() {
        deleteAllItems()
        deleteAllAttempts()
        deleteAllStats()
        deleteAllExamResults()
    }

    // --- Attempts ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: Attempt)

    @Query("SELECT * FROM attempts WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllAttempts(userId: String): Flow<List<Attempt>>

    @Query("SELECT * FROM attempts WHERE itemId = :itemId AND userId = :userId ORDER BY timestamp DESC")
    fun getAttemptsForItem(userId: String, itemId: String): Flow<List<Attempt>>

    @Query("SELECT * FROM attempts WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAttempts(userId: String, limit: Int): Flow<List<Attempt>>

    @Query("SELECT COUNT(*) FROM attempts WHERE userId = :userId")
    fun getTotalAttemptsCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempts WHERE userId = :userId AND result = 'CORRECT'")
    fun getCorrectAttemptsCount(userId: String): Flow<Int>

    @Query("""
        SELECT a.id AS attemptId, a.itemId AS itemId, i.name AS name, i.systemName AS systemName, 
               i.price AS price, i.cost AS cost, a.timestamp AS timestamp
        FROM attempts a
        INNER JOIN items i ON a.itemId = i.id
        WHERE a.result = 'SKIPPED' AND a.userId = :userId
        ORDER BY a.timestamp DESC
    """)
    fun getSkippedItems(userId: String): Flow<List<SkippedItemEntry>>

    @Query("SELECT DISTINCT itemId FROM attempts WHERE result = 'SKIPPED' AND userId = :userId ORDER BY timestamp DESC LIMIT 20")
    suspend fun getRecentlySkippedItemIds(userId: String): List<String>

    @Query("DELETE FROM attempts WHERE id = :attemptId AND userId = :userId")
    suspend fun deleteAttempt(userId: String, attemptId: String)

    @Query("SELECT * FROM attempts WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getAllAttemptsList(userId: String): List<Attempt>

    @Query("SELECT * FROM attempts ORDER BY timestamp ASC")
    suspend fun getAllAttemptsOverallList(): List<Attempt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttempts(attempts: List<Attempt>)

    // --- ItemStats ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(stats: ItemStats)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItemStatsList(statsList: List<ItemStats>)

    @Query("SELECT * FROM item_stats WHERE itemId = :itemId AND questionType = :questionType AND userId = :userId")
    suspend fun getStatsForItem(userId: String, itemId: String, questionType: String = ItemStats.QUESTION_TYPE_SELL_PRICE): ItemStats?

    @Query("SELECT * FROM item_stats WHERE userId = :userId")
    fun getAllStats(userId: String): Flow<List<ItemStats>>

    @Query("SELECT * FROM item_stats WHERE userId = :userId")
    suspend fun getAllItemStatsList(userId: String): List<ItemStats>

    // --- Exam Results ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamResult(result: ExamResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExamResults(results: List<ExamResult>)

    @Query("SELECT * FROM exam_results WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllExamResults(userId: String): Flow<List<ExamResult>>

    @Query("SELECT * FROM exam_results WHERE userId = :userId ORDER BY timestamp ASC")
    suspend fun getAllExamResultsAsc(userId: String): List<ExamResult>
}
