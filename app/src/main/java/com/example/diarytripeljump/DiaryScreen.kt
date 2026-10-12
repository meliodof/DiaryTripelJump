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
import androidx.compose.ui.text.style.TextAlign
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
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    val currentDateUpper = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(Date()).uppercase()

    val displayGreeting = remember(athleteProfile) {
        val baseGreeting = when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
            in 5..11 -> "Добрый день"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val nameToUse = if (athleteProfile?.greetingPreference == "NICKNAME") {
            val nick = athleteProfile?.nickname ?: ""
            if (nick.isNotBlank()) if (nick.startsWith("@")) nick else "@$nick" else athleteProfile?.firstName ?: ""
        } else {
            athleteProfile?.firstName ?: ""
        }

        if (nameToUse.isNotBlank()) "$baseGreeting, $nameToUse" else baseGreeting
    }

    val isBirthday = isTodayBirthday(athleteProfile?.birthDate)

    var selectedResultFilter by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = currentDateUpper,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Text(
                    text = displayGreeting,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // Birthday Congratulation Banner (If today is athlete's birthday!)
        if (isBirthday) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB300)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎉", fontSize = 32.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "С ДНЁМ РОЖДЕНИЯ!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Text(
                            text = "Желаем новых личных рекордов, высоких результатов и побед на всех стартах!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Next Competition Card
        if (uiState.upcomingCompetitions.isNotEmpty()) {
            val nextComp = uiState.upcomingCompetitions.first()
            NextCompetitionCard(
                competition = nextComp,
                isDarkTheme = uiState.isDarkTheme,
                onCardClick = { onNavigateToTab(2) }
            )
        } else {
            EmptyNextCompetitionCard(
                onCardClick = { onNavigateToTab(2) }
            )
        }

        // 3. Блок статистики сезона
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.seasonStats?.year ?: 2025} СЕЗОН",
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

        // 4. Последние результаты
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                val showCompetitions = selectedResultFilter == 0 || selectedResultFilter == 1
                val showTrainings = selectedResultFilter == 0 || selectedResultFilter == 2

                val hasResults = (showCompetitions && uiState.tripleJumpResults.isNotEmpty()) ||
                        (showTrainings && uiState.favoriteExerciseResults.isNotEmpty())

                if (!hasResults) {
                    Text(
                        text = "Нет сохраненных результатов в базе данных",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column {
                        if (showCompetitions && uiState.tripleJumpResults.isNotEmpty()) {
                            uiState.tripleJumpResults.take(3).forEachIndexed { index, result ->
                                ResultRowItem(
                                    category = result.event.uppercase(),
                                    resultText = result.result,
                                    dateText = result.date,
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
                                    dateText = exercise.date,
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
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
fun EmptyNextCompetitionCard(
    onCardClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "БЛИЖАЙШИЙ СТАРТ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Запланированных соревнований нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Нажмите, чтобы открыть календарь стартов и добавить соревнование.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(value = "${stats?.totalCompetitions ?: 0}", label = "Стартов")
            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(28.dp))
            StatItem(value = "${stats?.totalTrainings ?: 0}", label = "Тренировок")
            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(28.dp))
            val bestText = if (stats?.bestTripleJump != null && stats.bestTripleJump > 0) String.format(Locale.getDefault(), "%.2f м", stats.bestTripleJump) else "— м"
            StatItem(value = bestText, label = "Лучший прыжок")
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
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
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = category,
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isPB) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = "PB",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
            Text(
                text = resultText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
