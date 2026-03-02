package com.thingspeak.monitor.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * Reusable shimmer effect modifier for Jetpack Compose.
 * Use it on box/image placeholder during loading states.
 */
fun Modifier.shimmer(): Modifier = composed {
    val shimmerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val highlightColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing)
        ),
        label = "shimmer_x"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                shimmerColor,
                highlightColor,
                shimmerColor,
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}
