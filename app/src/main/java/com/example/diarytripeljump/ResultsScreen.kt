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
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.GoalProgress
import com.example.diarytripeljump.data.computeGoalProgress
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
    val goals by viewModel.goals.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val catalogExercises by viewModel.allExercises.collectAsState()

    // Цели: ключ цели = ключ рекорда
    var goalEditorOpen by remember { mutableStateOf(false) }
    var goalFixed by remember { mutableStateOf<GoalCandidate?>(null) }
    var goalExistingKey by remember { mutableStateOf<String?>(null) }
    val goalByKey = remember(goals) { goals.associateBy { it.recordKey } }
    val recordByKey = remember(records) { records.filter { !it.isAnti }.associateBy { it.key } }
    val goalCandidates = remember(catalogExercises, records) { buildGoalCandidates(catalogExercises, records) }

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
    var selectedSpikesFilter by rememberSaveable { mutableIntStateOf(0) } // 0: Любая обувь, 1: В шиповках, 2: Без шиповок

    val filteredResults = remember(
        records, selectedSourceFilter, selectedLocationFilter, selectedCategoryFilter, selectedRecordTypeFilter, selectedSpikesFilter
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
            val matchesSpikes = when (selectedSpikesFilter) {
                1 -> item.inSpikes
                2 -> !item.inSpikes
                else -> true
            }
            matchesSource && matchesLocation && matchesCategory && matchesType && matchesSpikes
        }
    }

    // Лучший результат в тройном прыжке (в метрах) для hero-карточки
    val bestJump = remember(records) {
        records
            .filter { !it.isAnti && it.isCompetition && it.unit == ResultUnit.M }
            .maxByOrNull { it.best }
    }

    val hasActiveFilters = selectedSourceFilter != 0 || selectedLocationFilter != 0 || selectedCategoryFilter != 0 ||
        selectedSpikesFilter != 0
    val isAntiMode = selectedRecordTypeFilter == 1
    val isGoalsMode = selectedRecordTypeFilter == 2

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
                    if (isGoalsMode) {
                        goalFixed = null
                        goalExistingKey = null
                        goalEditorOpen = true
                    } else {
                        isAddingAntiRecord = isAntiMode
                        showAddRecordDialog = true
                    }
                },
                expanded = fabExpanded,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text(text = if (isGoalsMode) "Цель" else if (isAntiMode) "Анти-рекорд" else "Рекорд") },
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
                    options = listOf("Рекорды", "Анти-рекорды", "Цели"),
                    selectedIndex = selectedRecordTypeFilter,
                    onSelect = { selectedRecordTypeFilter = it }
                )
            }

            if (isGoalsMode) {
                if (goals.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Flag,
                            title = "Целей пока нет",
                            message = "Поставьте цель по результату в любом упражнении или в тройном прыжке — прогресс считается автоматически.",
                            actionLabel = "Поставить цель",
                            onAction = { goalFixed = null; goalExistingKey = null; goalEditorOpen = true }
                        )
                    }
                } else {
                    items(goals, key = { "goal|" + it.recordKey }) { g ->
                        GoalCard(
                            progress = computeGoalProgress(g, recordByKey[g.recordKey]?.best),
                            onEdit = { goalFixed = null; goalExistingKey = g.recordKey; goalEditorOpen = true },
                            onDelete = { viewModel.deleteGoal(g.recordKey) }
                        )
                    }
                }
                return@LazyColumn
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
                    DropdownFilterChip(
                        title = "Обувь",
                        options = listOf("Любая обувь", "В шиповках", "Без шиповок"),
                        selectedIndex = selectedSpikesFilter,
                        onSelect = { selectedSpikesFilter = it }
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = {
                            selectedSourceFilter = 0
                            selectedCategoryFilter = 0
                            selectedLocationFilter = 0
                            selectedSpikesFilter = 0
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
                        goalProgress = goalByKey[record.key]?.takeIf { !record.isAnti }
                            ?.let { computeGoalProgress(it, record.best) },
                        onSetGoal = if (record.isAnti) null else ({
                            goalFixed = goalCandidateFor(record)
                            goalExistingKey = record.key
                            goalEditorOpen = true
                        }),
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
                        message = when {
                            hasActiveFilters -> "Попробуйте изменить или сбросить фильтры."
                            isAntiMode -> "Анти-рекорд — худший результат, от которого вы уходите. Добавьте его кнопкой ниже."
                            else -> "Внесите результаты в тренировке — рекорд появится здесь сам."
                        }
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
        AddRecordDialog(
            isAntiRecord = editingRecord.isAnti,
            inventoryItems = inventoryItems,
            onAddInventory = { name, unit -> viewModel.addInventory(name, unit) },
            initial = editingRecord.toRecordInput(),
            onDismiss = { editingKey = null },
            onConfirm = { input ->
                viewModel.updateRecord(editingRecord, input)
                editingKey = null
            },
            onResetManual = if (editingRecord.isManuallyEdited) ({
                viewModel.clearRecordOverride(editingRecord.key)
                editingKey = null
            }) else null,
            manualHint = if (!editingRecord.isManuallyEdited && editingRecord.deletableResultId == null) {
                "Запись создана из тренировки. Ручная правка действует до тех пор, пока рекорд не будет улучшен новым результатом."
            } else null
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
            inventoryItems = inventoryItems,
            onAddInventory = { name, unit -> viewModel.addInventory(name, unit) },
            onDismiss = { showAddRecordDialog = false },
            onConfirm = { input ->
                viewModel.addRecordResult(input = input, isAntiRecord = isAddingAntiRecord)
                showAddRecordDialog = false
            }
        )
    }

    if (goalEditorOpen) {
        val existing = goalExistingKey?.let { goalByKey[it] }
        GoalEditorDialog(
            candidates = goalCandidates,
            fixed = goalFixed,
            existing = existing,
            currentBestFor = { key -> recordByKey[key]?.best },
            onDismiss = { goalEditorOpen = false },
            onSave = { cand, target, deadline ->
                viewModel.saveGoal(cand.key, cand.name, cand.displayName, cand.unit, target, cand.lowerIsBetter, deadline)
                goalEditorOpen = false
            },
            onDelete = existing?.let { g -> { viewModel.deleteGoal(g.recordKey); goalEditorOpen = false } }
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
    goalProgress: GoalProgress? = null,
    onSetGoal: (() -> Unit)? = null,
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
                    text = styledTitle(record.displayName, record.name),
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
                    if (record.inSpikes) {
                        StatusPill(
                            text = "Шиповки",
                            contentColor = cs.onTertiaryContainer,
                            containerColor = cs.tertiaryContainer
                        )
                    }
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
                if (goalProgress != null) {
                    GoalProgressBlock(goalProgress, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
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
                // Одно меню действий вместо карандаша + «⋮»: карточка чище, редактирование — первым пунктом меню
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OverflowMenu(
                        actions = buildList {
                            add(MenuAction("Редактировать", Icons.Default.Edit, onClick = onEdit))
                            add(MenuAction("История обновлений", Icons.Default.History, onClick = onClick))
                            if (onSetGoal != null) {
                                add(MenuAction(if (goalProgress == null) "Поставить цель" else "Изменить цель", Icons.Default.Flag, onClick = onSetGoal))
                            }
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

/** Все показатели рекорда для формы редактирования. */
private fun RecordSummary.toRecordInput() = NewRecordInput(
    eventName = name,
    result = best,
    unit = unit,
    isIndoor = isIndoor,
    recordLevel = levelLabel,
    category = category,
    isCompetition = isCompetition,
    weather = weather,
    notes = notes,
    isSingleLeg = isSingleLeg,
    legSide = legSide,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = inventoryValue,
    inSpikes = inSpikes,
    date = bestDate
)

private fun recordValueText(value: Double, unit: ResultUnit): String = when (unit) {
    ResultUnit.M, ResultUnit.SECONDS -> String.format(Locale.US, "%.2f", value)
    else -> formatNumber(value).replace(',', '.')
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
                Text(text = styledTitle(record.displayName, record.name), style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
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
    SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(timestamp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecordDialog(
    isAntiRecord: Boolean,
    inventoryItems: List<InventoryItem>,
    onAddInventory: (name: String, unit: ResultUnit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (NewRecordInput) -> Unit,
    /** Показатели редактируемого рекорда; null — новая запись. */
    initial: NewRecordInput? = null,
    /** Вернуть рассчитанное значение рекорда из тренировок. */
    onResetManual: (() -> Unit)? = null,
    manualHint: String? = null
) {
    val cs = MaterialTheme.colorScheme
    var eventNameText by remember { mutableStateOf(initial?.eventName ?: "Тройной прыжок") }
    var resultValueText by remember { mutableStateOf(initial?.let { recordValueText(it.result, it.unit) } ?: "") }

    val units = listOf(ResultUnit.M, ResultUnit.CM, ResultUnit.SECONDS, ResultUnit.KG, ResultUnit.REPS)
    var selectedUnit by remember { mutableStateOf(initial?.unit ?: ResultUnit.M) }
    var isIndoor by remember { mutableStateOf(initial?.isIndoor ?: false) }
    var recordLevel by remember { mutableStateOf(initial?.recordLevel ?: "PB") }
    var selectedCategory by remember { mutableStateOf(initial?.category ?: "Прыжковые") }
    var isCompetition by remember { mutableStateOf(initial?.isCompetition ?: true) }

    var isSingleLeg by remember { mutableStateOf(initial?.isSingleLeg ?: false) }
    var legSide by remember { mutableStateOf(initial?.legSide) }
    var inSpikes by remember { mutableStateOf(initial?.inSpikes ?: false) }

    var inventoryName by remember { mutableStateOf(initial?.inventoryName) }
    var inventoryUnit by remember { mutableStateOf(initial?.inventoryUnit) }
    var inventoryValueText by remember {
        mutableStateOf(initial?.inventoryValue?.let { formatNumber(it).replace(',', '.') } ?: "")
    }
    var showCreateInventory by remember { mutableStateOf(false) }

    var weatherText by remember { mutableStateOf(initial?.weather ?: "") }
    var notesText by remember { mutableStateOf(initial?.notes ?: "") }
    // дата, когда рекорд установлен (по умолчанию — сегодня)
    var recordDate by remember { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }
    var pickingRecordDate by remember { mutableStateOf(false) }

    var showNameError by remember { mutableStateOf(false) }
    var showValueError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    initial != null && isAntiRecord -> "Редактировать анти-рекорд"
                    initial != null -> "Редактировать рекорд"
                    isAntiRecord -> "Новый анти-рекорд"
                    else -> "Новый рекорд"
                },
                fontWeight = FontWeight.Bold,
                color = if (isAntiRecord) cs.error else cs.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = eventNameText,
                    onValueChange = {
                        eventNameText = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Название упражнения / вида") },
                    isError = showNameError && eventNameText.isBlank(),
                    supportingText = if (showNameError && eventNameText.isBlank()) {
                        { Text("Это поле обязательно для заполнения") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "Вид рекорда", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Прыжковые" to ResultUnit.M, "Беговые" to ResultUnit.SECONDS, "Силовые" to ResultUnit.KG).forEach { (cat, defaultUnit) ->
                        ToggleChip(
                            label = cat,
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                selectedUnit = defaultUnit // единицу можно сменить ниже вручную
                            }
                        )
                    }
                }

                Text(text = "Единица результата", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    units.forEach { u ->
                        ToggleChip(label = getUnitTitle(u), selected = selectedUnit == u, onClick = { selectedUnit = u })
                    }
                }

                OutlinedTextField(
                    value = resultValueText,
                    onValueChange = { input ->
                        resultValueText = input.replace(',', '.').filter { it.isDigit() || it == '.' }
                        showValueError = false
                    },
                    label = { Text("Результат (${getUnitTitle(selectedUnit)})") },
                    isError = showValueError,
                    supportingText = if (showValueError) {
                        { Text("Введите число больше нуля") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DateField(
                    label = "Дата установки рекорда",
                    millis = recordDate,
                    onClick = { pickingRecordDate = true },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(color = cs.outlineVariant)

                // Левая / правая нога — как при создании упражнения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Исполнение на одной ноге", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        Text(
                            "Рекорд ведётся отдельно от результата на двух ногах",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSingleLeg,
                        onCheckedChange = {
                            isSingleLeg = it
                            if (!it) legSide = null
                        }
                    )
                }
                if (isSingleLeg) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ToggleChip(
                            label = "Левая",
                            selected = legSide == "LEFT",
                            onClick = { legSide = if (legSide == "LEFT") null else "LEFT" }
                        )
                        ToggleChip(
                            label = "Правая",
                            selected = legSide == "RIGHT",
                            onClick = { legSide = if (legSide == "RIGHT") null else "RIGHT" }
                        )
                        ToggleChip(
                            label = "По очереди",
                            selected = legSide == com.example.diarytripeljump.data.TitleGrammar.ALTERNATE,
                            onClick = {
                                val alternate = com.example.diarytripeljump.data.TitleGrammar.ALTERNATE
                                legSide = if (legSide == alternate) null else alternate
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("В шиповках", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                        Text(
                            "Рекорд в шиповках ведётся отдельно от результата в обычной обуви",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Switch(checked = inSpikes, onCheckedChange = { inSpikes = it })
                }

                HorizontalDivider(color = cs.outlineVariant)

                // Инвентарь, с которым установлен рекорд (или антирекорд); можно создать новый
                Text(text = "Инвентарь", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = inventoryName.isNullOrBlank(),
                        onClick = {
                            inventoryName = null
                            inventoryUnit = null
                            inventoryValueText = ""
                        },
                        label = { Text("Без инвентаря") }
                    )
                    inventoryItems.forEach { item ->
                        FilterChip(
                            selected = inventoryName.equals(item.name, ignoreCase = true) && inventoryUnit == item.unit,
                            onClick = {
                                inventoryName = item.name
                                inventoryUnit = item.unit
                            },
                            label = { Text("${item.name}, ${getUnitTitle(item.unit)}") }
                        )
                    }
                    AssistChip(
                        onClick = { showCreateInventory = true },
                        label = { Text("Новый") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        }
                    )
                }
                val loadedImplement = !inventoryName.isNullOrBlank() &&
                    com.example.diarytripeljump.data.RecordsEngine.isLoadedImplement(inventoryUnit, selectedUnit)
                if (loadedImplement) {
                    // штанга при результате в кг: рекорд и вес снаряда — одно и то же число
                    Text(
                        text = "Вес снаряда = результату: ${resultValueText.ifBlank { "—" }} ${getUnitTitle(selectedUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                } else if (!inventoryName.isNullOrBlank()) {
                    OutlinedTextField(
                        value = inventoryValueText,
                        onValueChange = { v -> inventoryValueText = v.replace(',', '.').filter { it.isDigit() || it == '.' } },
                        label = { Text("$inventoryName, ${getUnitTitle(inventoryUnit ?: ResultUnit.CM)}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = cs.outlineVariant)

                if (!isAntiRecord) {
                    Text(text = "Уровень рекорда", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleChip(label = "PB · личный", selected = recordLevel == "PB", onClick = { recordLevel = "PB" })
                        ToggleChip(label = "SB · сезонный", selected = recordLevel == "SB", onClick = { recordLevel = "SB" })
                    }
                }

                Text(text = "Где показан", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SegmentedControl(
                    options = listOf("На соревнованиях", "На тренировке"),
                    selectedIndex = if (isCompetition) 0 else 1,
                    onSelect = { isCompetition = it == 0 }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("В помещении", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isIndoor, onCheckedChange = { isIndoor = it })
                }
                if (!isIndoor) {
                    OutlinedTextField(
                        value = weatherText,
                        onValueChange = { weatherText = it },
                        label = { Text("Погода / условия (на улице)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Заметки (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onResetManual != null) {
                    TextButton(onClick = onResetManual) { Text("Вернуть автоматическое значение") }
                }
                if (manualHint != null) {
                    Text(text = manualHint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = resultValueText.toDoubleOrNull()
                    val loaded = com.example.diarytripeljump.data.RecordsEngine.isLoadedImplement(inventoryUnit, selectedUnit)
                    when {
                        eventNameText.isBlank() -> showNameError = true
                        value == null || value <= 0.0 -> showValueError = true
                        else -> {
                            val hasInv = !inventoryName.isNullOrBlank()
                            onConfirm(
                                NewRecordInput(
                                    eventName = eventNameText.trim(),
                                    result = value,
                                    unit = selectedUnit,
                                    isIndoor = isIndoor,
                                    recordLevel = recordLevel,
                                    category = selectedCategory,
                                    isCompetition = isCompetition,
                                    weather = weatherText.trim().ifBlank { null },
                                    notes = notesText.trim().ifBlank { null },
                                    isSingleLeg = isSingleLeg,
                                    legSide = if (isSingleLeg) legSide else null,
                                    inventoryName = if (hasInv) inventoryName else null,
                                    inventoryUnit = if (hasInv) inventoryUnit else null,
                                    inventoryValue = when {
                                        !hasInv -> null
                                        loaded -> value
                                        else -> inventoryValueText.toDoubleOrNull()
                                    },
                                    inSpikes = inSpikes,
                                    date = recordDate
                                )
                            )
                        }
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (pickingRecordDate) {
        AppDatePickerDialog(
            initialMillis = recordDate,
            onDismiss = { pickingRecordDate = false },
            onConfirm = {
                recordDate = it
                pickingRecordDate = false
            }
        )
    }

    if (showCreateInventory) {
        CreateInventoryDialog(
            onDismiss = { showCreateInventory = false },
            onConfirm = { name, unit ->
                onAddInventory(name, unit)
                // сразу выбираем только что созданный инвентарь
                inventoryName = name
                inventoryUnit = unit
                showCreateInventory = false
            }
        )
    }
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
