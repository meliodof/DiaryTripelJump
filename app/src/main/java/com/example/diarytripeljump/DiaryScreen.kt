package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.diarytripeljump.data.Competition
import com.example.diarytripeljump.data.SeasonStats
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DiaryScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onNavigateToTab: (Int) -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentDateUpper = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(Date()).uppercase()
    val greeting = when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
        in 5..11 -> "Добрый день"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    var selectedResultFilter by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header Row: Subtitle + Greeting & Action Buttons (Theme Toggle + Profile)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = currentDateUpper,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Top Right Action Buttons: Theme Toggle + Profile Avatar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme Switcher Button
                IconButton(
                    onClick = { viewModel.toggleTheme() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Сменить тему",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Profile Avatar Icon (Navigates specifically to Profile Screen)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Профиль",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. Next Competition Card (Adapts to Light / Dark Theme)
        if (uiState.upcomingCompetitions.isNotEmpty()) {
            val nextComp = uiState.upcomingCompetitions.first()
            NextCompetitionCard(
                competition = nextComp,
                isDarkTheme = uiState.isDarkTheme,
                onCardClick = { onNavigateToTab(2) }
            )
        } else {
            DefaultNextCompetitionCard(
                isDarkTheme = uiState.isDarkTheme,
                onCardClick = { onNavigateToTab(2) }
            )
        }

        // 3. Уникальный виджет: Фазы Тройного Прыжка
        TripleJumpPhasesWidget()

        // 4. Блок статистики сезона (PB, SB, Периоды подготовки, Старты)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.seasonStats?.year ?: 2026} СЕЗОН",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Статистика",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToTab(3) }
                )
            }

            SeasonStatsCard(stats = uiState.seasonStats)
        }

        // 5. Последние результаты (Соревновательные vs Тренировочные)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ПОСЛЕДНИЕ РЕЗУЛЬТАТЫ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Все рекорды",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToTab(3) }
                )
            }

            // Filter Chips: Все | Соревнования | Тренировки
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChipItem(
                    title = "Все",
                    isSelected = selectedResultFilter == 0,
                    onClick = { selectedResultFilter = 0 }
                )
                FilterChipItem(
                    title = "🏆 Соревнования",
                    isSelected = selectedResultFilter == 1,
                    onClick = { selectedResultFilter = 1 }
                )
                FilterChipItem(
                    title = "💪 Тренировки",
                    isSelected = selectedResultFilter == 2,
                    onClick = { selectedResultFilter = 2 }
                )
            }

            // Results Card Container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    val showCompetitions = selectedResultFilter == 0 || selectedResultFilter == 1
                    val showTrainings = selectedResultFilter == 0 || selectedResultFilter == 2

                    if (showCompetitions && uiState.tripleJumpResults.isNotEmpty()) {
                        uiState.tripleJumpResults.take(3).forEachIndexed { index, result ->
                            ResultRowItem(
                                category = result.event.uppercase(),
                                resultText = result.result,
                                dateText = "${result.date} · Чемпионат России",
                                isPB = result.isPB,
                                tagText = "СОРЕВНОВАНИЕ",
                                tagColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.AutoMirrored.Filled.DirectionsRun,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = { onNavigateToTab(3) }
                            )
                            if (index < uiState.tripleJumpResults.take(3).size - 1 || (showTrainings && uiState.favoriteExerciseResults.isNotEmpty())) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                            }
                        }
                    }

                    if (showTrainings && uiState.favoriteExerciseResults.isNotEmpty()) {
                        uiState.favoriteExerciseResults.take(3).forEachIndexed { index, exercise ->
                            ResultRowItem(
                                category = exercise.exercise.uppercase(),
                                resultText = exercise.result,
                                dateText = "${exercise.date} · Манеж · Силовая",
                                isPB = exercise.isPB,
                                tagText = "ТРЕНИРОВКА",
                                tagColor = MaterialTheme.colorScheme.secondary,
                                icon = Icons.Default.FitnessCenter,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                onClick = { onNavigateToTab(1) }
                            )
                            if (index < uiState.favoriteExerciseResults.take(3).size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
fun FilterChipItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Виджет: Фазы Тройного Прыжка
@Composable
fun TripleJumpPhasesWidget() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ФАЗЫ ПРЫЖКА",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Итого: 14.40 м",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Phase Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(0.30f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(0.34f)
                        .fillMaxHeight()
                        .background(Color(0xFF6B4EFF))
                )
            }

            // Phase Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PhaseDetailItem(label = "Скачок (Hop)", value = "5.15 м", percentage = "36%", color = MaterialTheme.colorScheme.primary)
                PhaseDetailItem(label = "Шаг (Step)", value = "4.35 м", percentage = "30%", color = MaterialTheme.colorScheme.secondary)
                PhaseDetailItem(label = "Прыжок (Jump)", value = "4.90 м", percentage = "34%", color = Color(0xFF6B4EFF))
            }
        }
    }
}

@Composable
fun PhaseDetailItem(
    label: String,
    value: String,
    percentage: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Text(
            text = "$value ($percentage)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NextCompetitionCard(
    competition: Competition,
    isDarkTheme: Boolean,
    onCardClick: () -> Unit
) {
    val containerBg = if (isDarkTheme) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.primaryContainer
    val titleColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    val subtitleColor = if (isDarkTheme) Color(0xFFA1A1A6) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "БЛИЖАЙШИЙ СТАРТ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "ЧЕРЕЗ 6 ДНЕЙ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFF8E8E93) else MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = competition.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = "${formatDate(competition.date)} · ${competition.location ?: "Место уточняется"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = competition.eventType,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkTheme) Color(0xFF2C2C2E) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "Квалификация",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) Color(0xFFD1D1D6) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultNextCompetitionCard(
    isDarkTheme: Boolean,
    onCardClick: () -> Unit
) {
    val containerBg = if (isDarkTheme) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.primaryContainer
    val titleColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    val subtitleColor = if (isDarkTheme) Color(0xFFA1A1A6) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "БЛИЖАЙШИЙ СТАРТ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "ЧЕРЕЗ 6 ДНЕЙ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFF8E8E93) else MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Чемпионат России",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = "21 Сен · Стадион Лужники, Москва",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "Тройной прыжок",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkTheme) Color(0xFF2C2C2E) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "Длина",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) Color(0xFFD1D1D6) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SeasonStatsCard(stats: SeasonStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatColumn(
                value = (stats?.personalRecords ?: 5).toString(),
                label = "PB",
                isOrange = true
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(36.dp))

            StatColumn(
                value = (stats?.seasonalRecords ?: 8).toString(),
                label = "SB",
                isOrange = false
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(36.dp))

            StatColumn(
                value = (stats?.totalTrainings ?: 4).toString(),
                label = "ПЕРИОДЫ ПОДГОТОВКИ",
                isOrange = false
            )

            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(36.dp))

            StatColumn(
                value = (stats?.totalCompetitions ?: 3).toString(),
                label = "СТАРТЫ",
                isOrange = false
            )
        }
    }
}

@Composable
fun StatColumn(
    value: String,
    label: String,
    isOrange: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (isOrange) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

@Composable
fun ResultRowItem(
    category: String,
    resultText: String,
    dateText: String,
    isPB: Boolean,
    tagText: String,
    tagColor: Color,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tagColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = tagColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tagText,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tagColor,
                            fontSize = 8.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    )

                    if (isPB) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "PB",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
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

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
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
