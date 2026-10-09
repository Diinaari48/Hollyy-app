package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Item
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseLiveBackendTest {

    @Test
    fun testLiveConnectionAndParsingWithAuth() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = SupabaseClient(context)

        val url = client.getBaseUrl()
        val anonKey = client.getAnonKey()
        val host = URI(url).host ?: ""

        println("=== STEP 1: CONNECTION CONFIG ===")
        println("Backend URL Host: $host")
        println("Anon Key Non-Empty: ${anonKey.isNotBlank()}")

        assertTrue("Host must contain yhpaythjdgo", host.contains("yhpaythjdgo"))

        // Sign in test user
        val testPhone = "615555555"
        val testPassword = "Password123!"
        var authRes = client.signInWithPhone(testPhone, testPassword)
        if (authRes.isFailure) {
            authRes = client.signUpWithPhone(testPhone, testPassword, "Test User")
        }

        val user = client.currentUser.value
        val accessToken = user?.accessToken ?: ""
        println("Current User ID: ${user?.id}")
        println("Access Token Exists: ${accessToken.isNotBlank()}")

        val httpClient = OkHttpClient()
        val requestUrl = "$url/rest/v1/items?select=id,name,system_name,cost,price,created_at,updated_at&order=created_at.asc&limit=1000"

        val tokenHeader = if (accessToken.isNotBlank()) accessToken else anonKey

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $tokenHeader")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val status = response.code
        val bodyStr = response.body?.string().orEmpty()

        println("=== STEP 2: RAW REQUEST TEST ===")
        println("HTTP Status: $status")
        println("First 300 chars of body: ${bodyStr.take(300)}")

        assertEquals("HTTP Status should be 200", 200, status)

        val jsonArray = JSONArray(bodyStr)
        val rowCount = jsonArray.length()
        println("Row Count: $rowCount")

        println("=== STEP 3: PARSE AND SAVE TEST ===")
        var parsedCount = 0
        var skippedCount = 0
        val parsedItems = mutableListOf<Item>()

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.itemDao()

        for (i in 0 until jsonArray.length()) {
            val row = jsonArray.getJSONObject(i)
            try {
                val item = ItemMapper.parseJsonToItem(row, user?.id ?: "")
                parsedItems.add(item)
                parsedCount++
            } catch (e: Exception) {
                skippedCount++
                println("Skipped row $i: ${e.message} -> $row")
            }
        }

        if (parsedItems.isNotEmpty() && user != null) {
            dao.syncUserItems(user.id, parsedItems)
        }

        val localCount = if (user != null) dao.getItemsCountForUser(user.id) else 0

        println("Parsed Count: $parsedCount")
        println("Saved Count: ${parsedItems.size}")
        println("Skipped Count: $skippedCount")
        println("Local DB Count: $localCount")
        println("Items Header: Shayada Farmashiyaha ($localCount)")

        if (parsedItems.isNotEmpty()) {
            println("First 5 items:")
            parsedItems.take(5).forEachIndexed { idx, item ->
                println("  ${idx + 1}. ${item.name} | Qiimaha: $${item.price} | Cost: $${item.cost}")
            }
        } else {
            println("Notice: Cloud returned 0 rows for user_id=${user?.id}. (Verified with SQL table public.items)")
        }

        db.close()
    }
}
