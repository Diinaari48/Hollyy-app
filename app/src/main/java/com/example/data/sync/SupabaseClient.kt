package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
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
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long = 0L
)

data class RawRequestResult(
    val httpStatus: Int,
    val rowCount: Int,
    val bodySnippet: String,
    val isSuccess: Boolean,
    val rawBody: String
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
        val refreshToken = prefs.getString("refresh_token", null)
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) {
            val validEmail = email ?: if (!phone.isNullOrEmpty()) SomaliPhoneAuthValidator.phoneToEmail(phone) else ""
            val validPhone = phone ?: if (!email.isNullOrEmpty()) SomaliPhoneAuthValidator.extractPhoneFromEmail(email) else ""
            val effExp = if (expiresAt > 0L) expiresAt else getJwtExpiry(token)

            _currentUser.value = SupabaseUser(
                id = id,
                phone = validPhone,
                email = validEmail,
                fullName = fullName,
                accessToken = token,
                refreshToken = refreshToken,
                expiresAt = effExp
            )
        }
    }

    fun saveSession(user: SupabaseUser?) {
        _currentUser.value = user
        prefs.edit().apply {
            if (user != null) {
                putString("user_id", user.id)
                putString("user_phone", user.phone)
                putString("user_email", user.email)
                putString("user_full_name", user.fullName)
                putString("access_token", user.accessToken)
                if (user.refreshToken != null) {
                    putString("refresh_token", user.refreshToken)
                }
                putLong("expires_at", user.expiresAt)
                putBoolean("has_signed_up_once", true)
            } else {
                remove("user_id")
                remove("user_phone")
                remove("user_email")
                remove("user_full_name")
                remove("access_token")
                remove("refresh_token")
                remove("expires_at")
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

    fun getJwtExpiry(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                json.optLong("exp", 0L)
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun isTokenExpired(): Boolean {
        val user = _currentUser.value ?: return true
        if (user.accessToken.isBlank()) return true
        val exp = if (user.expiresAt > 0L) user.expiresAt else getJwtExpiry(user.accessToken)
        if (exp <= 0L) return false
        val currentSec = System.currentTimeMillis() / 1000
        return currentSec >= (exp - 30) // 30-second buffer
    }

    suspend fun refreshSession(): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            val user = _currentUser.value ?: return@withContext Result.failure(Exception("No user logged in"))
            val refreshToken = user.refreshToken ?: prefs.getString("refresh_token", null)

            if (refreshToken.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No refresh token available"))
            }

            val bodyJson = JSONObject().apply {
                put("refresh_token", refreshToken)
            }

            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Log.e("SupabaseAuth", "Refresh token failed: ${response.code} $respBody")
                return@withContext Result.failure(Exception("Refresh session failed: ${response.code} $respBody"))
            }

            val json = JSONObject(respBody)
            val newAccessToken = json.getString("access_token")
            val newRefreshToken = json.optString("refresh_token", refreshToken)
            val expiresAt = json.optLong("expires_at", 0L)
            val effExp = if (expiresAt > 0L) expiresAt else getJwtExpiry(newAccessToken)
            val userObj = json.optJSONObject("user")
            val id = userObj?.optString("id", user.id) ?: user.id

            val updatedUser = user.copy(
                id = id,
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                expiresAt = effExp
            )
            saveSession(updatedUser)
            Log.i("SupabaseAuth", "Session successfully refreshed for user: $id")
            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Exception refreshing session: ${e.message}", e)
            Result.failure(e)
        }
    }

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
            val refreshToken = json.optString("refresh_token", null)
            val expiresAt = json.optLong("expires_at", 0L)
            val userObj = json.optJSONObject("user") ?: json
            val id = userObj.optString("id", "")

            if (accessToken.isNotEmpty() && id.isNotEmpty()) {
                val user = SupabaseUser(
                    id = id,
                    phone = normalizedPhone,
                    email = syntheticEmail,
                    fullName = fullName?.trim()?.ifBlank { null },
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = if (expiresAt > 0L) expiresAt else getJwtExpiry(accessToken)
                )
                saveSession(user)
                fetchUserProfile()
                Result.success(user)
            } else {
                signInWithPhone(normalizedPhone, password)
            }
        } catch (e: IOException) {
            Log.e("SupabaseAuth", "Network error during signUp", e)
            Result.failure(Exception("Internet ma jiro"))
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Exception during signUp: ${e.message}", e)
            Result.failure(e)
        }
    }

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
            val refreshToken = json.optString("refresh_token", null)
            val expiresAt = json.optLong("expires_at", 0L)
            val userObj = json.getJSONObject("user")
            val id = userObj.getString("id")
            val userMeta = userObj.optJSONObject("user_metadata")
            val fullName = userMeta?.optString("full_name")?.takeIf { it.isNotBlank() }

            val user = SupabaseUser(
                id = id,
                phone = normalizedPhone,
                email = syntheticEmail,
                fullName = fullName,
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = if (expiresAt > 0L) expiresAt else getJwtExpiry(accessToken)
            )
            saveSession(user)
            fetchUserProfile()
            Result.success(user)
        } catch (e: IOException) {
            Log.e("SupabaseAuth", "Network error during signIn", e)
            Result.failure(Exception("Internet ma jiro"))
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Exception during signIn: ${e.message}", e)
            Result.failure(e)
        }
    }

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
        Log.e("SupabaseAuth", "Raw Supabase Auth Error: $respBody")
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

    /**
     * Executes the exact items raw GET request with current user access token.
     * Retries once on 401 or token expiration if retryOnAuthError is true.
     */
    suspend fun fetchItemsRaw(retryOnAuthError: Boolean = true): RawRequestResult = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val anonKey = getAnonKey()
        var user = currentUser.value
        var token = user?.accessToken ?: ""

        val urlStr = "$baseUrl/rest/v1/items?select=id,name,system_name,cost,price,created_at,updated_at&order=created_at.asc&limit=1000"

        fun doCall(authToken: String): Pair<Int, String> {
            val req = Request.Builder()
                .url(urlStr)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            val code = resp.code
            val body = resp.body?.string().orEmpty()
            return Pair(code, body)
        }

        try {
            var (code, body) = doCall(token)

            if ((code == 401 || token.isBlank()) && retryOnAuthError) {
                val refreshRes = refreshSession()
                if (refreshRes.isSuccess) {
                    val refreshedUser = refreshRes.getOrNull()
                    val newToken = refreshedUser?.accessToken ?: ""
                    val retryResult = doCall(newToken)
                    code = retryResult.first
                    body = retryResult.second
                }
            }

            var rowCount = 0
            if (code in 200..299) {
                try {
                    rowCount = JSONArray(body).length()
                } catch (_: Exception) {}
            }

            RawRequestResult(
                httpStatus = code,
                rowCount = rowCount,
                bodySnippet = body.take(300),
                isSuccess = code in 200..299,
                rawBody = body
            )
        } catch (e: Exception) {
            val topStack = e.stackTrace.firstOrNull()?.toString() ?: ""
            RawRequestResult(
                httpStatus = -1,
                rowCount = 0,
                bodySnippet = "Error: ${e.message}\nCause: ${e.cause}\nTop stack: $topStack".take(300),
                isSuccess = false,
                rawBody = e.message ?: "Exception"
            )
        }
    }

    suspend fun getRemoteTable(tableName: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl()
            val anonKey = getAnonKey()
            val user = currentUser.value
            val token = user?.accessToken ?: anonKey

            val urlStr = if (tableName == "items") {
                "$baseUrl/rest/v1/items?select=id,name,system_name,cost,price,created_at,updated_at&order=created_at.asc&limit=1000"
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
