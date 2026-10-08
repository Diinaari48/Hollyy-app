package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Item
import com.example.util.DistractorGenerator

@Composable
fun ItemFormDialog(
    initialItem: Item? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, systemName: String?, cost: Double, wholesalePrice: Double, price: Double) -> Unit
) {
    val isEdit = initialItem != null
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var systemName by remember { mutableStateOf(initialItem?.systemName ?: "") }
    var priceText by remember { mutableStateOf(initialItem?.let { DistractorGenerator.formatPrice(it.price) } ?: "") }
    var wholesaleText by remember { mutableStateOf(initialItem?.let { if (it.wholesalePrice > 0.0) DistractorGenerator.formatPrice(it.wholesalePrice) else "" } ?: "") }
    var costText by remember { mutableStateOf(initialItem?.let { if (it.cost > 0.0) DistractorGenerator.formatPrice(it.cost) else "" } ?: "") }

    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) "Wax Ka Beddel Dawada" else "Ku Dar Dawo Cusub",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isEdit) {
                    Text(
                        text = "ℹ️ Taariikhdaada imtixaannada iyo guulaha xiriirka ah waa la ilaalinayaa (lama tirtirayo).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Magaca Dawada (tusaale: Amoxicillin 500mg) *") },
                    isError = nameError,
                    supportingText = if (nameError) { { Text("Magaca dawada waa lagama maarmaan") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // System Name
                OutlinedTextField(
                    value = systemName,
                    onValueChange = { systemName = it },
                    label = { Text("Magaca System-ka (ikhtiyaari, tusaale: AMOX-500)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_system_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Official Selling Price ("Qiimaha") - THE answer in quizzes
                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        priceError = (it.toDoubleOrNull() ?: 0.0) <= 0.0
                    },
                    label = { Text("Qiimaha Iibka Rasmiga ah (Qiimaha) *") },
                    placeholder = { Text("1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = priceError,
                    supportingText = if (priceError) { { Text("Geli qiimo sax ah oo ka weyn 0") } } else { { Text("Kani waa qiimaha lagu waydiin doono su'aalaha") } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_price_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Wholesale Price ("Macamil")
                OutlinedTextField(
                    value = wholesaleText,
                    onValueChange = { wholesaleText = it },
                    label = { Text("Qiimaha Macamilka (Jumlad / Macamil)") },
                    placeholder = { Text("1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_wholesale_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Cost
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Kharashka Dawada (Cost)") },
                    placeholder = { Text("0.90") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_item_cost_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priceText.trim().replace(",", ".").toDoubleOrNull()
                    val validName = name.isNotBlank()
                    val validPrice = (p != null && p > 0.0)

                    if (!validName) nameError = true
                    if (!validPrice) priceError = true

                    if (validName && validPrice && p != null) {
                        val cost = costText.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
                        val wholesale = wholesaleText.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
                        onSave(name, systemName, cost, wholesale, p)
                    }
                },
                modifier = Modifier.testTag("dialog_save_button")
            ) {
                Text(if (isEdit) "Keydi Isbeddelka" else "Ku Dar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Ka Noqo")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ma hubtaa inaad tirtirto?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Ma hubtaa inaad tirtirto dawada '$itemName'? Tallaabadan dib looma noqon karo.")
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text("Haa, Tirtir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Jooji")
            }
        }
    )
}
