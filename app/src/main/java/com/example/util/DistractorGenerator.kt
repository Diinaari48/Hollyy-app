package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object DistractorGenerator {

    /**
     * Formats price/cost identically with exactly two decimals (e.g. $0.50, $0.35, $1.00).
     */
    fun formatPrice(value: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        return DecimalFormat("0.00", symbols).format(value)
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0

    /**
     * Shared options generator used by Exam, popups, and Questions screen.
     * Guaranteed contract:
     * - Returns exactly 4 options
     * - All options are positive (> 0.0)
     * - All options are distinct when rounded to 2 decimals
     * - Exactly one option equals correctValue (abs difference < 0.0001)
     */
    fun buildOptions(
        correctValue: Double,
        allValues: List<Double> = emptyList()
    ): List<Double> {
        val roundedCorrect = round2(correctValue)

        for (attempt in 0 until 50) {
            // a) Start with a list containing correctValue
            val distractors = mutableListOf<Double>()

            fun isCandidateValid(c: Double): Boolean {
                val rc = round2(c)
                return rc > 0.0 &&
                        abs(rc - roundedCorrect) >= 0.009 &&
                        distractors.none { abs(it - rc) < 0.009 }
            }

            // Pool of potential distractors:
            val candidatePool = mutableListOf<Double>()

            // 1. Nearby catalog prices from allValues
            val catalogCandidates = allValues
                .filter { abs(it - roundedCorrect) >= 0.009 && it > 0.0 }
                .shuffled()
            for (p in catalogCandidates) {
                val rp = round2(p)
                if (abs(rp - roundedCorrect) <= roundedCorrect * 0.7 + 0.5) {
                    candidatePool.add(rp)
                }
            }

            // 2. Percentage shifts (±10% to ±60%)
            val percentages = listOf(
                -0.60, -0.50, -0.40, -0.35, -0.30, -0.25, -0.20, -0.15, -0.10,
                0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.50, 0.60
            ).shuffled()
            for (pct in percentages) {
                val candidate = round2(roundedCorrect * (1.0 + pct))
                candidatePool.add(candidate)
            }

            // 3. Nearby fixed offsets (±0.05, ±0.10, ±0.15, ±0.20, ±0.25, ±0.30, ±0.50)
            val offsets = listOf(
                -0.05, 0.05, -0.10, 0.10, -0.15, 0.15, -0.20, 0.20,
                -0.25, 0.25, -0.30, 0.30, -0.50, 0.50
            ).shuffled()
            for (off in offsets) {
                val candidate = round2(roundedCorrect + off)
                candidatePool.add(candidate)
            }

            // Shuffle pool and add valid candidates
            candidatePool.shuffle()
            for (c in candidatePool) {
                if (isCandidateValid(c)) {
                    distractors.add(round2(c))
                    if (distractors.size == 3) break
                }
            }

            // If fewer than 3 valid distractors exist, widen the range until there are 3
            var widenOffset = 0.05
            while (distractors.size < 3) {
                val high = round2(roundedCorrect + widenOffset)
                if (isCandidateValid(high)) {
                    distractors.add(high)
                }
                if (distractors.size < 3) {
                    val low = round2(roundedCorrect - widenOffset)
                    if (isCandidateValid(low)) {
                        distractors.add(low)
                    }
                }
                widenOffset = round2(widenOffset + 0.05)
            }

            // c) Shuffle the list
            val options = (distractors.take(3) + roundedCorrect).shuffled()

            // d) ASSERT before returning: options.size == 4 and exactly one option equals correctValue (abs difference < 0.0001)
            val matchCount = options.count { abs(it - roundedCorrect) < 0.0001 }
            val distinctCount = options.map { round2(it) }.toSet().size
            val allPositive = options.all { it > 0.0 }

            if (options.size == 4 && matchCount == 1 && distinctCount == 4 && allPositive) {
                return options
            }
        }

        // Guaranteed deterministic fallback if random loops somehow didn't assert (should never reach)
        val fallbackDistractors = mutableListOf<Double>()
        var step = 0.10
        while (fallbackDistractors.size < 3) {
            val high = round2(roundedCorrect + step)
            val low = round2(roundedCorrect - step)
            if (high > 0.0 && abs(high - roundedCorrect) >= 0.009 && !fallbackDistractors.contains(high)) {
                fallbackDistractors.add(high)
            }
            if (fallbackDistractors.size < 3 && low > 0.0 && abs(low - roundedCorrect) >= 0.009 && !fallbackDistractors.contains(low)) {
                fallbackDistractors.add(low)
            }
            step = round2(step + 0.05)
        }
        val finalOptions = (fallbackDistractors.take(3) + roundedCorrect).shuffled()
        check(finalOptions.size == 4 && finalOptions.count { abs(it - roundedCorrect) < 0.0001 } == 1) {
            "Options must contain exactly 4 items with exactly one matching correctValue"
        }
        return finalOptions
    }

    /**
     * Backward-compatible alias for existing code
     */
    fun generateOptions(
        correctPrice: Double,
        otherCatalogPrices: List<Double> = emptyList()
    ): List<Double> = buildOptions(correctPrice, otherCatalogPrices)
}
