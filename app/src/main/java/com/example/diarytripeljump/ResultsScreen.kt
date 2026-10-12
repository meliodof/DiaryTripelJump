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

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    val rawResultsList by viewModel.rawTripleJumpResults.collectAsState()
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()

    // Dialog States
    var showAddRecordDialog by remember { mutableStateOf(false) }
    var isAddingAntiRecord by remember { mutableStateOf(false) }

    // Filter States
    var selectedSourceFilter by remember { mutableIntStateOf(0) } // 0: Все, 1: Соревнования, 2: Тренировки
    var selectedLocationFilter by remember { mutableIntStateOf(0) } // 0: Все, 1: На улице, 2: В помещении
    var selectedCategoryFilter by remember { mutableIntStateOf(0) } // 0: Все, 1: Прыжковые, 2: Беговые, 3: Силовые
    var selectedRecordTypeFilter by remember { mutableIntStateOf(0) } // 0: Рекорды (Лучшие), 1: Анти-рекорды (Худшие)

    // Calculate dynamic interconnect records from training sessions
    val calculatedTrainingRecords = remember(trainingsList) {
        val list = mutableListOf<TripleJumpResult>()
        trainingsList.forEach { trainingItem ->
            trainingItem.exerciseResults.forEach { exRes ->
                if (exRes.result > 0) {
                    val exName = exRes.exerciseName.ifBlank { "Упражнение" }
                    val inferredCat = when {
                        exName.contains("бег", ignoreCase = true) || exName.contains("30 м", ignoreCase = true) -> "Беговые"
                        exName.contains("присед", ignoreCase = true) || exName.contains("штанг", ignoreCase = true) -> "Силовые"
                        else -> "Прыжковые"
                    }
                    list.add(
                        TripleJumpResult(
                            id = exRes.id,
                            eventName = exName,
                            result = exRes.result,
                            resultUnit = exRes.resultUnit,
                            date = exRes.date,
                            isIndoor = false,
                            isAntiRecord = false,
                            recordLevel = "PB",
                            exerciseCategory = inferredCat,
                            isCompetition = false,
                            notes = "Автоматический рекорд из тренировки"
                        )
                    )
                }
            }
        }
        list
    }

    val combinedAllResults = remember(rawResultsList, calculatedTrainingRecords) {
        (rawResultsList + calculatedTrainingRecords).distinctBy { "${it.eventName}_${it.result}_${it.date}" }
    }

    val filteredResults = remember(
        combinedAllResults,
        selectedSourceFilter,
        selectedLocationFilter,
        selectedCategoryFilter,
        selectedRecordTypeFilter
    ) {
        combinedAllResults.filter { item ->
            // Source Filter
            val matchesSource = when (selectedSourceFilter) {
                1 -> item.isCompetition
                2 -> !item.isCompetition
                else -> true
            }
            // Location Filter
            val matchesLocation = when (selectedLocationFilter) {
                1 -> !item.isIndoor
                2 -> item.isIndoor
                else -> true
            }
            // Category Filter
            val matchesCategory = when (selectedCategoryFilter) {
                1 -> item.exerciseCategory.contains("прыжк", ignoreCase = true) || item.eventName.contains("прыжок", ignoreCase = true)
                2 -> item.exerciseCategory.contains("бег", ignoreCase = true) || item.eventName.contains("бег", ignoreCase = true) || item.eventName.contains("30 м", ignoreCase = true)
                3 -> item.exerciseCategory.contains("силов", ignoreCase = true) || item.eventName.contains("штанг", ignoreCase = true) || item.eventName.contains("присед", ignoreCase = true)
                else -> true
            }
            // Record / Anti-Record Filter
            val matchesType = if (selectedRecordTypeFilter == 1) item.isAntiRecord else !item.isAntiRecord

            matchesSource && matchesLocation && matchesCategory && matchesType
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Section
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "ЛИЧНЫЕ ДОСТИЖЕНИЯ И АНТИ-РЕКОРДЫ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Рекорды",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. Action Buttons Directly Under 'Рекорды' Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isAddingAntiRecord = false
                    showAddRecordDialog = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "+ Добавить рекорд", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = {
                    isAddingAntiRecord = true
                    showAddRecordDialog = true
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(imageVector = Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "+ Добавить анти-рекорд", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        // 3. Filter Chips Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Filter Row 1: Record vs Anti-Record
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedRecordTypeFilter == 0,
                    onClick = { selectedRecordTypeFilter = 0 },
                    label = { Text("🏆 Рекорды (Лучшие)", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedRecordTypeFilter == 1,
                    onClick = { selectedRecordTypeFilter = 1 },
                    label = { Text("📉 Анти-рекорды (Худшие)", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.error
                    )
                )
            }

            // Filter Row 2: Source (Соревнования vs Тренировки)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedSourceFilter == 0,
                    onClick = { selectedSourceFilter = 0 },
                    label = { Text("Все источники", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedSourceFilter == 1,
                    onClick = { selectedSourceFilter = 1 },
                    label = { Text("🏆 Соревновательные", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedSourceFilter == 2,
                    onClick = { selectedSourceFilter = 2 },
                    label = { Text("🏋️ Тренировочные", fontSize = 10.sp) }
                )
            }

            // Filter Row 3: Category & Location
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == 0,
                    onClick = { selectedCategoryFilter = 0 },
                    label = { Text("Все виды", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == 1,
                    onClick = { selectedCategoryFilter = 1 },
                    label = { Text("🦘 Прыжковые", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == 2,
                    onClick = { selectedCategoryFilter = 2 },
                    label = { Text("🏃 Беговые", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedCategoryFilter == 3,
                    onClick = { selectedCategoryFilter = 3 },
                    label = { Text("💪 Силовые", fontSize = 10.sp) }
                )

                Spacer(modifier = Modifier.width(8.dp))

                FilterChip(
                    selected = selectedLocationFilter == 1,
                    onClick = { selectedLocationFilter = if (selectedLocationFilter == 1) 0 else 1 },
                    label = { Text("☀️ На улице", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = selectedLocationFilter == 2,
                    onClick = { selectedLocationFilter = if (selectedLocationFilter == 2) 0 else 2 },
                    label = { Text("🏛️ В помещении", fontSize = 10.sp) }
                )
            }
        }

        // 4. Results Cards List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (filteredResults.isNotEmpty()) {
                items(filteredResults) { result ->
                    RecordCard(
                        result = result,
                        onDelete = { viewModel.deleteTripleJumpResult(result.id) }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Нет сохраненных результатов по выбранным фильтрам",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
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

@Composable
fun RecordCard(
    result: TripleJumpResult,
    onDelete: () -> Unit
) {
    val tagColor = if (result.isAntiRecord) MaterialTheme.colorScheme.error
    else if (result.isCompetition) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    val icon = when {
        result.isAntiRecord -> Icons.Default.TrendingDown
        result.exerciseCategory.contains("бег", ignoreCase = true) -> Icons.AutoMirrored.Filled.DirectionsRun
        result.exerciseCategory.contains("силов", ignoreCase = true) -> Icons.Default.FitnessCenter
        else -> Icons.Default.EmojiEvents
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(tagColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tagColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = result.eventName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // PB / SB Badges
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (result.isAntiRecord) MaterialTheme.colorScheme.error
                            else if (result.recordLevel == "PB") Color(0xFFFFB300)
                            else MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = if (result.isAntiRecord) "📉 АНТИ-РЕКОРД" else if (result.recordLevel == "PB") "🏆 PB (Личный)" else "⭐ SB (Сезонный)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }

                    val sourceText = if (result.isCompetition) "Соревнование" else "Тренировка"
                    val locText = if (result.isIndoor) "🏛️ В помещении" else "☀️ На улице"
                    val weatherText = if (!result.isIndoor && !result.weatherConditions.isNullOrBlank()) " · ${result.weatherConditions}" else ""

                    Text(
                        text = "${formatDate(result.date)} · $sourceText · $locText$weatherText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatResultValue(result.result, result.resultUnit),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить рекорд",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

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
