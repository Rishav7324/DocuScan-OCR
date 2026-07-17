package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SageGreen,
    secondary = SlateGray,
    tertiary = SageGreen,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = LightSlate,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SageGreen,
    secondary = SlateGray,
    tertiary = SageGreen,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = LightSlate,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Default to gorgeous light/airy frosted theme
  dynamicColor: Boolean = false, // Keep theme colors consistent
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
