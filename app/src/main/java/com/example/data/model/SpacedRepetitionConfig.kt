package com.example.data.model

enum class SpeedCategory {
    FAST,   // < 4s: "Si fiican u yaqaanaa"
    NORMAL, // 4s - 8s: Normal
    SLOW    // > 8s: "Sax gaabis ah"
}

data class SpacedRepetitionConfig(
    val fastThresholdSeconds: Int = 4,
    val slowThresholdSeconds: Int = 8
) {
    fun categorize(responseTimeMs: Long): SpeedCategory {
        val seconds = responseTimeMs / 1000.0
        return when {
            seconds < fastThresholdSeconds -> SpeedCategory.FAST
            seconds > slowThresholdSeconds -> SpeedCategory.SLOW
            else -> SpeedCategory.NORMAL
        }
    }

    /**
     * Calculates the interval in milliseconds until next review based on streak and response speed.
     * Minutes -> Hours -> Days progression.
     */
    fun calculateNextDueIntervalMs(streak: Int, speed: SpeedCategory): Long {
        if (speed == SpeedCategory.SLOW) {
            // "Sax gaabis ah": returns soon (3 minutes)
            return 3 * 60 * 1000L
        }

        // Base intervals for streaks (in minutes):
        // Streak 1: ~15 mins
        // Streak 2: ~2 hours (120 mins)
        // Streak 3: ~12 hours (720 mins)
        // Streak 4: ~2 days (2880 mins)
        // Streak 5+: ~7 days (10080 mins)
        val baseMinutes = when (streak) {
            0, 1 -> 15L
            2 -> 120L
            3 -> 720L
            4 -> 2880L
            else -> 10080L
        }

        val multiplier = if (speed == SpeedCategory.FAST) 2.2 else 1.0
        val finalMinutes = (baseMinutes * multiplier).toLong()
        return finalMinutes * 60 * 1000L
    }
}
