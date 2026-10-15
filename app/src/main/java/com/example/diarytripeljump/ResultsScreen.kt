package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diarytripeljump.data.AthleteProfile
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.TripleJumpResult
import com.example.diarytripeljump.ui.components.AppScreenScaffold
import com.example.diarytripeljump.ui.components.DropdownFilterChip
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.components.screenContentPadding
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    val rawResultsList by viewModel.rawTripleJumpResults.collectAsState()
    val trainingsList by viewModel.allTrainingsWithExercises.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    var showAddRecordDialog by remember { mutableStateOf(false) }
    var showRecordHistoryDialog by remember { mutableStateOf(false) }
    var isAddingAntiRecord by remember { mutableStateOf(false) }

    var selectedSourceFilter by remember { mutableIntStateOf(0) }
    var selectedLocationFilter by remember { mutableIntStateOf(0) }
    var selectedCategoryFilter by remember { mutableIntStateOf(0) }
    var selectedRecordTypeFilter by remember { mutableIntStateOf(0) }

    val calculatedTrainingRecords = remember(trainingsList) {
        val list = mutableListOf<TripleJumpResult>()
        trainingsList.forEach { trainingItem ->
            trainingItem.exerciseResults.forEach { exRes ->
                if (exRes.result > 0) {
                    val exName = exRes.exerciseName.ifBlank { "Упражнение" }
                    val inferredCat = when {
                        exName.contains("бег", ignoreCase = true) -> "Беговые"
                        exName.contains("силов", ignoreCase = true) || exName.contains("присед", ignoreCase = true) -> "Силовые"
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
                            notes = "Рекорд из тренировки"
                        )
                    )
                }
            }
        }
        list
    }

    val combinedAllResults = remember(rawResultsList, calculatedTrainingRecords) {
        (rawResultsList + calculatedTrainingRecords).distinctBy { "${it.eventName}_${it.result}_${it.date}" }
            .sortedByDescending { it.date }
    }

    val filteredResults = remember(
        combinedAllResults,
        selectedSourceFilter,
        selectedLocationFilter,
        selectedCategoryFilter,
        selectedRecordTypeFilter
    ) {
        combinedAllResults.filter { item ->
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
                1 -> item.exerciseCategory.contains("прыжк", ignoreCase = true) || item.eventName.contains("прыжок", ignoreCase = true)
                2 -> item.exerciseCategory.contains("бег", ignoreCase = true) || item.eventName.contains("бег", ignoreCase = true) || item.eventName.contains("30 м", ignoreCase = true)
                3 -> item.exerciseCategory.contains("силов", ignoreCase = true) || item.eventName.contains("штанг", ignoreCase = true) || item.eventName.contains("присед", ignoreCase = true)
                else -> true
            }
            val matchesType = if (selectedRecordTypeFilter == 1) item.isAntiRecord else !item.isAntiRecord

            matchesSource && matchesLocation && matchesCategory && matchesType
        }
    }

    val bestJump = remember(combinedAllResults) {
        combinedAllResults
            .filter { !it.isAntiRecord && it.isCompetition && it.resultUnit == ResultUnit.M }
            .maxByOrNull { it.result }
    }

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
                containerColor = if (isAntiMode) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isAntiMode) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
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

            // History Button
            item {
                OutlinedButton(
                    onClick = { showRecordHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📜 История обновлений рекордов (${combinedAllResults.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (!isAntiMode && bestJump != null) {
                item { BestResultCard(result = bestJump) }
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
                        title = "Локация",
                        options = listOf("Все места", "На улице", "В помещении"),
                        selectedIndex = selectedLocationFilter,
                        onSelect = { selectedLocationFilter = it }
                    )
                }
            }

            if (filteredResults.isNotEmpty()) {
                items(filteredResults, key = { "rec_${it.id}_${it.date}" }) { result ->
                    RecordCardItem(
                        result = result,
                        onDelete = { viewModel.deleteTripleJumpResult(result.id) }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
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

    if (showRecordHistoryDialog) {
        RecordHistoryModalDialog(
            historyList = combinedAllResults,
            onDismiss = { showRecordHistoryDialog = false }
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

@Composable
fun BestResultCard(result: TripleJumpResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆 ГЛАВНЫЙ РЕКОРД (PB)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "ОФИЦИАЛЬНО",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = result.eventName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${formatDateLocal(result.date)} · ${if (result.isIndoor) "🏛️ В помещении" else "☀️ На улице"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Text(
                    text = formatResultValue(result.result, result.resultUnit),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RecordCardItem(
    result: TripleJumpResult,
    onDelete: () -> Unit
) {
    val tagColor = if (result.isAntiRecord) MaterialTheme.colorScheme.error
    else if (result.isCompetition) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

    val icon = when {
        result.isAntiRecord -> Icons.AutoMirrored.Filled.TrendingDown
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
                        text = "${formatDateLocal(result.date)} · $sourceText · $locText$weatherText",
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
fun RecordHistoryModalDialog(
    historyList: List<TripleJumpResult>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "📜 История обновлений рекордов", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (historyList.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(historyList) { item ->
                            val sourceLabel = if (item.isCompetition) "🏆 Соревнование" else "🏋️ Тренировка"
                            val typeLabel = if (item.isAntiRecord) "📉 Анти-рекорд" else if (item.recordLevel == "PB") "🏆 PB (Личный)" else "⭐ SB (Сезонный)"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.eventName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatResultValue(item.result, item.resultUnit),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatDateLocal(item.date)} · $sourceLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = typeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.isAntiRecord) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "История пока пуста",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
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
    var recordLevel by remember { mutableStateOf("PB") }
    var selectedCategory by remember { mutableStateOf("Прыжковые") }
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

                if (!isAntiRecord) {
                    Text(text = "Уровень рекорда:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = recordLevel == "PB",
                            onClick = { recordLevel = "PB" },
                            label = { Text("🏆 PB (Личный)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = recordLevel == "SB",
                            onClick = { recordLevel = "SB" },
                            label = { Text("⭐ SB (Сезонный)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Соревновательный результат:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Switch(checked = isCompetition, onCheckedChange = { isCompetition = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "В помещении (Indoor):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Switch(checked = isIndoor, onCheckedChange = { isIndoor = it })
                }

                if (!isIndoor) {
                    OutlinedTextField(
                        value = weatherText,
                        onValueChange = { weatherText = it },
                        label = { Text("Погода / Условия (на улице)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = resultValueText,
                    onValueChange = { input -> resultValueText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Результат (${getUnitTitleLocal(selectedUnit)})") },
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

fun getUnitTitleLocal(unit: ResultUnit): String {
    return when (unit) {
        ResultUnit.KG -> "кг"
        ResultUnit.CM -> "см"
        ResultUnit.M -> "м"
        ResultUnit.REPS -> "повторений"
        ResultUnit.SECONDS -> "сек"
    }
}

private fun formatDateLocal(timestamp: Long): String {
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
