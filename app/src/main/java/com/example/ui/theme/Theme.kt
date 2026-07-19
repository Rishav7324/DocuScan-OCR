package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = SageGreen,
    secondary = SlateGray,
    tertiary = GlassIndigo,
    background = GlassBackgroundDark,
    surface = GlassSurfaceDark,
    surfaceVariant = GlassSurfaceVariantDark,
    onPrimary = DeepCharcoal,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFE6EDF3),
    onSurface = Color(0xFFE6EDF3)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SageGreen,
    secondary = SlateGray,
    tertiary = GlassIndigo,
    background = GlassBackgroundLight,
    surface = GlassSurfaceLight,
    surfaceVariant = GlassSurfaceVariantLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(), // Follow the system by default
  dynamicColor: Boolean = false, // Keep theme colors consistent
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
