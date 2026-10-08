package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.db.ItemDao
import com.example.data.model.Attempt
import com.example.data.model.ExamResult
import com.example.data.model.Item
import com.example.data.model.ItemStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SyncResult(
    val success: Boolean,
    val itemsSynced: Int = 0,
    val attemptsSynced: Int = 0,
    val statsSynced: Int = 0,
    val examsSynced: Int = 0,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun summaryMessage(): String {
        return if (success) {
            "Isku-xirka waa guuleystay! ($itemsSynced dawo, $attemptsSynced jawaabood, $statsSynced stats, $examsSynced imtixaan)"
        } else {
            "Isku-xirka waa fashilmay: ${errorMessage ?: "Qalad aan la garaneyn"}"
        }
    }
}

class SupabaseSyncManager(
    private val context: Context,
    private val client: SupabaseClient,
    private val itemDao: ItemDao
) {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncResult = MutableStateFlow<SyncResult?>(null)
    val lastSyncResult: StateFlow<SyncResult?> = _lastSyncResult.asStateFlow()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun performSync(): SyncResult = withContext(Dispatchers.IO) {
        if (_isSyncing.value) {
            return@withContext SyncResult(false, errorMessage = "Isku-dhafid kale ayaa socota...")
        }

        if (!isOnline()) {
            val res = SyncResult(false, errorMessage = "Khadka internet-ka ma shaqeynayo (Offline). Xogta maxalliga ah ayaa la isticmaalayaa.")
            _lastSyncResult.value = res
            return@withContext res
        }

        val user = client.currentUser.value
        if (user == null) {
            val res = SyncResult(false, errorMessage = "Fadlan marka hore gal akoonkaaga Supabase (Login) si aad u xog-wadaagto.")
            _lastSyncResult.value = res
            return@withContext res
        }

        _isSyncing.value = true
        try {
            // 1. Sync ITEMS
            val itemsCount = syncItems(user.id)

            // 2. Sync ATTEMPTS
            val attemptsCount = syncAttempts(user.id)

            // 3. Sync ITEM_STATS
            val statsCount = syncItemStats(user.id)

            // 4. Sync EXAM_RESULTS
            val examsCount = syncExamResults(user.id)

            val successResult = SyncResult(
                success = true,
                itemsSynced = itemsCount,
                attemptsSynced = attemptsCount,
                statsSynced = statsCount,
                examsSynced = examsCount
            )
            _lastSyncResult.value = successResult
            successResult
        } catch (e: Exception) {
            val failResult = SyncResult(false, errorMessage = e.message ?: "Qalad lama filaan ah")
            _lastSyncResult.value = failResult
            failResult
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun syncItems(userId: String): Int {
        val remoteRes = client.getRemoteTable("items")
        if (remoteRes.isFailure) throw remoteRes.exceptionOrNull() ?: Exception("Failed fetching items")
        val remoteArray = remoteRes.getOrNull() ?: JSONArray()

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArray.length()) {
            val obj = remoteArray.getJSONObject(i)
            remoteMap[obj.getString("id")] = obj
        }

        val localItems = itemDao.getAllItemsList(userId)
        val localMap = localItems.associateBy { it.id }

        val itemsToPushToRemote = JSONArray()
        var syncedCount = 0

        // Handle remote items: if remote is newer, update local Room
        for ((remoteId, remoteObj) in remoteMap) {
            val remoteUpdatedAt = remoteObj.optLong("updated_at", remoteObj.optLong("created_at", 0L))
            val localItem = localMap[remoteId]

            if (localItem == null || remoteUpdatedAt > localItem.updatedAt) {
                val itemToSave = ItemMapper.parseJsonToItem(remoteObj, userId)
                itemDao.insertItem(itemToSave)
                syncedCount++
            }
        }

        // Handle local items: if local is newer or not in remote, push to Supabase
        for (localItem in localItems) {
            val remoteObj = remoteMap[localItem.id]
            val remoteUpdatedAt = remoteObj?.optLong("updated_at", 0L) ?: -1L

            if (remoteObj == null || localItem.updatedAt > remoteUpdatedAt) {
                val obj = JSONObject().apply {
                    put("id", localItem.id)
                    put("user_id", userId)
                    put("name", localItem.name)
                    put("system_name", localItem.systemName)
                    put("cost", localItem.cost)
                    put("customer_price", localItem.wholesalePrice)
                    put("public_price", localItem.price)
                    put("created_at", localItem.createdAt)
                    put("updated_at", localItem.updatedAt)
                }
                itemsToPushToRemote.put(obj)
                syncedCount++
            }
        }

        if (itemsToPushToRemote.length() > 0) {
            val pushRes = client.upsertRemoteTable("items", itemsToPushToRemote)
            if (pushRes.isFailure) throw pushRes.exceptionOrNull() ?: Exception("Failed uploading items")
        }

        return syncedCount
    }

    private suspend fun syncAttempts(userId: String): Int {
        val remoteRes = client.getRemoteTable("attempts")
        if (remoteRes.isFailure) throw remoteRes.exceptionOrNull() ?: Exception("Failed fetching attempts")
        val remoteArray = remoteRes.getOrNull() ?: JSONArray()

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArray.length()) {
            val obj = remoteArray.getJSONObject(i)
            remoteMap[obj.getString("id")] = obj
        }

        val localAttempts = itemDao.getAllAttemptsList(userId)
        val localMap = localAttempts.associateBy { it.id }

        val attemptsToPush = JSONArray()
        var count = 0

        val attemptsToInsertLocally = mutableListOf<Attempt>()
        for ((remoteId, remoteObj) in remoteMap) {
            val remoteUpdatedAt = remoteObj.optLong("updated_at", remoteObj.optLong("timestamp", 0L))
            val localAttempt = localMap[remoteId]

            if (localAttempt == null || remoteUpdatedAt > localAttempt.updatedAt) {
                attemptsToInsertLocally.add(
                    Attempt(
                        id = remoteId,
                        userId = userId,
                        itemId = remoteObj.getString("item_id"),
                        timestamp = remoteObj.getLong("timestamp"),
                        type = remoteObj.getString("type"),
                        result = remoteObj.getString("result"),
                        answerGiven = if (remoteObj.isNull("answer_given")) null else remoteObj.getDouble("answer_given"),
                        responseTimeMs = remoteObj.optLong("response_time_ms", 0L),
                        updatedAt = remoteUpdatedAt
                    )
                )
                count++
            }
        }
        if (attemptsToInsertLocally.isNotEmpty()) {
            itemDao.upsertAttempts(attemptsToInsertLocally)
        }

        for (localAttempt in localAttempts) {
            val remoteObj = remoteMap[localAttempt.id]
            val remoteUpdatedAt = remoteObj?.optLong("updated_at", 0L) ?: -1L

            if (remoteObj == null || localAttempt.updatedAt > remoteUpdatedAt) {
                val obj = JSONObject().apply {
                    put("id", localAttempt.id)
                    put("user_id", userId)
                    put("item_id", localAttempt.itemId)
                    put("timestamp", localAttempt.timestamp)
                    put("type", localAttempt.type)
                    put("result", localAttempt.result)
                    put("answer_given", localAttempt.answerGiven)
                    put("response_time_ms", localAttempt.responseTimeMs)
                    put("updated_at", localAttempt.updatedAt)
                }
                attemptsToPush.put(obj)
                count++
            }
        }

        if (attemptsToPush.length() > 0) {
            val pushRes = client.upsertRemoteTable("attempts", attemptsToPush)
            if (pushRes.isFailure) throw pushRes.exceptionOrNull() ?: Exception("Failed uploading attempts")
        }

        return count
    }

    private suspend fun syncItemStats(userId: String): Int {
        val remoteRes = client.getRemoteTable("item_stats")
        if (remoteRes.isFailure) throw remoteRes.exceptionOrNull() ?: Exception("Failed fetching item_stats")
        val remoteArray = remoteRes.getOrNull() ?: JSONArray()

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArray.length()) {
            val obj = remoteArray.getJSONObject(i)
            remoteMap[obj.getString("item_id")] = obj
        }

        val localStats = itemDao.getAllItemStatsList(userId)
        val localMap = localStats.associateBy { it.itemId }

        val statsToPush = JSONArray()
        var count = 0

        val statsToUpsertLocally = mutableListOf<ItemStats>()
        for ((remoteItemId, remoteObj) in remoteMap) {
            val remoteUpdatedAt = remoteObj.optLong("updated_at", 0L)
            val local = localMap[remoteItemId]

            if (local == null || remoteUpdatedAt > local.updatedAt) {
                statsToUpsertLocally.add(
                    ItemStats(
                        itemId = remoteItemId,
                        userId = userId,
                        correctStreak = remoteObj.optInt("correct_streak", 0),
                        wrongCount = remoteObj.optInt("wrong_count", 0),
                        skippedCount = remoteObj.optInt("skipped_count", 0),
                        weaknessScore = remoteObj.optDouble("weakness_score", 1.0),
                        nextDueAt = remoteObj.optLong("next_due_at", 0L),
                        status = remoteObj.optString("status", ItemStats.STATUS_NEW),
                        updatedAt = remoteUpdatedAt
                    )
                )
                count++
            }
        }
        if (statsToUpsertLocally.isNotEmpty()) {
            itemDao.upsertItemStatsList(statsToUpsertLocally)
        }

        for (local in localStats) {
            val remoteObj = remoteMap[local.itemId]
            val remoteUpdatedAt = remoteObj?.optLong("updated_at", 0L) ?: -1L

            if (remoteObj == null || local.updatedAt > remoteUpdatedAt) {
                val obj = JSONObject().apply {
                    put("item_id", local.itemId)
                    put("user_id", userId)
                    put("correct_streak", local.correctStreak)
                    put("wrong_count", local.wrongCount)
                    put("skipped_count", local.skippedCount)
                    put("weakness_score", local.weaknessScore)
                    put("next_due_at", local.nextDueAt)
                    put("status", local.status)
                    put("updated_at", local.updatedAt)
                }
                statsToPush.put(obj)
                count++
            }
        }

        if (statsToPush.length() > 0) {
            val pushRes = client.upsertRemoteTable("item_stats", statsToPush)
            if (pushRes.isFailure) throw pushRes.exceptionOrNull() ?: Exception("Failed uploading item_stats")
        }

        return count
    }

    private suspend fun syncExamResults(userId: String): Int {
        val remoteRes = client.getRemoteTable("exam_results")
        if (remoteRes.isFailure) throw remoteRes.exceptionOrNull() ?: Exception("Failed fetching exam_results")
        val remoteArray = remoteRes.getOrNull() ?: JSONArray()

        val remoteMap = mutableMapOf<String, JSONObject>()
        for (i in 0 until remoteArray.length()) {
            val obj = remoteArray.getJSONObject(i)
            remoteMap[obj.getString("id")] = obj
        }

        val localExams = itemDao.getAllExamResultsAsc(userId)
        val localMap = localExams.associateBy { it.id }

        val examsToPush = JSONArray()
        var count = 0

        val examsToUpsertLocally = mutableListOf<ExamResult>()
        for ((remoteId, remoteObj) in remoteMap) {
            val remoteUpdatedAt = remoteObj.optLong("updated_at", remoteObj.optLong("timestamp", 0L))
            val local = localMap[remoteId]

            if (local == null || remoteUpdatedAt > local.updatedAt) {
                examsToUpsertLocally.add(
                    ExamResult(
                        id = remoteId,
                        userId = userId,
                        timestamp = remoteObj.getLong("timestamp"),
                        totalQuestions = remoteObj.getInt("total_questions"),
                        correctCount = remoteObj.getInt("correct_count"),
                        wrongCount = remoteObj.getInt("wrong_count"),
                        skippedCount = remoteObj.getInt("skipped_count"),
                        scorePercentage = remoteObj.getInt("score_percentage"),
                        averageTimeMs = remoteObj.optLong("average_time_ms", 0L),
                        updatedAt = remoteUpdatedAt
                    )
                )
                count++
            }
        }
        if (examsToUpsertLocally.isNotEmpty()) {
            itemDao.upsertExamResults(examsToUpsertLocally)
        }

        for (local in localExams) {
            val remoteObj = remoteMap[local.id]
            val remoteUpdatedAt = remoteObj?.optLong("updated_at", 0L) ?: -1L

            if (remoteObj == null || local.updatedAt > remoteUpdatedAt) {
                val obj = JSONObject().apply {
                    put("id", local.id)
                    put("user_id", userId)
                    put("timestamp", local.timestamp)
                    put("total_questions", local.totalQuestions)
                    put("correct_count", local.correctCount)
                    put("wrong_count", local.wrongCount)
                    put("skipped_count", local.skippedCount)
                    put("score_percentage", local.scorePercentage)
                    put("average_time_ms", local.averageTimeMs)
                    put("updated_at", local.updatedAt)
                }
                examsToPush.put(obj)
                count++
            }
        }

        if (examsToPush.length() > 0) {
            val pushRes = client.upsertRemoteTable("exam_results", examsToPush)
            if (pushRes.isFailure) throw pushRes.exceptionOrNull() ?: Exception("Failed uploading exam_results")
        }

        return count
    }
}
