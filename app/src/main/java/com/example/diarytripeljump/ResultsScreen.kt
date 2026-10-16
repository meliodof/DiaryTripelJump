package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TripleJumpResult
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import com.example.diarytripeljump.ui.components.*
import com.example.diarytripeljump.ui.theme.NumberLarge
import com.example.diarytripeljump.ui.theme.NumberMedium
import com.example.diarytripeljump.ui.theme.extraColors
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.font.FontStyle
import com.example.diarytripeljump.data.formatNumber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.formatRecordDelta
import com.example.diarytripeljump.data.formatRecordValue

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    // Рекорды вычисляются автоматически из тренировок и ручных записей
    val records by viewModel.records.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    // Dialog States
    var showAddRecordDialog by remember { mutableStateOf(false) }
    var isAddingAntiRecord by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecordSummary?>(null) }
    var historyKey by remember { mutableStateOf<String?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }

    // Filter States
    var selectedSourceFilter by rememberSaveable { mutableIntStateOf(0) } // 0: Все, 1: Соревнования, 2: Тренировки
    var selectedLocationFilter by rememberSaveable { mutableIntStateOf(0) } // 0: Все, 1: На улице, 2: В помещении
    var selectedCategoryFilter by rememberSaveable { mutableIntStateOf(0) } // 0: Все, 1: Прыжковые, 2: Беговые, 3: Силовые
    var selectedRecordTypeFilter by rememberSaveable { mutableIntStateOf(0) } // 0: Рекорды, 1: Анти-рекорды

    val filteredResults = remember(
        records, selectedSourceFilter, selectedLocationFilter, selectedCategoryFilter, selectedRecordTypeFilter
    ) {
        records.filter { item ->
            val matchesSource = when (selectedSourceFilter) {
                1 -> item.isCompetition
                2 -> !item.isCompetition
                else -> true
            }
            val matchesLocation = when (selectedLocationFilter) {
                1 -> !item.isIndoor
                2 -> item.isIndoor
                else -> true
            }
            val matchesCategory = when (selectedCategoryFilter) {
                1 -> item.category.contains("прыжк", ignoreCase = true) || item.name.contains("прыжок", ignoreCase = true)
                2 -> item.category.contains("бег", ignoreCase = true) || item.name.contains("бег", ignoreCase = true)
                3 -> item.category.contains("силов", ignoreCase = true) || item.name.contains("штанг", ignoreCase = true) || item.name.contains("присед", ignoreCase = true)
                else -> true
            }
            val matchesType = if (selectedRecordTypeFilter == 1) item.isAnti else !item.isAnti
            matchesSource && matchesLocation && matchesCategory && matchesType
        }
    }

    // Лучший результат в тройном прыжке (в метрах) для hero-карточки
    val bestJump = remember(records) {
        records
            .filter { !it.isAnti && it.isCompetition && it.unit == ResultUnit.M }
            .maxByOrNull { it.best }
    }

    val hasActiveFilters = selectedSourceFilter != 0 || selectedLocationFilter != 0 || selectedCategoryFilter != 0
    val isAntiMode = selectedRecordTypeFilter == 1

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    AppScreenScaffold(
        modifier = modifier,
        title = "Рекорды",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isAddingAntiRecord = isAntiMode
                    showAddRecordDialog = true
                },
                expanded = fabExpanded,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text(text = if (isAntiMode) "Анти-рекорд" else "Рекорд") },
                containerColor = if (isAntiMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isAntiMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner, extraBottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SegmentedControl(
                    options = listOf("Рекорды", "Анти-рекорды"),
                    selectedIndex = selectedRecordTypeFilter,
                    onSelect = { selectedRecordTypeFilter = it }
                )
            }

            if (!isAntiMode && bestJump != null) {
                item { BestResultCard(record = bestJump, onClick = { historyKey = bestJump.key }) }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropdownFilterChip(
                        title = "Источник",
                        options = listOf("Все источники", "Соревнования", "Тренировки"),
                        selectedIndex = selectedSourceFilter,
                        onSelect = { selectedSourceFilter = it }
                    )
                    DropdownFilterChip(
                        title = "Вид",
                        options = listOf("Все виды", "Прыжковые", "Беговые", "Силовые"),
                        selectedIndex = selectedCategoryFilter,
                        onSelect = { selectedCategoryFilter = it }
                    )
                    DropdownFilterChip(
                        title = "Место",
                        options = listOf("Везде", "На улице", "В помещении"),
                        selectedIndex = selectedLocationFilter,
                        onSelect = { selectedLocationFilter = it }
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = {
                            selectedSourceFilter = 0
                            selectedCategoryFilter = 0
                            selectedLocationFilter = 0
                        }) { Text(text = "Сбросить") }
                    }
                }
            }

            if (filteredResults.isNotEmpty()) {
                item {
                    Text(
                        text = "${filteredResults.size} ${pluralRu(filteredResults.size, "рекорд", "рекорда", "рекордов")} · результаты из тренировок добавляются автоматически",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(filteredResults, key = { it.key }) { record ->
                    RecordCard(
                        record = record,
                        onClick = { historyKey = record.key },
                        onEdit = { editingKey = record.key },
                        onDelete = { recordToDelete = record }
                    )
                }
            } else {
                item {
                    EmptyState(
                        icon = if (isAntiMode) Icons.AutoMirrored.Filled.TrendingDown else Icons.Default.EmojiEvents,
                        title = if (hasActiveFilters) "Ничего не найдено" else if (isAntiMode) "Анти-рекордов пока нет" else "Рекордов пока нет",
                        message = if (hasActiveFilters) "Попробуйте изменить или сбросить фильтры."
                        else "Внесите результаты в тренировке — рекорд появится здесь сам."
                    )
                }
            }
        }
    }

    // История обновлений выбранного рекорда (обновляется вместе с данными)
    val historyRecord = historyKey?.let { key -> records.find { it.key == key } }
    if (historyRecord != null) {
        RecordHistorySheet(record = historyRecord, onDismiss = { historyKey = null })
    }

    val editingRecord = editingKey?.let { key -> records.find { it.key == key } }
    if (editingRecord != null) {
        EditRecordDialog(
            record = editingRecord,
            onDismiss = { editingKey = null },
            onSave = { value, date, indoor, weather, notes ->
                viewModel.updateRecord(editingRecord, value, date, indoor, weather, notes)
                editingKey = null
            },
            onResetManual = {
                viewModel.clearRecordOverride(editingRecord.key)
                editingKey = null
            }
        )
    }

    if (recordToDelete != null) {
        val target = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(text = "Удалить запись?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "«${target.displayName}» — ${formatRecordValue(target.best, target.unit)} будет удалена. Предыдущий результат станет рекордом.") },
            confirmButton = {
                Button(
                    onClick = {
                        target.deletableResultId?.let { viewModel.deleteTripleJumpResult(it) }
                        recordToDelete = null
                    },
                    colors = destructiveButtonColors()
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("Отмена") }
            }
        )
    }

    if (showAddRecordDialog) {
        AddRecordDialog(
            isAntiRecord = isAddingAntiRecord,
            onDismiss = { showAddRecordDialog = false },
            onConfirm = { eventName, resVal, unit, isIndoor, recordLevel, category, isCompetition, weather, notes ->
                viewModel.addRecordResult(
                    eventName = eventName,
                    result = resVal,
                    unit = unit,
                    isIndoor = isIndoor,
                    isAntiRecord = isAddingAntiRecord,
                    recordLevel = recordLevel,
                    exerciseCategory = category,
                    isCompetition = isCompetition,
                    weatherConditions = weather,
                    notes = notes
                )
                showAddRecordDialog = false
            }
        )
    }
}

