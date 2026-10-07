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
import com.example.diarytripeljump.data.ExerciseResult
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TrainingType
import com.example.diarytripeljump.data.TrainingWithExercises
import java.text.SimpleDateFormat
import java.util.*

enum class PairedUnitType {
    SECONDS_MS, // Сек / Мс
    KG_G,       // Кг / Г
    M_CM,       // М / См
    REPS_ONLY   // Раз
}

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
    var editingTrainingItem by remember { mutableStateOf<TrainingWithExercises?>(null) }
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

        // 3. Action Buttons Row Directly Under Calendar
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAddTrainingDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Добавить тренировку", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    if (activeTrainingsOnSelectedDay) {
                        warningMessage = "В этот день уже есть тренировка! Для отметки пропуска сначала удалите существующую тренировку."
                    } else {
                        showMarkSkippedDialog = true
                    }
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (activeTrainingsOnSelectedDay) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Пропуск дня", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        exercisesList = exercisesList,
                        onEditClick = { editingTrainingItem = trainingItem },
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

    // New Training Dialog
    if (showAddTrainingDialog) {
        AddTrainingDialog(
            exercisesList = exercisesList,
            initialDate = selectedDateMillis ?: System.currentTimeMillis(),
            existingInputs = emptyList(),
            dialogTitle = "Новая Тренировка",
            onDismiss = { showAddTrainingDialog = false },
            onConfirm = { date, exerciseInputs ->
                viewModel.addTrainingSession(
                    date = date,
                    trainingType = TrainingType.STRENGTH,
                    intensity = 8,
                    durationMinutes = 60,
                    notes = "",
                    exerciseInputs = exerciseInputs
                )
                showAddTrainingDialog = false
            },
            onSaveNewExerciseToDb = { name, category ->
                viewModel.saveExerciseToDatabase(name = name, categoryName = category)
            }
        )
    }

    // Edit Existing Training Dialog
    if (editingTrainingItem != null) {
        val session = editingTrainingItem!!.training
        val existingInputs = remember(editingTrainingItem) {
            editingTrainingItem!!.exerciseResults.map { exRes ->
                val exName = exercisesList.find { it.id == exRes.exerciseId }?.name ?: "Упражнение"
                val (pVal, sVal) = extractPrimarySecondaryValues(exRes)
                ExerciseResultInput(
                    exerciseId = exRes.exerciseId,
                    exerciseName = exName,
                    result = exRes.result,
                    unit = exRes.resultUnit,
                    primaryValue = pVal,
                    secondaryValue = sVal,
                    sets = exRes.sets,
                    reps = exRes.reps
                )
            }
        }

        AddTrainingDialog(
            exercisesList = exercisesList,
            initialDate = session.date,
            existingInputs = existingInputs,
            dialogTitle = "Редактировать Тренировку",
            onDismiss = { editingTrainingItem = null },
            onConfirm = { date, exerciseInputs ->
                viewModel.updateTrainingSessionWithExercises(
                    sessionId = session.id,
                    date = date,
                    exerciseInputs = exerciseInputs
                )
                editingTrainingItem = null
            },
            onSaveNewExerciseToDb = { name, category ->
                viewModel.saveExerciseToDatabase(name = name, categoryName = category)
            }
        )
    }

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
    exercisesList: List<Exercise>,
    onEditClick: () -> Unit,
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
                            text = if (isSkipped) "ПРОПУСК ТРЕНИРОВКИ" else "ТРЕНИРОВКА",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isSkipped) {
                            Text(
                                text = "Причина: ${session.skipReason ?: "Не указана"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isSkipped) {
                        IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
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
            }

            if (!isSkipped) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (trainingItem.exerciseResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        trainingItem.exerciseResults.forEach { exRes ->
                            val exName = exercisesList.find { it.id == exRes.exerciseId }?.name ?: "Упражнение"
                            val resultFormatted = formatPairedResult(exRes)
                            val setsInfo = when {
                                exRes.sets != null && exRes.sets > 0 && exRes.reps != null && exRes.reps > 0 -> "${exRes.sets} подходов × ${exRes.reps} раз"
                                exRes.reps != null && exRes.reps > 0 -> "${exRes.reps} раз"
                                exRes.sets != null && exRes.sets > 0 -> "${exRes.sets} подходов"
                                else -> ""
                            }
                            val detailsText = listOf(resultFormatted, setsInfo).filter { it.isNotBlank() }.joinToString(" • ")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• $exName",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = detailsText,
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

// Dialog: Add/Edit Training with Paired Result Units & Individual Sets/Reps
@Composable
fun AddTrainingDialog(
    exercisesList: List<Exercise>,
    initialDate: Long,
    existingInputs: List<ExerciseResultInput> = emptyList(),
    dialogTitle: String = "Новая Тренировка",
    onDismiss: () -> Unit,
    onConfirm: (
        date: Long,
        exerciseInputs: List<ExerciseResultInput>
    ) -> Unit,
    onSaveNewExerciseToDb: (name: String, category: String) -> Unit
) {
    // 1. Categories Filter: Силовые, Прыжковые, Беговые, ОФП, Восстановление, Другой
    var selectedCategoryFilter by remember { mutableIntStateOf(0) }
    var showAllExercisesInList by remember { mutableStateOf(false) }

    var showCreateExerciseModal by remember { mutableStateOf(false) }

    var addedExerciseInputs by remember { mutableStateOf(existingInputs) }

    val filteredExercises = remember(exercisesList, selectedCategoryFilter) {
        val list = exercisesList.distinctBy { it.name.trim().lowercase() }
        when (selectedCategoryFilter) {
            1 -> list.filter { it.categoryName?.contains("силов", ignoreCase = true) == true || it.categoryName == null }
            2 -> list.filter { it.categoryName?.contains("прыжк", ignoreCase = true) == true }
            3 -> list.filter { it.categoryName?.contains("бег", ignoreCase = true) == true }
            4 -> list.filter { it.categoryName?.contains("офп", ignoreCase = true) == true }
            5 -> list.filter { it.categoryName?.contains("восстан", ignoreCase = true) == true }
            6 -> list.filter { it.categoryName?.contains("друг", ignoreCase = true) == true }
            else -> list
        }
    }

    val exercisesToShow = if (showAllExercisesInList) filteredExercises else filteredExercises.take(4)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = dialogTitle, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Category Chips
                Text(
                    text = "Вид упражнения:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

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
                        label = { Text("🏃 Беговые", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedCategoryFilter == 4,
                        onClick = { selectedCategoryFilter = 4 },
                        label = { Text("🏋️ ОФП", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedCategoryFilter == 5,
                        onClick = { selectedCategoryFilter = 5 },
                        label = { Text("🧘 Восстановление", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = selectedCategoryFilter == 6,
                        onClick = { selectedCategoryFilter = 6 },
                        label = { Text("🌀 Другой", fontSize = 10.sp) }
                    )
                }

                // 2. Exercises List
                Text(
                    text = "Упражнения:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (filteredExercises.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        exercisesToShow.forEach { ex ->
                            val isAdded = addedExerciseInputs.any { it.exerciseName.equals(ex.name, ignoreCase = true) }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isAdded) {
                                            addedExerciseInputs = addedExerciseInputs.filter { !it.exerciseName.equals(ex.name, ignoreCase = true) }
                                        } else {
                                            val input = ExerciseResultInput(
                                                exerciseId = ex.id,
                                                exerciseName = ex.name,
                                                result = 0.0,
                                                unit = ex.defaultUnit,
                                                sets = null,
                                                reps = 10
                                            )
                                            addedExerciseInputs = addedExerciseInputs + input
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAdded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ex.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isAdded) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                    if (isAdded) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Добавлено",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredExercises.size > 4) {
                            TextButton(
                                onClick = { showAllExercisesInList = !showAllExercisesInList },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    text = if (showAllExercisesInList) "Свернуть" else "Показать все (${filteredExercises.size})...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Button 'Добавить новое упражнение'
                Button(
                    onClick = { showCreateExerciseModal = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Добавить новое упражнение", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 4. Selected Exercises Cards with Paired Result Units & Individual Sets/Reps
                if (addedExerciseInputs.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Параметры выбранных упражнений (${addedExerciseInputs.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
                                addedExerciseInputs = addedExerciseInputs.filterIndexed { i, _ -> i != index }
                            }
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
                    onClick = {
                        onConfirm(
                            initialDate,
                            addedExerciseInputs
                        )
                    }
                ) {
                    Text("Сохранить")
                }
            }
        }
    )

    // Sub-Dialog: Create New Exercise
    if (showCreateExerciseModal) {
        CreateNewExerciseDialog(
            onDismiss = { showCreateExerciseModal = false },
            onConfirm = { name, category, equipmentWeight ->
                onSaveNewExerciseToDb(name, category)

                val input = ExerciseResultInput(
                    exerciseId = System.currentTimeMillis(),
                    exerciseName = name,
                    result = equipmentWeight ?: 0.0,
                    unit = ResultUnit.KG,
                    primaryValue = equipmentWeight?.toInt(),
                    sets = null,
                    reps = 10
                )
                addedExerciseInputs = addedExerciseInputs + input
                showCreateExerciseModal = false
            }
        )
    }
}

// Editor Card for Individual Exercise Result, Paired Units, and Sets/Reps
@Composable
fun ExerciseResultEditorCard(
    input: ExerciseResultInput,
    onUpdate: (ExerciseResultInput) -> Unit,
    onRemove: () -> Unit
) {
    var hasResult by remember(input) { mutableStateOf(input.result > 0 || input.primaryValue != null) }
    var pairedUnitType by remember(input) {
        mutableStateOf(
            when (input.unit) {
                ResultUnit.SECONDS -> PairedUnitType.SECONDS_MS
                ResultUnit.KG -> PairedUnitType.KG_G
                ResultUnit.M -> PairedUnitType.M_CM
                ResultUnit.REPS -> PairedUnitType.REPS_ONLY
                else -> PairedUnitType.KG_G
            }
        )
    }

    var primaryValStr by remember(input) { mutableStateOf(input.primaryValue?.toString() ?: "") }
    var secondaryValStr by remember(input) { mutableStateOf(input.secondaryValue?.toString() ?: "") }

    var setsStr by remember(input) { mutableStateOf(input.sets?.toString() ?: "") }
    var repsStr by remember(input) { mutableStateOf(input.reps?.toString() ?: "") }

    fun calculateAndEmit() {
        val p = primaryValStr.filter { it.isDigit() }.toIntOrNull()
        val s = secondaryValStr.filter { it.isDigit() }.toIntOrNull()
        val setsVal = setsStr.filter { it.isDigit() }.toIntOrNull()
        val repsVal = repsStr.filter { it.isDigit() }.toIntOrNull()

        val (calcResult, unit) = if (hasResult) {
            when (pairedUnitType) {
                PairedUnitType.SECONDS_MS -> {
                    val totalSec = (p ?: 0) + (s ?: 0) / 1000.0
                    totalSec to ResultUnit.SECONDS
                }
                PairedUnitType.KG_G -> {
                    val totalKg = (p ?: 0) + (s ?: 0) / 1000.0
                    totalKg to ResultUnit.KG
                }
                PairedUnitType.M_CM -> {
                    val totalM = (p ?: 0) + (s ?: 0) / 100.0
                    totalM to ResultUnit.M
                }
                PairedUnitType.REPS_ONLY -> {
                    (p ?: 0).toDouble() to ResultUnit.REPS
                }
            }
        } else {
            0.0 to ResultUnit.KG
        }

        onUpdate(
            input.copy(
                result = calcResult,
                unit = unit,
                primaryValue = p,
                secondaryValue = s,
                sets = setsVal,
                reps = repsVal
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "• ${input.exerciseName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 1. Result Toggle & Paired Unit Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Результат (опционально):", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                Switch(
                    checked = hasResult,
                    onCheckedChange = {
                        hasResult = it
                        calculateAndEmit()
                    }
                )
            }

            if (hasResult) {
                // Unit Pair Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = pairedUnitType == PairedUnitType.KG_G,
                        onClick = { pairedUnitType = PairedUnitType.KG_G; calculateAndEmit() },
                        label = { Text("🏋️ Кг / Г", fontSize = 9.sp) }
                    )
                    FilterChip(
                        selected = pairedUnitType == PairedUnitType.M_CM,
                        onClick = { pairedUnitType = PairedUnitType.M_CM; calculateAndEmit() },
                        label = { Text("📏 М / См", fontSize = 9.sp) }
                    )
                    FilterChip(
                        selected = pairedUnitType == PairedUnitType.SECONDS_MS,
                        onClick = { pairedUnitType = PairedUnitType.SECONDS_MS; calculateAndEmit() },
                        label = { Text("⏱ Сек / Мс", fontSize = 9.sp) }
                    )
                    FilterChip(
                        selected = pairedUnitType == PairedUnitType.REPS_ONLY,
                        onClick = { pairedUnitType = PairedUnitType.REPS_ONLY; calculateAndEmit() },
                        label = { Text("🔢 Раз", fontSize = 9.sp) }
                    )
                }

                // Dual Input Fields (Два окошка для ввода значений)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val label1 = when (pairedUnitType) {
                        PairedUnitType.SECONDS_MS -> "Секунды"
                        PairedUnitType.KG_G -> "Килограммы"
                        PairedUnitType.M_CM -> "Метры"
                        PairedUnitType.REPS_ONLY -> "Количество раз"
                    }

                    OutlinedTextField(
                        value = primaryValStr,
                        onValueChange = {
                            primaryValStr = it.filter { char -> char.isDigit() }
                            calculateAndEmit()
                        },
                        label = { Text(label1, fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    if (pairedUnitType != PairedUnitType.REPS_ONLY) {
                        val label2 = when (pairedUnitType) {
                            PairedUnitType.SECONDS_MS -> "Миллисекунды"
                            PairedUnitType.KG_G -> "Граммы"
                            PairedUnitType.M_CM -> "Сантиметры"
                            else -> ""
                        }

                        OutlinedTextField(
                            value = secondaryValStr,
                            onValueChange = {
                                secondaryValStr = it.filter { char -> char.isDigit() }
                                calculateAndEmit()
                            },
                            label = { Text(label2, fontSize = 9.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // 2. Sets & Reps Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = setsStr,
                    onValueChange = {
                        setsStr = it.filter { char -> char.isDigit() }
                        calculateAndEmit()
                    },
                    label = { Text("Подходов (опцион.)", fontSize = 9.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = repsStr,
                    onValueChange = {
                        repsStr = it.filter { char -> char.isDigit() }
                        calculateAndEmit()
                    },
                    label = { Text("Повторений", fontSize = 9.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        }
    }
}

// Sub-Dialog: Create New Exercise
@Composable
fun CreateNewExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, equipmentWeight: Double?) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Силовые") }
    var weightText by remember { mutableStateOf("") }

    val availableCategories = listOf("Силовые", "Прыжковые", "Беговые", "ОФП", "Восстановление", "Другой")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Новое Упражнение", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Название упражнения") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Вид упражнения:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { input -> weightText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Вес снаряда (кг) [если со снарядом]") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        onConfirm(nameText, selectedCategory, weightText.toDoubleOrNull())
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

// Dialog: Mark Skipped Day
@Composable
fun MarkSkippedDayDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (date: Long, reason: String) -> Unit
) {
    var selectedReasonOption by remember { mutableStateOf("Отдых") }
    var customReasonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Пропуск Тренировочного Дня", fontWeight = FontWeight.Bold) },
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

                OutlinedTextField(
                    value = customReasonText,
                    onValueChange = { customReasonText = it },
                    label = { Text("Уточнение причины (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (customReasonText.isNotBlank()) "$selectedReasonOption: $customReasonText" else selectedReasonOption
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
        ResultUnit.REPS -> "${res.result.toInt()} раз"
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
