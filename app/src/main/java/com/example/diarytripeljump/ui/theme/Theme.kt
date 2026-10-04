package com.example.diarytripeljump.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF34A873),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B382B),
    onPrimaryContainer = Color(0xFF86E3B5),
    secondary = Color(0xFFF06A3A),
    onSecondary = Color.White,
    background = Color(0xFF121314),
    onBackground = Color(0xFFE6E8E6),
    surface = Color(0xFF1E2022),
    onSurface = Color(0xFFE6E8E6),
    surfaceVariant = Color(0xFF26282B),
    onSurfaceVariant = Color(0xFF9E9EA0),
    outline = Color(0xFF2C2E31)
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = ForestGreenLight,
    onPrimaryContainer = ForestGreenDark,
    secondary = TerracottaOrange,
    onSecondary = Color.White,
    secondaryContainer = TerracottaOrangeLight,
    onSecondaryContainer = TerracottaOrange,
    background = OffWhiteBackground,
    onBackground = TextPrimaryDark,
    surface = CardWhite,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardWhite,
    onSurfaceVariant = TextSecondaryGrey,
    outline = DividerLight
)

@Composable
fun DiaryTripelJumpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
