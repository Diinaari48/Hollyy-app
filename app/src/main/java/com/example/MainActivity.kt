package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.navigation.NavTab
import com.example.ui.navigation.Screen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BulkImportScreen
import com.example.ui.screens.ExamScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.QuestionScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SkippedScreen
import com.example.ui.theme.QiimoQuizTheme
import com.example.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(this)
        handleIntent(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val fontScale by viewModel.fontScale.collectAsState()
            val currentUser by viewModel.currentUser.collectAsState()

            QiimoQuizTheme(
                themeMode = themeMode,
                fontScale = fontScale
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (currentUser == null) {
                        AuthScreen(viewModel = viewModel)
                    } else {
                        AppNavigation(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val openExam = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_EXAM, false)
        if (openExam) {
            viewModel.selectTab(NavTab.EXAM)
            viewModel.startExam(questionCount = 20, timeLimitSeconds = 15)
            return
        }

        val openQuiz = intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_QUIZ, false)
        if (openQuiz) {
            val itemId = intent.getStringExtra(NotificationHelper.EXTRA_ITEM_ID)
            viewModel.navigateTo(Screen.Quiz(mode = Screen.Quiz.MODE_DRILL, initialItemId = itemId))
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val currentScreen = viewModel.currentScreen
    val selectedTab by viewModel.selectedTab.collectAsState()

    // Handle sub-screens stack
    when (currentScreen) {
        is Screen.BulkImport -> {
            BulkImportScreen(viewModel = viewModel)
            return
        }
        is Screen.Search -> {
            SearchScreen(viewModel = viewModel)
            return
        }
        is Screen.Quiz -> {
            QuestionScreen(viewModel = viewModel)
            return
        }
        is Screen.Skipped -> {
            SkippedScreen(viewModel = viewModel)
            return
        }
        else -> {
            // Main 5-tab screen with persistent bottom navigation bar
        }
    }

    // Back button behavior on main screens: if not on Home, pressing back returns to Home
    BackHandler(enabled = selectedTab != NavTab.HOME) {
        viewModel.selectTab(NavTab.HOME)
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    viewModel.selectTab(tab)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                NavTab.ITEMS -> ItemsScreen(viewModel = viewModel)
                NavTab.EXAM -> ExamScreen(viewModel = viewModel)
                NavTab.HOME -> HomeScreen(viewModel = viewModel)
                NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                NavTab.REPORT -> ReportScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppBottomNavigationBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    // 5 Icon+Label tabs in EXACT left-to-right order:
    // 1. Shayada (Items)
    // 2. Imtixaan (Exam)
    // 3. Home (CENTER & Default)
    // 4. Settings (Gear)
    // 5. Report (Bar chart)
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        // Tab 1: Shayada (Items)
        NavigationBarItem(
            selected = selectedTab == NavTab.ITEMS,
            onClick = { onTabSelected(NavTab.ITEMS) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Shayada"
                )
            },
            label = {
                Text(
                    text = "Shayada",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == NavTab.ITEMS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_tab_items")
        )

        // Tab 2: Imtixaan (Exam)
        NavigationBarItem(
            selected = selectedTab == NavTab.EXAM,
            onClick = { onTabSelected(NavTab.EXAM) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = "Imtixaan"
                )
            },
            label = {
                Text(
                    text = "Imtixaan",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == NavTab.EXAM) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_tab_exam")
        )

        // Tab 3: Home (CENTER & Default on launch)
        NavigationBarItem(
            selected = selectedTab == NavTab.HOME,
            onClick = { onTabSelected(NavTab.HOME) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home"
                )
            },
            label = {
                Text(
                    text = "Home",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == NavTab.HOME) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_tab_home")
        )

        // Tab 4: Settings (Gear icon)
        NavigationBarItem(
            selected = selectedTab == NavTab.SETTINGS,
            onClick = { onTabSelected(NavTab.SETTINGS) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = {
                Text(
                    text = "Settings",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == NavTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_tab_settings")
        )

        // Tab 5: Report (Bar chart icon)
        NavigationBarItem(
            selected = selectedTab == NavTab.REPORT,
            onClick = { onTabSelected(NavTab.REPORT) },
            icon = {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Report"
                )
            },
            label = {
                Text(
                    text = "Report",
                    fontSize = 11.sp,
                    fontWeight = if (selectedTab == NavTab.REPORT) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.testTag("nav_tab_report")
        )
    }
}
