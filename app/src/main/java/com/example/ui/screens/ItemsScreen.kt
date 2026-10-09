package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.data.model.ItemStats
import com.example.ui.MainViewModel
import com.example.ui.components.DeleteConfirmationDialog
import com.example.ui.components.ItemCard
import com.example.ui.components.ItemFormDialog
import com.example.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ItemsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.allItems.collectAsState()
    val isItemsLoading by viewModel.isItemsLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadItems()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL | LEARNED | LEARNING | NEW

    var itemToEdit by remember { mutableStateOf<Item?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Item?>(null) }

    // Temporary Debug Dialog State (activated by long-pressing the title)
    var showDebugDialog by remember { mutableStateOf(false) }
    var debugInfoText by remember { mutableStateOf("") }
    var isFetchingDebug by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val filteredItems = items.filter { itemWithStats ->
        val matchesQuery = if (searchQuery.isBlank()) true else {
            val q = searchQuery.trim().lowercase()
            itemWithStats.item.name.lowercase().contains(q) ||
                    (itemWithStats.item.systemName?.lowercase()?.contains(q) == true)
        }
        val matchesFilter = when (selectedFilter) {
            "LEARNED" -> itemWithStats.stats?.status == ItemStats.STATUS_LEARNED
            "LEARNING" -> itemWithStats.stats?.status == ItemStats.STATUS_LEARNING
            "NEW" -> itemWithStats.stats?.status == ItemStats.STATUS_NEW || itemWithStats.stats == null
            else -> true
        }
        matchesQuery && matchesFilter
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .testTag("items_header_title_box")
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    showDebugDialog = true
                                    isFetchingDebug = true
                                    coroutineScope.launch {
                                        debugInfoText = viewModel.getDebugInfo()
                                        isFetchingDebug = false
                                    }
                                }
                            )
                    ) {
                        Text(
                            text = "Shayada Farmashiyaha (${items.size})",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("items_header_title_text")
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.BulkImport) },
                        modifier = Modifier.testTag("items_bulk_import_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Soo geli CSV/Tiro badan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("items_fab_add")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Ku dar dawo cusub")
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isItemsLoading,
            onRefresh = { viewModel.loadItems() },
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("items_pull_to_refresh_box")
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Fixed Search bar at the top with instant filtering & clear (x) button
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Raadi magaca dawada ama qiimaha...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Tirtir raadinta")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("items_search_input")
                )

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("Dhammaan (${items.size})", fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_chip_all")
                    )
                    FilterChip(
                        selected = selectedFilter == "LEARNED",
                        onClick = { selectedFilter = "LEARNED" },
                        label = { Text("La Xafiday", fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_chip_learned")
                    )
                    FilterChip(
                        selected = selectedFilter == "LEARNING",
                        onClick = { selectedFilter = "LEARNING" },
                        label = { Text("Baranaya", fontSize = 12.sp) },
                        modifier = Modifier.testTag("filter_chip_learning")
                    )
                }

                // List of Items or Empty State
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Medication,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) "Wax dawo ah lagama helin raadintaada" else "Ma jiraan wax dawooyin ah weli",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) "Hubi inaad si sax ah u qortay magaca dawada." else "Ku dar dawooyinka farmashiyahaaga si aad u bilowdo xifdinta qiimaha.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showAddDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("empty_add_item_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ku dar shay")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.navigateTo(Screen.BulkImport) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Soo geli CSV")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredItems, key = { it.item.id }) { itemWithStats ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { itemToEdit = itemWithStats.item },
                                        onLongClick = { itemToDelete = itemWithStats.item }
                                    )
                            ) {
                                ItemCard(
                                    itemWithStats = itemWithStats,
                                    onEdit = { itemToEdit = itemWithStats.item },
                                    onDelete = { itemToDelete = itemWithStats.item },
                                    onQuizItem = {
                                        viewModel.navigateTo(
                                            Screen.Quiz(
                                                mode = Screen.Quiz.MODE_DRILL,
                                                initialItemId = itemWithStats.item.id
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Temporary Debug Dialog
    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = {
                Text("Items Debug Info", fontWeight = FontWeight.Bold)
            },
            text = {
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isFetchingDebug) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Soo qaadaya macluumaadka...", fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = debugInfoText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDebugDialog = false }) {
                    Text("Xir")
                }
            }
        )
    }

    // Add Item Dialog
    if (showAddDialog) {
        ItemFormDialog(
            initialItem = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, systemName, cost, price ->
                viewModel.addItem(
                    name = name,
                    systemName = systemName,
                    cost = cost,
                    price = price
                )
                showAddDialog = false
            }
        )
    }

    // Edit Item Dialog / Bottom Sheet Modal
    itemToEdit?.let { item ->
        ItemFormDialog(
            initialItem = item,
            onDismiss = { itemToEdit = null },
            onSave = { name, systemName, cost, price ->
                viewModel.updateItem(
                    item.copy(
                        name = name,
                        systemName = systemName,
                        cost = cost,
                        price = price
                    )
                )
                itemToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            itemName = item.name,
            onDismiss = { itemToDelete = null },
            onConfirmDelete = {
                viewModel.deleteItem(item)
                itemToDelete = null
            }
        )
    }
}
