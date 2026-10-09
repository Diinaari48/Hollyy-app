package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.repository.PharmacyRepository
import com.example.util.DistractorGenerator
import com.example.util.SampleData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuizOptionsUnitTest {

    @Test
    fun testOptionsGeneration_forEverySampleItem_PriceAndCost_1000Iterations() {
        val sampleItems = SampleData.sampleMedicines
        val allPrices = sampleItems.map { it.price }
        val allCosts = sampleItems.filter { it.cost > 0.0 }.map { it.cost }

        println("======================================================================")
        println("STARTING COMPREHENSIVE 1,000-ITERATION OPTION SET VALIDATION")
        println("Sample Item Count: ${sampleItems.size}")
        println("======================================================================")

        var totalOptionSetsPrice = 0
        var totalOptionSetsCost = 0
        val pricePassList = mutableListOf<String>()
        val costPassList = mutableListOf<String>()

        // 1. TEST FOR PRICE QUESTIONS
        println("\n--- [PART 1: PRICE QUESTIONS - 1,000 RUNS PER ITEM] ---")
        for (item in sampleItems) {
            val correctPrice = (item.price * 100.0).roundToInt() / 100.0
            for (i in 1..1000) {
                val options = DistractorGenerator.buildOptions(correctPrice, allPrices)

                // 1. Exactly 4 options
                assertEquals("Options count must be 4", 4, options.size)

                // 2. All distinct when rounded to 2 decimals
                val distinctCount = options.map { (it * 100.0).roundToInt() }.toSet().size
                assertEquals("Options must all be distinct (no duplicates)", 4, distinctCount)

                // 3. All positive
                assertTrue("All options must be strictly positive", options.all { it > 0.0 })

                // 4. Exactly one equals correct value
                val matchCount = options.count { abs(it - correctPrice) < 0.0001 }
                assertEquals("Exactly one option must match correct price", 1, matchCount)

                // 5. Correct value is always present
                assertTrue("Correct price must be present in options", options.any { abs(it - correctPrice) < 0.0001 })

                // 6. Formatted identically (all 2 decimals)
                val formattedOptions = options.map { DistractorGenerator.formatPrice(it) }
                assertTrue("Formatted option should have 2 decimals", formattedOptions.all { it.matches(Regex("\\d+\\.\\d{2}")) })

                totalOptionSetsPrice++
            }
            val status = "PASS: [PRICE] ${item.name.padEnd(35)} | Correct: $${DistractorGenerator.formatPrice(correctPrice)} | 1,000 sets verified"
            pricePassList.add(status)
            println(status)
        }

        // 2. TEST FOR COST QUESTIONS (skip items with cost <= 0.0)
        println("\n--- [PART 2: COST QUESTIONS - 1,000 RUNS PER ITEM] ---")
        for (item in sampleItems) {
            if (item.cost <= 0.0) continue

            val correctCost = (item.cost * 100.0).roundToInt() / 100.0
            for (i in 1..1000) {
                val options = DistractorGenerator.buildOptions(correctCost, allCosts)

                // 1. Exactly 4 options
                assertEquals("Options count must be 4", 4, options.size)

                // 2. All distinct
                val distinctCount = options.map { (it * 100.0).roundToInt() }.toSet().size
                assertEquals("Options must all be distinct (no duplicates)", 4, distinctCount)

                // 3. All positive
                assertTrue("All options must be strictly positive", options.all { it > 0.0 })

                // 4. Exactly one equals correct value
                val matchCount = options.count { abs(it - correctCost) < 0.0001 }
                assertEquals("Exactly one option must match correct cost", 1, matchCount)

                // 5. Correct value is always present
                assertTrue("Correct cost must be present in options", options.any { abs(it - correctCost) < 0.0001 })

                // 6. Formatted identically
                val formattedOptions = options.map { DistractorGenerator.formatPrice(it) }
                assertTrue("Formatted option should have 2 decimals", formattedOptions.all { it.matches(Regex("\\d+\\.\\d{2}")) })

                totalOptionSetsCost++
            }
            val status = "PASS: [COST]  ${item.name.padEnd(35)} | Correct: $${DistractorGenerator.formatPrice(correctCost)} | 1,000 sets verified"
            costPassList.add(status)
            println(status)
        }

        val grandTotalSets = totalOptionSetsPrice + totalOptionSetsCost
        println("\n======================================================================")
        println("SUMMARY RESULTS")
        println("Total Price Option Sets Tested: $totalOptionSetsPrice")
        println("Total Cost Option Sets Tested:  $totalOptionSetsCost")
        println("GRAND TOTAL OPTION SETS TESTED: $grandTotalSets")
        println("ALL TESTS PASSED (100% SUCCESS RATE, ZERO MISSING OR INVALID ANSWERS)")
        println("======================================================================")
    }

    @Test
    fun testLiveExam_10QuestionsWithCorrectAnswerVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.itemDao()
        val userId = "test_user_exam"
        val repository = PharmacyRepository(dao, getUserId = { userId })

        // Seed sample items into database
        val sampleItemsWithUser = SampleData.sampleMedicines.map { it.copy(userId = userId) }
        dao.insertItems(sampleItemsWithUser)

        println("\n======================================================================")
        println("RUNNING LIVE EXAM (10 QUESTIONS)")
        println("======================================================================")

        val questions = repository.generateExamQuestions(requestedCount = 10, userId = userId)
        assertEquals("Exam must generate 10 questions", 10, questions.size)

        questions.forEachIndexed { index, q ->
            val correctFormatted = DistractorGenerator.formatPrice(q.targetPrice)
            val optionsFormatted = q.options.map { "$${DistractorGenerator.formatPrice(it)}" }

            // Verify assertions for this live exam question
            assertEquals("Question must have 4 options", 4, q.options.size)
            assertEquals("All 4 options must be distinct", 4, q.options.map { (it * 100.0).roundToInt() }.toSet().size)
            assertTrue("All options must be positive", q.options.all { it > 0.0 })

            val isCorrectPresent = q.options.any { abs(it - q.targetPrice) < 0.0001 }
            assertTrue("Correct answer MUST be among the options", isCorrectPresent)

            val matchCount = q.options.count { abs(it - q.targetPrice) < 0.0001 }
            assertEquals("Exactly one option must match the correct answer", 1, matchCount)

            println("\n[Question ${index + 1}/10] [Type: ${q.questionType}]")
            println("  Prompt:         \"${q.questionText}\"")
            println("  Options:        ${optionsFormatted.joinToString("  |  ")}")
            println("  Correct Answer: $$correctFormatted")
            println("  Verification:   PASS -> Correct answer $$correctFormatted is among options: YES")
        }

        println("\n======================================================================")
        println("LIVE EXAM 10/10 QUESTIONS VERIFIED: CORRECT ANSWER ALWAYS PRESENT")
        println("======================================================================")

        db.close()
    }
}
