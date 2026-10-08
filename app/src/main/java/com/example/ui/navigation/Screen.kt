package com.example.ui.navigation

enum class NavTab(val titleSo: String) {
    ITEMS("Shayada"),
    EXAM("Imtixaan"),
    HOME("Home"),
    SETTINGS("Settings"),
    REPORT("Report")
}

sealed class Screen(val route: String, val titleSo: String) {
    object Main : Screen("main", "Bogga Hore")
    object Home : Screen("home", "Home")
    object Items : Screen("items", "Shayada")
    object BulkImport : Screen("bulk_import", "Soo Geli Badan")
    object Search : Screen("search", "Raadi Qiimaha")
    object Exam : Screen("exam", "Imtixaan")
    data class Quiz(val mode: String = MODE_DRILL, val initialItemId: String? = null) : Screen("quiz", "Tababarka Qiimaha") {
        companion object {
            const val MODE_DRILL = "DRILL"
            const val MODE_EXAM = "EXAM"
        }
    }
    object Report : Screen("report", "Warbixinta")
    object Skipped : Screen("skipped", "La Dhaafay")
    object Settings : Screen("settings", "Hagaajinta")
}

