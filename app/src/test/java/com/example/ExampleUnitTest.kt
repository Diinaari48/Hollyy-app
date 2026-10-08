package com.example

import com.example.data.model.Item
import com.example.util.CsvImporter
import com.example.util.DistractorGenerator
import com.example.util.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun testDistractorGenerator_hasFourUniquePositiveOptions() {
        val correctPrice = 2.50
        val options = DistractorGenerator.generateOptions(correctPrice)

        assertEquals(4, options.size)
        // No duplicates
        assertEquals(4, options.toSet().size)
        // Contains correct price
        assertTrue(options.any { abs(it - correctPrice) < 0.009 })
        // All options are strictly positive
        assertTrue(options.all { it > 0.0 })
    }

    @Test
    fun testDistractorGenerator_withDecimals() {
        val correctPrice = 1.25
        val options = DistractorGenerator.generateOptions(correctPrice)

        assertEquals(4, options.size)
        assertTrue(options.any { abs(it - correctPrice) < 0.009 })
        assertTrue(options.all { it > 0.0 })
    }

    @Test
    fun testCsvImporter_parsesSampleDataCorrectly() {
        val result = CsvImporter.parse(SampleData.SAMPLE_CSV)

        assertTrue(result.validItems.isNotEmpty())
        assertEquals(10, result.validItems.size)
        assertEquals(0, result.skippedRows.size)

        val first = result.validItems.first()
        assertEquals("Paracetamol 500mg", first.name)
        assertEquals(0.50, first.cost, 0.01)
        assertEquals("PARA-500", first.systemName)
        assertEquals(0.80, first.wholesalePrice, 0.01)
        assertEquals(1.00, first.price, 0.01)
    }

    @Test
    fun testCsvImporter_skipsRowsWithoutPrice() {
        val csvWithInvalid = """
            Item,cost,Magaca systemka,macamil,qiimaha
            Valid Item 1,0.50,VAL-1,0.80,1.25
            No Price Item,0.50,NOP-1,0.80,
            Zero Price Item,0.50,ZER-1,0.80,0.00
            Invalid Price Item,0.50,INV-1,0.80,free
            Valid Item 2,1.00,VAL-2,1.50,2.00
        """.trimIndent()

        val result = CsvImporter.parse(csvWithInvalid)

        assertEquals(2, result.validItems.size)
        assertEquals(3, result.skippedRows.size)
        assertEquals("Valid Item 1", result.validItems[0].name)
        assertEquals(1.25, result.validItems[0].price, 0.01)
        assertEquals("Valid Item 2", result.validItems[1].name)
        assertEquals(2.00, result.validItems[1].price, 0.01)
    }

    @Test
    fun testWorkSessionConfig_quietHoursDetection() {
        val config = com.example.data.model.WorkSessionConfig(
            quietHoursEnabled = true,
            quietStartHour = 22,
            quietStartMinute = 0,
            quietEndHour = 7,
            quietEndMinute = 0
        )

        // 23:30 is inside quiet hours
        val lateNight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 30)
        }
        assertTrue(config.isInsideQuietHours(lateNight))

        // 04:15 is inside quiet hours
        val earlyMorning = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 4)
            set(java.util.Calendar.MINUTE, 15)
        }
        assertTrue(config.isInsideQuietHours(earlyMorning))

        // 14:00 is outside quiet hours
        val afternoon = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
        }
        assertFalse(config.isInsideQuietHours(afternoon))

        assertEquals("07:00", config.formatQuietEnd())
    }

    @Test
    fun testTodayReport_exactFormatting() {
        val now = System.currentTimeMillis()
        val attempts = listOf(
            com.example.data.model.Attempt(itemId = "item_1", timestamp = now, type = "EXAM", result = "CORRECT", responseTimeMs = 2500),
            com.example.data.model.Attempt(itemId = "item_2", timestamp = now, type = "EXAM", result = "CORRECT", responseTimeMs = 3000),
            com.example.data.model.Attempt(itemId = "item_3", timestamp = now, type = "CHOICE", result = "WRONG", responseTimeMs = 6000),
            com.example.data.model.Attempt(itemId = "item_4", timestamp = now, type = "CHOICE", result = "SKIPPED", responseTimeMs = 12000)
        )

        val report = com.example.util.ReportAnalyzer.calculateTodayReport(attempts)
        assertEquals(2, report.correct)
        assertEquals(1, report.wrong)
        assertEquals(1, report.skipped)
        assertEquals(4, report.total)
        assertEquals(50, report.accuracyPercent)
        assertEquals("Maanta 2 sax, 1 khalad, 1 la dhaafay", report.summaryText)
    }

    @Test
    fun testTopWeakestItems_ranksCorrectly() {
        val item1 = Item(id = "item_1", name = "Amox 500mg", price = 2.5)
        val item2 = Item(id = "item_2", name = "Para 500mg", price = 1.0)

        val items = listOf(
            com.example.data.model.ItemWithStats(item = item1, stats = null),
            com.example.data.model.ItemWithStats(item = item2, stats = null)
        )

        val attempts = listOf(
            com.example.data.model.Attempt(itemId = "item_1", type = "CHOICE", result = "WRONG", responseTimeMs = 4000),
            com.example.data.model.Attempt(itemId = "item_1", type = "CHOICE", result = "WRONG", responseTimeMs = 5000),
            com.example.data.model.Attempt(itemId = "item_1", type = "CHOICE", result = "SKIPPED", responseTimeMs = 9000),
            com.example.data.model.Attempt(itemId = "item_2", type = "CHOICE", result = "CORRECT", responseTimeMs = 2000)
        )

        val weakest = com.example.util.ReportAnalyzer.calculateTop10WeakestItems(items, attempts)
        assertEquals(1, weakest.size)
        assertEquals("Amox 500mg", weakest.first().item.name)
        assertEquals(2, weakest.first().wrongCount)
        assertEquals(1, weakest.first().skippedCount)
    }

    @Test
    fun testConsecutiveDaysStreak_singleDay() {
        val attempts = listOf(
            com.example.data.model.Attempt(itemId = "item_1", timestamp = System.currentTimeMillis(), type = "CHOICE", result = "CORRECT")
        )
        val streak = com.example.util.ReportAnalyzer.calculateConsecutiveDaysStreak(attempts)
        assertEquals(1, streak)
    }

    @Test
    fun testSomaliPhoneValidator_validNumbersNormalized() {
        // Direct 9 digits starting with 61 and 68
        assertEquals("611234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("611234567"))
        assertEquals("681234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("681234567"))

        // With +252, 252, or leading 0
        assertEquals("611234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("+252611234567"))
        assertEquals("611234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("252611234567"))
        assertEquals("611234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("0611234567"))
        assertEquals("681234567", com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("+252 68 123 4567"))

        // Synthetic email creation with holly.com
        assertEquals("611234567@holly.com", com.example.util.SomaliPhoneAuthValidator.phoneToEmail("611234567"))
        assertEquals("681234567@holly.com", com.example.util.SomaliPhoneAuthValidator.phoneToEmail("+252 68 123 4567"))
    }

    @Test
    fun testSomaliPhoneValidator_invalidNumbersRejected() {
        // Invalid prefixes
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("621234567"))
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("711234567"))
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("651234567"))

        // Invalid lengths
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("6112345"))
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber("6112345678"))
        org.junit.Assert.assertNull(com.example.util.SomaliPhoneAuthValidator.normalizePhoneNumber(""))
    }
}
