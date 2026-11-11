package com.example.diarytripeljump

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.Advice
import com.example.diarytripeljump.data.AdviceKind
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.theme.extraColors

/** Сколько советов видно сразу: остальные — по кнопке «Ещё». */
private const val ADVICE_VISIBLE = 2

/**
 * Советы на главной: самые важные сверху. Нажатие открывает раздел или рекорд,
 * «Почему?» показывает, на каких данных совет основан, «Понятно» скрывает его.
 */
@Composable
fun AdviceSection(
    advice: List<Advice>,
    onOpen: (Advice) -> Unit,
    onDismiss: (Advice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (advice.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) advice else advice.take(ADVICE_VISIBLE)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "Советы", subtitle = "По данным вашего дневника")
        AppCard(modifier = Modifier.animateContentSize()) {
            shown.forEachIndexed { index, item ->
                AdviceRow(item, onOpen = { onOpen(item) }, onDismiss = { onDismiss(item) })
                if (index < shown.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            if (advice.size > ADVICE_VISIBLE) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(start = 56.dp, bottom = 4.dp)
                ) {
                    Text(if (expanded) "Свернуть" else "Ещё ${advice.size - ADVICE_VISIBLE}")
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdviceRow(advice: Advice, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = adviceLook(advice.kind)
    var showWhy by remember(advice.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = advice.route != null, onClick = onOpen)
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(icon = icon, tint = tint, size = 40.dp, iconSize = 20.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(advice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text(advice.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (showWhy) {
                Text(
                    advice.because,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row {
                TextButton(onClick = { showWhy = !showWhy }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(if (showWhy) "Скрыть" else "Почему?")
                }
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Понятно")
                }
            }
        }
    }
}

@Composable
internal fun adviceLook(kind: AdviceKind): Pair<ImageVector, Color> = when (kind) {
    AdviceKind.RISK -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.error
    AdviceKind.CHANCE -> Icons.Default.Lightbulb to MaterialTheme.colorScheme.primary
    AdviceKind.GOOD -> Icons.Default.EmojiEvents to MaterialTheme.extraColors.gold
}
