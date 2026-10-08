package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

object SupabaseConfig {

    private const val PREFS_NAME = "supabase_prefs"
    private const val KEY_CUSTOM_URL = "custom_url"
    private const val KEY_CUSTOM_ANON_KEY = "custom_anon_key"

    /**
     * Reads Supabase URL from:
     * 1. User custom override in app settings (if provided)
     * 2. BuildConfig.SUPABASE_URL (injected via Secrets Gradle plugin from .env / secrets panel)
     * 3. System environment variable SUPABASE_URL
     */
    fun getSupabaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_URL, null)?.trim()
        if (!custom.isNullOrEmpty() && custom.contains("yhpaythjdgo")) return custom

        val buildConfigUrl = readBuildConfigField("SUPABASE_URL").trim()
        if (buildConfigUrl.isNotEmpty() && !buildConfigUrl.contains("your-project-id")) return buildConfigUrl

        val sysEnv = (System.getenv("SUPABASE_URL") ?: "").trim()
        if (sysEnv.isNotEmpty() && !sysEnv.contains("your-project-id")) return sysEnv

        return "https://yhpaythjdgoexbwucsbe.supabase.co"
    }

    fun getSupabaseAnonKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_ANON_KEY, null)?.trim()
        if (!custom.isNullOrEmpty() && !custom.contains("your_supabase_anon_key")) return custom

        val buildConfigKey = readBuildConfigField("SUPABASE_ANON_KEY").trim()
        if (buildConfigKey.isNotEmpty() && !buildConfigKey.contains("your_supabase_anon_key")) return buildConfigKey

        val sysEnv = (System.getenv("SUPABASE_ANON_KEY") ?: "").trim()
        if (sysEnv.isNotEmpty() && !sysEnv.contains("your_supabase_anon_key")) return sysEnv

        return "sb_publishable_mj4SeRpFNHyIlnQxN2VUXg_bYk7Ni9K"
    }

    fun isConfigured(context: Context): Boolean {
        val url = getSupabaseUrl(context)
        val key = getSupabaseAnonKey(context)
        return url.isNotEmpty() && !url.contains("your-project-id") && key.isNotEmpty() && !key.contains("your_supabase_anon_key")
    }

    fun saveCustomCredentials(context: Context, url: String?, anonKey: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (url != null) putString(KEY_CUSTOM_URL, url) else remove(KEY_CUSTOM_URL)
            if (anonKey != null) putString(KEY_CUSTOM_ANON_KEY, anonKey) else remove(KEY_CUSTOM_ANON_KEY)
            apply()
        }
    }

    private fun readBuildConfigField(fieldName: String): String {
        return try {
            val field = BuildConfig::class.java.getField(fieldName)
            (field.get(null) as? String) ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
