package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ResultsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Filter state (0: Все, 1: Соревновательные, 2: Тренировочные)
    var selectedFilter by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row & Add Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ЛИЧНЫЕ ДОСТИЖЕНИЯ",
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

            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Добавить", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Filter Chips Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChipItem(
                title = "Все",
                isSelected = selectedFilter == 0,
                onClick = { selectedFilter = 0 }
            )
            FilterChipItem(
                title = "🏆 Соревновательные",
                isSelected = selectedFilter == 1,
                onClick = { selectedFilter = 1 }
            )
            FilterChipItem(
                title = "💪 Тренировочные",
                isSelected = selectedFilter == 2,
                onClick = { selectedFilter = 2 }
            )
        }

        val showCompetitions = selectedFilter == 0 || selectedFilter == 1
        val showTrainings = selectedFilter == 0 || selectedFilter == 2

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Section 1: Соревновательные результаты
            if (showCompetitions && uiState.tripleJumpResults.isNotEmpty()) {
                item {
                    Text(
                        text = "СОРЕВНОВАТЕЛЬНЫЕ РЕКОРДЫ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                itemsIndexed(uiState.tripleJumpResults) { _, result ->
                    RecordCard(
                        title = result.event,
                        resultText = result.result,
                        dateText = "${result.date} · ЧР 2026 · Лужники",
                        isPB = result.isPB,
                        tagText = "СОРЕВНОВАНИЕ",
                        tagColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onDelete = { viewModel.deleteTripleJumpResult(result.id) }
                    )
                }
            }

            // Section 2: Тренировочные рекорды в упражнениях
            if (showTrainings && uiState.favoriteExerciseResults.isNotEmpty()) {
                item {
                    Text(
                        text = "ТРЕНИРОВОЧНЫЕ РЕКОРДЫ В УПРАЖНЕНИЯХ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                itemsIndexed(uiState.favoriteExerciseResults) { _, exercise ->
                    RecordCard(
                        title = exercise.exercise,
                        resultText = exercise.result,
                        dateText = "${exercise.date} · Силовая тренировка",
                        isPB = exercise.isPB,
                        tagText = "ТРЕНИРОВКА",
                        tagColor = MaterialTheme.colorScheme.secondary,
                        icon = Icons.Default.FitnessCenter,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onDelete = { viewModel.deleteExerciseResult(exercise.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddResultDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { resultVal, weather, notes ->
                viewModel.addTripleJumpResult(
                    result = resultVal,
                    date = System.currentTimeMillis(),
                    weatherConditions = weather,
                    notes = notes
                )
                showAddDialog = false
            }
        )
    }
}

@Composable
fun RecordCard(
    title: String,
    resultText: String,
    dateText: String,
    isPB: Boolean,
    tagText: String,
    tagColor: Color,
    icon: ImageVector,
    iconTint: Color,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tagColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = tagColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = tagText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = tagColor,
                                fontSize = 8.sp
                            )
                        }
                    }

                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isPB) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "PB",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Text(
                    text = resultText,
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
fun AddResultDialog(
    onDismiss: () -> Unit,
    onConfirm: (result: Double, weather: String, notes: String) -> Unit
) {
    var resultText by remember { mutableStateOf("14.60") }
    var weatherText by remember { mutableStateOf("Ясно, +22°C") }
    var notesText by remember { mutableStateOf("Новый рекорд") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Новый Рекорд / Результат", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = resultText,
                    onValueChange = { resultText = it },
                    label = { Text("Результат (метров)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weatherText,
                    onValueChange = { weatherText = it },
                    label = { Text("Условия / Погода") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Заметки к прыжку") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = resultText.toDoubleOrNull() ?: 14.50
                    onConfirm(res, weatherText, notesText)
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
