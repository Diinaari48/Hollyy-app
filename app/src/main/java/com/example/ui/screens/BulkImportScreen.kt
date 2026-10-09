package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningGold
import com.example.util.DistractorGenerator
import com.example.util.SampleData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var rawText by remember { mutableStateOf("") }
    val previewResult by viewModel.bulkImportPreview.collectAsState()
    val successMessage by viewModel.bulkImportSuccessMessage.collectAsState()

    BackHandler {
        viewModel.clearBulkPreview()
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soo Geli Tiro Badan (CSV)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.clearBulkPreview()
                            viewModel.navigateBack()
                        },
                        modifier = Modifier.testTag("bulk_import_back_btn")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Ka noqo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Qaabka tiirarka (Columns format):",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Item, cost, Magaca systemka, qiimaha",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Safafka aan qiimo lahayn si toos ah ayaa loo dhaafayaa.\n• Waxaad isticmaali kartaa comma (,), tab ama semicolon (;).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            // Quick Template Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = { rawText = SampleData.SAMPLE_CSV },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("bulk_load_sample_csv_btn")
                ) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Geli Tusaale CSV")
                }

                if (rawText.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            rawText = ""
                            viewModel.clearBulkPreview()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tirtir")
                    }
                }
            }

            // Text Area Input
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                },
                label = { Text("Dheji qoraalka ama CSV halkan") },
                placeholder = { Text("Item,cost,Magaca systemka,qiimaha\nParacetamol 500mg,0.50,PARA-500,1.00") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("bulk_csv_text_input"),
                maxLines = 15,
                shape = RoundedCornerShape(14.dp)
            )

            // Preview Action Button
            Button(
                onClick = { viewModel.previewBulkCsv(rawText) },
                enabled = rawText.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("bulk_preview_button")
            ) {
                Text("Fiiri Hordhaca (Preview)", fontWeight = FontWeight.Bold)
            }

            // Success message
            if (successMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = successMessage!!,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Preview Table Section
            if (previewResult != null) {
                val preview = previewResult!!

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Hordhaca Dawooyinka La Helay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // Stats Summary Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "La Aqbalay", fontSize = 11.sp, color = SuccessGreen)
                                    Text(
                                        text = "${preview.validItems.size} Dawo",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (preview.skippedRows.isNotEmpty()) ErrorRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(text = "La Dhaafay", fontSize = 11.sp, color = if (preview.skippedRows.isNotEmpty()) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = "${preview.skippedRows.size} Saf",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (preview.skippedRows.isNotEmpty()) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // List of valid items preview
                        if (preview.validItems.isNotEmpty()) {
                            Text(
                                text = "Tusaalooyinka la gelin doono:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            preview.validItems.take(5).forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (!item.systemName.isNullOrBlank()) {
                                                Text(text = "Sys: ${item.systemName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(
                                            text = "Qiimaha: $${DistractorGenerator.formatPrice(item.price)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            if (preview.validItems.size > 5) {
                                Text(
                                    text = "+ Iyo ${preview.validItems.size - 5} dawo oo kale...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Skipped rows warnings
                        if (preview.skippedRows.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Safafka la dhaafay (Sabab):",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ErrorRed
                            )
                            preview.skippedRows.take(3).forEach { skipped ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ErrorRed.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Saf ${skipped.lineNumber}: ${skipped.reason}",
                                            fontSize = 11.sp,
                                            color = ErrorRed
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Commit Save Button
                        Button(
                            onClick = { viewModel.commitBulkImport() },
                            enabled = preview.validItems.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("bulk_commit_save_button")
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keydi Dhammaan (${preview.validItems.size} Dawo)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
