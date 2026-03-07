package com.thingspeak.monitor.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingspeak.monitor.R
import com.thingspeak.monitor.feature.channel.data.local.AlertRuleEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertRulesScreen(
    channelId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlertRulesViewModel = hiltViewModel()
) {
    LaunchedEffect(channelId) {
        viewModel.loadChannel(channelId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AlertRuleEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alert_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chart_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.alert_add_rule)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.rules.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alert_no_rules), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.rules) { rule ->
                    val fieldName = uiState.channel?.fieldNames?.get(rule.fieldNumber) ?: "Field ${rule.fieldNumber}"
                    AlertRuleItem(
                        rule = rule,
                        fieldName = fieldName,
                        onEditClick = { editingRule = rule },
                        onDeleteClick = { viewModel.deleteRule(rule) },
                        onToggleEnabled = { viewModel.toggleRule(rule, it) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        val fields = uiState.channel?.fieldNames ?: emptyMap()
        AddRuleDialog(
            fields = fields,
            onDismiss = { showAddDialog = false },
            onConfirm = { fieldNumber, condition, threshold ->
                viewModel.addRule(channelId, fieldNumber, condition, threshold)
                showAddDialog = false
            }
        )
    }

    // Edit dialog
    editingRule?.let { rule ->
        val fields = uiState.channel?.fieldNames ?: emptyMap()
        EditRuleDialog(
            rule = rule,
            fields = fields,
            onDismiss = { editingRule = null },
            onConfirm = { newFieldNumber, newCondition, newThreshold ->
                viewModel.updateRule(rule, newFieldNumber, newCondition, newThreshold)
                editingRule = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertRuleItem(
    rule: AlertRuleEntity,
    fieldName: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = fieldName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${rule.condition.replace("_", " ")} ${rule.thresholdValue}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggleEnabled
            )
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dialog_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    fields: Map<Int, String>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Double) -> Unit
) {
    var fieldNumber by remember { mutableStateOf(fields.keys.firstOrNull() ?: 1) }
    var condition by remember { mutableStateOf("GREATER_THAN") }
    var threshold by remember { mutableStateOf("") }

    var expandedField by remember { mutableStateOf(false) }
    var expandedCondition by remember { mutableStateOf(false) }

    val conditions = listOf("GREATER_THAN" to "Greater Than", "LESS_THAN" to "Less Than")
    val selectedConditionLabel = conditions.find { it.first == condition }?.second ?: "Greater Than"
    val selectedFieldLabel = fields[fieldNumber] ?: "Field $fieldNumber"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alert_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                ExposedDropdownMenuBox(
                    expanded = expandedField,
                    onExpandedChange = { expandedField = !expandedField }
                ) {
                    OutlinedTextField(
                        value = selectedFieldLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.alert_select_field)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedField) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedField,
                        onDismissRequest = { expandedField = false }
                    ) {
                        fields.forEach { (num, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    fieldNumber = num
                                    expandedField = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedCondition,
                    onExpandedChange = { expandedCondition = !expandedCondition }
                ) {
                    OutlinedTextField(
                        value = selectedConditionLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.alert_condition)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCondition) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCondition,
                        onDismissRequest = { expandedCondition = false }
                    ) {
                        conditions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    condition = key
                                    expandedCondition = false
                                }
                            )
                        }
                    }
                }

                val isError = threshold.isNotEmpty() && threshold.toDoubleOrNull() == null
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.alert_threshold)) },
                    isError = isError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val isThresholdValid = threshold.isNotEmpty() && threshold.toDoubleOrNull() != null
            TextButton(
                onClick = {
                    threshold.toDoubleOrNull()?.let { onConfirm(fieldNumber, condition, it) }
                },
                enabled = isThresholdValid
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRuleDialog(
    rule: AlertRuleEntity,
    fields: Map<Int, String>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Double) -> Unit
) {
    var fieldNumber by remember { mutableStateOf(rule.fieldNumber) }
    var condition by remember { mutableStateOf(rule.condition) }
    var threshold by remember { mutableStateOf(rule.thresholdValue.toString()) }

    var expandedField by remember { mutableStateOf(false) }
    var expandedCondition by remember { mutableStateOf(false) }

    val conditions = listOf("GREATER_THAN" to "Greater Than", "LESS_THAN" to "Less Than")
    val selectedConditionLabel = conditions.find { it.first == condition }?.second ?: "Greater Than"
    val selectedFieldLabel = fields[fieldNumber] ?: "Field $fieldNumber"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                ExposedDropdownMenuBox(
                    expanded = expandedField,
                    onExpandedChange = { expandedField = !expandedField }
                ) {
                    OutlinedTextField(
                        value = selectedFieldLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.alert_select_field)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedField) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedField,
                        onDismissRequest = { expandedField = false }
                    ) {
                        fields.forEach { (num, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    fieldNumber = num
                                    expandedField = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedCondition,
                    onExpandedChange = { expandedCondition = !expandedCondition }
                ) {
                    OutlinedTextField(
                        value = selectedConditionLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.alert_condition)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCondition) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCondition,
                        onDismissRequest = { expandedCondition = false }
                    ) {
                        conditions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    condition = key
                                    expandedCondition = false
                                }
                            )
                        }
                    }
                }

                val isError = threshold.isNotEmpty() && threshold.toDoubleOrNull() == null
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text(stringResource(R.string.alert_threshold)) },
                    isError = isError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val isThresholdValid = threshold.isNotEmpty() && threshold.toDoubleOrNull() != null
            TextButton(
                onClick = {
                    threshold.toDoubleOrNull()?.let { onConfirm(fieldNumber, condition, it) }
                },
                enabled = isThresholdValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun AlertRulesScreenPreview() {
    com.thingspeak.monitor.core.designsystem.theme.ThingSpeakMonitorTheme {
        AlertRulesScreen(
            channelId = 12345,
            onNavigateBack = {}
        )
    }
}
