package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Minimalist clean palette brush definitions
val LightBlueGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF), // Pure white top
        Color(0xFFFBFDFD), // Clean warm-white body
        Color(0xFFFAFAFA)  // Base
    )
)

val AmbientGlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x0A88BDA4), // Subtle sage green warm glow
        Color.Transparent
    ),
    radius = 700f
)

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Overlay a very subtle sage-green radial light ray for a premium, clean aesthetic
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AmbientGlowGradient)
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    var appliedModifier = modifier
        .shadow(
            elevation = 4.dp,
            shape = shape,
            ambientColor = Color(0x0D000000),
            spotColor = Color(0x0D000000)
        )
        .clip(shape)
        .background(Color.White) // Solid White Card Surface
        .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), shape) // Extremely clean, soft border

    if (onClick != null) {
        appliedModifier = appliedModifier.clickable(onClick = onClick)
    }

    Column(
        modifier = appliedModifier.padding(20.dp),
        content = content
    )
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color(0x99FFFFFF))
            .border(BorderStroke(1.dp, Color(0x7FFFFFFF)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}
