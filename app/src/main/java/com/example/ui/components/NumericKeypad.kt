package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonModifier = Modifier
        .height(60.dp)
        .padding(4.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Rows: 1 2 3
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("1", "2", "3").forEach { digit ->
                KeypadDigitButton(
                    digit = digit,
                    modifier = buttonModifier.weight(1f),
                    onClick = { onDigitClick(digit) }
                )
            }
        }

        // Rows: 4 5 6
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("4", "5", "6").forEach { digit ->
                KeypadDigitButton(
                    digit = digit,
                    modifier = buttonModifier.weight(1f),
                    onClick = { onDigitClick(digit) }
                )
            }
        }

        // Rows: 7 8 9
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("7", "8", "9").forEach { digit ->
                KeypadDigitButton(
                    digit = digit,
                    modifier = buttonModifier.weight(1f),
                    onClick = { onDigitClick(digit) }
                )
            }
        }

        // Row: . 0 Backspace
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            KeypadDigitButton(
                digit = ".",
                modifier = buttonModifier.weight(1f),
                onClick = { onDigitClick(".") }
            )
            KeypadDigitButton(
                digit = "0",
                modifier = buttonModifier.weight(1f),
                onClick = { onDigitClick("0") }
            )
            FilledTonalButton(
                onClick = onBackspace,
                shape = RoundedCornerShape(14.dp),
                modifier = buttonModifier
                    .weight(1f)
                    .testTag("keypad_backspace")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Tirtir hal lambar"
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Action Row: Tirtir Dhammaan (Clear) & Xaqiiji (Submit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("keypad_clear_btn")
            ) {
                Text("Nadiifi", fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(2f)
                    .height(52.dp)
                    .testTag("keypad_submit_btn")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Xaqiiji Qiimaha", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.testTag("keypad_digit_$digit")
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
