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
import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.RunUp
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
        RecordDetailScreen(
            record = historyRecord,
            goal = goalByKey[historyRecord.key],
            onDismiss = { historyKey = null },
            onEdit = { editingKey = historyRecord.key },
            onSetGoal = {
                goalFixed = goalCandidateFor(historyRecord)
                goalExistingKey = historyRecord.key
                goalEditorOpen = true
            }
        )
    }

    val editingRecord = editingKey?.let { key -> records.find { it.key == key } }
    if (editingRecord != null) {
        AddRecordDialog(
            isAntiRecord = editingRecord.isAnti,
            exercises = catalogExercises,
            inventoryItems = inventoryItems,
            inventoryActions = viewModel.inventoryActions,
            onSaveExercise = { viewModel.saveExerciseToDatabase(it) },
            onUpdateExercise = { ex, cfg -> viewModel.updateExercise(ex, cfg) },
            onDeleteExercise = { viewModel.deleteExerciseFromCatalog(it) },
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
            exercises = catalogExercises,
            inventoryItems = inventoryItems,
            inventoryActions = viewModel.inventoryActions,
            onSaveExercise = { viewModel.saveExerciseToDatabase(it) },
            onUpdateExercise = { ex, cfg -> viewModel.updateExercise(ex, cfg) },
            onDeleteExercise = { viewModel.deleteExerciseFromCatalog(it) },
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

private fun formatFullDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(timestamp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRecordDialog(
    isAntiRecord: Boolean,
    exercises: List<Exercise>,
    inventoryItems: List<InventoryItem>,
    inventoryActions: InventoryActions,
    onSaveExercise: (ExerciseConfig) -> Unit,
    onUpdateExercise: (Exercise, ExerciseConfig) -> Unit,
    onDeleteExercise: (Exercise) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (NewRecordInput) -> Unit,
    /** Показатели редактируемого рекорда; null — новая запись. */
    initial: NewRecordInput? = null,
    /** Вернуть рассчитанное значение рекорда из тренировок. */
    onResetManual: (() -> Unit)? = null,
    manualHint: String? = null
) {
    val cs = MaterialTheme.colorScheme
    // упражнение выбирается и меняется так же, как в тренировке
    var config by remember { mutableStateOf(initial?.toConfig() ?: defaultRecordConfig()) }
    var resultValueText by remember { mutableStateOf(initial?.let { recordValueText(it.result, it.unit) } ?: "") }
    var showCreateExercise by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var pendingConfig by remember { mutableStateOf<ExerciseConfig?>(null) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var isIndoor by remember { mutableStateOf(initial?.isIndoor ?: false) }
    var recordLevel by remember { mutableStateOf(initial?.recordLevel ?: "PB") }
    var isCompetition by remember { mutableStateOf(initial?.isCompetition ?: true) }

    var weatherText by remember { mutableStateOf(initial?.weather ?: "") }
    var notesText by remember { mutableStateOf(initial?.notes ?: "") }
    // дата, когда рекорд установлен (по умолчанию — сегодня)
    var recordDate by remember { mutableLongStateOf(initial?.date ?: System.currentTimeMillis()) }
    var pickingRecordDate by remember { mutableStateOf(false) }

    var showValueError by remember { mutableStateOf(false) }
    val selectableExercises = remember(exercises) { exercises.filterNot { RunUp.isExercise(it.name) } }
    val exerciseError = EntryRules.recordExerciseError(config.name)
    val valueError = EntryRules.resultError(resultValueText, config.unit)
    val dateError = EntryRules.dateError(recordDate, hasResult = true)
    val loadedImplement = config.isLoadedImplement && !config.inventoryName.isNullOrBlank()

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
                // Выбранное упражнение: нажатие открывает список, карандаш — конструктор, как в тренировке
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showExercisePicker = true }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(text = "Упражнение", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(text = styledTitle(config.title(), config.name), style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    }
                    IconButton(onClick = { editingConfig = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить снаряд, шиповки или ногу", tint = cs.onSurfaceVariant)
                    }
                }
                if (exerciseError != null) {
                    Text(text = exerciseError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }

                OutlinedTextField(
                    value = resultValueText,
                    onValueChange = { input ->
                        resultValueText = EntryRules.cleanNumber(input)
                        showValueError = false
                    },
                    label = { Text("Результат (${getUnitTitle(config.unit)})") },
                    isError = showValueError && valueError != null,
                    supportingText = when {
                        showValueError && valueError != null -> {
                            { Text(valueError) }
                        }
                        // штанга при результате в кг: рекорд и вес снаряда — одно и то же число
                        loadedImplement -> {
                            { Text("Вес снаряда равен результату") }
                        }
                        else -> null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                DateField(
                    label = "Дата установки рекорда",
                    millis = recordDate,
                    onClick = { pickingRecordDate = true },
                    modifier = Modifier.fillMaxWidth()
                )

                if (dateError != null) {
                    Text(text = dateError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }

                // Уровень, место и заметки нужны не всегда: по умолчанию скрыты
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(
                        if (showDetails) "Скрыть подробности"
                        else "Подробности: " + listOfNotNull(
                            recordLevel.takeIf { !isAntiRecord },
                            if (isCompetition) "на соревнованиях" else "на тренировке"
                        ).joinToString(" · ")
                    )
                }
                if (showDetails) {
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
                }
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
                enabled = exerciseError == null && dateError == null,
                onClick = {
                    val value = resultValueText.toDoubleOrNull()
                    if (value == null || valueError != null) {
                        showValueError = true
                    } else {
                        onConfirm(
                            NewRecordInput(
                                eventName = config.name.trim(),
                                result = value,
                                unit = config.unit,
                                isIndoor = isIndoor,
                                recordLevel = recordLevel,
                                category = config.category,
                                isCompetition = isCompetition,
                                weather = weatherText.trim().ifBlank { null },
                                notes = notesText.trim().ifBlank { null },
                                isSingleLeg = config.isSingleLeg,
                                legSide = if (config.isSingleLeg) config.legSide else null,
                                inventoryName = config.inventoryName,
                                inventoryUnit = config.inventoryUnit,
                                inventoryValue = config.inventoryValue,
                                inSpikes = config.inSpikes,
                                date = recordDate
                            )
                        )
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

    if (showExercisePicker) {
        ExercisePickerScreen(
            title = "Упражнение",
            doneLabel = "Готово",
            onDismiss = { showExercisePicker = false }
        ) {
            CatalogExercisePicker(
                exercises = selectableExercises,
                isSelected = { it.variantKey() == config.variantKey() },
                onToggle = {
                    config = it.toConfig()
                    showExercisePicker = false
                },
                onEdit = { editingExercise = it },
                onDelete = { exerciseToDelete = it }
            )
            FilledTonalButton(
                onClick = { showCreateExercise = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Создать упражнение")
            }
        }
    }

    if (showCreateExercise) {
        ExerciseEditorDialog(
            initial = null,
            inventoryItems = inventoryItems,
            inventoryActions = inventoryActions,
            onDismiss = { showCreateExercise = false },
            onConfirm = { cfg ->
                onSaveExercise(cfg)
                config = cfg
                showCreateExercise = false
                showExercisePicker = false
            }
        )
    }

    // Правка выбранного упражнения: конструктор с параметрами этого рекорда
    if (editingConfig) {
        ExerciseEditorDialog(
            initial = null,
            initialConfig = config,
            inventoryItems = inventoryItems,
            inventoryActions = inventoryActions,
            onDismiss = { editingConfig = false },
            onConfirm = { cfg ->
                editingConfig = false
                if (cfg.variantKey() == config.variantKey() && cfg.name.trim() == config.name.trim()) config = cfg
                else pendingConfig = cfg
            }
        )
    }

    pendingConfig?.let { cfg ->
        val catalogEx = exercises.firstOrNull { it.variantKey() == config.variantKey() }
        val newExists = exercises.any { it.variantKey() == cfg.variantKey() }
        ExerciseChangedDialog(
            oldTitle = config.title(),
            newTitle = cfg.title(),
            newExists = newExists,
            onDismiss = { pendingConfig = null },
            onCreate = {
                if (!newExists) onSaveExercise(cfg)
                config = cfg
                pendingConfig = null
            },
            onReplace = {
                if (catalogEx != null) onUpdateExercise(catalogEx, cfg) else onSaveExercise(cfg)
                config = cfg
                pendingConfig = null
            }
        )
    }

    // Правка упражнения из списка справочника
    editingExercise?.let { target ->
        ExerciseEditorDialog(
            initial = target,
            inventoryItems = inventoryItems,
            inventoryActions = inventoryActions,
            onDismiss = { editingExercise = null },
            onConfirm = { cfg ->
                onUpdateExercise(target, cfg)
                if (target.variantKey() == config.variantKey()) config = cfg
                editingExercise = null
            }
        )
    }

    exerciseToDelete?.let { target ->
        DeleteExerciseDialog(
            name = target.title(),
            onDismiss = { exerciseToDelete = null },
            onConfirm = {
                onDeleteExercise(target)
                exerciseToDelete = null
            }
        )
    }
}

/** Новый рекорд по умолчанию — в тройном прыжке. */
private fun defaultRecordConfig() = ExerciseConfig(
    name = "Тройной прыжок", category = "Прыжковое упражнение", isSingleLeg = false, legSide = null,
    inventoryName = null, inventoryUnit = null, inventoryValue = null, unit = ResultUnit.M
)

/** Показатели рекорда как конфигурация конструктора; старые короткие виды приводятся к видам справочника. */
private fun NewRecordInput.toConfig() = ExerciseConfig(
    name = eventName,
    category = when (category) {
        "Прыжковые" -> "Прыжковое упражнение"
        "Беговые" -> "Беговое упражнение"
        "Силовые" -> "Силовое упражнение"
        else -> category
    },
    isSingleLeg = isSingleLeg,
    legSide = legSide,
    inventoryName = inventoryName,
    inventoryUnit = inventoryUnit,
    inventoryValue = inventoryValue,
    inSpikes = inSpikes,
    unit = unit
)

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
