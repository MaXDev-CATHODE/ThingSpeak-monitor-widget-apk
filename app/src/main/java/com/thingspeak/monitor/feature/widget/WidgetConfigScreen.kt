package com.thingspeak.monitor.feature.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import com.thingspeak.monitor.R

/**
 * Configuration screen for the ThingSpeak widget.
 *
 * Allows the user to enter a Channel ID (required), optional Read API Key,
 * and optional friendly channel name. Validates that Channel ID is a positive integer.
 *
 * @param onSave Callback invoked when the user presses Save with valid input.
 */
@Composable
fun WidgetConfigScreen(
    initialChannelId: Long? = null,
    initialApiKey: String? = null,
    initialChannelName: String? = null,
    initialBgColorHex: String? = null,
    initialTextColorHex: String? = null,
    initialTransparency: Float? = null,
    initialFontSize: Int? = null,
    initialVisibleFields: Set<Int>? = null,
    initialChartField: Int? = null,
    initialIsGlass: Boolean? = null,
    initialBgColorMode: String? = null,
    initialChartResults: Int? = 60,
    isSaving: Boolean = false,
    isGridMode: Boolean = false,
    availableChannels: List<com.thingspeak.monitor.core.datastore.SavedChannel> = emptyList(),
    onRefreshRequest: ((Long, String) -> Unit)? = null,
    initialAlertRules: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule> = emptyList(),
    onSave: (channelId: Long, apiKey: String, channelName: String, bgColor: String?, textColor: String?, transparency: Float, fontSize: Int, visibleFields: Set<Int>, chartField: Int, isGlass: Boolean, bgColorMode: String?, chartResults: Int, alertRules: List<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>) -> Unit,
) {
    var channelIdText by remember { mutableStateOf(initialChannelId?.toString() ?: "") }
    var apiKey by remember { mutableStateOf(initialApiKey ?: "") }
    var channelName by remember { mutableStateOf(initialChannelName ?: "") }
    var channelIdError by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    
    var visibleFields by remember(initialVisibleFields) { 
        mutableStateOf(initialVisibleFields ?: if (isGridMode) (1..4).toSet() else (1..8).toSet()) 
    }
    
    LaunchedEffect(isGridMode) {
        if (isGridMode) {
            val filtered = visibleFields.filter { it in 1..4 }.toSet()
            if (filtered.size != visibleFields.size) {
                visibleFields = if (filtered.isEmpty()) (1..4).toSet() else filtered
            }
        }
    }
    var chartField by remember { mutableIntStateOf(initialChartField ?: 1) }

    var selectedColorHex by remember { mutableStateOf<String?>(initialBgColorHex ?: "#FFFFFF") }
    var selectedTextColorHex by remember { mutableStateOf<String?>(initialTextColorHex) }
    var transparency by remember { mutableStateOf(initialTransparency ?: 1.0f) }
    var fontSize by remember { mutableStateOf(initialFontSize?.toFloat() ?: 12f) }
    var isGlass by remember { mutableStateOf(initialIsGlass ?: false) }
    var bgColorMode by remember { mutableStateOf(initialBgColorMode ?: WidgetPrefsKeys.COLOR_MODE_CUSTOM) }
    var chartResults by remember { mutableStateOf(initialChartResults?.toFloat() ?: 60f) }

    // Map of fieldNumber -> (Min string, Max string)
    var fieldAlerts by remember(initialAlertRules, initialVisibleFields) {
        val rulesMap = mutableMapOf<Int, Pair<String, String>>()
        initialVisibleFields?.forEach { fieldNum ->
            val minVal = initialAlertRules.find { it.fieldNumber == fieldNum && it.condition == "LESS_THAN" }?.thresholdValue?.toString() ?: ""
            val maxVal = initialAlertRules.find { it.fieldNumber == fieldNum && it.condition == "GREATER_THAN" }?.thresholdValue?.toString() ?: ""
            rulesMap[fieldNum] = minVal to maxVal
        }
        mutableStateOf(rulesMap.toMap())
    }

    var showVisualOptions by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(availableChannels.isEmpty()) }

    val channelIdValue = channelIdText.toLongOrNull()
    val isChannelIdValid = channelIdValue != null && channelIdValue > 0
    val isApiKeyValid = apiKey.isBlank() || apiKey.length >= 16 
    val isFieldsValid = if (isGridMode) visibleFields.size in 1..4 else visibleFields.isNotEmpty()
    val isValid = isChannelIdValid && isApiKeyValid && isFieldsValid

    val textColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
    val currentFieldNames = availableChannels.find { it.id.toString() == channelIdText }?.fieldNames ?: emptyMap()

    val bgColors = listOf(
        "#FFFFFF" to Color.White,
        "#F44336" to Color(0xFFF44336),
        "#2196F3" to Color(0xFF2196F3),
        "#4CAF50" to Color(0xFF4CAF50),
        "#FF9800" to Color(0xFFFF9800),
        "#9C27B0" to Color(0xFF9C27B0),
        "#212121" to Color(0xFF212121),
    )
    
    val textColors = listOf(
        null to null,
        "#FFFFFF" to Color.White,
        "#000000" to Color.Black,
        "#F44336" to Color(0xFFF44336),
        "#2196F3" to Color(0xFF2196F3),
        "#4CAF50" to Color(0xFF4CAF50),
        "#FF9800" to Color(0xFFFF9800),
        "#FFD700" to Color(0xFFFFD700),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.widget_configure_title),
            style = MaterialTheme.typography.headlineSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )

        if (availableChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.widget_pick_saved),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableChannels.forEach { ch ->
                    val isSelected = channelIdText == ch.id.toString()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                channelIdText = ch.id.toString()
                                apiKey = ch.apiKey ?: ""
                                channelName = ch.name
                                selectedColorHex = ch.widgetBgColorHex
                                transparency = ch.widgetTransparency
                                fontSize = ch.widgetFontSize.toFloat()
                                visibleFields = ch.widgetVisibleFields?.let { if (isGridMode && it.size > 4) it.take(4).toSet() else it } 
                                    ?: if (isGridMode) (1..4).toSet() else (1..8).toSet()
                                isGlass = ch.isGlassmorphismEnabled
                                chartField = ch.chartField
                                if (ch.fieldNames.isEmpty()) {
                                    onRefreshRequest?.invoke(ch.id, ch.apiKey ?: "")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = if (isSelected) 
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                        else null
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.name.ifBlank { stringResource(R.string.widget_channel_default_name, ch.id) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = stringResource(R.string.widget_id_format, ch.id),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else textColor).copy(alpha = 0.7f)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = textColor.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showManualEntry = !showManualEntry }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.widget_manual_config),
                style = MaterialTheme.typography.titleSmall,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showManualEntry) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f)
            )
        }

        if (showManualEntry) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = channelIdText,
                onValueChange = { input ->
                    channelIdText = input.filter { it.isDigit() || it.isWhitespace() }.trim()
                    channelIdError = channelIdText.isNotEmpty() &&
                        (channelIdText.toLongOrNull()?.let { it <= 0 } ?: true)
                },
                label = { Text(stringResource(R.string.widget_channel_id_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = channelIdError,
                supportingText = if (channelIdError) {
                    { Text(stringResource(R.string.widget_channel_id_error)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text(stringResource(R.string.widget_api_key_label)) },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (apiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = channelName,
                onValueChange = { channelName = it },
                label = { Text(stringResource(R.string.widget_channel_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showVisualOptions = !showVisualOptions }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.widget_styling_options),
                style = MaterialTheme.typography.titleSmall,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showVisualOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.6f)
            )
        }

        if (showVisualOptions) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(stringResource(R.string.widget_bg_color), style = MaterialTheme.typography.bodySmall, color = textColor)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(bgColors.size) { index ->
                    val (hex, color) = bgColors[index]
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(color, CircleShape)
                            .border(
                                width = if (selectedColorHex == hex) 3.dp else 1.dp,
                                color = if (selectedColorHex == hex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = hex }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text("Text Color", style = MaterialTheme.typography.bodySmall, color = textColor)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(textColors.size) { index ->
                    val (hex, color) = textColors[index]
                    val isSelected = selectedTextColorHex == hex
                    if (hex == null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White, Color.Black)), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { selectedTextColorHex = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("A", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color!!, CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { selectedTextColorHex = hex }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.widget_transparency_label, (transparency * 100).toInt()), style = MaterialTheme.typography.bodySmall, color = textColor)
            Slider(
                value = transparency,
                onValueChange = { transparency = it },
                valueRange = 0.1f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.widget_font_size_label, fontSize.toInt()), style = MaterialTheme.typography.bodySmall, color = textColor)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 8f..20f,
                steps = 12,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = isGlass,
                    onCheckedChange = { isGlass = it }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.widget_glassmorphism), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Switch(
                    checked = bgColorMode == WidgetPrefsKeys.COLOR_MODE_SYSTEM,
                    onCheckedChange = { enabled ->
                        bgColorMode = if (enabled) WidgetPrefsKeys.COLOR_MODE_SYSTEM else WidgetPrefsKeys.COLOR_MODE_CUSTOM
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.widget_system_colors), style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        }
            
        Spacer(modifier = Modifier.height(16.dp))
        Text("Analysis Depth (Results): ${chartResults.toInt()}", style = MaterialTheme.typography.bodySmall, color = textColor)
        Slider(
            value = chartResults,
            onValueChange = { chartResults = it },
            valueRange = 1f..8000f,
            steps = 7,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(1, 10, 60, 100, 500, 1000, 2000, 4000, 8000).forEach { v ->
                Text(
                    text = if (v >= 1000) "${v / 1000}k" else "$v",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.4f),
                    fontSize = 9.sp
                )
            }
        }
        Text("Higher = more detail, lower = faster load.", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
        
        Spacer(modifier = Modifier.height(24.dp))

        if (showVisualOptions) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.widget_visible_fields), style = MaterialTheme.typography.titleMedium, color = textColor)
            if (isGridMode) {
                Text(
                    text = stringResource(R.string.widget_grid_fields_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFieldsValid && visibleFields.size < 4) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Column {
                val availableFieldNumbers = (1..8).toList()
                val chunkedRows = availableFieldNumbers.chunked(2)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunkedRows.forEach { rowFields ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val f1 = rowFields.getOrNull(0)
                            val f2 = rowFields.getOrNull(1)
                            
                            if (f1 != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    androidx.compose.material3.Checkbox(
                                        checked = visibleFields.contains(f1),
                                        enabled = visibleFields.contains(f1) || visibleFields.size < 4 || !isGridMode,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) visibleFields = visibleFields + f1
                                            else visibleFields = visibleFields - f1
                                        }
                                    )
                                    val name1 = currentFieldNames[f1]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.widget_field_name, f1)
                                    Text(text = name1, style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            
                            if (f2 != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    androidx.compose.material3.Checkbox(
                                        checked = visibleFields.contains(f2),
                                        enabled = visibleFields.contains(f2) || visibleFields.size < 4 || !isGridMode,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) visibleFields = visibleFields + f2
                                            else visibleFields = visibleFields - f2
                                        }
                                    )
                                    val name2 = currentFieldNames[f2]?.takeIf { it.isNotBlank() } ?: stringResource(R.string.widget_field_name, f2)
                                    Text(text = name2, style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            AlertThresholdSection(
                visibleFields = visibleFields,
                fieldNames = currentFieldNames,
                fieldAlerts = fieldAlerts,
                onFieldAlertChange = { fieldNum, min, max ->
                    fieldAlerts = fieldAlerts.toMutableMap().apply { this[fieldNum] = min to max }
                },
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (isValid) {
                        onSave(
                            channelIdValue!!, 
                            apiKey, 
                            channelName, 
                            selectedColorHex,
                            selectedTextColorHex,
                            transparency, 
                            fontSize.toInt(), 
                            visibleFields,
                            chartField,
                            isGlass,
                            bgColorMode,
                            chartResults.toInt(),
                            // Build AlertRules from fieldAlerts map
                            fieldAlerts.flatMap { (fieldNum, pair) ->
                                val (min, max) = pair
                                val rules = mutableListOf<com.thingspeak.monitor.feature.channel.domain.model.AlertRule>()
                                min.toDoubleOrNull()?.let {
                                    rules.add(com.thingspeak.monitor.feature.channel.domain.model.AlertRule(
                                        channelId = channelIdValue,
                                        fieldNumber = fieldNum,
                                        condition = "LESS_THAN",
                                        thresholdValue = it
                                    ))
                                }
                                max.toDoubleOrNull()?.let {
                                    rules.add(com.thingspeak.monitor.feature.channel.domain.model.AlertRule(
                                        channelId = channelIdValue,
                                        fieldNumber = fieldNum,
                                        condition = "GREATER_THAN",
                                        thresholdValue = it
                                    ))
                                }
                                rules
                            }
                        )
                    }
                },
                enabled = isValid && !isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.widget_save))
                }
            }
            
            
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (isSaving) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
}

@Composable
private fun AlertThresholdSection(
    visibleFields: Set<Int>,
    fieldNames: Map<Int, String>,
    fieldAlerts: Map<Int, Pair<String, String>>,
    onFieldAlertChange: (Int, String, String) -> Unit,
    textColor: Color
) {
    Text("Field Thresholds (Alarms)", style = MaterialTheme.typography.titleMedium, color = textColor)
    Text("Set Min/Max for alerts. Leave empty to disable.", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(12.dp))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visibleFields.sorted().forEach { fieldNum ->
            val name = fieldNames[fieldNum]?.takeIf { it.isNotBlank() } ?: "Field $fieldNum"
            val (minStr, maxStr) = fieldAlerts[fieldNum] ?: ("" to "")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minStr,
                        onValueChange = { onFieldAlertChange(fieldNum, it.trim(), maxStr) },
                        label = { Text("Min", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = maxStr,
                        onValueChange = { onFieldAlertChange(fieldNum, minStr, it.trim()) },
                        label = { Text("Max", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WidgetConfigScreenPreview() {
    MaterialTheme {
        WidgetConfigScreen(onSave = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> })
    }
}
