package com.ratsah.brahmi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = Purple80,
  secondary = PurpleGrey80,
  tertiary = Pink80
)

/**
 * Force a pure-black background across the setup and catalog screens
 * when the system is in dark mode.
 */
private fun ColorScheme.withBlackBackground(): ColorScheme = copy(
  background = Color.Black,
  surface = Color.Black,
)

/**
 * Mirror of [withBlackBackground] for light mode: forces a pure #FFFFFF
 * background and surface.
 */
private fun ColorScheme.withWhiteBackground(): ColorScheme = copy(
  background = Color.White,
  surface = Color.White,
)

@Suppress("SpellCheckingInspection")
private val LightColorScheme = lightColorScheme(
  primary = Purple40,
  secondary = PurpleGrey40,
  tertiary = Pink40

  /* Other default colors to override
  background = Color(0xFFFFFBFE),
  surface = Color(0xFFFFFBFE),
  onPrimary = Color.White,
  onSecondary = Color.White,
  onTertiary = Color.White,
  onBackground = Color(0xFF1C1B1F),
  onSurface = Color(0xFF1C1B1F),
  */
)

@Composable
fun BrahmiKeyboardTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) {
        dynamicDarkColorScheme(context).withBlackBackground()
      } else {
        dynamicLightColorScheme(context).withWhiteBackground()
      }
    }

    darkTheme -> DarkColorScheme.withBlackBackground()
    else -> LightColorScheme.withWhiteBackground()
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}