package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt
import com.example.diarytripeljump.data.RunUpScope
import com.example.diarytripeljump.data.RunUpShot
import com.example.diarytripeljump.data.RunUpTarget
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.data.runUpShots
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.AppDatePickerDialog
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.theme.extraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private fun dayLabel(millis: Long) = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

/**
 * Раздел «Попадания на разбеге»: кусок дорожки с бруском в 3D, каждая попытка — чёрный след шиповки ровно там,
 * сколько стоп введено в попытке (0 — брусок, плюс — заступ, минус — недоступ). Нажатие на след показывает,
 * что это за попытка, и позволяет оставить к ней комментарий.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunUpScreen(viewModel: DiaryViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gold = MaterialTheme.extraColors.gold
    val trainings by viewModel.allTrainingsWithExercises.collectAsState()
    val competitions by viewModel.allCompetitions.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val shots = remember(trainings, competitions) { runUpShots(trainings, competitions) }
    val sessions = remember(shots) { RunUpTarget.sessions(shots) }
    var scope by remember { mutableStateOf<RunUpScope>(RunUpScope.Last) }
    var choosingSession by remember { mutableStateOf(false) }
    var pickingFrom by remember { mutableStateOf(false) }
    var periodFrom by remember { mutableStateOf<Long?>(null) }
    var picked by remember(scope) { mutableStateOf<RunUpShot?>(null) }
    var editingNote by remember { mutableStateOf<RunUpShot?>(null) }
    var heatMode by remember { mutableStateOf(false) }
    val profile by viewModel.athleteProfile.collectAsState()
    val view = remember(shots, scope) { RunUpTarget.build(shots, scope, now) }

    val scopeLabel = when (val s = scope) {
        RunUpScope.Last -> sessions.firstOrNull()?.label ?: "последняя тренировка"
        is RunUpScope.Session -> sessions.firstOrNull { it.id == s.id }?.label ?: "выбранная тренировка"
        is RunUpScope.Days -> if (s.days <= 31) "за месяц" else "за 3 месяца"
        RunUpScope.All -> "за всё время"
        is RunUpScope.Period -> "${dayLabel(s.from)} — ${dayLabel(s.to)}"
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Попадания на разбеге", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_RUNUP_TARGET), id = "runup.help")
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "scope") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleChip("Последняя", scope == RunUpScope.Last, { scope = RunUpScope.Last })
                    ToggleChip("Тренировка…", scope is RunUpScope.Session, { choosingSession = true })
                    ToggleChip("Месяц", scope == RunUpScope.Days(30), { scope = RunUpScope.Days(30) })
                    ToggleChip("3 месяца", scope == RunUpScope.Days(91), { scope = RunUpScope.Days(91) })
                    ToggleChip("Всё время", scope == RunUpScope.All, { scope = RunUpScope.All })
                    ToggleChip("Период…", scope is RunUpScope.Period, { pickingFrom = true })
                }
            }
            item(key = "target") {
                AppCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            scopeLabel + (view?.let { " · ${it.shots.size} ${pluralRu(it.shots.size, "попытка", "попытки", "попыток")}" } ?: ""),
                            style = MaterialTheme.typography.titleSmall, color = cs.onSurface
                        )
                        if (view == null) {
                            Text(
                                if (shots.isEmpty()) "Попыток с отметкой попадания в брусок пока нет." else "В этом охвате попыток с отметкой нет.",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                            )
                            return@Column
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ToggleChip("Следы", !heatMode, { heatMode = false })
                            ToggleChip("Тепловая карта", heatMode, { heatMode = true; picked = null })
                        }
                        val placed = remember(view) { RunUpTarget.stacks(view.shots) }
                        // кусок дорожки: от −span до +span стоп, не меньше ±2 стоп, кратно половине стопы
                        val span = remember(view) { max(2.0, ceil((view.shots.maxOf { abs(it.feet) } + 0.5) * 2) / 2) }
                        RunUpBoard3D(
                            placed = placed, median = view.median, span = span, picked = picked, onPick = { picked = it },
                            start = gold,
                            modifier = Modifier.fillMaxWidth().height(380.dp).clip(MaterialTheme.shapes.medium),
                            heat = heatMode,
                            takeOffLeg = profile?.leadingLeg
                        )
                        if (heatMode) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("реже", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                Box(
                                    modifier = Modifier.width(140.dp).height(10.dp).clip(CircleShape)
                                        .background(Brush.horizontalGradient((0..10).map { heatColor(it / 10f) }))
                                )
                                Text("чаще", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        }
                        picked?.let { s ->
                            Text(
                                "${dayLabel(s.date)} · ${s.sourceLabel.substringBefore(',').removePrefix("Тренировка ").let { if (s.competition) it else "тренировка" }} · " +
                                    RunUp.describe(RunUpAttempt(boardFeet = s.feet)),
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                            )
                            if (!s.note.isNullOrBlank()) {
                                Text("«${s.note}»", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                            if (s.ref != null) {
                                TextButton(onClick = { editingNote = s }, contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (s.note.isNullOrBlank()) "Комментарий к попытке" else "Изменить комментарий")
                                }
                            }
                        }
                        Text(
                            "Попал ${view.hits} · заступ ${view.fouls} · недоступ ${view.shorts}",
                            style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant
                        )
                        Text(
                            when {
                                view.move > 0 -> "Обычно заступ на ${formatNumber(abs(view.median))} ст. Отнесите отметку примерно на ${formatNumber(view.move)} ст."
                                view.move < 0 -> "Обычно недоступ ${formatNumber(abs(view.median))} ст. Поднесите отметку примерно на ${formatNumber(-view.move)} ст."
                                view.shots.size < RunUpTarget.MIN_FOR_ADVICE -> "Попыток мало для совета по отметке."
                                else -> "Попытки вокруг бруска — отметку можно не трогать."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurface
                        )
                    }
                }
            }
        }
    }

    if (choosingSession) {
        AlertDialog(
            onDismissRequest = { choosingSession = false },
            title = { Text("Тренировка или старт") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(sessions, key = { it.id }) { s ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { scope = RunUpScope.Session(s.id); choosingSession = false }.padding(vertical = 10.dp)) {
                            Text(s.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (s.competition) FontWeight.Medium else FontWeight.Normal)
                            Text("${s.shots} ${pluralRu(s.shots, "попытка", "попытки", "попыток")} с отметкой", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingSession = false }) { Text("Закрыть") } }
        )
    }
    editingNote?.let { s ->
        var text by remember(s) { mutableStateOf(s.note.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = null },
            title = { Text("Комментарий к попытке") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${dayLabel(s.date)} · ${RunUp.describe(RunUpAttempt(boardFeet = s.feet))}",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(300) },
                        placeholder = { Text("Например: сбился на последних шагах") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    s.ref?.let { viewModel.saveAttemptNote(it, text.trim()) }
                    picked = s.copy(note = text.trim().ifEmpty { null })
                    editingNote = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editingNote = null }) { Text("Отмена") } }
        )
    }
    if (pickingFrom) {
        AppDatePickerDialog(initialMillis = now - 30 * 86_400_000L, onDismiss = { pickingFrom = false }) { from ->
            pickingFrom = false
            periodFrom = from
        }
    }
    periodFrom?.let { from ->
        AppDatePickerDialog(initialMillis = now, onDismiss = { periodFrom = null }) { to ->
            scope = RunUpScope.Period(minOf(from, to), maxOf(from, to))
            periodFrom = null
        }
    }
}
