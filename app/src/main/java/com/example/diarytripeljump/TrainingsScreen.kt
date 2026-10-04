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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.ExerciseCategory
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TrainingType
import com.example.diarytripeljump.data.TrainingWithExercises
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TrainingsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()
    val rawExercisesList by viewModel.allExercises.collectAsState()

    // Deduplicate exercises by name
    val exercisesList = remember(rawExercisesList) {
        rawExercisesList.distinctBy { it.name.trim().lowercase() }
    }

    var calendarMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }

    var showAddTrainingDialog by remember { mutableStateOf(false) }
    var showMarkSkippedDialog by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Row
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "ПЛАН И СЕССИИ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Дневник Тренировок",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. Compact Calendar Widget
        CompactTrainingCalendarWidget(
            currentMonth = calendarMonth,
            onMonthChange = { newMonth -> calendarMonth = newMonth },
            selectedDateMillis = selectedDateMillis,
            onDateSelected = { selectedDateMillis = it },
            trainingsList = trainingsList
        )

        // 3. Legend & Action Buttons Row UNDER Legend
        val activeTrainingsOnSelectedDay = remember(trainingsList, selectedDateMillis) {
            if (selectedDateMillis == null) false
            else {
                val calSelected = Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                trainingsList.any { item ->
                    val calItem = Calendar.getInstance().apply { timeInMillis = item.training.date }
                    calItem.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                            calItem.get(Calendar.DAY_OF_YEAR) == calSelected.get(Calendar.DAY_OF_YEAR) &&
                            !item.training.isSkipped
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color Legend Row (Includes Jump Training Color Coordination)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem(color = Color(0xFF1E4D3B), label = "Силовая")
                    LegendItem(color = Color(0xFFE05A2B), label = "Техническая")
                    LegendItem(color = Color(0xFFF57F17), label = "Прыжковая")
                    LegendItem(color = Color(0xFF6B4EFF), label = "ОФП")
                    LegendItem(color = Color(0xFF00A884), label = "Восстановление")
                    LegendItem(color = MaterialTheme.colorScheme.error, label = "Пропуск")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // BOTH Buttons UNDER Legend Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Button 1: Add Training
                    Button(
                        onClick = { showAddTrainingDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Добавить", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    // Button 2: Mark Skipped Day
                    OutlinedButton(
                        onClick = {
                            if (activeTrainingsOnSelectedDay) {
                                warningMessage = "В этот день уже есть тренировка! Для отметки пропуска сначала удалите существующую тренировку."
                            } else {
                                showMarkSkippedDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (activeTrainingsOnSelectedDay) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Пропуск дня", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Selected Date Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedDateMillis != null) "ТРЕНИРОВКИ НА ${formatDate(selectedDateMillis!!).uppercase()}" else "ВСЕ ТРЕНИРОВКИ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            if (selectedDateMillis != null) {
                Text(
                    text = "Показать все",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { selectedDateMillis = null }
                )
            }
        }

        val filteredTrainings = remember(trainingsList, selectedDateMillis) {
            if (selectedDateMillis == null) {
                trainingsList
            } else {
                val calSelected = Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
                trainingsList.filter { item ->
                    val calItem = Calendar.getInstance().apply { timeInMillis = item.training.date }
                    calItem.get(Calendar.YEAR) == calSelected.get(Calendar.YEAR) &&
                            calItem.get(Calendar.DAY_OF_YEAR) == calSelected.get(Calendar.DAY_OF_YEAR)
                }
            }
        }

        // 5. Trainings List
        if (filteredTrainings.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredTrainings) { trainingItem ->
                    TrainingSessionCard(
                        trainingItem = trainingItem,
                        onDeleteClick = { viewModel.deleteTrainingSession(trainingItem.training) }
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "На этот день нет запланированных тренировок",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = { showAddTrainingDialog = true }) {
                        Text(text = "+ Добавить тренировку", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Warning Dialog if day has active trainings
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

    // Dialog 1: Add Training
    if (showAddTrainingDialog) {
        AddTrainingDialog(
            exercisesList = exercisesList,
            initialDate = selectedDateMillis ?: System.currentTimeMillis(),
            onDismiss = { showAddTrainingDialog = false },
            onConfirm = { date, type, intensity, duration, notes, exerciseInputs, hop, step, jump, speed ->
                viewModel.addTrainingSession(
                    date = date,
                    trainingType = type,
                    intensity = intensity,
                    durationMinutes = duration,
                    notes = notes,
                    exerciseInputs = exerciseInputs,
                    hopPhase = hop,
                    stepPhase = step,
                    jumpPhase = jump,
                    runUpSpeed = speed
                )
                showAddTrainingDialog = false
            }
        )
    }

    // Dialog 2: Mark Skipped Day
    if (showMarkSkippedDialog) {
        MarkSkippedDayDialog(
            initialDate = selectedDateMillis ?: System.currentTimeMillis(),
            onDismiss = { showMarkSkippedDialog = false },
            onConfirm = { date, reason ->
                viewModel.addSkippedDaySession(date, reason)
                showMarkSkippedDialog = false
            }
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp
        )
    }
}

@Composable
fun CompactTrainingCalendarWidget(
    currentMonth: Calendar,
    onMonthChange: (Calendar) -> Unit,
    selectedDateMillis: Long?,
    onDateSelected: (Long) -> Unit,
    trainingsList: List<TrainingWithExercises>
) {
    val monthFormat = SimpleDateFormat("LLLL yyyy", Locale.forLanguageTag("ru"))
    val monthTitle = remember(currentMonth) { monthFormat.format(currentMonth.time).replaceFirstChar { it.uppercase() } }

    val trainingsByDay = remember(trainingsList, currentMonth) {
        val map = mutableMapOf<String, List<TrainingWithExercises>>()
        trainingsList.forEach { item ->
            val cal = Calendar.getInstance().apply { timeInMillis = item.training.date }
            val key = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
            val currentList = map[key] ?: emptyList()
            map[key] = currentList + item
        }
        map
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newCal = currentMonth.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        onMonthChange(newCal)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        val newCal = currentMonth.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        onMonthChange(newCal)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(30.dp)
                    )
                }
            }

            val daysGrid = remember(currentMonth) {
                val cal = currentMonth.clone() as Calendar
                cal.set(Calendar.DAY_OF_MONTH, 1)
                var firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 2
                if (firstDayOfWeek < 0) firstDayOfWeek += 7

                val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val list = mutableListOf<Calendar?>()
                for (i in 0 until firstDayOfWeek) list.add(null)
                for (d in 1..maxDays) {
                    val dayCal = cal.clone() as Calendar
                    dayCal.set(Calendar.DAY_OF_MONTH, d)
                    list.add(dayCal)
                }
                list
            }

            val chunkedDays = daysGrid.chunked(7)

            chunkedDays.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    week.forEach { dayCal ->
                        if (dayCal == null) {
                            Spacer(modifier = Modifier.width(30.dp))
                        } else {
                            val dayNum = dayCal.get(Calendar.DAY_OF_MONTH)
                            val dayKey = "${dayCal.get(Calendar.YEAR)}_${dayCal.get(Calendar.DAY_OF_YEAR)}"
                            val dayTrainings = trainingsByDay[dayKey] ?: emptyList()

                            val isSelected = remember(selectedDateMillis, dayCal) {
                                if (selectedDateMillis == null) false
                                else {
                                    val calSel = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                    calSel.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                            calSel.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier
                                    .width(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onDateSelected(dayCal.timeInMillis) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected || dayTrainings.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (dayTrainings.isNotEmpty()) {
                                        dayTrainings.take(3).forEach { item ->
                                            val dotColor = if (item.training.isSkipped) MaterialTheme.colorScheme.error
                                            else getTrainingTypeColor(item.training.trainingType)

                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else dotColor)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrainingSessionCard(
    trainingItem: TrainingWithExercises,
    onDeleteClick: () -> Unit
) {
    val session = trainingItem.training
    val isSkipped = session.isSkipped
    val typeColor = if (isSkipped) MaterialTheme.colorScheme.error else getTrainingTypeColor(session.trainingType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(typeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSkipped) Icons.Default.Block else getTrainingTypeIcon(session.trainingType),
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isSkipped) "ПРОПУСК ТРЕНИРОВКИ" else getTrainingTypeTitle(session.trainingType),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSkipped) "Причина: ${session.skipReason ?: "Не указана"}"
                            else "${session.durationMinutes ?: 60} мин · Интенсивность ${session.intensity ?: 5}/10",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!session.notes.isNullOrEmpty()) {
                Text(
                    text = "Заметка: ${session.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            if (!isSkipped) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (trainingItem.exerciseResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        trainingItem.exerciseResults.forEach { exRes ->
                            val setsText = if (exRes.sets != null && exRes.sets > 0) " (${exRes.sets} подходов × ${exRes.reps ?: 1} раз)" else ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• Упражнение #${exRes.exerciseId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${exRes.result} ${getUnitTitle(exRes.resultUnit)}$setsText",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Enhanced Add Training Dialog
@Composable
fun AddTrainingDialog(
    exercisesList: List<Exercise>,
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (
        date: Long,
        type: TrainingType,
        intensity: Int,
        duration: Int,
        notes: String,
        exerciseInputs: List<ExerciseResultInput>,
        hop: Double?,
        step: Double?,
        jump: Double?,
        speed: Double?
    ) -> Unit
) {
    var autoDetectType by remember { mutableStateOf(true) }
    var selectedType by remember { mutableStateOf(TrainingType.STRENGTH) }
    var intensity by remember { mutableFloatStateOf(8f) }
    var durationText by remember { mutableStateOf("90") }
    var notesText by remember { mutableStateOf("") }

    // Exercise Mode (From List vs Custom)
    var isCustomExerciseMode by remember { mutableStateOf(false) }
    var customExerciseName by remember { mutableStateOf("") }

    // Exercise Category Filter (0: All, 1: Strength, 2: Jump, 3: Technical, 4: Conditioning, 5: Recovery)
    var selectedCategoryFilter by remember { mutableIntStateOf(0) }

    var selectedExercise by remember { mutableStateOf(exercisesList.firstOrNull()) }
    var resultValueText by remember { mutableStateOf("120") }
    var selectedUnit by remember { mutableStateOf(ResultUnit.KG) }

    // Sets & Reps
    var hasSeries by remember { mutableStateOf(true) }
    var setsText by remember { mutableStateOf("5") }
    var repsText by remember { mutableStateOf("8") }

    var addedExerciseInputs by remember { mutableStateOf(listOf<ExerciseResultInput>()) }

    var hopText by remember { mutableStateOf("5.15") }
    var stepText by remember { mutableStateOf("4.35") }
    var jumpText by remember { mutableStateOf("4.90") }
    var speedText by remember { mutableStateOf("9.8") }

    // Auto-detect Training Type logic
    val calculatedType = remember(addedExerciseInputs, selectedType, autoDetectType) {
        if (!autoDetectType || addedExerciseInputs.isEmpty()) selectedType
        else {
            val hasJumpOrTechnical = hopText.isNotBlank() || stepText.isNotBlank() || addedExerciseInputs.any { it.unit == ResultUnit.M }
            val hasStrength = addedExerciseInputs.any { it.unit == ResultUnit.KG }
            when {
                hasJumpOrTechnical && hasStrength -> TrainingType.TECHNICAL_STRENGTH
                hasJumpOrTechnical -> TrainingType.JUMP
                else -> TrainingType.STRENGTH
            }
        }
    }

    // Deduplicated exercises list filtered by category
    val filteredExercises = remember(exercisesList, selectedCategoryFilter) {
        val list = exercisesList.distinctBy { it.name.trim().lowercase() }
        when (selectedCategoryFilter) {
            1 -> list.filter { it.category == ExerciseCategory.SQUAT || it.category == ExerciseCategory.LUNGE || it.category == ExerciseCategory.DEADLIFT || it.category == ExerciseCategory.POWER_CLEAN }
            2 -> list.filter { it.category == ExerciseCategory.JUMP || it.category == ExerciseCategory.JUMP_BOX || it.category == ExerciseCategory.BROAD_JUMP || it.category == ExerciseCategory.VERTICAL_JUMP || it.category == ExerciseCategory.DEPTH_JUMP }
            3 -> list.filter { it.category == ExerciseCategory.HURDLES }
            4 -> list.filter { it.category == ExerciseCategory.CORE || it.category == ExerciseCategory.PLANK }
            5 -> list.filter { it.category == ExerciseCategory.MOBILITY || it.category == ExerciseCategory.STRETCHING }
            else -> list
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Новая Тренировка", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto Detect Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Авто-определение вида:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Switch(checked = autoDetectType, onCheckedChange = { autoDetectType = it })
                }

                val currentTypeColor = getTrainingTypeColor(calculatedType)

                Text(
                    text = "Вид: ${getTrainingTypeTitle(calculatedType)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = currentTypeColor
                )

                // Training Type Choice Chips with COLOR COORDINATION matching calendar legend!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrainingType.entries.forEach { type ->
                        val isTypeSelected = calculatedType == type
                        val typeColor = getTrainingTypeColor(type)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isTypeSelected) typeColor else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                autoDetectType = false
                                selectedType = type
                            }
                        ) {
                            Text(
                                text = getTrainingTypeTitle(type),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isTypeSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isTypeSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Text(text = "Интенсивность: ${intensity.toInt()}/10", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 1f..10f,
                    steps = 8
                )

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Длительность (мин)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Заметки (разминка, барьеры 76см и др.)") },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                if (calculatedType == TrainingType.TECHNICAL || calculatedType == TrainingType.JUMP || calculatedType == TrainingType.TECHNICAL_STRENGTH || calculatedType == TrainingType.TECHNICAL_RECOVERY) {
                    Text(text = "Фазы тройного прыжка:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = hopText,
                            onValueChange = { hopText = it },
                            label = { Text("Hop (м)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stepText,
                            onValueChange = { stepText = it },
                            label = { Text("Step (м)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = jumpText,
                            onValueChange = { jumpText = it },
                            label = { Text("Jump (м)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Exercise Source Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = !isCustomExerciseMode,
                        onClick = { isCustomExerciseMode = false },
                        label = { Text("Из списка", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = isCustomExerciseMode,
                        onClick = { isCustomExerciseMode = true },
                        label = { Text("Своё упражнение", fontSize = 11.sp) }
                    )
                }

                if (isCustomExerciseMode) {
                    OutlinedTextField(
                        value = customExerciseName,
                        onValueChange = { customExerciseName = it },
                        label = { Text("Название упражнения") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategoryFilter == 0,
                            onClick = { selectedCategoryFilter = 0 },
                            label = { Text("Все", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedCategoryFilter == 1,
                            onClick = { selectedCategoryFilter = 1 },
                            label = { Text("💪 Силовые", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedCategoryFilter == 2,
                            onClick = { selectedCategoryFilter = 2 },
                            label = { Text("🦘 Прыжковые", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedCategoryFilter == 3,
                            onClick = { selectedCategoryFilter = 3 },
                            label = { Text("📐 Барьеры", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedCategoryFilter == 4,
                            onClick = { selectedCategoryFilter = 4 },
                            label = { Text("🏃 ОФП", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = selectedCategoryFilter == 5,
                            onClick = { selectedCategoryFilter = 5 },
                            label = { Text("🧘 Восстановление", fontSize = 10.sp) }
                        )
                    }

                    // Exercise Choice List Under Categories
                    Text(text = "Выберите упражнение:", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        filteredExercises.forEach { ex ->
                            val isExSelected = selectedExercise?.id == ex.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedExercise = ex },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isExSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isExSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isExSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sets & Reps Configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Указать подходы и повторения:", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                    Switch(checked = hasSeries, onCheckedChange = { hasSeries = it })
                }

                if (hasSeries) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = setsText,
                            onValueChange = { setsText = it },
                            label = { Text("Подходов") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it },
                            label = { Text("Повторений") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Result Unit Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResultUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text(getUnitTitle(unit), fontSize = 10.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = resultValueText,
                        onValueChange = { resultValueText = it },
                        label = { Text("Значение (${getUnitTitle(selectedUnit)})") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        val valDouble = resultValueText.toDoubleOrNull() ?: 100.0
                        val exId = selectedExercise?.id ?: 1L
                        val exName = if (isCustomExerciseMode && customExerciseName.isNotBlank()) customExerciseName else (selectedExercise?.name ?: "Упражнение")
                        val input = ExerciseResultInput(
                            exerciseId = exId,
                            exerciseName = exName,
                            result = valDouble,
                            unit = selectedUnit,
                            sets = if (hasSeries) setsText.toIntOrNull() else null,
                            reps = if (hasSeries) repsText.toIntOrNull() else null
                        )
                        addedExerciseInputs = addedExerciseInputs + input
                    }) {
                        Text("+")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        initialDate,
                        calculatedType,
                        intensity.toInt(),
                        durationText.toIntOrNull() ?: 60,
                        notesText,
                        addedExerciseInputs,
                        hopText.toDoubleOrNull(),
                        stepText.toDoubleOrNull(),
                        jumpText.toDoubleOrNull(),
                        speedText.toDoubleOrNull()
                    )
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

// Dialog: Mark Skipped Day
@Composable
fun MarkSkippedDayDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (date: Long, reason: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("Отдых") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Пропуск Тренировочного Дня", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Укажите причину пропуска:", style = MaterialTheme.typography.labelMedium)

                listOf("🌴 Отдых", "🤒 Заболел", "🩹 Травмировался", "❓ Другая причина").forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedReason = reason }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = reason, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(initialDate, selectedReason) }) {
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

fun getUnitTitle(unit: ResultUnit): String {
    return when (unit) {
        ResultUnit.KG -> "кг"
        ResultUnit.CM -> "см"
        ResultUnit.M -> "м"
        ResultUnit.REPS -> "раз"
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
