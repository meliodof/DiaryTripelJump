package com.example.diarytripeljump

import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.Competition
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.ExerciseResult
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TrainingType
import com.example.diarytripeljump.data.TrainingWithExercises
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.ui.theme.NumberLarge
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.TrainingPeriod
import com.example.diarytripeljump.data.PeriodRules
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.diarytripeljump.data.startOfDay
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt
import com.example.diarytripeljump.data.SetResults
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.data.formatRecordValue
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect as GeoRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.diarytripeljump.ui.components.*

@Composable
fun TrainingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {},
    /** Открыть окно новой тренировки сразу, например с главного экрана. */
    openNewTraining: Boolean = false,
    onNewTrainingOpened: () -> Unit = {}
) {
    val confirm = LocalConfirm.current
    val complexes by viewModel.complexes.collectAsState()
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()
    val rawExercisesList by viewModel.allExercises.collectAsState()
    val competitionsList by viewModel.allCompetitions.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val trainingPeriods by viewModel.trainingPeriods.collectAsState()
    val records by viewModel.records.collectAsState()
    val goals by viewModel.goals.collectAsState()
    // подробности упражнения из сохранённой тренировки и его полная история
    var openedExercise by remember { mutableStateOf<ExerciseResult?>(null) }
    var openedRecordKey by remember { mutableStateOf<String?>(null) }
    var editingPeriod by remember { mutableStateOf<TrainingPeriod?>(null) }
    var showPeriodDialog by remember { mutableStateOf(false) }

    // Дубликаты справочника убираются по варианту: одно название с разными свойствами — разные упражнения
    val exercisesList = remember(rawExercisesList) {
        rawExercisesList.distinctBy { it.variantKey() }
    }

    // rememberSaveable: при возврате на вкладку открыт тот же месяц и день, а не сегодняшний
    var calendarMonth by rememberSaveable { mutableStateOf(Calendar.getInstance()) }
    var selectedDateMillis by rememberSaveable { mutableStateOf<Long?>(System.currentTimeMillis()) }

    var showAddTrainingDialog by remember { mutableStateOf(false) }
    LaunchedEffect(openNewTraining) {
        if (openNewTraining) {
            selectedDateMillis = System.currentTimeMillis()
            showAddTrainingDialog = true
            onNewTrainingOpened()
        }
    }
    var editingTrainingItem by remember { mutableStateOf<TrainingWithExercises?>(null) }
    var editingSkippedTrainingItem by remember { mutableStateOf<TrainingWithExercises?>(null) }
    var showMarkSkippedDialog by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var trainingToDelete by remember { mutableStateOf<TrainingWithExercises?>(null) }
    var exerciseToRemove by remember { mutableStateOf<Pair<TrainingWithExercises, ExerciseResult>?>(null) }
    // перенос дня перетаскиванием в календаре ждёт подтверждения
    var pendingMove by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    val activeTrainingsOnSelectedDay = remember(trainingsList, selectedDateMillis) {
        if (selectedDateMillis == null) false
        else {
            val key = calDayKey(selectedDateMillis!!)
            trainingsList.any { item -> calDayKey(item.training.date) == key && !item.training.isSkipped }
        }
    }

    val filteredTrainings = remember(trainingsList, selectedDateMillis) {
        if (selectedDateMillis == null) {
            trainingsList
        } else {
            val key = calDayKey(selectedDateMillis!!)
            // в течение дня — по времени: утренняя тренировка выше вечерней
            trainingsList.filter { item -> calDayKey(item.training.date) == key }.sortedBy { it.training.date }
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    AppScreenScaffold(
        modifier = modifier,
        title = "Тренировки",
        onHelp = rememberHelp(HelpTopic.TRAININGS),
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTrainingDialog = true },
                expanded = fabExpanded,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text(text = "Тренировка") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner, extraBottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(modifier = Modifier.coachTarget("trainings.calendar")) {
                CompactTrainingCalendarWidget(
                    currentMonth = calendarMonth,
                    onMonthChange = { newMonth -> calendarMonth = newMonth },
                    selectedDateMillis = selectedDateMillis,
                    onDateSelected = { selectedDateMillis = it },
                    trainingsList = trainingsList,
                    competitionsList = competitionsList,
                    periods = trainingPeriods,
                    onMoveDay = { from, to -> pendingMove = from to to }
                )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Подготовительные периоды",
                        subtitle = "Выделяются цветом на календаре",
                        actionLabel = "Добавить",
                        onAction = {
                            editingPeriod = null
                            showPeriodDialog = true
                        }
                    )
                    if (trainingPeriods.isNotEmpty()) {
                        val todayStart = remember { startOfDay(System.currentTimeMillis()) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            trainingPeriods.sortedByDescending { it.startDate }.forEach { period ->
                                PeriodCard(
                                    period = period,
                                    todayStart = todayStart,
                                    // открыть период в календаре: месяц начала, выбран первый день
                                    onShow = {
                                        calendarMonth = Calendar.getInstance().apply { timeInMillis = period.startDate }
                                        selectedDateMillis = period.startDate
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                    onEdit = {
                                        editingPeriod = period
                                        showPeriodDialog = true
                                    },
                                    onDelete = {
                                        confirm.delete("период", "Период «${period.name}» будет удалён. Тренировки не изменятся.") {
                                            viewModel.deleteTrainingPeriod(period)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionHeader(
                        title = if (selectedDateMillis != null) formatDayTitle(selectedDateMillis!!) else "Все тренировки",
                        subtitle = if (filteredTrainings.isEmpty()) "Записей нет"
                        else "${filteredTrainings.size} ${pluralRu(filteredTrainings.size, "запись", "записи", "записей")}",
                        actionLabel = if (selectedDateMillis != null) "Показать все" else null,
                        onAction = { selectedDateMillis = null }
                    )
                    val dayPeriods = remember(selectedDateMillis, trainingPeriods) {
                        selectedDateMillis?.let { PeriodRules.containing(it, trainingPeriods) } ?: emptyList()
                    }
                    if (dayPeriods.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dayPeriods.forEach { p ->
                                val c = parseHexColor(p.colorHex)
                                StatusPill(text = p.name, contentColor = c, containerColor = c.copy(alpha = 0.16f), icon = Icons.Default.DateRange)
                            }
                        }
                    }
                    if (selectedDateMillis != null) {
                        AssistChip(
                            onClick = {
                                if (activeTrainingsOnSelectedDay) {
                                    warningMessage = "В этот день уже есть тренировка! Для отметки пропуска сначала удалите существующую тренировку."
                                } else {
                                    showMarkSkippedDialog = true
                                }
                            },
                            label = { Text(text = "Отметить пропуск") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            if (filteredTrainings.isNotEmpty()) {
                items(filteredTrainings, key = { it.training.id }) { trainingItem ->
                    TrainingSessionCard(
                        trainingItem = trainingItem,
                        exercisesList = exercisesList,
                        onEditClick = {
                            if (trainingItem.training.isSkipped) {
                                editingSkippedTrainingItem = trainingItem
                            } else {
                                editingTrainingItem = trainingItem
                            }
                        },
                        onDeleteClick = { trainingToDelete = trainingItem },
                        onDeleteExercise = { exerciseToRemove = trainingItem to it },
                        onOpenExercise = { openedExercise = it }
                    )
                }
            } else {
                item {
                    EmptyState(
                        icon = Icons.Default.EventAvailable,
                        title = "В этот день ничего нет",
                        message = "Запишите тренировку или отметьте пропуск.",
                        actionLabel = "Добавить тренировку",
                        onAction = { showAddTrainingDialog = true }
                    )
                }
            }
        }
    }

    if (showPeriodDialog) {
        PeriodEditorDialog(
            initial = editingPeriod,
            existingPeriods = trainingPeriods,
            onDismiss = { showPeriodDialog = false },
            onSave = { name, start, end, color ->
                val save = {
                    viewModel.saveTrainingPeriod(editingPeriod?.id, name, start, end, color) { ok ->
                        if (ok) showPeriodDialog = false
                        else warningMessage = "Период с такой датой начала уже есть. Выберите другую дату начала."
                    }
                }
                val editing = editingPeriod
                if (editing != null) confirm.save("Период «${editing.name}»") { save() } else save()
            }
        )
    }

    if (trainingToDelete != null) {
        val target = trainingToDelete!!
        AlertDialog(
            onDismissRequest = { trainingToDelete = null },
            title = { Text(text = if (target.training.isSkipped) "Удалить отметку о пропуске?" else "Удалить тренировку?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Запись за ${formatDate(target.training.date)} будет удалена без возможности восстановления.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrainingSession(target.training)
                        trainingToDelete = null
                    },
                    colors = destructiveButtonColors()
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { trainingToDelete = null }) { Text("Отмена") }
            }
        )
    }

    exerciseToRemove?.let { (item, res) ->
        val isLast = item.exerciseResults.size <= 1
        val exName = res.exerciseName.ifBlank { exercisesList.find { it.id == res.exerciseId }?.name ?: "Упражнение" }
        AlertDialog(
            onDismissRequest = { exerciseToRemove = null },
            title = { Text("Удалить упражнение?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isLast) "«$exName» — последнее упражнение, поэтому вся тренировка за ${formatDate(item.training.date)} будет удалена."
                    else "«$exName» будет убрано из тренировки за ${formatDate(item.training.date)}. Рекорды пересчитаются автоматически."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isLast) viewModel.deleteTrainingSession(item.training)
                        else viewModel.deleteExerciseResultEntry(res)
                        exerciseToRemove = null
                    },
                    colors = destructiveButtonColors()
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { exerciseToRemove = null }) { Text("Отмена") } }
        )
    }

    if (warningMessage != null) {
        AlertDialog(
            onDismissRequest = { warningMessage = null },
            title = { Text(text = "Внимание", fontWeight = FontWeight.Bold) },
            text = { Text(text = warningMessage!!) },
            confirmButton = {
                TextButton(onClick = { warningMessage = null }) {
                    Text("Понятно")
                }
            }
        )
    }

    pendingMove?.let { (from, to) ->
        val fmt = remember { SimpleDateFormat("d MMMM", Locale("ru")) }
        val moving = trainingsList.filter { startOfDay(it.training.date) == startOfDay(from) }
        val targetBusy = trainingsList.any { startOfDay(it.training.date) == startOfDay(to) }
        AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("Перенести на ${fmt.format(Date(to))}?") },
            text = {
                Text(
                    "Записи за ${fmt.format(Date(from))} (${moving.size} ${pluralRu(moving.size, "запись", "записи", "записей")}) " +
                        "переедут на ${fmt.format(Date(to))}. Результаты сохранятся, рекорды пересчитаются по новой дате." +
                        if (targetBusy) " В этот день уже есть записи — они останутся рядом." else ""
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.moveTrainingDay(from, to)
                    selectedDateMillis = to
                    pendingMove = null
                }) { Text("Перенести") }
            },
            dismissButton = { TextButton(onClick = { pendingMove = null }) { Text("Отмена") } }
        )
    }

    openedExercise?.let { result ->
        val key = result.recordKey()
        ExerciseResultDetailsDialog(
            result = result,
            record = records.find { it.key == key },
            onDismiss = { openedExercise = null },
            onOpenHistory = {
                openedExercise = null
                openedRecordKey = key
            }
        )
    }

    openedRecordKey?.let { key ->
        records.find { it.key == key }?.let { record ->
            RecordDetailScreen(
                record = record,
                goal = goals.firstOrNull { it.recordKey == key },
                onDismiss = { openedRecordKey = null }
            )
        }
    }

    // New Training Dialog
    if (showAddTrainingDialog) {
        AddTrainingDialog(
            exercisesList = exercisesList,
            initialDate = selectedDateMillis ?: System.currentTimeMillis(),
            existingInputs = emptyList(),
            dialogTitle = "Новая тренировка",
            onDismiss = { showAddTrainingDialog = false },
            onConfirm = { date, exerciseInputs, notes ->
                viewModel.addTrainingSession(
                    date = date,
                    trainingType = TrainingType.STRENGTH,
                    intensity = 8,
                    durationMinutes = 60,
                    notes = notes,
                    exerciseInputs = exerciseInputs
                )
                showAddTrainingDialog = false
            },
            onSaveExercise = { cfg ->
                viewModel.saveExerciseToDatabase(cfg)
            },
            onUpdateExercise = { exercise, cfg ->
                viewModel.updateExercise(exercise, cfg)
            },
            inventoryItems = inventoryItems,
            inventoryActions = viewModel.inventoryActions,
            complexes = complexes,
            onSaveComplex = { id, name, description, ids -> viewModel.saveComplex(id, name, description, ids) },
            onDeleteExercise = { viewModel.deleteExerciseFromCatalog(it) }
        )
    }

    // Edit Existing Training Dialog
    if (editingTrainingItem != null) {
        val session = editingTrainingItem!!.training
        val existingInputs = remember(editingTrainingItem) {
            editingTrainingItem!!.exerciseResults.map { exRes ->
                val exName = if (exRes.exerciseName.isNotBlank()) exRes.exerciseName
                             else exercisesList.find { it.id == exRes.exerciseId }?.name ?: "Упражнение"
                val (pVal, sVal) = extractPrimarySecondaryValues(exRes)
                val setResList = exRes.setResultsJson?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: emptyList()
                val loaded = com.example.diarytripeljump.data.RecordsEngine.isLoadedImplement(exRes.inventoryUnit, exRes.resultUnit)

                ExerciseResultInput(
                    exerciseId = exRes.exerciseId,
                    exerciseName = exName,
                    result = exRes.result,
                    unit = exRes.resultUnit,
                    primaryValue = pVal,
                    secondaryValue = sVal,
                    sets = exRes.sets,
                    reps = exRes.reps,
                    setResults = setResList,
                    isSingleLeg = exRes.isSingleLeg,
                    legSide = exRes.legSide,
                    inventoryName = exRes.inventoryName,
                    inventoryUnit = exRes.inventoryUnit,
                    // вес штанги = результат, в форме он не хранится отдельно
                    inventoryValue = if (loaded) null else exRes.inventoryValue,
                    inSpikes = exRes.inSpikes,
                    runUp = exRes.runUpJson?.let { RunUp.decode(it) },
                    titleLayout = exRes.titleLayout,
                    repsPerSet = SetResults.parseReps(exRes.repsPerSetJson),
                    isTimed = exRes.isTimed,
                    notes = exRes.notes
                )
            }
        }

        AddTrainingDialog(
            exercisesList = exercisesList,
            initialDate = session.date,
            keepInitialTime = true,
            existingInputs = existingInputs,
            initialNotes = session.notes.orEmpty(),
            dialogTitle = "Тренировка",
            onDismiss = { editingTrainingItem = null },
            onConfirm = { date, exerciseInputs, notes ->
                viewModel.updateTrainingSessionWithExercises(
                    sessionId = session.id,
                    date = date,
                    exerciseInputs = exerciseInputs,
                    notes = notes
                )
                editingTrainingItem = null
            },
            onSaveExercise = { cfg ->
                viewModel.saveExerciseToDatabase(cfg)
            },
            onUpdateExercise = { exercise, cfg ->
                viewModel.updateExercise(exercise, cfg)
            },
            inventoryItems = inventoryItems,
            inventoryActions = viewModel.inventoryActions,
            complexes = complexes,
            onSaveComplex = { id, name, description, ids -> viewModel.saveComplex(id, name, description, ids) },
            onDeleteExercise = { viewModel.deleteExerciseFromCatalog(it) }
        )
    }

    if (showMarkSkippedDialog) {
        MarkSkippedDayDialog(
            initialDate = selectedDateMillis ?: System.currentTimeMillis(),
            initialReason = "",
            onDismiss = { showMarkSkippedDialog = false },
            onConfirm = { date, reason ->
                viewModel.addSkippedDaySession(date, reason)
                showMarkSkippedDialog = false
            }
        )
    }

    if (editingSkippedTrainingItem != null) {
        val session = editingSkippedTrainingItem!!.training
        MarkSkippedDayDialog(
            initialDate = session.date,
            initialReason = session.skipReason ?: "",
            onDismiss = { editingSkippedTrainingItem = null },
            onConfirm = { date, reason ->
                confirm.save("Отметка о пропуске") {
                    viewModel.deleteTrainingSession(session)
                    viewModel.addSkippedDaySession(date, reason)
                    editingSkippedTrainingItem = null
                }
            }
        )
    }
}

private val PeriodColorOptions = listOf(
    "#2E7D6B", "#2B5CA8", "#B8471F", "#8E44AD", "#C79100", "#D81B60", "#00838F", "#6D4C41"
)

/** Карточка периода: цветная полоса слева, даты, длительность, статус и прогресс текущего периода. */
@Composable
private fun PeriodCard(
    period: TrainingPeriod,
    todayStart: Long,
    onShow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val color = parseHexColor(period.colorHex)
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }
    val start = startOfDay(period.startDate)
    val end = startOfDay(period.endDate)
    val totalDays = (Math.round((end - start) / 86_400_000.0) + 1).toInt()
    val status = when {
        todayStart < start -> 0
        todayStart > end -> 2
        else -> 1
    }
    val passed = if (status == 1) (Math.round((todayStart - start) / 86_400_000.0) + 1).toInt() else 0

    AppCard(
        onClick = onShow,
        containerColor = color.copy(alpha = if (status == 1) 0.10f else 0.04f),
        border = BorderStroke(1.dp, color.copy(alpha = if (status == 1) 0.55f else 0.25f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 8.dp, bottom = 12.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = period.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Text(
                            text = "${fmt.format(Date(period.startDate))} — ${fmt.format(Date(period.endDate))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    OverflowMenu(
                        actions = listOf(
                            MenuAction("Редактировать", Icons.Default.Edit, onClick = onEdit),
                            MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDelete)
                        )
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = when (status) {
                            0 -> "Впереди"
                            1 -> "Идёт сейчас"
                            else -> "Завершён"
                        },
                        contentColor = if (status == 1) Color.White else color,
                        containerColor = if (status == 1) color else color.copy(alpha = 0.16f)
                    )
                    StatusPill(
                        text = "$totalDays ${pluralRu(totalDays, "день", "дня", "дней")}",
                        contentColor = cs.onSurfaceVariant,
                        containerColor = cs.surfaceContainerHigh
                    )
                }
                if (status == 1) {
                    Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(
                            progress = { passed.toFloat() / totalDays },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = color,
                            trackColor = color.copy(alpha = 0.18f)
                        )
                        Text(
                            text = "День $passed из $totalDays",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Окно «Подготовительный период»: название, даты начала и окончания, цвет на календаре. */
@Composable
fun PeriodEditorDialog(
    initial: TrainingPeriod?,
    existingPeriods: List<TrainingPeriod> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (name: String, start: Long, end: Long, colorHex: String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val today = remember { startOfDay(System.currentTimeMillis()) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var start by remember { mutableStateOf(initial?.startDate ?: today) }
    var end by remember { mutableStateOf(initial?.endDate ?: (today + 28L * 24 * 60 * 60 * 1000)) }
    var colorHex by remember { mutableStateOf(initial?.colorHex ?: PeriodColorOptions.first()) }
    var showNameError by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }

    // Два периода с одной датой начала недопустимы; вложенность и общий конец — можно
    val effectiveStart = minOf(start, end)
    val conflict = remember(existingPeriods, effectiveStart, initial?.id) {
        PeriodRules.findStartConflict(existingPeriods, effectiveStart, initial?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_PERIOD) {
            Text(
                text = if (initial == null) "Подготовительный период" else "Редактировать период",
                fontWeight = FontWeight.Bold
            )
        } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Название периода") },
                    isError = showNameError,
                    supportingText = if (showNameError) {
                        { Text("Введите название") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Даты выбираются одним календарём-диапазоном; быстрые варианты — от даты начала
                val rangeFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("ru")) }
                val days = (Math.round((startOfDay(maxOf(start, end)) - startOfDay(minOf(start, end))) / 86_400_000.0) + 1).toInt()
                Surface(
                    onClick = { pickingStart = true },
                    shape = RoundedCornerShape(14.dp),
                    color = parseHexColor(colorHex).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, parseHexColor(colorHex).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = parseHexColor(colorHex))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${rangeFmt.format(Date(minOf(start, end)))} — ${rangeFmt.format(Date(maxOf(start, end)))}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "$days ${pluralRu(days, "день", "дня", "дней")} · нажмите, чтобы выбрать на календаре",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(4, 6, 8, 12).forEach { weeks ->
                        val presetEnd = minOf(start, end) + (weeks * 7 - 1) * 86_400_000L
                        FilterChip(
                            selected = days == weeks * 7,
                            onClick = {
                                start = minOf(start, end)
                                end = presetEnd
                            },
                            label = { Text("$weeks нед.") }
                        )
                    }
                }
                if (conflict != null) {
                    val fmtC = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                    Text(
                        text = "Период «${conflict.name}» уже начинается ${fmtC.format(Date(conflict.startDate))}. " +
                            "Выберите другую дату начала — период может вкладываться в другой или заканчиваться в тот же день.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                }

                Text(text = "Цвет на календаре", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PeriodColorOptions.forEach { hex ->
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(hex))
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Выбрано",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = conflict == null, onClick = {
                if (name.isBlank()) showNameError = true
                else onSave(name.trim(), minOf(start, end), maxOf(start, end), colorHex)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (pickingStart) {
        PeriodRangePickerDialog(
            initialStart = minOf(start, end),
            initialEnd = maxOf(start, end),
            onDismiss = { pickingStart = false },
            onConfirm = { s, e ->
                start = s
                end = e
                pickingStart = false
            }
        )
    }
}

/** Выбор периода на одном календаре: первое нажатие — начало, второе — конец, диапазон подсвечивается. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodRangePickerDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long) -> Unit
) {
    // DateRangePicker работает в UTC: переводим локальные даты в UTC-полночь и обратно
    fun toUtc(local: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = local }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    fun fromUtc(utc: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toUtc(initialStart),
        initialSelectedEndDateMillis = toUtc(initialEnd)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: return@TextButton
                    onConfirm(fromUtc(s), fromUtc(e))
                }
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    text = "Выберите начало и конец периода",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            },
            showModeToggle = false
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun calDayKey(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun formatDayTitle(millis: Long): String {
    return SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        .format(Date(millis))
        .replaceFirstChar { it.uppercase() }
}

/** Календарь месяца: крупные ячейки (≈48dp), индикаторы тренировок, пропусков и стартов. */
@Composable
fun CompactTrainingCalendarWidget(
    currentMonth: Calendar,
    onMonthChange: (Calendar) -> Unit,
    selectedDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    trainingsList: List<TrainingWithExercises>,
    competitionsList: List<Competition> = emptyList(),
    periods: List<TrainingPeriod> = emptyList(),
    /** Перенос записей дня: удерживайте день с тренировкой и перетащите его на другой. */
    onMoveDay: ((fromDay: Long, toDay: Long) -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val monthFormat = remember { SimpleDateFormat("LLLL yyyy", Locale("ru")) }

    // перетаскивание дня: границы ячеек в координатах окна, откуда тянем и где сейчас палец
    val cellBounds = remember { HashMap<Long, GeoRect>() }
    var dragFrom by remember { mutableStateOf<Long?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val dropTarget = dragFrom?.let { cellBounds.entries.firstOrNull { (_, r) -> r.contains(dragPos) }?.key }
    val monthTitle = remember(currentMonth.timeInMillis) {
        monthFormat.format(currentMonth.time).replaceFirstChar { it.uppercase() }
    }

    val trainedKeys = remember(trainingsList) {
        trainingsList.filter { !it.training.isSkipped }.map { calDayKey(it.training.date) }.toSet()
    }
    val skippedKeys = remember(trainingsList) {
        trainingsList.filter { it.training.isSkipped }.map { calDayKey(it.training.date) }.toSet()
    }
    val competitionKeys = remember(competitionsList) {
        competitionsList.map { calDayKey(it.date) }.toSet()
    }

    // Границы периодов — по началу дня, чтобы сравнение было по датам, а не по времени
    val periodRanges = remember(periods) {
        periods.map { Triple(it, startOfDay(it.startDate), startOfDay(it.endDate)) }
    }
    val monthPeriods = remember(periods, currentMonth.timeInMillis) {
        val first = (currentMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis.let { startOfDay(it) }
        val last = (currentMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.timeInMillis.let { startOfDay(it) }
        periodRanges.filter { (_, st, en) -> st <= last && en >= first }.map { it.first }
    }

    val selectedKey = selectedDateMillis?.let { calDayKey(it) }
    val todayKey = remember { calDayKey(System.currentTimeMillis()) }

    val weeks = remember(currentMonth.timeInMillis) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        var offset = cal.get(Calendar.DAY_OF_WEEK) - 2
        if (offset < 0) offset += 7
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = mutableListOf<Calendar?>()
        repeat(offset) { cells.add(null) }
        for (d in 1..maxDays) {
            val dayCal = cal.clone() as Calendar
            dayCal.set(Calendar.DAY_OF_MONTH, d)
            cells.add(dayCal)
        }
        while (cells.size % 7 != 0) cells.add(null)
        cells.chunked(7)
    }

    val isCurrentMonth = remember(currentMonth.timeInMillis) {
        val now = Calendar.getInstance()
        now.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
    }

    AppCard {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val newCal = currentMonth.clone() as Calendar
                    newCal.add(Calendar.MONTH, -1)
                    onMonthChange(newCal)
                }) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц")
                }

                Text(
                    text = monthTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center
                )

                if (!isCurrentMonth || selectedKey != todayKey) {
                    TextButton(onClick = {
                        onMonthChange(Calendar.getInstance())
                        onDateSelected(System.currentTimeMillis())
                    }) { Text(text = "Сегодня") }
                }

                IconButton(onClick = {
                    val newCal = currentMonth.clone() as Calendar
                    newCal.add(Calendar.MONTH, 1)
                    onMonthChange(newCal)
                }) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Следующий месяц")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEachIndexed { column, dayCal ->
                        if (dayCal == null) {
                            Spacer(modifier = Modifier.weight(1f))
                        } else {
                            val key = calDayKey(dayCal.timeInMillis)
                            val dayStart = startOfDay(dayCal.timeInMillis)
                            val isSelected = key == selectedKey
                            val isToday = key == todayKey
                            val hasCompetition = key in competitionKeys
                            val hasTraining = key in trainedKeys
                            val hasSkipped = key in skippedKeys

                            // Периоды могут вкладываться: «главный» — самый короткий, остальные — тонкие полоски снизу
                            val covering = periodRanges.filter { (_, st, en) -> dayStart in st..en }
                            val period = covering.minWithOrNull(
                                compareBy<Triple<TrainingPeriod, Long, Long>> { it.third - it.second }.thenByDescending { it.second }
                            )
                            val otherPeriods = covering.filter { it !== period }.take(2)
                            val periodColor = period?.let { parseHexColor(it.first.colorHex) }
                            val isPeriodStart = period != null && dayStart == period.second
                            val isPeriodEnd = period != null && dayStart == period.third
                            val isEdge = isPeriodStart || isPeriodEnd
                            val canDrag = onMoveDay != null && (hasTraining || hasSkipped)
                            val isDragSource = dragFrom == dayStart
                            val isDropTarget = dropTarget == dayStart && dragFrom != dayStart

                            val dotOnSelected = Color.White
                            var circleModifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) cs.primary else Color.Transparent)
                            circleModifier = when {
                                isDropTarget -> circleModifier.border(BorderStroke(2.5.dp, cs.tertiary), CircleShape)
                                isSelected -> circleModifier
                                isToday -> circleModifier.border(BorderStroke(1.5.dp, cs.primary), CircleShape)
                                else -> circleModifier
                            }
                            val onFill = isSelected

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .onGloballyPositioned { cellBounds[dayStart] = it.boundsInRoot() }
                                    .alpha(if (isDragSource) 0.4f else 1f)
                                    .then(
                                        if (!canDrag) Modifier else Modifier.pointerInput(dayStart) {
                                            // долгое нажатие и перетаскивание переносит записи дня на другую дату
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { local ->
                                                    dragFrom = dayStart
                                                    dragPos = (cellBounds[dayStart]?.topLeft ?: Offset.Zero) + local
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, delta ->
                                                    change.consume()
                                                    dragPos += delta
                                                },
                                                onDragEnd = {
                                                    val from = dragFrom
                                                    val to = cellBounds.entries.firstOrNull { (_, r) -> r.contains(dragPos) }?.key
                                                    dragFrom = null
                                                    if (from != null && to != null && to != from) onMoveDay?.invoke(from, to)
                                                },
                                                onDragCancel = { dragFrom = null }
                                            )
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Период — обводка без заливки: сплошной контур через неделю,
                                // скругляется на днях начала и окончания периода
                                if (periodColor != null) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val stroke = 1.6.dp.toPx()
                                        val bandH = size.height * 0.78f
                                        val top = (size.height - bandH) / 2 + stroke / 2
                                        val bottom = top + bandH - stroke
                                        val r = (bottom - top) / 2
                                        val inset = 3.dp.toPx() + stroke / 2
                                        val left = if (isPeriodStart) inset else 0f
                                        val right = if (isPeriodEnd) size.width - inset else size.width
                                        val outline = Path()
                                        when {
                                            isPeriodStart && isPeriodEnd ->
                                                outline.addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(r)))
                                            isPeriodStart -> {
                                                outline.moveTo(right, top)
                                                outline.lineTo(left + r, top)
                                                outline.arcTo(GeoRect(left, top, left + 2 * r, bottom), 270f, -180f, false)
                                                outline.lineTo(right, bottom)
                                            }
                                            isPeriodEnd -> {
                                                outline.moveTo(left, top)
                                                outline.lineTo(right - r, top)
                                                outline.arcTo(GeoRect(right - 2 * r, top, right, bottom), 270f, 180f, false)
                                                outline.lineTo(left, bottom)
                                            }
                                            else -> {
                                                outline.moveTo(0f, top)
                                                outline.lineTo(size.width, top)
                                                outline.moveTo(0f, bottom)
                                                outline.lineTo(size.width, bottom)
                                            }
                                        }
                                        drawPath(outline, periodColor, style = Stroke(width = stroke))
                                    }
                                }
                                // Внешние (вложенные) периоды — непрерывные линии снизу ячейки
                                if (otherPeriods.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        otherPeriods.forEach { (p, st, en) ->
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .padding(
                                                        start = if (dayStart == st) 6.dp else 0.dp,
                                                        end = if (dayStart == en) 6.dp else 0.dp
                                                    )
                                                    .background(parseHexColor(p.colorHex).copy(alpha = 0.8f))
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = circleModifier.clickable { onDateSelected(dayCal.timeInMillis) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || isToday || isEdge || hasTraining || hasCompetition) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> cs.onPrimary
                                                isToday -> cs.primary
                                                else -> cs.onSurface
                                            }
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.height(5.dp)
                                        ) {
                                            if (hasCompetition) {
                                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (onFill) dotOnSelected else cs.tertiary))
                                            }
                                            if (hasTraining) {
                                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (onFill) dotOnSelected else cs.primary))
                                            }
                                            if (hasSkipped) {
                                                Box(Modifier.size(5.dp).clip(CircleShape).background(if (onFill) dotOnSelected else cs.error))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = cs.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = cs.primary, label = "Тренировки")
                LegendItem(color = cs.error, label = "Пропуски")
                LegendItem(color = cs.tertiary, label = "Старты")
            }
            if (onMoveDay != null) {
                Text(
                    text = if (dragFrom != null) "Отпустите на нужном дне" else "Удерживайте день с тренировкой и перетащите, чтобы перенести",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (dragFrom != null) cs.tertiary else cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            if (monthPeriods.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    monthPeriods.forEach { period ->
                        LegendItem(color = parseHexColor(period.colorHex), label = period.name)
                    }
                    if (monthPeriods.size > 1) {
                        Text(
                            text = "линия снизу — внешний период",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Строка упражнения в карточке тренировки, посчитанная заранее. */
private class ExerciseRowUi(
    val result: ExerciseResult,
    val name: String,
    val title: String,
    val best: Double?,
    val detail: String?,
    val runUpLines: List<String>?
)

private fun exerciseRowUi(exRes: ExerciseResult, exercisesList: List<Exercise>): ExerciseRowUi {
    val exName = if (exRes.exerciseName.isNotBlank()) exRes.exerciseName
    else exercisesList.find { it.id == exRes.exerciseId }?.name ?: "Упражнение"
    // справа — лучший результат крупно, под названием — все попытки мелко
    val attempts = exRes.setResultsJson?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.filter { it > 0 } ?: emptyList()
    val lowerBetter = RecordsEngine.isLowerBetter(exName, exRes.resultUnit)
    val candidates = attempts + listOfNotNull(exRes.result.takeIf { it > 0 })
    val repsPlan = SetResults.repsBySet(exRes.sets, exRes.reps, exRes.repsPerSetJson)
    val runUpLines = exRes.runUpJson?.let { RunUp.decode(it) }?.map { RunUp.describe(it) }
    return ExerciseRowUi(
        result = exRes,
        name = exName,
        title = RecordsEngine.exerciseTitle(
            exName, exRes.isSingleLeg, exRes.legSide, exRes.inSpikes,
            exRes.inventoryName, exRes.inventoryUnit, exRes.inventoryValue, exRes.resultUnit, exRes.titleLayout
        ),
        best = if (lowerBetter) candidates.minOrNull() else candidates.maxOrNull(),
        detail = when {
            runUpLines != null -> runUpLines.mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n").ifBlank { "Попытки не заполнены" }
            attempts.size > 1 -> SetResults.summary(attempts, repsPlan, { plainAttempt(it) }, exRes.isTimed)
            repsPlan.isNotEmpty() -> SetResults.label(repsPlan, exRes.isTimed)
            else -> null
        },
        runUpLines = runUpLines
    )
}

/** Ключ рекорда, к которому относится упражнение из тренировки. */
private fun ExerciseResult.recordKey(): String = RecordsEngine.exerciseRecordKey(
    exerciseName.trim(), isSingleLeg, legSide, resultUnit, inSpikes,
    RecordsEngine.recordInventory(inventoryName, inventoryUnit, inventoryValue, resultUnit)
)

/** Подробности упражнения из сохранённой тренировки: все подходы, лучший результат и сравнение с рекордом. */
@Composable
private fun ExerciseResultDetailsDialog(
    result: ExerciseResult,
    record: RecordSummary?,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val title = RecordsEngine.exerciseTitle(
        result.exerciseName, result.isSingleLeg, result.legSide, result.inSpikes,
        result.inventoryName, result.inventoryUnit, result.inventoryValue, result.resultUnit, result.titleLayout
    )
    val attempts = result.setResultsJson?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }.orEmpty()
    val lowerBetter = RecordsEngine.isLowerBetter(result.exerciseName, result.resultUnit)
    val filled = attempts.filter { it > 0 } + listOfNotNull(result.result.takeIf { it > 0 })
    val best = if (lowerBetter) filled.minOrNull() else filled.maxOrNull()
    val plan = SetResults.repsBySet(result.sets, result.reps, result.repsPerSetJson)
    val lines = SetResults.setLines(attempts, plan, { formatRecordValue(it, result.resultUnit) }, result.isTimed)
    val runUp = result.runUpJson?.let { RunUp.decode(it) }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_DETAILS) { Text(text = styledTitle(title, result.exerciseName), style = MaterialTheme.typography.titleLarge) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date(result.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                if (best != null) {
                    Column {
                        Text(text = "Лучший результат", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        Text(text = formatRecordValue(best, result.resultUnit), style = NumberLarge, color = cs.onSurface)
                    }
                }
                if (runUp.isNotEmpty()) {
                    runUp.forEachIndexed { i, attempt ->
                        Text(text = "${i + 1}. ${RunUp.describe(attempt)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                } else {
                    lines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                    }
                }
                if (record != null) {
                    HorizontalDivider(color = cs.outlineVariant)
                    Text(
                        text = if (best != null && best == record.best) "Это рекорд упражнения"
                        else "Рекорд упражнения: ${formatRecordValue(record.best, record.unit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = cs.primary
                    )
                }
                if (!result.notes.isNullOrBlank()) {
                    Text(text = result.notes, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = if (record != null) {
            { TextButton(onClick = onOpenHistory) { Text("История упражнения") } }
        } else null
    )
}

/** Карточка тренировки: меню действий «⋮», превью первого упражнения, раскрытие списка. */
@Composable
fun TrainingSessionCard(
    trainingItem: TrainingWithExercises,
    exercisesList: List<Exercise>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteExercise: (ExerciseResult) -> Unit = {},
    /** Нажатие на упражнение открывает его подробности. */
    onOpenExercise: (ExerciseResult) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val session = trainingItem.training
    val isSkipped = session.isSkipped
    val accent = if (isSkipped) cs.error else cs.primary
    val exerciseCount = trainingItem.exerciseResults.size

    var isExpanded by remember { mutableStateOf(false) }
    // названия, лучшие результаты и подписи считаются один раз, а не при каждом появлении карточки на экране
    val rowsUi = remember(trainingItem.exerciseResults, exercisesList) {
        trainingItem.exerciseResults.map { exerciseRowUi(it, exercisesList) }
    }
    val exercisesToRender = if (isExpanded) rowsUi else rowsUi.take(1)
    val timeText = remember(session.date) { SimpleDateFormat("HH:mm", Locale("ru")).format(Date(session.date)) }

    AppCard(
        containerColor = if (isSkipped) cs.errorContainer.copy(alpha = 0.25f) else cs.surface
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = if (isSkipped) Icons.Default.Block else getTrainingTypeIcon(session.trainingType),
                    tint = accent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Заголовок по содержанию: первые упражнения вместо одинакового «Тренировка»
                    val names = remember(rowsUi) { rowsUi.filter { it.result.exerciseName.isNotBlank() }.map { it.title }.distinct() }
                    Text(
                        text = when {
                            isSkipped -> "Пропуск тренировки"
                            names.isEmpty() -> "Тренировка"
                            names.size <= 2 -> names.joinToString(", ")
                            else -> names.take(2).joinToString(", ") + " +${names.size - 2}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSkipped) "Причина: ${session.skipReason ?: "не указана"}"
                        else timeText +
                            ", $exerciseCount ${pluralRu(exerciseCount, "упражнение", "упражнения", "упражнений")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OverflowMenu(
                    actions = listOf(
                        MenuAction("Редактировать", Icons.Default.Edit, onClick = onEditClick),
                        MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDeleteClick)
                    )
                )
            }

            if (!isSkipped && !session.notes.isNullOrBlank()) {
                NoteLine(text = session.notes, modifier = Modifier.padding(end = 12.dp, top = 6.dp))
            }

            if (!isSkipped && trainingItem.exerciseResults.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(end = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = cs.outlineVariant)

                    exercisesToRender.forEach { row ->
                        val exRes = row.result
                        val exName = row.name
                        val best = row.best
                        val runUpLines = row.runUpLines
                        val detail = row.detail
                        val fullTitle = row.title

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenExercise(exRes) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = styledTitle(fullTitle, exName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurface
                                )
                                if (detail != null) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = if (runUpLines != null) Int.MAX_VALUE else 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!exRes.notes.isNullOrBlank()) {
                                    NoteLine(text = exRes.notes, maxLines = 2)
                                }
                            }
                            if (best != null) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatRecordValue(best, exRes.resultUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary,
                                    textAlign = TextAlign.End
                                )
                            }
                            IconButton(
                                onClick = { onDeleteExercise(exRes) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Удалить упражнение",
                                    tint = cs.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (exerciseCount > 1) {
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Свернуть" else "Ещё ${exerciseCount - 1}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Dialog: Add/Edit Training with Explicit Separated Exercise Cards & Confirmation Popups
/** Наибольшая длина заметки: заметка — пара строк, а не дневник целиком. */
private const val NOTES_MAX = 500

/** Заметка в карточке тренировки: значок и текст курсивом. */
@Composable
private fun NoteLine(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            Icons.AutoMirrored.Filled.Notes,
            contentDescription = "Заметка",
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = cs.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AddTrainingDialog(
    exercisesList: List<Exercise>,
    initialDate: Long,
    /** true — сохранённая тренировка со своим временем; у новой подставляется текущее время. */
    keepInitialTime: Boolean = false,
    existingInputs: List<ExerciseResultInput> = emptyList(),
    /** Заметка к тренировке в целом. */
    initialNotes: String = "",
    dialogTitle: String = "Новая тренировка",
    onDismiss: () -> Unit,
    onConfirm: (
        date: Long,
        exerciseInputs: List<ExerciseResultInput>,
        notes: String
    ) -> Unit,
    onSaveExercise: (ExerciseConfig) -> Unit,
    onUpdateExercise: (Exercise, ExerciseConfig) -> Unit = { _, _ -> },
    inventoryItems: List<InventoryItem> = emptyList(),
    inventoryActions: InventoryActions = InventoryActions(),
    complexes: List<ComplexUi> = emptyList(),
    onSaveComplex: (id: Long?, name: String, description: String?, exerciseIds: List<Long>) -> Unit = { _, _, _, _ -> },
    onDeleteExercise: (Exercise) -> Unit = {}
) {
    val confirm = LocalConfirm.current
    // дату и время можно поменять и у новой, и у сохранённой тренировки; за день тренировок может быть несколько
    var trainingDate by remember {
        mutableLongStateOf(
            if (keepInitialTime) initialDate
            else startOfDay(initialDate) + (System.currentTimeMillis() - startOfDay(System.currentTimeMillis()))
        )
    }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var editingComplex by remember { mutableStateOf<ComplexUi?>(null) }
    // правка упражнения прямо в тренировке: индекс выполнения и параметры, ожидающие выбора «заменить / создать»
    var variantEditIndex by remember { mutableStateOf<Int?>(null) }
    var pendingVariant by remember { mutableStateOf<Pair<Int, ExerciseConfig>?>(null) }
    var showCreateComplexModal by remember { mutableStateOf(false) }
    var exerciseToDeleteFromCatalog by remember { mutableStateOf<Exercise?>(null) }
    var showCreateExerciseModal by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }

    var addedExerciseInputs by remember { mutableStateOf(existingInputs) }
    var trainingNotes by remember { mutableStateOf(initialNotes) }

    // Deletion & Save Confirmation States
    var itemToDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }

    val hasResults = addedExerciseInputs.any { input -> input.result > 0 || input.setResults.any { it > 0 } }
    val dateError = EntryRules.dateError(trainingDate, hasResults)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_TRAINING) {
            Text(text = dialogTitle, style = MaterialTheme.typography.titleLarge)
        } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.coachTarget("training.date"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField(label = "Дата тренировки", millis = trainingDate, onClick = { pickingDate = true }, modifier = Modifier.weight(1f))
                    TimeField(label = "Время", millis = trainingDate, onClick = { pickingTime = true })
                }
                if (dateError != null) {
                    Text(text = dateError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                OutlinedTextField(
                    value = trainingNotes,
                    onValueChange = { trainingNotes = it.take(NOTES_MAX) },
                    label = { Text("Заметка к тренировке") },
                    placeholder = { Text("Самочувствие, погода, задачи на тренировку") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().coachTarget("training.notes")
                )

                FilledTonalButton(
                    onClick = { showExercisePicker = true },
                    modifier = Modifier.fillMaxWidth().coachTarget("training.add"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Добавить упражнение")
                }

                if (addedExerciseInputs.isNotEmpty()) {
                    Text(
                        text = "В тренировке · ${addedExerciseInputs.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    addedExerciseInputs.forEachIndexed { index, input ->
                        ExerciseResultEditorCard(
                            input = input,
                            onUpdate = { updatedInput ->
                                addedExerciseInputs = addedExerciseInputs.mapIndexed { i, item ->
                                    if (i == index) updatedInput else item
                                }
                            },
                            onRemove = {
                                itemToDeleteIndex = index
                            },
                            onEditExercise = { variantEditIndex = index }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }

                Button(
                    enabled = dateError == null,
                    onClick = {
                        if (addedExerciseInputs.isNotEmpty()) {
                            showSaveConfirmDialog = true
                        } else {
                            onConfirm(trainingDate, addedExerciseInputs, trainingNotes)
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            }
        }
    )

    if (showExercisePicker) {
        ExercisePickerScreen(
            title = "Упражнения",
            doneLabel = if (addedExerciseInputs.isEmpty()) "Готово" else "Готово · ${addedExerciseInputs.size}",
            helpTopic = HelpTopic.F_PICKER,
            onDismiss = { showExercisePicker = false }
        ) {
            CatalogExercisePicker(
                exercises = exercisesList,
                isSelected = { ex -> addedExerciseInputs.any { it.variantKey() == ex.variantKey() } },
                onToggle = { ex ->
                    val exKey = ex.variantKey()
                    addedExerciseInputs = if (addedExerciseInputs.any { it.variantKey() == exKey }) {
                        addedExerciseInputs.filter { it.variantKey() != exKey }
                    } else addedExerciseInputs + ex.toInput()
                },
                onEdit = { editingExercise = it },
                onDelete = { exerciseToDeleteFromCatalog = it }
            )

            // Комплексы: нажатие добавляет в тренировку все упражнения комплекса
            Text(
                text = "Комплексы",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            run {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { showCreateComplexModal = true },
                        label = { Text("Новый комплекс") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                    complexes.filter { it.exercises.isNotEmpty() }.forEach { c ->
                        val allAdded = c.exercises.all { ex -> addedExerciseInputs.any { it.variantKey() == ex.variantKey() } }
                        AssistChip(
                            onClick = {
                                val toAdd = c.exercises
                                    .filter { ex -> addedExerciseInputs.none { it.variantKey() == ex.variantKey() } }
                                    .map { it.toInput() }
                                addedExerciseInputs = addedExerciseInputs + toAdd
                            },
                            label = { Text("${c.complex.name} · ${c.exercises.size}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (allAdded) Icons.Default.Check else Icons.Default.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            // карандаш на чипе — изменить состав и порядок комплекса
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Изменить комплекс",
                                    modifier = Modifier
                                        .size(AssistChipDefaults.IconSize)
                                        .clickable { editingComplex = c }
                                )
                            }
                        )
                    }
                }
            }

            // Разбег — отдельное упражнение: вместо результата окно с попытками
            OutlinedButton(
                onClick = {
                    if (addedExerciseInputs.none { it.runUp != null }) {
                        addedExerciseInputs = addedExerciseInputs + ExerciseResultInput(
                            exerciseId = 0, exerciseName = RunUp.NAME, result = 0.0, unit = ResultUnit.CM, runUp = emptyList()
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = RunUp.NAME)
            }

            // Создание упражнения — в конструкторе
            FilledTonalButton(
                onClick = { showCreateExerciseModal = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Создать упражнение")
            }
        }
    }

    exerciseToDeleteFromCatalog?.let { target ->
        DeleteExerciseDialog(
            name = target.name,
            onDismiss = { exerciseToDeleteFromCatalog = null },
            onConfirm = {
                onDeleteExercise(target)
                exerciseToDeleteFromCatalog = null
            }
        )
    }

    // Delete Confirmation Popup Dialog
    if (itemToDeleteIndex != null) {
        val targetItem = addedExerciseInputs.getOrNull(itemToDeleteIndex!!)
        AlertDialog(
            onDismissRequest = { itemToDeleteIndex = null },
            title = { Text(text = "Удаление упражнения", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Вы действительно хотите удалить упражнение '${targetItem?.exerciseName ?: ""}' из этой тренировки?") },
            confirmButton = {
                Button(
                    onClick = {
                        addedExerciseInputs = addedExerciseInputs.filterIndexed { i, _ -> i != itemToDeleteIndex }
                        itemToDeleteIndex = null
                    },
                    colors = destructiveButtonColors()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeleteIndex = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Save Confirmation Popup Dialog
    if (showSaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmDialog = false },
            title = { Text(text = "Сохранение тренировки", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Сохранить тренировку с ${addedExerciseInputs.size} упражнениями?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirmDialog = false
                        onConfirm(trainingDate, addedExerciseInputs, trainingNotes)
                    }
                ) {
                    Text("Да, сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Создание и правка комплекса прямо из окна тренировки
    if (showCreateComplexModal || editingComplex != null) {
        val target = editingComplex
        ComplexEditorDialog(
            initial = target,
            allExercises = exercisesList.distinctBy { it.variantKey() },
            onDismiss = { showCreateComplexModal = false; editingComplex = null },
            onSave = { name, description, ids ->
                onSaveComplex(target?.complex?.id, name, description, ids)
                showCreateComplexModal = false
                editingComplex = null
            }
        )
    }

    if (pickingDate) {
        AppDatePickerDialog(
            initialMillis = trainingDate,
            onDismiss = { pickingDate = false },
            onConfirm = {
                // выбранный день, время остаётся прежним
                trainingDate = startOfDay(it) + (trainingDate - startOfDay(trainingDate))
                pickingDate = false
            }
        )
    }

    if (pickingTime) {
        AppTimePickerDialog(
            initialMillis = trainingDate,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                trainingDate = startOfDay(trainingDate) + (hour * 60L + minute) * 60_000L
                pickingTime = false
            }
        )
    }

    // Правка упражнения прямо в тренировке: конструктор с параметрами этого выполнения
    variantEditIndex?.let { index ->
        val input = addedExerciseInputs.getOrNull(index)
        if (input == null) {
            variantEditIndex = null
        } else {
            val catalogEx = exercisesList.firstOrNull { it.id == input.exerciseId }
                ?: exercisesList.firstOrNull { it.variantKey() == input.variantKey() }
            ExerciseEditorDialog(
                initial = null,
                initialConfig = input.toConfig(catalogEx?.categoryName ?: "Силовое упражнение"),
                inventoryItems = inventoryItems,
                inventoryActions = inventoryActions,
                onDismiss = { variantEditIndex = null },
                onConfirm = { cfg ->
                    variantEditIndex = null
                    // ничего, что отличает упражнение, не поменялось — просто обновить параметры
                    if (cfg.variantKey() == input.variantKey() && cfg.name.trim() == input.exerciseName.trim()) {
                        addedExerciseInputs = addedExerciseInputs.mapIndexed { i, item -> if (i == index) item.withConfig(cfg) else item }
                    } else {
                        pendingVariant = index to cfg
                    }
                }
            )
        }
    }

    // Изменённое упражнение: заменить старое в справочнике или сохранить как новое
    pendingVariant?.let { (index, cfg) ->
        val input = addedExerciseInputs.getOrNull(index)
        if (input == null) {
            pendingVariant = null
        } else {
            val oldKey = input.variantKey()
            val catalogEx = exercisesList.firstOrNull { it.id == input.exerciseId }
                ?: exercisesList.firstOrNull { it.variantKey() == oldKey }
            val newExists = exercisesList.any { it.variantKey() == cfg.variantKey() }
            ExerciseChangedDialog(
                oldTitle = input.title(),
                newTitle = cfg.title(),
                newExists = newExists,
                onDismiss = { pendingVariant = null },
                onCreate = {
                    if (!newExists) onSaveExercise(cfg)
                    addedExerciseInputs = addedExerciseInputs.mapIndexed { i, item ->
                        if (i == index) item.withConfig(cfg, exerciseId = 0) else item
                    }
                    pendingVariant = null
                },
                onReplace = {
                    if (catalogEx != null) onUpdateExercise(catalogEx, cfg) else onSaveExercise(cfg)
                    // в этой тренировке меняются все выполнения прежнего варианта
                    addedExerciseInputs = addedExerciseInputs.map { item ->
                        if (item.variantKey() == oldKey) item.withConfig(cfg) else item
                    }
                    pendingVariant = null
                }
            )
        }
    }

    // Sub-Dialog: Новое упражнение
    if (showCreateExerciseModal) {
        ExerciseEditorDialog(
            initial = null,
            inventoryItems = inventoryItems,
            inventoryActions = inventoryActions,
            onDismiss = { showCreateExerciseModal = false },
            onConfirm = { cfg ->
                onSaveExercise(cfg)
                addedExerciseInputs = addedExerciseInputs + cfg.toInput()
                showCreateExerciseModal = false
            }
        )
    }

    // Sub-Dialog: Редактировать упражнение
    if (editingExercise != null) {
        val target = editingExercise!!
        ExerciseEditorDialog(
            initial = target,
            inventoryItems = inventoryItems,
            inventoryActions = inventoryActions,
            onDismiss = { editingExercise = null },
            onConfirm = { cfg ->
                confirm.save("Упражнение «${target.name}»") {
                    onUpdateExercise(target, cfg)
                    // Уже добавленные в эту тренировку выполнения этого упражнения получают новые параметры
                    val targetKey = target.variantKey()
                    addedExerciseInputs = addedExerciseInputs.map { item ->
                        if (item.variantKey() == targetKey) item.withConfig(cfg) else item
                    }
                    editingExercise = null
                }
            }
        )
    }
}

// Карточка упражнения в форме тренировки: параметры (нога, инвентарь) берутся из справочника
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseResultEditorCard(
    input: ExerciseResultInput,
    onUpdate: (ExerciseResultInput) -> Unit,
    onRemove: () -> Unit,
    /** Открыть конструктор: поменять снаряд, шиповки, ногу или название этого упражнения. */
    onEditExercise: () -> Unit = {}
) {
    var showPerSetDialog by remember { mutableStateOf(false) }
    var showRunUpDialog by remember { mutableStateOf(false) }

    var setsStr by remember(input.exerciseName) { mutableStateOf(input.sets?.toString() ?: "") }
    var repsStr by remember(input.exerciseName) { mutableStateOf(input.reps?.toString() ?: "") }
    // в подходах может быть разное число повторений
    var variedReps by remember(input.exerciseName) { mutableStateOf(input.repsPerSet != null) }
    val perSetReps = remember(input.exerciseName) {
        mutableStateListOf<String>().apply { addAll(input.repsPerSet.orEmpty().map { it.toString() }) }
    }

    fun syncPerSetReps() {
        val sets = (setsStr.toIntOrNull() ?: 1).coerceAtLeast(1)
        while (perSetReps.size < sets) perSetReps.add(repsStr)
        while (perSetReps.size > sets) perSetReps.removeAt(perSetReps.lastIndex)
    }

    fun repsPlan(): List<Int> {
        val sets = (setsStr.toIntOrNull() ?: 1).coerceAtLeast(1)
        val common = (repsStr.toIntOrNull() ?: 10).coerceAtLeast(1)
        return List(sets) { i -> if (variedReps) (perSetReps.getOrNull(i)?.toIntOrNull() ?: 1).coerceAtLeast(1) else common }
    }

    /** Сколько результатов вводится в подходе: при работе на время — один на подход. */
    fun resultPlan(): List<Int> = repsPlan().let { plan -> if (input.isTimed) List(plan.size) { 1 } else plan }

    val cs = MaterialTheme.colorScheme

    // Итоговое значение упражнения (для рекордов) — лучший результат среди подходов
    fun calculateAndEmit(newSetResults: List<Double> = input.setResults, timed: Boolean = input.isTimed) {
        val setsVal = setsStr.filter { it.isDigit() }.toIntOrNull()
        val repsVal = repsStr.filter { it.isDigit() }.toIntOrNull()

        val lowerBetter = RecordsEngine.isLowerBetter(input.exerciseName, input.unit)
        val positives = newSetResults.filter { it > 0 }
        val best = when {
            positives.isEmpty() -> 0.0
            lowerBetter -> positives.min()
            else -> positives.max()
        }

        onUpdate(
            input.copy(
                result = best, sets = setsVal, reps = repsVal, setResults = newSetResults,
                repsPerSet = if (variedReps) repsPlan() else null, isTimed = timed
            )
        )
    }

    /** Переключение повторения / секунды: уже введённые результаты сохраняются. */
    fun setTimed(timed: Boolean) {
        if (timed == input.isTimed) return
        val plan = repsPlan()
        calculateAndEmit(
            if (timed) SetResults.toPerSet(input.setResults, plan) else SetResults.toPerRep(input.setResults, plan),
            timed
        )
    }

    AppCard(containerColor = cs.surfaceContainerLow) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Полное название со снарядом, шиповками и ногой; нажатие открывает конструктор
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditExercise)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = input.styledTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface
                    )
                }
                IconButton(onClick = onEditExercise) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Изменить снаряд, шиповки или ногу",
                        tint = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Убрать упражнение",
                        tint = cs.onSurfaceVariant
                    )
                }
            }

            if (input.runUp != null) {
                input.runUp.forEachIndexed { i, attempt ->
                    Text(
                        text = "${i + 1}. ${RunUp.describe(attempt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showRunUpDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(imageVector = Icons.Default.Straighten, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (input.runUp.isEmpty()) "Ввести попытки разбега" else "Изменить попытки разбега")
                }
            } else {
            Row(
                modifier = Modifier.fillMaxWidth().coachTarget("card.sets"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = setsStr,
                    onValueChange = {
                        setsStr = it.filter { char -> char.isDigit() }
                        if (variedReps) syncPerSetReps()
                        calculateAndEmit()
                    },
                    label = { Text("Подходов") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                if (!variedReps) {
                    OutlinedTextField(
                        value = repsStr,
                        onValueChange = {
                            repsStr = it.filter { char -> char.isDigit() }
                            calculateAndEmit()
                        },
                        label = { Text(if (input.isTimed) "Секунд" else "Повторений") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ToggleChip(label = "Повторения", selected = !input.isTimed, onClick = { setTimed(false) })
                ToggleChip(label = "Секунды", selected = input.isTimed, onClick = { setTimed(true) })
                ToggleChip(
                    label = "Разные в подходах",
                    selected = variedReps,
                    onClick = {
                        variedReps = !variedReps
                        if (variedReps) syncPerSetReps()
                        calculateAndEmit()
                    }
                )
            }
            if (variedReps) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    perSetReps.indices.forEach { i ->
                        OutlinedTextField(
                            value = perSetReps[i],
                            onValueChange = { v ->
                                perSetReps[i] = v.filter { char -> char.isDigit() }
                                calculateAndEmit()
                            },
                            label = { Text("Подход ${i + 1}") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(104.dp)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showPerSetDialog = true },
                modifier = Modifier.fillMaxWidth().coachTarget("card.results")
            ) {
                Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (input.setResults.any { it > 0 }) "Результаты: заполнено ${input.setResults.count { it > 0 }} из ${resultPlan().sum()}"
                    else "Ввести результаты попыток"
                )
            }
            }

            // Заметка к упражнению: поле появляется по нажатию, чтобы не перегружать карточку
            var notesOpen by remember(input.exerciseName) { mutableStateOf(!input.notes.isNullOrBlank()) }
            if (notesOpen) {
                OutlinedTextField(
                    value = input.notes.orEmpty(),
                    onValueChange = { onUpdate(input.copy(notes = it.take(NOTES_MAX))) },
                    label = { Text("Заметка к упражнению") },
                    placeholder = { Text("Техника, ощущения, что поправить") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            notesOpen = false
                            onUpdate(input.copy(notes = null))
                        }) { Icon(Icons.Default.Close, contentDescription = "Убрать заметку") }
                    },
                    modifier = Modifier.fillMaxWidth().coachTarget("card.notes")
                )
            } else {
                TextButton(
                    onClick = { notesOpen = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.coachTarget("card.notes")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить заметку")
                }
            }
        }
    }

    if (showRunUpDialog) {
        RunUpResultsDialog(
            exerciseName = input.title(),
            initial = input.runUp.orEmpty(),
            onDismiss = { showRunUpDialog = false },
            onConfirm = { attempts ->
                onUpdate(input.copy(result = 0.0, runUp = attempts))
                showRunUpDialog = false
            }
        )
    }

    if (showPerSetDialog) {
        PerSetResultsModalDialog(
            exerciseName = input.title(),
            repsBySet = resultPlan(),
            planLabel = SetResults.label(repsPlan(), input.isTimed),
            unit = input.unit,
            initialResults = input.setResults,
            onDismiss = { showPerSetDialog = false },
            onConfirm = { newResults ->
                calculateAndEmit(newResults)
                showPerSetDialog = false
            }
        )
    }
}

/** Строка формы разбега. [board]: 0 — не указано, 1 — заступил, 2 — не доступил, 3 — попал. [move]: 0 — нет, 1 — отнёс, 2 — поднёс. */
private data class RunUpRow(
    val board: Int = 0,
    val boardFeet: String = "",
    val move: Int = 0,
    val moveCm: String = "",
    val moveFeet: String = ""
) {
    fun toAttempt(): RunUpAttempt {
        val feet = boardFeet.toDoubleOrNull()
        val sign = if (move == 2) -1 else 1
        return RunUpAttempt(
            boardFeet = when (board) {
                1 -> feet
                2 -> feet?.let { -it }
                3 -> 0.0
                else -> null
            },
            moveCm = moveCm.toDoubleOrNull()?.takeIf { move != 0 }?.let { it * sign },
            moveFeet = moveFeet.toDoubleOrNull()?.takeIf { move != 0 }?.let { it * sign }
        )
    }
}

private fun plainNumber(value: Double): String = Math.abs(value).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }

private fun RunUpAttempt.toRow(): RunUpRow {
    val moved = moveCm ?: moveFeet
    return RunUpRow(
        board = when {
            boardFeet == null -> 0
            boardFeet > 0 -> 1
            boardFeet < 0 -> 2
            else -> 3
        },
        boardFeet = boardFeet?.takeIf { it != 0.0 }?.let { plainNumber(it) } ?: "",
        move = when {
            moved == null -> 0
            moved > 0 -> 1
            else -> 2
        },
        moveCm = moveCm?.let { plainNumber(it) } ?: "",
        moveFeet = moveFeet?.let { plainNumber(it) } ?: ""
    )
}

/**
 * Окно попыток с разбега: попал или нет, сколько стоп, на сколько отнёс или поднёс отметку.
 * [fixedCount] — у старта число попыток задано, и номер попытки сохраняется вместе с пустыми.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunUpResultsDialog(
    exerciseName: String,
    initial: List<RunUpAttempt>,
    onDismiss: () -> Unit,
    onConfirm: (List<RunUpAttempt>) -> Unit,
    fixedCount: Int? = null
) {
    val cs = MaterialTheme.colorScheme
    val rows = remember {
        mutableStateListOf<RunUpRow>().apply {
            addAll(initial.map { it.toRow() })
            if (fixedCount != null) {
                while (size < fixedCount) add(RunUpRow())
                while (size > fixedCount) removeAt(lastIndex)
            } else if (isEmpty()) {
                add(RunUpRow())
            }
        }
    }
    fun numeric(v: String) = v.replace(',', '.').filter { it.isDigit() || it == '.' }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_RUNUP) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Разбег", style = MaterialTheme.typography.titleLarge)
                Text(text = exerciseName, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
            }
        } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rows.forEachIndexed { i, row ->
                    AppCard(containerColor = cs.surfaceContainerLow) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Попытка ${i + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cs.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (rows.size > 1 && fixedCount == null) {
                                    IconButton(onClick = { rows.removeAt(i) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Убрать попытку", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            FlowRow(
                                modifier = Modifier.coachTarget("runup.board"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf(1 to "Заступил", 2 to "Не доступил", 3 to "Попал").forEach { (code, label) ->
                                    ToggleChip(
                                        label = label,
                                        selected = row.board == code,
                                        onClick = { rows[i] = row.copy(board = if (row.board == code) 0 else code) }
                                    )
                                }
                            }
                            if (row.board == 1 || row.board == 2) {
                                OutlinedTextField(
                                    value = row.boardFeet,
                                    onValueChange = { rows[i] = row.copy(boardFeet = numeric(it)) },
                                    label = { Text("Сколько стоп") },
                                    suffix = { Text("ст.") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1 to "Отнёс", 2 to "Поднёс").forEach { (code, label) ->
                                    ToggleChip(
                                        label = label,
                                        selected = row.move == code,
                                        onClick = { rows[i] = row.copy(move = if (row.move == code) 0 else code) }
                                    )
                                }
                            }
                            if (row.move != 0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = row.moveCm,
                                        onValueChange = { rows[i] = row.copy(moveCm = numeric(it)) },
                                        label = { Text("В см") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = row.moveFeet,
                                        onValueChange = { rows[i] = row.copy(moveFeet = numeric(it)) },
                                        label = { Text("В стопах") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                if (fixedCount == null) {
                    TextButton(onClick = { rows.add(RunUpRow()) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ещё попытка")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val attempts = rows.map { it.toAttempt() }
                onConfirm(if (fixedCount != null) attempts else attempts.filterNot { it.isEmpty })
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Список упражнений справочника с фильтром по виду: общий для тренировки и рекордов. */
@Composable
fun CatalogExercisePicker(
    exercises: List<Exercise>,
    isSelected: (Exercise) -> Boolean,
    onToggle: (Exercise) -> Unit,
    onEdit: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit
) {
    var selectedCategoryFilter by remember { mutableIntStateOf(0) }
    var showAllExercisesInList by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filteredExercises = remember(exercises, selectedCategoryFilter, query) {
        val list = exercises.distinctBy { it.variantKey() }
            .filter { query.isBlank() || it.title().contains(query.trim(), ignoreCase = true) }
        when (selectedCategoryFilter) {
            1 -> list.filter { it.categoryName?.contains("бег", ignoreCase = true) == true }
            2 -> list.filter { it.categoryName?.contains("техник", ignoreCase = true) == true || it.categoryName?.contains("тройн", ignoreCase = true) == true }
            3 -> list.filter { it.categoryName?.contains("силов", ignoreCase = true) == true }
            4 -> list.filter { it.categoryName?.contains("прыжк", ignoreCase = true) == true }
            5 -> list.filter { it.categoryName?.contains("офп", ignoreCase = true) == true }
            else -> list
        }
    }
    val exercisesToShow = if (showAllExercisesInList || query.isNotBlank()) filteredExercises else filteredExercises.take(8)

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Поиск упражнения") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().coachTarget("picker.search")
    )

    // Фильтр справочника по виду: короткие подписи, обычный размер шрифта
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Все", "Беговые", "Техника", "Силовые", "Прыжковые", "ОФП").forEachIndexed { index, label ->
            FilterChip(
                selected = selectedCategoryFilter == index,
                onClick = { selectedCategoryFilter = index },
                label = { Text(label) }
            )
        }
    }

    Text(
        text = "Упражнения",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (filteredExercises.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            exercisesToShow.forEach { ex ->
                val selected = isSelected(ex)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(ex) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                            // снаряд, шиповки, нога — прямо в названии упражнения
                            Text(
                                text = ex.styledTitle(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Выбрано",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { onEdit(ex) }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать упражнение",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { onDelete(ex) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить упражнение",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (query.isBlank() && filteredExercises.size > 8) {
                TextButton(
                    onClick = { showAllExercisesInList = !showAllExercisesInList },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = if (showAllExercisesInList) "Свернуть" else "Показать все · ${filteredExercises.size}")
                }
            }
        }
    }
}

/** Отдельный экран выбора упражнения: не перегружает окно тренировки или рекорда. */
@Composable
fun ExercisePickerScreen(
    title: String,
    doneLabel: String,
    onDismiss: () -> Unit,
    /** Подсказки по этому экрану выбора; null — без кнопки «?». */
    helpTopic: HelpTopic? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                    // заголовок в одну строку: при крупном шрифте телефона он сокращается, а не переносится по буквам
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (helpTopic != null) HelpIconButton(onClick = rememberHelp(helpTopic), id = "picker.help")
                    Button(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 8.dp).coachTarget("picker.done")
                    ) { Text(doneLabel, maxLines = 1, softWrap = false) }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/** Упражнение изменилось в форме: заменить его в справочнике или создать новое. */
@Composable
fun ExerciseChangedDialog(
    oldTitle: String,
    newTitle: String,
    newExists: Boolean,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onReplace: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Упражнение изменилось") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Было: $oldTitle", style = MaterialTheme.typography.bodyMedium)
                Text("Стало: $newTitle", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Заменить — старое упражнение в справочнике получит новые параметры. " +
                        "Создать новое — старое останется, здесь будет новое. " +
                        "Прошлые тренировки и их рекорды не меняются в обоих случаях.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = onCreate) { Text(if (newExists) "Взять существующее" else "Создать новое") } },
        dismissButton = { TextButton(onClick = onReplace) { Text("Заменить") } }
    )
}

/**
 * Ввод результатов: полей столько, сколько подходов × повторений, сгруппированы по подходам.
 * У каждого подхода можно отметить, что все его повторения выполнены с одним весом, или задать один вес на все подходы:
 * тогда вводится одно значение, а в список оно записывается на каждое повторение.
 * Лучшая попытка подсвечивается, пустые поля не учитываются.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerSetResultsModalDialog(
    exerciseName: String,
    /** Сколько повторений в каждом подходе. */
    repsBySet: List<Int>,
    unit: ResultUnit,
    initialResults: List<Double>,
    onDismiss: () -> Unit,
    onConfirm: (List<Double>) -> Unit,
    /** Подпись плана, если она отличается от числа полей: «3 × 30 сек». */
    planLabel: String = SetResults.label(repsBySet)
) {
    val cs = MaterialTheme.colorScheme
    val sets = repsBySet.size
    val total = repsBySet.sum()
    val starts = repsBySet.runningFold(0) { sum, count -> sum + count }
    fun rangeOf(set: Int) = starts[set] until starts[set + 1]
    val values = remember(repsBySet, initialResults) {
        mutableStateListOf<String>().apply {
            repeat(maxOf(total, initialResults.size)) { i ->
                // без округления: 3.85 должно остаться 3.85, а 130.0 показываться как 130
                add(initialResults.getOrNull(i)?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
            }
        }
    }
    var oneForAll by remember(repsBySet) { mutableStateOf(SetResults.sameValueMode(initialResults, repsBySet) == SetResults.ALL) }
    /** Подходы, в которых все повторения выполнены с одним значением. */
    val sameInSet = remember(repsBySet, initialResults) {
        mutableStateListOf<Boolean>().apply { addAll(SetResults.sameBySet(initialResults, repsBySet)) }
    }
    val multiRepSets = (0 until sets).filter { repsBySet[it] > 1 }

    /** Включённый подход получает первое введённое в нём значение на все повторения. */
    fun setSameInSet(set: Int, same: Boolean) {
        sameInSet[set] = same
        if (same) {
            val range = rangeOf(set)
            val first = range.map { values[it] }.firstOrNull { it.isNotBlank() } ?: ""
            for (i in range) values[i] = first
        }
    }

    /** Общие переключатели: значение разносится на все подходы или на каждый подход сразу. */
    fun applyMode(newMode: Int) {
        oneForAll = newMode == SetResults.ALL
        when (newMode) {
            SetResults.ALL -> {
                val first = values.firstOrNull { it.isNotBlank() } ?: ""
                for (i in 0 until total) values[i] = first
            }
            SetResults.PER_SET -> multiRepSets.forEach { setSameInSet(it, true) }
            else -> for (s in 0 until sets) sameInSet[s] = false
        }
    }

    val unitLabel = when (unit) {
        ResultUnit.SECONDS -> "сек"
        ResultUnit.M -> "м"
        ResultUnit.CM -> "см"
        ResultUnit.KG -> "кг"
        ResultUnit.REPS -> "раз"
    }
    val word = if (unit == ResultUnit.KG) "вес" else "результат"
    val wordWith = if (unit == ResultUnit.KG) "весом" else "результатом"
    val lowerBetter = RecordsEngine.isLowerBetter(exerciseName, unit)
    val parsed = values.map { it.toDoubleOrNull()?.takeIf { v -> v > 0 } }
    // те же правила, что в рекордах: пустое поле пропускается, введённое значение должно быть верным
    val badIndexes = values.indices.filter { values[it].isNotBlank() && EntryRules.resultError(values[it], unit) != null }
    val valueError = badIndexes.firstOrNull()?.let { EntryRules.resultError(values[it], unit) }
    val filled = parsed.filterNotNull()
    val best = if (lowerBetter) filled.minOrNull() else filled.maxOrNull()
    val bestIndex = if (best == null) -1 else parsed.indexOf(best)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_RESULTS) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Результаты попыток", style = MaterialTheme.typography.titleLarge)
                Text(text = exerciseName, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
            }
        } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = if (best != null) cs.primaryContainer else cs.surfaceContainerHigh) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (best != null) Icons.Default.EmojiEvents else Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (best != null) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (best != null) "Лучшая: ${formatRecordValue(best, unit)}, заполнено ${filled.size} из ${values.size}"
                            else "$planLabel: всего ${pluralRu(total, "попытка", "попытки", "попыток").let { "$total $it" }}. Пустые не учитываются",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (best != null) cs.onPrimaryContainer else cs.onSurfaceVariant
                        )
                    }
                }

                if (total > 1) {
                    FlowRow(
                        modifier = Modifier.coachTarget("results.modes"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ToggleChip(
                            label = "Каждый раз отдельно",
                            selected = !oneForAll && sameInSet.none { it },
                            onClick = { applyMode(SetResults.EACH) }
                        )
                        if (multiRepSets.isNotEmpty() && sets > 1) {
                            ToggleChip(
                                label = "Один $word на каждый подход",
                                selected = !oneForAll && multiRepSets.all { sameInSet[it] },
                                onClick = { applyMode(SetResults.PER_SET) }
                            )
                        }
                        ToggleChip(
                            label = if (sets > 1) "Один $word на все подходы" else "Один $word на весь подход",
                            selected = oneForAll,
                            onClick = { applyMode(SetResults.ALL) }
                        )
                    }
                }

                if (valueError != null) {
                    Text(text = valueError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }

                if (oneForAll) {
                    SetResultField(
                        number = 1,
                        value = values[0],
                        unitLabel = unitLabel,
                        isBest = bestIndex >= 0,
                        isError = 0 in badIndexes,
                        isLast = true,
                        onChange = { v -> for (i in 0 until total) values[i] = v },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // всё, что сверх плана подходов, добавлено кнопкой «Ещё попытка»
                    val groups = (0 until sets).map { rangeOf(it).toList() } +
                        listOfNotNull((total until values.size).toList().takeIf { it.isNotEmpty() })
                    groups.forEachIndexed { g, group ->
                        val isSet = g < sets
                        if (sets > 1 || !isSet) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isSet) "Подход ${g + 1}" else "Дополнительно",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                if (isSet && group.size > 1) {
                                    ToggleChip(
                                        label = "Все повторения с одним $wordWith",
                                        selected = sameInSet[g],
                                        onClick = { setSameInSet(g, !sameInSet[g]) }
                                    )
                                }
                            }
                        }
                        if (isSet && sameInSet[g]) {
                            SetResultField(
                                number = g + 1,
                                value = values[group.first()],
                                unitLabel = unitLabel,
                                isBest = bestIndex in group,
                                isError = group.first() in badIndexes,
                                isLast = group.last() == values.lastIndex,
                                onChange = { v -> group.forEach { i -> values[i] = v } },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            group.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pair.forEach { i ->
                                        SetResultField(
                                            number = i - group.first() + 1,
                                            value = values[i],
                                            unitLabel = unitLabel,
                                            isBest = i == bestIndex,
                                            isError = i in badIndexes,
                                            isLast = i == values.lastIndex,
                                            onChange = { values[i] = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    TextButton(onClick = { values.add("") }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ещё попытка")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = badIndexes.isEmpty(),
                onClick = { onConfirm(values.map { it.toDoubleOrNull() ?: 0.0 }) }
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Поле одной попытки: номер в кружке (лучшая — закрашена), число крупно, единица справа. */
@Composable
private fun SetResultField(
    number: Int,
    value: String,
    unitLabel: String,
    isBest: Boolean,
    isLast: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(EntryRules.cleanNumber(v)) },
        isError = isError,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = { Text("—", style = MaterialTheme.typography.titleMedium) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isBest) cs.primary else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isBest) cs.onPrimary else cs.onSurfaceVariant
                )
            }
        },
        suffix = { Text(unitLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

/** Параметры упражнения, задаваемые в справочнике. */
data class ExerciseConfig(
    val name: String,
    val category: String,
    val isSingleLeg: Boolean,
    val legSide: String?,
    val inventoryName: String?,
    val inventoryUnit: ResultUnit?,
    val inventoryValue: Double?,
    val inSpikes: Boolean = false,
    /** В чём измеряется результат: от неё зависит, является ли снаряд «нагружаемым» (штанга при результате в кг). */
    val unit: ResultUnit = ResultUnit.KG,
    /** Где в названии стоят нога, снаряд и шиповки; null — автоматически. */
    val titleLayout: String? = null
)

// Dialog: Mark Skipped Day
@Composable
fun MarkSkippedDayDialog(
    initialDate: Long,
    initialReason: String = "",
    onDismiss: () -> Unit,
    onConfirm: (date: Long, reason: String) -> Unit
) {
    var selectedReasonOption by remember {
        mutableStateOf(
            when {
                initialReason.contains("Отдых", ignoreCase = true) -> "🌴 Отдых"
                initialReason.contains("Заболел", ignoreCase = true) -> "🤒 Заболел"
                initialReason.contains("Травм", ignoreCase = true) -> "🩹 Травмировался"
                initialReason.isNotBlank() -> "✏️ Другая причина"
                else -> "🌴 Отдых"
            }
        )
    }

    var customReasonText by remember {
        mutableStateOf(
            if (selectedReasonOption == "✏️ Другая причина") initialReason else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_SKIP) { Text(text = "Пропуск Тренировочного Дня", fontWeight = FontWeight.Bold) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Укажите причину пропуска:", style = MaterialTheme.typography.labelMedium)

                listOf("🌴 Отдых", "🤒 Заболел", "🩹 Травмировался", "✏️ Другая причина").forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReasonOption = option }
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReasonOption == option,
                            onClick = { selectedReasonOption = option }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }

                if (selectedReasonOption == "✏️ Другая причина") {
                    OutlinedTextField(
                        value = customReasonText,
                        onValueChange = { customReasonText = it },
                        label = { Text("Укажите причину пропуска") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (selectedReasonOption == "✏️ Другая причина") {
                        if (customReasonText.isNotBlank()) customReasonText else "Другая причина"
                    } else {
                        selectedReasonOption
                    }
                    onConfirm(initialDate, finalReason)
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

fun getInferredUnitForExercise(exerciseName: String, fallbackUnit: ResultUnit = ResultUnit.KG): ResultUnit {
    val nameLower = exerciseName.lowercase()
    return when {
        nameLower.contains("30 м") || nameLower.contains("60 м") || nameLower.contains("100 м") || nameLower.contains("150 м") || nameLower.contains("200 м") || nameLower.contains("бег") || nameLower.contains("спринт") || nameLower.contains("секунд") || nameLower.contains("сек") -> ResultUnit.SECONDS
        nameLower.contains("присед") || nameLower.contains("штанг") || nameLower.contains("жим") || nameLower.contains("тяга") || nameLower.contains("рывок") || nameLower.contains("взятие") || nameLower.contains("гантел") || nameLower.contains("силов") || nameLower.contains("выпад") -> ResultUnit.KG
        nameLower.contains("прыжок") || nameLower.contains("тройной") || nameLower.contains("скачок") || nameLower.contains("шаг") || nameLower.contains("длину") -> ResultUnit.M
        nameLower.contains("барьер") || nameLower.contains("тумб") || nameLower.contains("высоту") -> ResultUnit.CM
        else -> fallbackUnit
    }
}

/** Значение попытки без единицы: 100, 11.97, 3.85. */
private fun plainAttempt(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.getDefault(), "%.2f", v).trimEnd('0').trimEnd(',', '.')

fun formatSetResultsBreakdown(res: ExerciseResult): String {
    if (res.setResultsJson.isNullOrBlank()) return ""
    val list = res.setResultsJson.split(",").mapNotNull { it.toDoubleOrNull() }.filter { it > 0 }
    if (list.isEmpty()) return ""

    val formattedValues = list.map { valNum ->
        when (res.resultUnit) {
            ResultUnit.SECONDS -> String.format(Locale.getDefault(), "%.2f сек", valNum)
            ResultUnit.M -> String.format(Locale.getDefault(), "%.2f м", valNum)
            ResultUnit.CM -> "${valNum.toInt()} см"
            ResultUnit.KG -> "${valNum.toInt()} кг"
            ResultUnit.REPS -> "${valNum.toInt()} повторений"
        }
    }

    return "(${formattedValues.joinToString(", ")})"
}

fun formatPairedResult(res: ExerciseResult): String {
    if (res.result <= 0) return ""
    return when (res.resultUnit) {
        ResultUnit.SECONDS -> {
            val totalMs = (res.result * 1000).toInt()
            val sec = totalMs / 1000
            val ms = totalMs % 1000
            if (ms > 0) "$sec сек $ms мс" else "$sec сек"
        }
        ResultUnit.KG -> {
            val totalG = (res.result * 1000).toInt()
            val kg = totalG / 1000
            val g = totalG % 1000
            if (g > 0) "$kg кг $g г" else "$kg кг"
        }
        ResultUnit.M -> {
            val totalCm = (res.result * 100).toInt()
            val m = totalCm / 100
            val cm = totalCm % 100
            if (cm > 0) "$m м $cm см" else "$m м"
        }
        ResultUnit.CM -> "${res.result.toInt()} см"
        ResultUnit.REPS -> "${res.result.toInt()} повторений"
    }
}

fun extractPrimarySecondaryValues(res: ExerciseResult): Pair<Int?, Int?> {
    if (res.result <= 0) return null to null
    return when (res.resultUnit) {
        ResultUnit.SECONDS -> {
            val totalMs = (res.result * 1000).toInt()
            (totalMs / 1000) to (totalMs % 1000)
        }
        ResultUnit.KG -> {
            val totalG = (res.result * 1000).toInt()
            (totalG / 1000) to (totalG % 1000)
        }
        ResultUnit.M -> {
            val totalCm = (res.result * 100).toInt()
            (totalCm / 100) to (totalCm % 100)
        }
        else -> res.result.toInt() to null
    }
}

fun getUnitTitle(unit: ResultUnit): String {
    return when (unit) {
        ResultUnit.KG -> "кг"
        ResultUnit.CM -> "см"
        ResultUnit.M -> "м"
        ResultUnit.REPS -> "повторений"
        ResultUnit.SECONDS -> "сек"
    }
}

fun getTrainingTypeColor(type: TrainingType): Color {
    return when (type) {
        TrainingType.STRENGTH -> Color(0xFF1E4D3B)
        TrainingType.TECHNICAL -> Color(0xFFE05A2B)
        TrainingType.JUMP -> Color(0xFFF57F17)
        TrainingType.CONDITIONING -> Color(0xFF6B4EFF)
        TrainingType.RECOVERY -> Color(0xFF00A884)
        TrainingType.TECHNICAL_STRENGTH -> Color(0xFFD84315)
        TrainingType.TECHNICAL_RECOVERY -> Color(0xFF00838F)
        TrainingType.STRENGTH_CONDITIONING -> Color(0xFF283593)
        TrainingType.HYBRID -> Color(0xFF8E24AA)
    }
}

fun getTrainingTypeIcon(type: TrainingType): ImageVector {
    return when (type) {
        TrainingType.STRENGTH -> Icons.Default.FitnessCenter
        TrainingType.TECHNICAL -> Icons.AutoMirrored.Filled.DirectionsRun
        TrainingType.JUMP -> Icons.Default.Height
        TrainingType.CONDITIONING -> Icons.Default.Speed
        TrainingType.RECOVERY -> Icons.Default.SelfImprovement
        TrainingType.TECHNICAL_STRENGTH -> Icons.Default.FitnessCenter
        TrainingType.TECHNICAL_RECOVERY -> Icons.AutoMirrored.Filled.DirectionsRun
        TrainingType.STRENGTH_CONDITIONING -> Icons.Default.Speed
        TrainingType.HYBRID -> Icons.Default.AutoAwesome
    }
}

fun getTrainingTypeTitle(type: TrainingType): String {
    return when (type) {
        TrainingType.STRENGTH -> "Силовая"
        TrainingType.TECHNICAL -> "Техническая"
        TrainingType.JUMP -> "Прыжковая"
        TrainingType.CONDITIONING -> "ОФП"
        TrainingType.RECOVERY -> "Восстановление"
        TrainingType.TECHNICAL_STRENGTH -> "Технико-силовая"
        TrainingType.TECHNICAL_RECOVERY -> "Технико-восстановительная"
        TrainingType.STRENGTH_CONDITIONING -> "Силово-ОФП"
        TrainingType.HYBRID -> "Смешанная"
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
    return "$day $month"
}
