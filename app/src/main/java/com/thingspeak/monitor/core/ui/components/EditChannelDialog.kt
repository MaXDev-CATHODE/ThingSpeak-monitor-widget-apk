package com.thingspeak.monitor.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thingspeak.monitor.R
import com.thingspeak.monitor.core.datastore.ChannelPreferences
import com.thingspeak.monitor.core.datastore.SavedChannel
import com.thingspeak.monitor.feature.channel.domain.model.Channel

/**
 * Dialog for editing an existing ThingSpeak channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChannelDialog(
    channel: SavedChannel,
    onDismiss: () -> Unit,
    onConfirm: (
        id: Long, 
        name: String, 
        apiKey: String?, 
        chartType: String, 
        chartResults: Int,
        chartColor: String,
        chartBgColor: String,
        fieldColors: Map<Int, String>,
        fieldYMin: Map<Int, Double>,
        fieldYMax: Map<Int, Double>,
        textColor: String,
        visibleFields: Set<Int>,
        widgetBgColorHex: String
    ) -> Unit
) {
    val channelId = channel.id.toString()
    var channelName by remember { mutableStateOf(channel.name) }
    var apiKey by remember { mutableStateOf(channel.apiKey ?: "") }
    var chartType by remember { mutableStateOf(channel.chartType) }
    var chartResultsString by remember { mutableStateOf(channel.chartResults.toString()) }
    
    var chartColor by remember { mutableStateOf(channel.chartColor ?: "#2196F3") }
    var chartBgColor by remember { mutableStateOf(channel.chartBgColor ?: "#FFFFFF") }
    var textColor by remember { mutableStateOf(channel.textColor ?: "#000000") }
    var widgetBgColorHex by remember { mutableStateOf(channel.widgetBgColorHex ?: "#FFFFFF") }
    
    // Per-field states
    var visibleFields by remember { mutableStateOf(channel.widgetVisibleFields ?: (1..8).toSet()) }
    var fieldColors by remember { mutableStateOf(channel.fieldColors) }
    var fieldYMinStrings by remember { 
        mutableStateOf(channel.fieldYMin.mapValues { it.value.toString() }) 
    }
    var fieldYMaxStrings by remember { 
        mutableStateOf(channel.fieldYMax.mapValues { it.value.toString() }) 
    }

    val chartTypes = listOf("line", "bar", "column", "spline", "step")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_edit_channel_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = channelId,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.dialog_channel_id)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text(stringResource(R.string.dialog_channel_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.dialog_api_key)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("General Chart Options", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                // Chart Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = chartType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Chart Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        chartTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    chartType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Chart Results
                OutlinedTextField(
                    value = chartResultsString,
                    onValueChange = { if (it.all { char -> char.isDigit() }) chartResultsString = it },
                    label = { Text("Results Count (1-1000)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = chartBgColor,
                    onValueChange = { chartBgColor = it },
                    label = { Text("Chart Background (Hex)") },
                    placeholder = { Text("#FFFFFF") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Fields Configuration", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Per-field configuration
                for (i in 1..8) {
                    val fieldName = channel.fieldNames[i]
                    if (fieldName != null || i == 1) { // Always show at least field 1
                        val isVisible = visibleFields.contains(i)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = isVisible,
                                        onCheckedChange = { checked ->
                                            visibleFields = if (checked) visibleFields + i else visibleFields - i
                                        }
                                    )
                                    Text(
                                        text = fieldName ?: "Field $i",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                if (isVisible) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = fieldColors[i] ?: chartColor,
                                        onValueChange = { newColor ->
                                            fieldColors = fieldColors + (i to newColor)
                                        },
                                        label = { Text("Color (Hex)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = fieldYMinStrings[i] ?: "",
                                            onValueChange = { 
                                                fieldYMinStrings = fieldYMinStrings + (i to it)
                                            },
                                            label = { Text("Y Min") },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        OutlinedTextField(
                                            value = fieldYMaxStrings[i] ?: "",
                                            onValueChange = { 
                                                fieldYMaxStrings = fieldYMaxStrings + (i to it)
                                            },
                                            label = { Text("Y Max") },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Widget Styles", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = textColor,
                    onValueChange = { textColor = it },
                    label = { Text("Text Color (Hex)") },
                    placeholder = { Text("#000000") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = widgetBgColorHex,
                    onValueChange = { widgetBgColorHex = it },
                    label = { Text("Widget Background (Hex)") },
                    placeholder = { Text("#FFFFFF") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resultsAsInt = chartResultsString.toIntOrNull()?.coerceIn(1..1000) ?: 60
                    val fieldYMin = fieldYMinStrings.mapValues { it.value.toDoubleOrNull() }.filterValues { it != null } as Map<Int, Double>
                    val fieldYMax = fieldYMaxStrings.mapValues { it.value.toDoubleOrNull() }.filterValues { it != null } as Map<Int, Double>
                    
                    onConfirm(
                        channel.id, 
                        channelName, 
                        apiKey.ifBlank { null }, 
                        chartType, 
                        resultsAsInt,
                        chartColor,
                        chartBgColor,
                        fieldColors,
                        fieldYMin,
                        fieldYMax,
                        textColor,
                        visibleFields,
                        widgetBgColorHex
                    )
                }
            ) {
                Text(stringResource(R.string.alert_save_channel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
