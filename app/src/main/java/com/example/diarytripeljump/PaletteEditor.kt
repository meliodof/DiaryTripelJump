package com.example.diarytripeljump

import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.CustomPalette
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.theme.LightPrimary
import com.example.diarytripeljump.ui.theme.LightSecondary
import com.example.diarytripeljump.ui.theme.LightTertiary

private fun palette(primary: Long, secondary: Long, tertiary: Long) =
    CustomPalette(Color(primary).toArgb(), Color(secondary).toArgb(), Color(tertiary).toArgb())

private val Presets = listOf(
    "Изумруд" to palette(0xFF0F6B4F, 0xFFB8471F, 0xFF2B5CA8),
    "Гранат" to palette(0xFFA3123C, 0xFFC77700, 0xFF3D5A80),
    "Закат" to palette(0xFFC2410C, 0xFF7C3AED, 0xFF0F766E),
    "Фиалка" to palette(0xFF6D3BD1, 0xFFD6336C, 0xFF0B7285),
    "Графит" to palette(0xFF39424E, 0xFFD9480F, 0xFF1C7ED6)
)

/** Цвет как тон, насыщенность и яркость: так его удобно настраивать ползунками. */
private fun hsvOf(argb: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }

/** Окно своей расцветки: готовые наборы и настройка трёх цветов. Тёмная и светлая темы остаются. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteEditorDialog(
    initial: CustomPalette?,
    onDismiss: () -> Unit,
    onSave: (CustomPalette) -> Unit,
    onReset: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var current by remember {
        mutableStateOf(initial ?: CustomPalette(LightPrimary.toArgb(), LightSecondary.toArgb(), LightTertiary.toArgb()))
    }
    var slot by remember { mutableIntStateOf(0) }

    val selected = when (slot) {
        0 -> current.primary
        1 -> current.secondary
        else -> current.tertiary
    }
    val hsv = hsvOf(selected)

    fun update(hue: Float = hsv[0], saturation: Float = hsv[1], value: Float = hsv[2]) {
        val argb = Color.hsv(hue, saturation, value).toArgb()
        current = when (slot) {
            0 -> current.copy(primary = argb)
            1 -> current.copy(secondary = argb)
            else -> current.copy(tertiary = argb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_PALETTE) { Text(text = "Своя расцветка", fontWeight = FontWeight.Bold) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Как будет выглядеть
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(modifier = Modifier.weight(2f).height(56.dp).background(Color(current.primary)))
                    Box(modifier = Modifier.weight(1f).height(56.dp).background(Color(current.secondary)))
                    Box(modifier = Modifier.weight(1f).height(56.dp).background(Color(current.tertiary)))
                }

                Text(text = "Готовые наборы", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Presets.forEach { (name, preset) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { current = preset }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(preset.primary), Color(preset.secondary))))
                                    .border(
                                        width = if (preset == current) 3.dp else 0.dp,
                                        color = cs.onSurface,
                                        shape = CircleShape
                                    )
                            )
                            Text(text = name, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }

                SegmentedControl(
                    options = listOf("Основной", "Акцент", "Третий"),
                    selectedIndex = slot,
                    onSelect = { slot = it }
                )

                ColorSlider("Оттенок", hsv[0], 0f..360f) { update(hue = it) }
                ColorSlider("Насыщенность", hsv[1], 0f..1f) { update(saturation = it) }
                ColorSlider("Яркость", hsv[2], 0.1f..1f) { update(value = it) }

                Text(
                    text = "Светлая и тёмная темы подстроятся сами. Слишком бледный цвет приложение затемнит, чтобы текст оставался читаемым.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                if (initial != null) {
                    TextButton(onClick = onReset) { Text("Вернуть стандартные цвета") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(current) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}
