package com.example.diarytripeljump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.Advice
import com.example.diarytripeljump.data.AdviceEngine
import com.example.diarytripeljump.data.AdviceKind
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.coachTarget

private val AdviceKind.groupTitle: String
    get() = when (this) {
        AdviceKind.RISK -> "Важно"
        AdviceKind.CHANCE -> "Можно улучшить"
        AdviceKind.GOOD -> "Хорошие новости"
    }

/**
 * Раздел аналитики «Советы»: все советы по данным дневника, разбитые по важности,
 * с данными, на которых они основаны, и описанием правил. Скрытые с главной здесь остаются.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdviceScreen(viewModel: DiaryViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val all by viewModel.allAdvice.collectAsState()
    val dismissed by viewModel.dismissedAdvice.collectAsState()
    val records by viewModel.records.collectAsState()
    val goals by viewModel.goals.collectAsState()
    var filter by rememberSaveable { mutableStateOf<AdviceKind?>(null) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var openedRecord by remember { mutableStateOf<String?>(null) }

    val shown = all.filter { filter == null || it.kind == filter }

    fun open(a: Advice) {
        val key = a.recordKey
        if (key != null && records.any { it.key == key }) openedRecord = key else a.route?.let(onNavigate)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text(text = "Советы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_ADVICE), id = "advice.help")
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FlowRow(
                    modifier = Modifier.coachTarget("advice.filters"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ToggleChip(label = "Все · ${all.size}", selected = filter == null, onClick = { filter = null })
                    AdviceKind.entries.forEach { kind ->
                        val count = all.count { it.kind == kind }
                        if (count > 0) {
                            ToggleChip(label = "${kind.groupTitle} · $count", selected = filter == kind, onClick = { filter = kind })
                        }
                    }
                }
            }

            if (all.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Lightbulb,
                        title = "Пока советов нет",
                        message = "Советы появятся, когда в дневнике будет больше тренировок, попыток разбега, целей и стартов."
                    )
                }
            }

            AdviceKind.entries.forEach { kind ->
                val group = shown.filter { it.kind == kind }
                if (group.isNotEmpty()) {
                    item(key = "group:$kind") {
                        Text(
                            kind.groupTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(group, key = { "advice:${it.id}" }) { a ->
                        AdviceCard(
                            advice = a,
                            hidden = a.id in dismissed,
                            onOpen = { open(a) },
                            onHide = { viewModel.dismissAdvice(a) },
                            onRestore = { viewModel.restoreAdvice(a) },
                            modifier = if (a == shown.first()) Modifier.coachTarget("advice.card") else Modifier
                        )
                    }
                }
            }

            // Как считаются советы: правила и пороги
            item(key = "rules") {
                AppCard(modifier = Modifier.coachTarget("advice.rules")) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = cs.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Как считаются советы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRules = !showRules }) {
                            Icon(if (showRules) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Показать правила")
                        }
                    }
                    if (showRules) {
                        Column(
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdviceEngine.rules.forEach { (title, text) ->
                                Column {
                                    Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSurface)
                                    Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = cs.outlineVariant)
                            Text(
                                "Советы — подсказки по цифрам вашего дневника, а не указания тренера или врача.",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    openedRecord?.let { key ->
        records.find { it.key == key }?.let { record ->
            RecordDetailScreen(
                record = record,
                goal = goals.firstOrNull { it.recordKey == key },
                onDismiss = { openedRecord = null }
            )
        }
    }
}

/** Совет целиком: текст, данные, на которых он основан, и действия. */
@Composable
private fun AdviceCard(
    advice: Advice,
    hidden: Boolean,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint) = adviceLook(advice.kind)
    AppCard(modifier = modifier.alpha(if (hidden) 0.6f else 1f), onClick = if (advice.route != null) onOpen else null) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            IconBadge(icon = icon, tint = tint, size = 44.dp, iconSize = 22.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(advice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(advice.text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(
                    "Почему: ${advice.because}",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = cs.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (advice.route != null) {
                        OutlinedButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                            Text(if (advice.recordKey != null) "Открыть рекорд" else "Открыть раздел")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (hidden) {
                        TextButton(onClick = onRestore) { Text("Вернуть на главную") }
                    } else {
                        TextButton(onClick = onHide) { Text("Скрыть с главной") }
                    }
                }
            }
        }
    }
}
