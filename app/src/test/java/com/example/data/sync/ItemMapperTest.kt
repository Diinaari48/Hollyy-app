package com.example.data.sync

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ItemMapperTest {

    @Test
    fun testMapper_withNullCostAndNullSystemName() {
        val jsonStr = """
            {
                "id": "7e1a022c-3d72-4379-85b5-1fbd5a29e71d",
                "user_id": "usr_123",
                "name": "Paracetamol 500mg",
                "system_name": null,
                "cost": null,
                "customer_price": 1.50,
                "public_price": 2.00,
                "created_at": 1700000000000,
                "updated_at": 1700000000000
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val item = ItemMapper.parseJsonToItem(json, "usr_123")

        assertEquals("7e1a022c-3d72-4379-85b5-1fbd5a29e71d", item.id)
        assertEquals("usr_123", item.userId)
        assertEquals("Paracetamol 500mg", item.name)
        assertNull(item.systemName)
        assertEquals(0.0, item.cost, 0.001)
        assertEquals(1.50, item.wholesalePrice, 0.001)
        assertEquals(2.00, item.price, 0.001)
    }

    @Test
    fun testMapper_withStringNumbers() {
        val jsonStr = """
            {
                "id": "8e2b033d-4e83-5480-96c6-2gce6b30f82e",
                "user_id": "usr_456",
                "name": "Amoxicillin 500mg",
                "system_name": "AMOX-500",
                "cost": "1.20",
                "customer_price": "2.50",
                "public_price": "3.50",
                "created_at": "1700000000123",
                "updated_at": "1700000000123"
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val item = ItemMapper.parseJsonToItem(json, "usr_456")

        assertEquals("8e2b033d-4e83-5480-96c6-2gce6b30f82e", item.id)
        assertEquals("AMOX-500", item.systemName)
        assertEquals(1.20, item.cost, 0.001)
        assertEquals(2.50, item.wholesalePrice, 0.001)
        assertEquals(3.50, item.price, 0.001)
    }
}
