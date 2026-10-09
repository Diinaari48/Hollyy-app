package com.example.data.sync

import com.example.data.model.Item
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

object ItemMapper {

    fun parseJsonToItem(obj: JSONObject, fallbackUserId: String = ""): Item {
        val id = obj.optString("id", "").trim().ifBlank {
            throw IllegalArgumentException("Missing or empty required field 'id'")
        }

        val userId = obj.optString("user_id", "").trim().ifBlank { fallbackUserId }

        val name = if (obj.has("name") && !obj.isNull("name")) {
            obj.getString("name").trim()
        } else {
            throw IllegalArgumentException("Missing required field 'name' for item $id")
        }
        if (name.isEmpty()) {
            throw IllegalArgumentException("Name cannot be blank for item $id")
        }

        val systemName = if (obj.has("system_name") && !obj.isNull("system_name")) {
            obj.optString("system_name", "").trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val cost = parseDouble(obj, "cost") ?: 0.0

        val price = parseDouble(obj, "price")
            ?: throw IllegalArgumentException("Missing required selling price field ('price') for item $id ($name)")

        val createdAt = parseTimestamp(obj, "created_at") ?: System.currentTimeMillis()
        val updatedAt = parseTimestamp(obj, "updated_at") ?: createdAt

        return Item(
            id = id,
            userId = userId,
            name = name,
            systemName = systemName,
            cost = cost,
            price = price,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is String -> {
                val cleaned = value.replace("$", "").replace(",", ".").trim()
                cleaned.toDoubleOrNull()
            }
            else -> null
        }
    }

    private fun parseTimestamp(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> {
                val str = value.trim()
                str.toLongOrNull() ?: try {
                    Instant.parse(str).toEpochMilli()
                } catch (_: Exception) {
                    try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        sdf.parse(str)?.time
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            else -> null
        }
    }
}
