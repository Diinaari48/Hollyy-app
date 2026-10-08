package com.example.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

        // Sign in or sign up a test user to get a real session token
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
        val requestUrl = "$url/rest/v1/items?select=id,name,system_name,cost,customer_price,public_price&order=created_at.asc&limit=100"

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
        val jsonArray = JSONArray(bodyStr)
        val rowCount = jsonArray.length()
        println("Row Count: $rowCount")
        println("First 300 chars of body: ${bodyStr.take(300)}")

        assertEquals(200, status)

        println("=== STEP 3: PARSE AND SAVE TEST ===")
        var parsedCount = 0
        val parsedItems = mutableListOf<com.example.data.model.Item>()

        for (i in 0 until jsonArray.length()) {
            val row = jsonArray.getJSONObject(i)
            try {
                val item = ItemMapper.parseJsonToItem(row, user?.id ?: "")
                parsedItems.add(item)
                parsedCount++
            } catch (e: Exception) {
                println("Failed row $i: ${e.message} -> $row")
            }
        }

        println("Parsed Count: $parsedCount")
        println("First 5 Item Names:")
        parsedItems.take(5).forEachIndexed { idx, item ->
            println("  ${idx + 1}. ${item.name} (System: ${item.systemName ?: "N/A"}, Price: $${item.price}, Macamil: $${item.wholesalePrice}, Cost: $${item.cost})")
        }

        assertEquals(rowCount, parsedCount)
    }
}
