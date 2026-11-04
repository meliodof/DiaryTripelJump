package com.example.diarytripeljump

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.*
import com.example.diarytripeljump.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Упражнение или вид, для которого можно поставить цель. */
data class GoalCandidate(
    val key: String,
    val name: String,
    val displayName: String,
    val unit: ResultUnit,
    val lowerIsBetter: Boolean
)

/** Кандидат для упражнения каталога: ключ тот же, что у рекорда, поэтому прогресс считается сам. */
fun goalCandidateFor(ex: Exercise): GoalCandidate {
    val unit = ex.defaultUnit
    return GoalCandidate(
        key = RecordsEngine.exerciseRecordKey(
            ex.name, ex.isSingleLeg, ex.legSide, unit, ex.inSpikes,
            RecordsEngine.recordInventory(ex.inventoryName, ex.inventoryUnit, ex.inventoryValue, unit)
        ),
        name = ex.name,
        displayName = ex.title(),
        unit = unit,
        lowerIsBetter = RecordsEngine.isLowerBetter(ex.name, unit)
    )
}

fun goalCandidateFor(record: RecordSummary) = GoalCandidate(
    key = record.key,
    name = record.name,
    displayName = record.displayName + if (record.isCompetition) " (старт)" else "",
    unit = record.unit,
    lowerIsBetter = record.lowerIsBetter
)

/** Все упражнения каталога и все существующие рекорды (кроме антирекордов), включая тройной прыжок. */
fun buildGoalCandidates(exercises: List<Exercise>, records: List<RecordSummary>): List<GoalCandidate> {
    val map = LinkedHashMap<String, GoalCandidate>()
    exercises.forEach { goalCandidateFor(it).let { c -> map.putIfAbsent(c.key, c) } }
    records.filter { !it.isAnti }.forEach { goalCandidateFor(it).let { c -> map.putIfAbsent(c.key, c) } }
    val tripleKey = RecordsEngine.manualRecordKey("Тройной прыжок", false, true, ResultUnit.M, false, null, null)
    map.putIfAbsent(
        tripleKey,
        GoalCandidate(tripleKey, "Тройной прыжок", "Тройной прыжок (старт)", ResultUnit.M, false)
    )
    return map.values.sortedBy { it.displayName.lowercase() }
}

private fun unitLabel(unit: ResultUnit) = when (unit) {
    ResultUnit.M -> "м"
    ResultUnit.CM -> "см"
    ResultUnit.KG -> "кг"
    ResultUnit.SECONDS -> "сек"
    ResultUnit.REPS -> "раз"
}

private fun formatDeadline(millis: Long) = SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(millis))

/** Полоса прогресса цели: текущий рекорд → цель. */
@Composable
fun GoalProgressBlock(progress: GoalProgress, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val goal = progress.goal
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress.achieved) cs.tertiary else cs.primary,
            trackColor = cs.surfaceContainerHigh
        )
        val text = buildString {
            append("Цель ${formatRecordValue(goal.targetValue, goal.unit)}")
            when {
                progress.achieved -> append(" · достигнута")
                progress.remaining != null -> append(" · осталось ${formatGoalRemaining(progress.remaining, goal.unit)}")
                else -> append(" · пока нет результатов")
            }
            goal.deadline?.let { append(" · до ${formatDeadline(it)}") }
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
fun GoalCard(progress: GoalProgress, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Default.Flag, tint = if (progress.achieved) cs.tertiary else cs.primary, size = 44.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = styledTitle(progress.goal.displayName, progress.goal.name),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = progress.current?.let { formatRecordValue(it, progress.goal.unit) } ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                }
                GoalProgressBlock(progress)
            }
            OverflowMenu(
                actions = listOf(
                    MenuAction("Изменить цель", Icons.Default.Edit, onClick = onEdit),
                    MenuAction("Удалить цель", Icons.Default.DeleteOutline, destructive = true, onClick = onDelete)
                )
            )
        }
    }
}

/**
 * Постановка и правка цели. Если [fixed] задан (или цель уже существует) — упражнение не меняется,
 * иначе его выбирают поиском по списку.
 */
@Composable
fun GoalEditorDialog(
    candidates: List<GoalCandidate>,
    fixed: GoalCandidate?,
    existing: ExerciseGoal?,
    currentBestFor: (String) -> Double?,
    onDismiss: () -> Unit,
    onSave: (GoalCandidate, Double, Long?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val initial = fixed ?: existing?.let {
        GoalCandidate(it.recordKey, it.name, it.displayName, it.unit, it.lowerIsBetter)
    }
    var selected by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var targetText by remember {
        mutableStateOf(
            existing?.targetValue?.let {
                if (existing.unit == ResultUnit.M || existing.unit == ResultUnit.SECONDS) String.format(Locale.US, "%.2f", it) else formatNumber(it)
            } ?: ""
        )
    }
    var deadline by remember { mutableStateOf(existing?.deadline) }
    var pickingDate by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val matches = remember(query, candidates) {
        candidates.filter { query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true) }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая цель" else "Изменить цель", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val sel = selected
                if (initial != null && (fixed != null || existing != null)) {
                    Text(styledTitle(initial.displayName, initial.name), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                } else if (sel == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Упражнение") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (matches.isEmpty()) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    matches.forEach { c ->
                        Surface(
                            onClick = { selected = c },
                            shape = MaterialTheme.shapes.small,
                            color = cs.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = styledTitle(c.displayName, c.name) + AnnotatedString(", " + unitLabel(c.unit)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(styledTitle(sel.displayName, sel.name), style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { selected = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Выбрать другое")
                        }
                    }
                }

                if (sel != null) {
                    currentBestFor(sel.key)?.let {
                        Text(
                            "Сейчас: ${formatRecordValue(it, sel.unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { v ->
                            targetText = v.replace(',', '.').filter { it.isDigit() || it == '.' }
                            showError = false
                        },
                        label = { Text("Цель, ${unitLabel(sel.unit)}") },
                        isError = showError,
                        supportingText = {
                            Text(
                                if (showError) "Введите число больше нуля"
                                else if (sel.lowerIsBetter) "Чем меньше время, тем лучше" else "Чем больше, тем лучше"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(
                            label = "Срок (необязательно)",
                            millis = deadline ?: System.currentTimeMillis(),
                            onClick = { pickingDate = true },
                            modifier = Modifier.weight(1f)
                        )
                        if (deadline != null) {
                            IconButton(onClick = { deadline = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Убрать срок")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sel = selected
                val v = targetText.toDoubleOrNull()
                if (sel == null || v == null || v <= 0.0) showError = true else onSave(sel, v, deadline)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )

    if (pickingDate) {
        AppDatePickerDialog(
            initialMillis = deadline ?: System.currentTimeMillis(),
            onDismiss = { pickingDate = false },
            onConfirm = { deadline = it; pickingDate = false }
        )
    }
}
