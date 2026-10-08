package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.util.SomaliPhoneAuthValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SupabaseUser(
    val id: String,
    val phone: String,
    val email: String,
    val fullName: String?,
    val accessToken: String
)

class SupabaseClient(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val prefs: SharedPreferences = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
    val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

    init {
        loadSavedSession()
    }

    private fun loadSavedSession() {
        val id = prefs.getString("user_id", null)
        val phone = prefs.getString("user_phone", null)
        val email = prefs.getString("user_email", null)
        val fullName = prefs.getString("user_full_name", null)
        val token = prefs.getString("access_token", null)
        if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) {
            val validEmail = email ?: if (!phone.isNullOrEmpty()) SomaliPhoneAuthValidator.phoneToEmail(phone) else ""
            val validPhone = phone ?: if (!email.isNullOrEmpty()) SomaliPhoneAuthValidator.extractPhoneFromEmail(email) else ""
            _currentUser.value = SupabaseUser(
                id = id,
                phone = validPhone,
                email = validEmail,
                fullName = fullName,
                accessToken = token
            )
        }
    }

    private fun saveSession(user: SupabaseUser?) {
        _currentUser.value = user
        prefs.edit().apply {
            if (user != null) {
                putString("user_id", user.id)
                putString("user_phone", user.phone)
                putString("user_email", user.email)
                putString("user_full_name", user.fullName)
                putString("access_token", user.accessToken)
                putBoolean("has_signed_up_once", true)
            } else {
                remove("user_id")
                remove("user_phone")
                remove("user_email")
                remove("user_full_name")
                remove("access_token")
            }
            apply()
        }
    }

    fun hasSignedUpOnce(): Boolean {
        return prefs.getBoolean("has_signed_up_once", false)
    }

    fun setHasSignedUpOnce(value: Boolean) {
        prefs.edit().putBoolean("has_signed_up_once", value).apply()
    }

    fun getBaseUrl(): String {
        val url = SupabaseConfig.getSupabaseUrl(context).trimEnd('/')
        return if (url.isNotEmpty() && !url.startsWith("http")) "https://$url" else url
    }

    fun getAnonKey(): String = SupabaseConfig.getSupabaseAnonKey(context)

    /**
     * Signs up a new user using the standard Supabase Auth client method (POST /auth/v1/signup).
     * Uses synthetic email "{phone}@holly.com" behind the scenes.
     * If session is null/empty, immediately calls signInWithPhone with the same credentials.
     */
    suspend fun signUpWithPhone(
        normalizedPhone: String,
        password: String,
        fullName: String?
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            if (baseUrl.isEmpty() || anonKey.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Supabase URL ama Anon Key lama helin. Fadlan hubi .env ama Settings.")
                )
            }

            val syntheticEmail = SomaliPhoneAuthValidator.phoneToEmail(normalizedPhone)

            val bodyJson = JSONObject().apply {
                put("email", syntheticEmail)
                put("password", password)
                val dataObj = JSONObject().apply {
                    put("phone", normalizedPhone)
                    if (!fullName.isNullOrBlank()) {
                        put("full_name", fullName.trim())
                    }
                }
                put("data", dataObj)
            }

            val request = Request.Builder()
                .url("$baseUrl/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = mapAuthError(respBody, isSignUp = true)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(respBody)
            val accessToken = json.optString("access_token", "")
            val userObj = json.optJSONObject("user") ?: json
            val id = userObj.optString("id", "")

            if (accessToken.isNotEmpty() && id.isNotEmpty()) {
                val user = SupabaseUser(
                    id = id,
                    phone = normalizedPhone,
                    email = syntheticEmail,
                    fullName = fullName?.trim()?.ifBlank { null },
                    accessToken = accessToken
                )
                saveSession(user)
                fetchUserProfile()
                Result.success(user)
            } else {
                // If signUp returns no session (or email confirm was on), immediately call signInWithPhone with same credentials
                signInWithPhone(normalizedPhone, password)
            }
        } catch (e: IOException) {
            android.util.Log.e("SupabaseAuth", "Network error during signUp", e)
            Result.failure(Exception("Internet ma jiro"))
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuth", "Exception during signUp: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing user using their normalized Somali phone number and password.
     */
    suspend fun signInWithPhone(
        normalizedPhone: String,
        password: String
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            if (baseUrl.isEmpty() || anonKey.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Supabase URL ama Anon Key lama helin.")
                )
            }

            val syntheticEmail = SomaliPhoneAuthValidator.phoneToEmail(normalizedPhone)

            val bodyJson = JSONObject().apply {
                put("email", syntheticEmail)
                put("password", password)
            }

            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = mapAuthError(respBody, isSignUp = false)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(respBody)
            val accessToken = json.getString("access_token")
            val userObj = json.getJSONObject("user")
            val id = userObj.getString("id")
            val userMeta = userObj.optJSONObject("user_metadata")
            val fullName = userMeta?.optString("full_name")?.takeIf { it.isNotBlank() }

            val user = SupabaseUser(
                id = id,
                phone = normalizedPhone,
                email = syntheticEmail,
                fullName = fullName,
                accessToken = accessToken
            )
            saveSession(user)
            fetchUserProfile()
            Result.success(user)
        } catch (e: IOException) {
            android.util.Log.e("SupabaseAuth", "Network error during signIn", e)
            Result.failure(Exception("Internet ma jiro"))
        } catch (e: Exception) {
            android.util.Log.e("SupabaseAuth", "Exception during signIn: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Reads the logged-in user's profile from the public.profiles table (populated by trigger).
     * Does NOT insert into profiles from client.
     */
    suspend fun fetchUserProfile(): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        try {
            val user = _currentUser.value ?: return@withContext Result.failure(Exception("No user logged in"))
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            val token = user.accessToken

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/profiles?id=eq.${user.id}&select=phone,full_name")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error fetching profile: ${response.code} $body"))
            }

            val jsonArray = JSONArray(body)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                val phone = obj.optString("phone", user.phone).ifBlank { user.phone }
                val fullName = obj.optString("full_name", user.fullName ?: "").ifBlank { null } ?: user.fullName
                val updatedUser = user.copy(phone = phone, fullName = fullName)
                saveSession(updatedUser)
                Result.success(Pair(phone, fullName))
            } else {
                Result.success(Pair(user.phone, user.fullName))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        saveSession(null)
    }

    private fun mapAuthError(respBody: String, isSignUp: Boolean): String {
        android.util.Log.e("SupabaseAuth", "Raw Supabase Auth Error: $respBody")
        println("Raw Supabase Auth Error: $respBody")
        val lower = respBody.lowercase()
        return when {
            lower.contains("rate limit") || lower.contains("over_email_send_rate_limit") || lower.contains("email rate limit exceeded") -> {
                "Isku day mar kale hal saac kadib"
            }
            lower.contains("already registered") || lower.contains("user already exists") || lower.contains("already been registered") || lower.contains("email address already in use") -> {
                "Number-kan horey ayaa loo diiwaangeliyay, fadlan gal"
            }
            lower.contains("email not confirmed") || lower.contains("email_not_confirmed") -> {
                "Cilad habeynta server-ka: Confirm email waa in la damiyaa"
            }
            lower.contains("invalid login credentials") || lower.contains("invalid_grant") || lower.contains("invalid credentials") -> {
                "Number ama password waa khalad"
            }
            lower.contains("password should be at least") || lower.contains("weak_password") -> {
                "Erayga sirta ah waa inuu ka koobnaadaa ugu yaraan 6 xaraf"
            }
            lower.contains("network") || lower.contains("timeout") || lower.contains("unable to resolve host") -> {
                "Internet ma jiro"
            }
            else -> {
                try {
                    val json = JSONObject(respBody)
                    val msg = json.optString("msg", json.optString("error_description", json.optString("message", json.optString("error", ""))))
                    if (msg.isNotBlank()) msg else if (isSignUp) "Diiwaangelintu waa fashilantay" else "Galitaanku waa fashilmay"
                } catch (_: Exception) {
                    if (isSignUp) "Diiwaangelintu waa fashilantay" else "Galitaanku waa fashilmay"
                }
            }
        }
    }

    suspend fun getRemoteTable(tableName: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            val user = currentUser.value
            val token = user?.accessToken ?: anonKey

            val urlStr = if (tableName == "items") {
                "$baseUrl/rest/v1/items?select=id,name,system_name,cost,customer_price,public_price&order=created_at.asc&limit=100"
            } else {
                "$baseUrl/rest/v1/$tableName?select=*"
            }

            val request = Request.Builder()
                .url(urlStr)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error fetching $tableName: ${response.code} $body"))
            }

            Result.success(JSONArray(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertRemoteTable(tableName: String, jsonArray: JSONArray): Result<Unit> = withContext(Dispatchers.IO) {
        if (jsonArray.length() == 0) return@withContext Result.success(Unit)

        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            val user = currentUser.value
            val token = user?.accessToken ?: anonKey

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/$tableName")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error upserting to $tableName: ${response.code} $body"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
