package com.thingspeak.monitor.feature.chart.presentation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (Instant, Instant) -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        onDateRangeSelected(
                            Instant.ofEpochMilli(start),
                            Instant.ofEpochMilli(end).plusSeconds(86399) // Till end of day
                        )
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null && 
                          dateRangePickerState.selectedEndDateMillis != null
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select date range",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            },
            showModeToggle = false,
            modifier = Modifier.weight(1f)
        )
    }
}