/** Крупная карточка с личным рекордом в тройном прыжке. */
@Composable
private fun BestResultCard(record: RecordSummary, onClick: () -> Unit) {
    val extra = MaterialTheme.extraColors
    AppCard(containerColor = extra.goldContainer, border = null, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(
                icon = Icons.Default.EmojiEvents,
                tint = extra.gold,
                container = extra.gold.copy(alpha = 0.18f),
                size = 52.dp,
                iconSize = 28.dp
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Личный рекорд",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.onGoldContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = formatRecordValue(record.best, record.unit),
                    style = MaterialTheme.typography.displaySmall,
                    color = extra.onGoldContainer
                )
                Text(
                    text = "${record.displayName} · ${formatFullDate(record.bestDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.onGoldContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "История рекорда",
                tint = extra.onGoldContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/** Карточка рекорда: значок вида, название, бейджи, метаданные, значение, кнопка правки и меню. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordCard(
    record: RecordSummary,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val tint = when {
        record.isAnti -> cs.secondary
        record.isCompetition -> cs.tertiary
        else -> cs.primary
    }

    val icon = when {
        record.isAnti -> Icons.AutoMirrored.Filled.TrendingDown
        record.category.contains("бег", ignoreCase = true) -> Icons.AutoMirrored.Filled.DirectionsRun
        record.category.contains("силов", ignoreCase = true) -> Icons.Default.FitnessCenter
        else -> Icons.Default.EmojiEvents
    }

    val lastEntry = record.history.lastOrNull()

    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBadge(icon = icon, tint = tint, size = 44.dp)

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        record.isAnti -> StatusPill("Анти-рекорд", cs.onSecondaryContainer, cs.secondaryContainer, icon = Icons.AutoMirrored.Filled.TrendingDown)
                        record.levelLabel == "PB" -> StatusPill("PB", extra.onGoldContainer, extra.goldContainer, icon = Icons.Default.Star)
                        else -> StatusPill("SB", cs.onSecondaryContainer, cs.secondaryContainer, icon = Icons.Default.Star)
                    }
                    StatusPill(
                        text = if (record.isCompetition) "Старт" else "Тренировка",
                        contentColor = cs.onSurfaceVariant,
                        containerColor = cs.surfaceContainerHigh
                    )
                    if (lastEntry?.previousValue != null && !record.isManuallyEdited) {
                        StatusPill(
                            text = formatRecordDelta(lastEntry.value, lastEntry.previousValue, record.unit),
                            contentColor = cs.onPrimaryContainer,
                            containerColor = cs.primaryContainer
                        )
                    }
                    if (record.history.size > 1) {
                        StatusPill(
                            text = "×${record.history.size}",
                            contentColor = cs.onSurfaceVariant,
                            containerColor = cs.surfaceContainerHigh,
                            icon = Icons.Default.History
                        )
                    }
                    if (record.isManuallyEdited) {
                        StatusPill(
                            text = "Изменено",
                            contentColor = cs.onSurfaceVariant,
                            containerColor = cs.surfaceContainerHigh,
                            icon = Icons.Default.Edit
                        )
                    }
                }

                val details = buildList {
                    add(formatFullDate(record.bestDate))
                    add(if (record.isIndoor) "В помещении" else "На улице")
                    if (!record.weather.isNullOrBlank()) add(record.weather)
                    if (!record.inventoryLabel.isNullOrBlank()) add(record.inventoryLabel)
                }
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!record.notes.isNullOrBlank()) {
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRecordValue(record.best, record.unit),
                    style = NumberLarge,
                    color = cs.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = 2.dp, end = 12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать запись",
                            tint = cs.primary
                        )
                    }
                    OverflowMenu(
                        actions = buildList {
                            add(MenuAction("История обновлений", Icons.Default.History, onClick = onClick))
                            add(MenuAction("Редактировать", Icons.Default.Edit, onClick = onEdit))
                            if (record.deletableResultId != null) {
                                add(MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDelete))
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Ручное редактирование рекорда или анти-рекорда: результат, дата, место, условия, заметка. */
@Composable
private fun EditRecordDialog(
    record: RecordSummary,
    onDismiss: () -> Unit,
    onSave: (value: Double, date: Long, isIndoor: Boolean, weather: String?, notes: String?) -> Unit,
    onResetManual: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var valueText by remember {
        mutableStateOf(
            when (record.unit) {
                ResultUnit.M, ResultUnit.SECONDS -> String.format(Locale.US, "%.2f", record.best)
                else -> formatNumber(record.best)
            }
        )
    }
    var date by remember { mutableStateOf(record.bestDate) }
    var isIndoor by remember { mutableStateOf(record.isIndoor) }
    var weather by remember { mutableStateOf(record.weather ?: "") }
    var notes by remember { mutableStateOf(record.notes ?: "") }
    var showValueError by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    val unitLabel = when (record.unit) {
        ResultUnit.M -> "м"
        ResultUnit.CM -> "см"
        ResultUnit.KG -> "кг"
        ResultUnit.SECONDS -> "сек"
        ResultUnit.REPS -> "раз"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (record.isAnti) "Редактировать анти-рекорд" else "Редактировать рекорд",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = record.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v ->
                        valueText = v.replace(',', '.').filter { it.isDigit() || it == '.' }
                        showValueError = false
                    },
                    label = { Text("Результат, $unitLabel") },
                    isError = showValueError,
                    supportingText = if (showValueError) {
                        { Text("Введите число больше нуля") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DateField(label = "Дата", millis = date, onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth())

                Text(text = "Место", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SegmentedControl(
                    options = listOf("На улице", "В помещении"),
                    selectedIndex = if (isIndoor) 1 else 0,
                    onSelect = { isIndoor = it == 1 }
                )

                OutlinedTextField(
                    value = weather,
                    onValueChange = { weather = it },
                    label = { Text("Условия (погода, ветер, покрытие)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметка") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (record.isManuallyEdited) {
                    TextButton(onClick = {
                        onResetManual()
                    }) { Text("Вернуть автоматическое значение") }
                } else if (record.deletableResultId == null) {
                    Text(
                        text = "Запись создана из тренировки. Ручная правка действует до тех пор, пока рекорд не будет улучшен новым результатом.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = valueText.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0) showValueError = true
                else onSave(parsed, date, isIndoor, weather.trim().ifBlank { null }, notes.trim().ifBlank { null })
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (pickingDate) {
        AppDatePickerDialog(
            initialMillis = date,
            onDismiss = { pickingDate = false },
            onConfirm = {
                date = it
                pickingDate = false
            }
        )
    }
}

/** Нижняя панель с историей: когда и насколько обновлялся рекорд. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordHistorySheet(record: RecordSummary, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = record.displayName, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                Text(
                    text = (if (record.isAnti) "Анти-рекорд" else "Личный рекорд") + " · " +
                        if (record.isCompetition) "старты" else "тренировки",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

            AppCard(containerColor = extra.goldContainer, border = null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Сейчас", style = MaterialTheme.typography.labelLarge, color = extra.onGoldContainer.copy(alpha = 0.8f))
                        Text(
                            text = formatRecordValue(record.best, record.unit),
                            style = MaterialTheme.typography.headlineMedium,
                            color = extra.onGoldContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatFullDate(record.bestDate), style = MaterialTheme.typography.bodyMedium, color = extra.onGoldContainer)
                        Text(
                            "Всего результатов: ${record.attempts}",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.onGoldContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Text(text = "История обновлений", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)

            val entries = record.history.reversed()
            AppCard {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(
                            icon = if (index == 0) Icons.Default.EmojiEvents else Icons.Default.History,
                            tint = if (index == 0) extra.gold else cs.onSurfaceVariant,
                            size = 36.dp,
                            iconSize = 18.dp
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = formatRecordValue(entry.value, record.unit),
                                style = NumberMedium,
                                color = cs.onSurface
                            )
                            val meta = buildList {
                                add(formatFullDate(entry.date))
                                add(if (entry.isCompetition) "старт" else "тренировка")
                                if (!entry.inventoryLabel.isNullOrBlank()) add(entry.inventoryLabel)
                            }
                            Text(
                                text = meta.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        if (entry.previousValue != null) {
                            StatusPill(
                                text = formatRecordDelta(entry.value, entry.previousValue, record.unit),
                                contentColor = cs.onPrimaryContainer,
                                containerColor = cs.primaryContainer
                            )
                        } else {
                            StatusPill(
                                text = "Первый результат",
                                contentColor = cs.onSurfaceVariant,
                                containerColor = cs.surfaceContainerHigh
                            )
                        }
                    }
                    if (index < entries.lastIndex) {
                        HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(start = 64.dp))
                    }
                }
            }

            if (entries.size == 1) {
                Text(
                    text = "Рекорд пока не обновлялся. Новые лучшие результаты появятся здесь автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatFullDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru")).format(Date(timestamp))

@Composable
fun AddRecordDialog(
    isAntiRecord: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        eventName: String,
        result: Double,
        unit: ResultUnit,
        isIndoor: Boolean,
        recordLevel: String,
        category: String,
        isCompetition: Boolean,
        weather: String,
        notes: String
    ) -> Unit
) {
    var eventNameText by remember { mutableStateOf("Тройной прыжок") }
    var resultValueText by remember { mutableStateOf("14.80") }

    var selectedUnit by remember { mutableStateOf(ResultUnit.M) }
    var isIndoor by remember { mutableStateOf(false) }
    var recordLevel by remember { mutableStateOf("PB") } // PB vs SB
    var selectedCategory by remember { mutableStateOf("Прыжковые") } // Прыжковые, Беговые, Силовые
    var isCompetition by remember { mutableStateOf(true) }

    var weatherText by remember { mutableStateOf("Ясно, +22°C") }
    var notesText by remember { mutableStateOf("") }

    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isAntiRecord) "Новый Анти-Рекорд" else "Новый Рекорд",
                fontWeight = FontWeight.Bold,
                color = if (isAntiRecord) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = eventNameText,
                    onValueChange = {
                        eventNameText = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Название упражнения / вида") },
                    isError = showNameError && eventNameText.isBlank(),
                    supportingText = if (showNameError && eventNameText.isBlank()) {
                        { Text("Это поле обязательно для заполнения", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selection
                Text(text = "Вид упражнения:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("🦘 Прыжковые", "🏃 Беговые", "💪 Силовые").forEach { cat ->
                        FilterChip(
                            selected = selectedCategory.contains(cat.takeLast(5), ignoreCase = true),
                            onClick = {
                                selectedCategory = cat
                                selectedUnit = when {
                                    cat.contains("бег", ignoreCase = true) -> ResultUnit.SECONDS
                                    cat.contains("силов", ignoreCase = true) -> ResultUnit.KG
                                    else -> ResultUnit.M
                                }
                            },
                            label = { Text(cat, fontSize = 10.sp) }
                        )
                    }
                }

                // PB vs SB Selection
                if (!isAntiRecord) {
                    Text(text = "Уровень рекорда:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = recordLevel == "PB",
                            onClick = { recordLevel = "PB" },
                            label = { Text("🏆 PB (Личный рекорд)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = recordLevel == "SB",
                            onClick = { recordLevel = "SB" },
                            label = { Text("⭐ SB (Сезонный рекорд)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Source Selection: Competition vs Training
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Соревновательный результат:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Switch(checked = isCompetition, onCheckedChange = { isCompetition = it })
                }

                // Indoor vs Outdoor Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "В помещении (Indoor):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Switch(checked = isIndoor, onCheckedChange = { isIndoor = it })
                }

                // Weather input field HIDDEN for indoor!
                if (!isIndoor) {
                    OutlinedTextField(
                        value = weatherText,
                        onValueChange = { weatherText = it },
                        label = { Text("Погода / Условия (на улице)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Result Input Value
                OutlinedTextField(
                    value = resultValueText,
                    onValueChange = { input -> resultValueText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Результат (${getUnitTitle(selectedUnit)})") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Заметки (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eventNameText.isNotBlank()) {
                        val resDouble = resultValueText.toDoubleOrNull() ?: 0.0
                        onConfirm(
                            eventNameText,
                            resDouble,
                            selectedUnit,
                            isIndoor,
                            recordLevel,
                            selectedCategory,
                            isCompetition,
                            weatherText,
                            notesText
                        )
                    } else {
                        showNameError = true
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

fun formatResultValue(value: Double, unit: ResultUnit): String {
    return when (unit) {
        ResultUnit.KG -> "${value.toInt()} кг"
        ResultUnit.CM -> "${value.toInt()} см"
        ResultUnit.M -> String.format(Locale.getDefault(), "%.2f м", value)
        ResultUnit.REPS -> "${value.toInt()} повторений"
        ResultUnit.SECONDS -> String.format(Locale.getDefault(), "%.2f сек", value)
    }
}

private fun formatDate(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val day = calendar[Calendar.DAY_OF_MONTH]
    val month = when (calendar[Calendar.MONTH]) {
        Calendar.JANUARY -> "Янв"
        Calendar.FEBRUARY -> "Фев"
        Calendar.MARCH -> "Мар"
        Calendar.APRIL -> "Апр"
        Calendar.MAY -> "Май"
        Calendar.JUNE -> "Июн"
        Calendar.JULY -> "Июл"
        Calendar.AUGUST -> "Авг"
        Calendar.SEPTEMBER -> "Сен"
        Calendar.OCTOBER -> "Окт"
        Calendar.NOVEMBER -> "Ноя"
        Calendar.DECEMBER -> "Дек"
        else -> ""
    }
    return "$day $month ${calendar[Calendar.YEAR]}"
}
