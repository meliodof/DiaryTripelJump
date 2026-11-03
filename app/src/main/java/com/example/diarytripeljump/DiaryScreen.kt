package com.example.diarytripeljump

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.example.diarytripeljump.data.Competition
import com.example.diarytripeljump.data.CompetitionPhase
import com.example.diarytripeljump.data.phase
import com.example.diarytripeljump.data.CompetitionStatus
import com.example.diarytripeljump.data.RecordEvent
import com.example.diarytripeljump.data.SeasonStats
import com.example.diarytripeljump.data.formatRecordDelta
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.data.TrainingWithExercises
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.ProfileAvatarButton
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.components.StatTile
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.components.screenContentPadding
import com.example.diarytripeljump.ui.theme.NumberMedium
import com.example.diarytripeljump.ui.theme.extraColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.ExerciseGoal
import com.example.diarytripeljump.data.RecordSummary
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.computeGoalProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DiaryScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onNavigate: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val trainings by viewModel.allTrainingsWithExercises.collectAsState()
    val competitions by viewModel.allCompetitions.collectAsState()
    val startResults by viewModel.tripleJumpStartResults.collectAsState()
    val favorites by viewModel.favoriteExercises.collectAsState()
    val allExercises by viewModel.allExercises.collectAsState()
    val records by viewModel.records.collectAsState()
    val goals by viewModel.goals.collectAsState()
    var showFavoritesPicker by remember { mutableStateOf(false) }
    val todayStart = com.example.diarytripeljump.ui.components.rememberTodayStart()

    val dateText = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
            .format(Date())
            .replaceFirstChar { it.uppercase() }
    }

    val displayGreeting = remember(athleteProfile) {
        val baseGreeting = when (Calendar.getInstance()[Calendar.HOUR_OF_DAY]) {
            in 5..11 -> "Доброе утро"
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Полоса под статус-баром: контент прокручивается под неё и не налезает на часы/иконки
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = screenContentPadding(inner, extraTop = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                HomeHeader(
                    dateText = dateText,
                    greeting = displayGreeting,
                    avatarUri = athleteProfile?.avatarUriString,
                    onOpenProfile = onOpenProfile
                )
            }

            if (isBirthday) {
                item { BirthdayBanner() }
            }

            item {
                val next = competitions
                    .filter { it.phase(todayStart) != CompetitionPhase.PAST }
                    .minByOrNull { it.date }
                if (next != null) {
                    NextCompetitionHero(competition = next, onClick = { onNavigate(Routes.COMPETITIONS) })
                } else {
                    EmptyState(
                        icon = Icons.Default.EventAvailable,
                        title = "Ближайших стартов нет",
                        message = "Добавьте соревнование — здесь появится обратный отсчёт до старта.",
                        actionLabel = "Добавить старт",
                        onAction = { onNavigate(Routes.COMPETITIONS) }
                    )
                }
            }

            item {
                WeekOverviewCard(
                    trainings = trainings,
                    competitions = competitions,
                    onClick = { onNavigate(Routes.TRAININGS) }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Сезон ${uiState.seasonStats?.year ?: Calendar.getInstance()[Calendar.YEAR]}",
                        actionLabel = "Рекорды",
                        onAction = { onNavigate(Routes.RECORDS) }
                    )
                    SeasonStatsRow(
                        stats = uiState.seasonStats,
                        onTrainingsClick = { onNavigate(Routes.TRAININGS) },
                        onCompetitionsClick = { onNavigate(Routes.COMPETITIONS) },
                        onRecordsClick = { onNavigate(Routes.RECORDS) }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Результаты в тройном прыжке",
                        subtitle = "Прошедшие старты",
                        actionLabel = "Старты",
                        onAction = { onNavigate(Routes.COMPETITIONS) }
                    )
                    if (startResults.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.EmojiEvents,
                            title = "Пока нет результатов стартов",
                            message = "Добавьте попытки в завершённый старт — результат появится здесь."
                        )
                    } else {
                        AppCard {
                            val rows = startResults.take(5)
                            rows.forEachIndexed { index, row ->
                                StartResultRow(row = row, onClick = { onNavigate(Routes.COMPETITIONS) })
                                if (index < rows.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        title = "Мои упражнения",
                        subtitle = "Избранные упражнения из каталога",
                        actionLabel = "Выбрать",
                        onAction = { showFavoritesPicker = true }
                    )
                    if (favorites.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.FitnessCenter,
                            title = "Упражнения не выбраны",
                            message = "Выберите упражнения, за результатами которых хотите следить, — они появятся здесь.",
                            actionLabel = "Выбрать упражнения",
                            onAction = { showFavoritesPicker = true }
                        )
                    } else {
                        AppCard {
                            favorites.forEachIndexed { index, ex ->
                                FavoriteExerciseRow(
                                    exercise = ex,
                                    records = records,
                                    goals = goals,
                                    trainings = trainings,
                                    onClick = { onNavigate(Routes.RECORDS) }
                                )
                                if (index < favorites.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFavoritesPicker) {
        FavoritesPickerDialog(
            exercises = remember(allExercises) { allExercises.distinctBy { it.variantKey() }.sortedBy { it.title().lowercase() } },
            onToggle = { ex, fav -> viewModel.setExerciseFavorite(ex, fav) },
            onDismiss = { showFavoritesPicker = false }
        )
    }
}

@Composable
private fun HomeHeader(
    dateText: String,
    greeting: String,
    avatarUri: String?,
    onOpenProfile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        ProfileAvatarButton(avatarUri = avatarUri, onClick = onOpenProfile, size = 44.dp)
    }
}

@Composable
private fun BirthdayBanner() {
    val extra = MaterialTheme.extraColors
    Surface(
        shape = MaterialTheme.shapes.large,
        color = extra.goldContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🎉", style = MaterialTheme.typography.displaySmall)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "С днём рождения!",
                    style = MaterialTheme.typography.titleMedium,
                    color = extra.onGoldContainer
                )
                Text(
                    text = "Желаем новых личных рекордов, высоких результатов и побед на всех стартах!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.onGoldContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/** Hero-карточка с обратным отсчётом до ближайшего старта. */
@Composable
private fun NextCompetitionHero(
    competition: Competition,
    onClick: () -> Unit
) {
    val extra = MaterialTheme.extraColors
    val onHero = extra.onHero
    val days = daysUntil(competition.date)
    val countdown = when {
        days <= 0 -> "Сегодня"
        days == 1 -> "Завтра"
        else -> "Через $days ${pluralRu(days, "день", "дня", "дней")}"
    }
    val place = listOfNotNull(competition.location, competition.address)
        .firstOrNull { it.isNotBlank() } ?: "Место уточняется"

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = "Ближайший старт",
                        contentColor = onHero,
                        containerColor = onHero.copy(alpha = 0.18f),
                        icon = Icons.Default.EmojiEvents
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Открыть старты",
                        tint = onHero.copy(alpha = 0.9f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.headlineLarge,
                        color = onHero
                    )
                    Text(
                        text = competition.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = onHero.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroInfo(icon = Icons.Default.Event, text = formatDateShort(competition.date), color = onHero)
                    HeroInfo(icon = Icons.Default.Place, text = place, color = onHero, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroInfo(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class WeekDay(
    val label: String,
    val number: Int,
    val isToday: Boolean,
    val trained: Boolean,
    val skipped: Boolean,
    val competition: Boolean
)

/** Полоса «Эта неделя»: пн–вс, отметки тренировок, пропусков и стартов. */
@Composable
private fun WeekOverviewCard(
    trainings: List<TrainingWithExercises>,
    competitions: List<Competition>,
    onClick: () -> Unit
) {
    val week = remember(trainings, competitions) { buildWeek(trainings, competitions) }
    val trainedCount = week.count { it.trained }

    AppCard(onClick = onClick) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Эта неделя",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$trainedCount ${pluralRu(trainedCount, "тренировка", "тренировки", "тренировок")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = day.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        WeekDayCircle(day)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = MaterialTheme.colorScheme.primary, label = "Тренировка")
                LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "Старт")
                LegendDot(color = MaterialTheme.colorScheme.error, label = "Пропуск")
            }
        }
    }
}

@Composable
private fun WeekDayCircle(day: WeekDay) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        day.competition -> cs.tertiary to cs.onTertiary
        day.trained -> cs.primary to cs.onPrimary
        day.skipped -> cs.errorContainer to cs.onErrorContainer
        else -> Color.Transparent to cs.onSurface
    }
    var modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(bg)
    if (day.isToday && bg == Color.Transparent) {
        modifier = modifier.border(BorderStroke(2.dp, cs.primary), CircleShape)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (day.trained && !day.competition) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        } else {
            Text(
                text = day.number.toString(),
                style = NumberMedium,
                color = if (day.isToday && bg == Color.Transparent) cs.primary else fg
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

@Composable
private fun SeasonStatsRow(
    stats: SeasonStats?,
    onTrainingsClick: () -> Unit,
    onCompetitionsClick: () -> Unit,
    onRecordsClick: () -> Unit
) {
    val best = stats?.bestTripleJump
    val bestText = if (best != null && best > 0) String.format(Locale.getDefault(), "%.2f", best) else "—"

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            value = "${stats?.totalCompetitions ?: 0}",
            label = "Стартов",
            icon = Icons.Default.EmojiEvents,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
            onClick = onCompetitionsClick
        )
        StatTile(
            value = "${stats?.totalTrainings ?: 0}",
            label = "Тренировок",
            icon = Icons.Default.FitnessCenter,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onTrainingsClick
        )
        StatTile(
            value = bestText,
            label = "Лучший, м",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
            onClick = onRecordsClick
        )
    }
}

@Composable
private fun StartResultRow(row: StartResultUi, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val dateText = remember(row.competition.date) {
        SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(row.competition.date))
    }
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Default.EmojiEvents, tint = cs.tertiary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.competition.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(dateText, row.placement?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = String.format(Locale.getDefault(), "%.2f м", row.best), style = NumberMedium, color = cs.onSurface)
                if (row.isPersonalBest) {
                    StatusPill(text = "Лучший", contentColor = extra.onGoldContainer, containerColor = extra.goldContainer, icon = Icons.Default.Star)
                }
            }
        }
    }
}

@Composable
private fun FavoriteExerciseRow(
    exercise: Exercise,
    records: List<RecordSummary>,
    goals: List<ExerciseGoal>,
    trainings: List<TrainingWithExercises>,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val unit = exercise.defaultUnit
    val key = RecordsEngine.exerciseRecordKey(
        exercise.name, exercise.isSingleLeg, exercise.legSide, unit, exercise.inSpikes,
        RecordsEngine.recordInventory(exercise.inventoryName, exercise.inventoryUnit, exercise.inventoryValue, unit)
    )

    val record = remember(records, key) {
        records.firstOrNull { !it.isAnti && it.key == key }
            ?: records.firstOrNull {
                !it.isAnti && !it.isCompetition && it.name.equals(exercise.name.trim(), true) && it.isSingleLeg == exercise.isSingleLeg
            }
    }
    // последний внесённый результат этого упражнения
    val last = remember(trainings, key) {
        trainings.asSequence()
            .filter { !it.training.isSkipped }
            .flatMap { it.exerciseResults.asSequence() }
            .filter {
                RecordsEngine.exerciseRecordKey(
                    it.exerciseName, it.isSingleLeg, it.legSide, it.resultUnit, it.inSpikes,
                    RecordsEngine.recordInventory(it.inventoryName, it.inventoryUnit, it.inventoryValue, it.resultUnit)
                ) == key && it.result > 0
            }
            .maxByOrNull { it.date }
    }
    val goalProgress = goals.firstOrNull { it.recordKey == (record?.key ?: key) }
        ?.let { computeGoalProgress(it, record?.best) }

    Surface(onClick = onClick, color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon = Icons.Default.FitnessCenter, tint = cs.primary)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = exercise.title(),
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // «Последний» — значение и дата отдельными строками, чтобы ничего не обрезалось
                    if (last != null) {
                        Text(
                            text = "Последний ${formatRecordValue(last.result, last.resultUnit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            text = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(last.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    } else {
                        Text(text = "Результатов пока нет", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = record?.let { formatRecordValue(it.best, it.unit) } ?: "—",
                        style = NumberMedium,
                        color = cs.onSurface
                    )
                    Text(text = "рекорд", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            if (goalProgress != null) {
                GoalProgressBlock(goalProgress, modifier = Modifier.padding(start = 52.dp))
            }
        }
    }
}

/** Выбор упражнений, которые показываются на главном экране (то же, что «Избранное» в каталоге). */
@Composable
private fun FavoritesPickerDialog(
    exercises: List<Exercise>,
    onToggle: (Exercise, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val shown = remember(exercises, query) {
        exercises.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Упражнения на главной", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (shown.isEmpty()) {
                        Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                    shown.forEach { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(ex, !ex.isFavorite) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = ex.isFavorite, onCheckedChange = { onToggle(ex, it) })
                            Text(
                                text = ex.title(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}

private fun buildWeek(
    trainings: List<TrainingWithExercises>,
    competitions: List<Competition>
): List<WeekDay> {
    val cal = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY }
    val todayKey = dayKey(cal.timeInMillis)
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

    val trainedKeys = trainings.filter { !it.training.isSkipped }.map { dayKey(it.training.date) }.toSet()
    val skippedKeys = trainings.filter { it.training.isSkipped }.map { dayKey(it.training.date) }.toSet()
    val competitionKeys = competitions
        .filter { it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
        .map { dayKey(it.date) }
        .toSet()

    val labels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    return labels.map { label ->
        val key = dayKey(cal.timeInMillis)
        val day = WeekDay(
            label = label,
            number = cal.get(Calendar.DAY_OF_MONTH),
            isToday = key == todayKey,
            trained = key in trainedKeys,
            skipped = key in skippedKeys && key !in trainedKeys,
            competition = key in competitionKeys
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
        day
    }
}

private fun dayKey(millis: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
}

private fun daysUntil(timestamp: Long): Int {
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val diff = startOfDay(timestamp) - startOfDay(System.currentTimeMillis())
    return Math.round(diff / 86_400_000.0).toInt()
}

private fun formatDateShort(timestamp: Long): String {
    return SimpleDateFormat("d MMM", Locale("ru")).format(Date(timestamp))
}
