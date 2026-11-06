package com.example.diarytripeljump.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.diarytripeljump.data.CustomPalette

/** Контраст двух цветов по WCAG: от 1 до 21. */
fun contrast(a: Color, b: Color): Float {
    val light = maxOf(a.luminance(), b.luminance())
    val dark = minOf(a.luminance(), b.luminance())
    return (light + 0.05f) / (dark + 0.05f)
}

/** Цвет сдвигается к чёрному или белому, пока не станет читаемым на [background]. */
fun readableOn(color: Color, background: Color, minContrast: Float = 3f): Color {
    val target = if (background.luminance() > 0.5f) Color.Black else Color.White
    var result = color
    var step = 0
    while (contrast(result, background) < minContrast && step < 10) {
        step++
        result = lerp(color, target, step / 10f)
    }
    return result
}

private fun textOn(color: Color): Color = if (color.luminance() > 0.4f) Color(0xFF14151A) else Color.White

/** Подставляет цвета пользователя в базовую схему: подложки, текст и фон считаются сами. */
fun ColorScheme.withPalette(palette: CustomPalette, dark: Boolean): ColorScheme {
    fun accent(argb: Int): Color = readableOn(Color(argb), surface)
    fun container(color: Color): Color = lerp(color, if (dark) Color.Black else Color.White, if (dark) 0.55f else 0.8f)
    fun onContainer(color: Color): Color = lerp(color, if (dark) Color.White else Color.Black, 0.75f)

    val first = accent(palette.primary)
    val second = accent(palette.secondary)
    val third = accent(palette.tertiary)
    return copy(
        primary = first,
        onPrimary = textOn(first),
        primaryContainer = container(first),
        onPrimaryContainer = onContainer(first),
        secondary = second,
        onSecondary = textOn(second),
        secondaryContainer = container(second),
        onSecondaryContainer = onContainer(second),
        tertiary = third,
        onTertiary = textOn(third),
        tertiaryContainer = container(third),
        onTertiaryContainer = onContainer(third),
        // фон только чуть окрашивается: текст и карточки остаются читаемыми при любом цвете
        background = lerp(background, first, 0.03f),
        surfaceContainerLow = lerp(surfaceContainerLow, first, 0.03f),
        surfaceContainer = lerp(surfaceContainer, first, 0.04f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, first, 0.05f)
    )
}

/** Градиент большой карточки следует за основным цветом. */
fun ExtraColors.withPalette(palette: CustomPalette): ExtraColors {
    val base = Color(palette.primary)
    val start = lerp(base, Color.Black, 0.35f)
    val end = lerp(base, Color.Black, 0.12f)
    return copy(heroStart = start, heroEnd = end, onHero = textOn(end))
}
