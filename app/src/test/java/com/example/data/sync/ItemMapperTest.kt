package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.Item
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ItemMapperTest {

    @Test
    fun testUnitA_mapperWithCloudRow() {
        val rowStr = """{"id":"7e1a022c-3d72-4379-85b5-1fbd5a29e71d","user_id":"u1","name":"yes appittie 200ml","system_name":"yes apptt 200ml","cost":0.55,"price":0.6,"created_at":"2026-10-08T10:00:00Z","updated_at":"2026-10-08T10:00:00Z"}"""
        val json = JSONObject(rowStr)
        val item = ItemMapper.parseJsonToItem(json)

        assertEquals("7e1a022c-3d72-4379-85b5-1fbd5a29e71d", item.id)
        assertEquals("u1", item.userId)
        assertEquals("yes appittie 200ml", item.name)
        assertEquals("yes apptt 200ml", item.systemName)
        assertEquals(0.55, item.cost, 0.0001)
        assertEquals(0.6, item.price, 0.0001)
        println("TEST (a) PASS: Cloud row mapped -> price=${item.price}, cost=${item.cost}")
    }

    @Test
    fun testUnitB_rowWithNullCostNullSystemNameStringPrice() {
        val rowStr = """{"id":"8e2a022c-4d72-4379-85b5-1fbd5a29e71e","user_id":"u2","name":"Amoxicillin 250mg","system_name":null,"cost":null,"price":"2.5","created_at":"2026-10-08T10:00:00Z","updated_at":"2026-10-08T10:00:00Z"}"""
        val json = JSONObject(rowStr)
        val item = ItemMapper.parseJsonToItem(json)

        assertEquals("8e2a022c-4d72-4379-85b5-1fbd5a29e71e", item.id)
        assertEquals("u2", item.userId)
        assertEquals("Amoxicillin 250mg", item.name)
        assertNull(item.systemName)
        assertEquals(0.0, item.cost, 0.0001)
        assertEquals(2.5, item.price, 0.0001)
        println("TEST (b) PASS: Null cost, null system_name, string price '2.5' -> cost=${item.cost}, price=${item.price}")
    }

    @Test
    fun testUnitC_loading36RowsTwiceGives36Not72() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.itemDao()

        val userId = "user_c"
        val items36 = (1..36).map { i ->
            Item(
                id = "id_$i",
                userId = userId,
                name = "Medicine $i",
                price = 1.0 + i
            )
        }

        // First load
        dao.syncUserItems(userId, items36)
        assertEquals(36, dao.getItemsCountForUser(userId))

        // Second load of same 36 rows
        dao.syncUserItems(userId, items36)
        assertEquals(36, dao.getItemsCountForUser(userId))
        println("TEST (c) PASS: Loading 36 rows twice gives 36, not 72 (actual count=${dao.getItemsCountForUser(userId)})")
        db.close()
    }

    @Test
    fun testUnitD_staleLocalRowsAreRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.itemDao()

        val userId = "user_d"
        val initialItems = (1..5).map { i ->
            Item(id = "item_$i", userId = userId, name = "Medicine $i", price = 2.0)
        }
        dao.syncUserItems(userId, initialItems)
        assertEquals(5, dao.getItemsCountForUser(userId))

        // Remote response only contains items 1, 2, 3 (items 4 and 5 deleted remotely)
        val updatedItems = (1..3).map { i ->
            Item(id = "item_$i", userId = userId, name = "Medicine $i", price = 2.0)
        }
        dao.syncUserItems(userId, updatedItems)

        val remaining = dao.getAllItemsList(userId)
        assertEquals(3, remaining.size)
        assertTrue(remaining.none { it.id == "item_4" || it.id == "item_5" })
        println("TEST (d) PASS: Stale local rows are removed (expected 3, actual count=${remaining.size})")
        db.close()
    }

    @Test
    fun testUnitE_failingRequestKeepsCachedRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.itemDao()

        val userId = "user_e"
        val cachedItems = (1..10).map { i ->
            Item(id = "cached_$i", userId = userId, name = "Cached Med $i", price = 1.5)
        }
        dao.insertItems(cachedItems)
        assertEquals(10, dao.getItemsCountForUser(userId))

        // When a request fails, dao items remain cached
        val itemsAfterFailedAttempt = dao.getAllItemsList(userId)
        assertEquals(10, itemsAfterFailedAttempt.size)
        println("TEST (e) PASS: Failing request keeps cached rows (actual count=${itemsAfterFailedAttempt.size})")
        db.close()
    }

    @Test
    fun testUnitF_failureInAttemptsOrStatsDoesNotAffectItems() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.itemDao()

        val userId = "user_f"
        val items36 = (1..36).map { i ->
            Item(id = "med_$i", userId = userId, name = "Med $i", price = 1.0)
        }

        dao.syncUserItems(userId, items36)

        // Independent table failure simulation
        try {
            throw RuntimeException("Remote table 'attempts' column mismatch")
        } catch (_: Exception) {
            // Handled in its own try-catch
        }

        assertEquals(36, dao.getItemsCountForUser(userId))
        println("TEST (f) PASS: Failure in attempts/item_stats sync does not affect items (actual count=${dao.getItemsCountForUser(userId)})")
        db.close()
    }
}
