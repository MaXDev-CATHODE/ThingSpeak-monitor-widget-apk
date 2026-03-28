package com.thingspeak.monitor.feature.chart.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EightFieldSelector(
    selectedFields: Set<Int>,
    fieldNames: Map<Int, String>,
    onFieldToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldColors = listOf(
        "#2196F3", // F1
        "#F44336", // F2
        "#4CAF50", // F3
        "#FF9800", // F4
        "#9C27B0", // F5
        "#00BCD4", // F6
        "#FFC107", // F7
        "#E91E63"  // F8
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Fields Control",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 280.dp)
        ) {
            items((1..8).toList()) { fieldIndex ->
                val isSelected = selectedFields.contains(fieldIndex)
                val colorHex = fieldColors.getOrElse(fieldIndex - 1) { "#888888" }
                val fieldColor = Color(android.graphics.Color.parseColor(colorHex))
                val name = fieldNames[fieldIndex] ?: "Field $fieldIndex"

                FilterChip(
                    selected = isSelected,
                    onClick = { onFieldToggle(fieldIndex) },
                    label = {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(fieldColor, CircleShape)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = fieldColor.copy(alpha = 0.15f),
                        selectedLabelColor = fieldColor,
                        selectedLeadingIconColor = fieldColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) fieldColor else MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = fieldColor,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 2.dp
                    )
                )
            }
        }
    }
}
