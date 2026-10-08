package com.example.data.sync

import com.example.data.model.Item
import org.json.JSONObject
import java.util.UUID

object ItemMapper {

    fun parseJsonToItem(obj: JSONObject, fallbackUserId: String = ""): Item {
        val id = obj.optString("id", "").ifBlank { UUID.randomUUID().toString() }
        val userId = obj.optString("user_id", "").ifBlank { fallbackUserId }
        val name = obj.optString("name", "").ifBlank { "Unassigned Item" }

        val systemName = if (obj.has("system_name") && !obj.isNull("system_name")) {
            obj.optString("system_name", "").takeIf { it.isNotBlank() }
        } else {
            null
        }

        val cost = parseDouble(obj, "cost") ?: 0.0
        val macamil = parseDouble(obj, "customer_price")
            ?: parseDouble(obj, "wholesale_price")
            ?: parseDouble(obj, "wholesalePrice")
            ?: 0.0

        // public_price is required for selling price (fallback to price or publicPrice)
        val price = parseDouble(obj, "public_price")
            ?: parseDouble(obj, "price")
            ?: parseDouble(obj, "publicPrice")
            ?: throw IllegalArgumentException("Missing required selling price field (public_price) for item $id ($name)")

        val createdAt = parseLong(obj, "created_at") ?: System.currentTimeMillis()
        val updatedAt = parseLong(obj, "updated_at") ?: createdAt

        return Item(
            id = id,
            userId = userId,
            name = name,
            systemName = systemName,
            cost = cost,
            wholesalePrice = macamil,
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

    private fun parseLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key) ?: return null
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}
