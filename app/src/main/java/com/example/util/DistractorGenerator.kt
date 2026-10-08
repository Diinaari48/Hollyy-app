package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

object DistractorGenerator {

    /**
     * Formats price nicely (e.g. 1.25 or 5.0 or 12).
     */
    fun formatPrice(value: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US)
        return if (value % 1.0 == 0.0) {
            DecimalFormat("0.##", symbols).format(value)
        } else {
            DecimalFormat("0.00", symbols).format(value)
        }
    }

    /**
     * Generates 3 distractors close to [correctPrice] (e.g. ±10% to 40%, step shifts, catalog prices).
     * Returns a list of 4 options shuffled (or with correct one placed randomly).
     */
    fun generateOptions(
        correctPrice: Double,
        otherCatalogPrices: List<Double> = emptyList()
    ): List<Double> {
        val candidates = mutableSetOf<Double>()
        val hasDecimals = (correctPrice % 1.0 != 0.0)

        // 1. Nearby catalog prices within 50%
        val nearbyCatalog = otherCatalogPrices
            .filter { abs(it - correctPrice) > 0.009 && abs(it - correctPrice) <= correctPrice * 0.7 }
            .shuffled()

        for (p in nearbyCatalog) {
            candidates.add(roundToSensible(p, hasDecimals))
            if (candidates.size >= 2) break
        }

        // 2. Percentage shifts (±10%, ±20%, ±25%, ±33%, ±40%)
        val percentages = listOf(-0.35, -0.25, -0.20, -0.15, -0.10, 0.10, 0.15, 0.20, 0.25, 0.35, 0.50).shuffled()
        for (pct in percentages) {
            val candidate = correctPrice * (1.0 + pct)
            val rounded = roundToSensible(candidate, hasDecimals)
            if (rounded > 0.05 && abs(rounded - correctPrice) > 0.009) {
                candidates.add(rounded)
            }
            if (candidates.size >= 5) break
        }

        // 3. Step shifts based on magnitude
        val step = when {
            correctPrice <= 2.0 -> 0.25
            correctPrice <= 5.0 -> 0.50
            correctPrice <= 20.0 -> 1.0
            correctPrice <= 50.0 -> 2.5
            else -> 5.0
        }
        val stepMultipliers = listOf(-2, -1, 1, 2, 3, -3).shuffled()
        for (m in stepMultipliers) {
            val candidate = correctPrice + (m * step)
            val rounded = roundToSensible(candidate, hasDecimals)
            if (rounded > 0.05 && abs(rounded - correctPrice) > 0.009) {
                candidates.add(rounded)
            }
        }

        // Pick 3 unique distractors
        val distractorList = candidates
            .filter { abs(it - correctPrice) > 0.009 && it > 0.0 }
            .shuffled()
            .take(3)
            .toMutableList()

        // Fallbacks if not enough
        var fallbackOffset = step
        while (distractorList.size < 3) {
            val altHigh = roundToSensible(correctPrice + fallbackOffset, hasDecimals)
            val altLow = roundToSensible(max(0.1, correctPrice - fallbackOffset), hasDecimals)
            if (!distractorList.contains(altHigh) && abs(altHigh - correctPrice) > 0.009) {
                distractorList.add(altHigh)
            }
            if (distractorList.size < 3 && !distractorList.contains(altLow) && abs(altLow - correctPrice) > 0.009) {
                distractorList.add(altLow)
            }
            fallbackOffset += step
        }

        val allFour = (distractorList.take(3) + correctPrice).shuffled()
        return allFour
    }

    private fun roundToSensible(value: Double, preserveDecimals: Boolean): Double {
        return if (preserveDecimals || value < 10.0) {
            // Round to 2 decimal places or nice halves/quarters
            (value * 100.0).roundToInt() / 100.0
        } else {
            // If whole price, round to whole integer or half
            (value * 2.0).roundToInt() / 2.0
        }
    }
}
