package com.example.diarytripeljump

import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.RunUpAttempt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
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
import com.example.diarytripeljump.data.Competition
import com.example.diarytripeljump.data.CompetitionStatus
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.diarytripeljump.data.CompetitionPhase
import com.example.diarytripeljump.data.phase
import com.example.diarytripeljump.ui.components.*
import com.example.diarytripeljump.ui.theme.NumberLarge
import com.example.diarytripeljump.ui.theme.extraColors

@Composable
fun CompetitionsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    val competitionsList by viewModel.allCompetitions.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCompetition by remember { mutableStateOf<Competition?>(null) }
    var itemToDelete by remember { mutableStateOf<Competition?>(null) }
    var skippingCompetition by remember { mutableStateOf<Competition?>(null) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Раздел определяется по дате и обновляется автоматически (в полночь)
    val todayStart = rememberTodayStart()
    val upcomingList = remember(competitionsList, todayStart) {
        competitionsList.filter { it.phase(todayStart) == CompetitionPhase.UPCOMING }.sortedBy { it.date }
    }
    val currentList = remember(competitionsList, todayStart) {
        competitionsList.filter { it.phase(todayStart) == CompetitionPhase.CURRENT }.sortedBy { it.date }
    }
    val pastList = remember(competitionsList, todayStart) {
        competitionsList.filter { it.phase(todayStart) == CompetitionPhase.PAST }.sortedByDescending { it.date }
    }

    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    AppScreenScaffold(
        modifier = modifier,
        title = "Старты",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                expanded = fabExpanded,
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                text = { Text(text = "Старт") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                    options = listOf(
                        "Предстоящие (${upcomingList.size})",
                        "Текущие (${currentList.size})",
                        "Прошедшие (${pastList.size})"
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            val shown = when (selectedTab) {
                0 -> upcomingList
                1 -> currentList
                else -> pastList
            }

            if (shown.isNotEmpty()) {
                items(shown, key = { it.id }) { competition ->
                    CompetitionCardItem(
                        competition = competition,
                        onEditClick = { editingCompetition = competition },
                        onDeleteClick = { itemToDelete = competition },
                        onSkipClick = { skippingCompetition = competition }
                    )
                }
            } else {
                item {
                    when (selectedTab) {
                        0 -> EmptyState(
                            icon = Icons.Default.EmojiEvents,
                            title = "Нет предстоящих стартов",
                            message = "Добавьте соревнование, и оно появится здесь с обратным отсчётом.",
                            actionLabel = "Добавить старт",
                            onAction = { showAddDialog = true }
                        )
                        1 -> EmptyState(
                            icon = Icons.Default.EventAvailable,
                            title = "Сегодня стартов нет",
                            message = "В день соревнования оно автоматически появится в этом разделе."
                        )
                        else -> EmptyState(
                            icon = Icons.Default.Flag,
                            title = "Пока нет прошедших стартов",
                            message = "На следующий день после старта соревнование переедет сюда."
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog: Add Competition
    if (showAddDialog) {
        AddOrEditCompetitionDialog(
            competitionToEdit = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, country, city, streetAndNumber, eventTime, hasQual, qualStandard, didNotQual, placement, attemptsJson, date, runUpJson ->
                viewModel.addCompetition(
                    name = name,
                    country = country,
                    city = city,
                    streetAndNumber = streetAndNumber,
                    eventTime = eventTime,
                    hasQualification = hasQual,
                    qualificationStandard = qualStandard,
                    didNotQualifyForFinal = didNotQual,
                    placement = placement,
                    attemptsJson = attemptsJson,
                    date = date,
                    eventType = "Тройной прыжок",
                    runUpJson = runUpJson
                )
                showAddDialog = false
            }
        )
    }

    // Modal Dialog: Edit Competition
    if (editingCompetition != null) {
        AddOrEditCompetitionDialog(
            competitionToEdit = editingCompetition,
            onDismiss = { editingCompetition = null },
            onConfirm = { name, country, city, streetAndNumber, eventTime, hasQual, qualStandard, didNotQual, placement, attemptsJson, date, runUpJson ->
                val fullAddr = listOfNotNull(country, city, streetAndNumber).joinToString(", ")
                val updated = editingCompetition!!.copy(
                    name = name,
                    country = country,
                    city = city,
                    streetAndNumber = streetAndNumber,
                    location = fullAddr,
                    address = fullAddr,
                    eventTime = eventTime,
                    hasQualification = hasQual,
                    qualificationStandard = qualStandard,
                    didNotQualifyForFinal = didNotQual,
                    placement = placement,
                    attemptsJson = attemptsJson,
                    date = date,
                    runUpJson = runUpJson
                )
                viewModel.updateCompetition(updated)
                editingCompetition = null
            }
        )
    }

    // Modal Dialog: Skip Competition
    if (skippingCompetition != null) {
        SkipCompetitionReasonDialog(
            competition = skippingCompetition!!,
            onDismiss = { skippingCompetition = null },
            onConfirm = { reason ->
                val skippedComp = skippingCompetition!!.copy(
                    status = CompetitionStatus.SKIPPED,
                    skipReason = reason
                )
                viewModel.updateCompetition(skippedComp)
                skippingCompetition = null
            }
        )
    }

    // Modal Dialog: Delete Competition Confirmation
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Удаление соревнования", fontWeight = FontWeight.Bold) },
            text = { Text("Вы действительно хотите удалить соревнование '${itemToDelete?.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCompetition(itemToDelete!!)
                        itemToDelete = null
                    },
                    colors = destructiveButtonColors()
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private val ShortMonthsUpper = listOf("ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН", "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК")

private fun compDaysUntil(timestamp: Long): Int {
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

/** Карточка старта в стиле «билета»: слева плашка с датой, справа название, статусы и детали. */
@Composable
fun CompetitionCardItem(
    competition: Competition,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.extraColors
    val isSkipped = competition.status == CompetitionStatus.SKIPPED
    val days = compDaysUntil(competition.date)
    val isToday = days == 0 && !isSkipped
    val isFuture = days > 0 && !isSkipped

    val badgeContainer = when {
        isSkipped -> cs.errorContainer
        isToday -> cs.primary
        isFuture -> cs.tertiaryContainer
        else -> cs.surfaceContainerHigh
    }
    val badgeContent = when {
        isSkipped -> cs.onErrorContainer
        isToday -> cs.onPrimary
        isFuture -> cs.onTertiaryContainer
        else -> cs.onSurfaceVariant
    }

    val cal = remember(competition.date) { Calendar.getInstance().apply { timeInMillis = competition.date } }
    val monthLabel = ShortMonthsUpper[cal.get(Calendar.MONTH)]
    val dayLabel = cal.get(Calendar.DAY_OF_MONTH).toString()
    val fullAddr = competition.address ?: competition.location

    AppCard(
        containerColor = if (isSkipped) cs.errorContainer.copy(alpha = 0.2f) else cs.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Плашка с датой
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(badgeContainer)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeContent.copy(alpha = 0.85f)
                )
                Text(
                    text = dayLabel,
                    style = NumberLarge,
                    color = badgeContent
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = competition.name,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    OverflowMenu(
                        modifier = Modifier.offset(y = (-8).dp),
                        actions = buildList {
                            add(MenuAction("Редактировать", Icons.Default.Edit, onClick = onEditClick))
                            if (!isSkipped) add(MenuAction("Пропустить старт", Icons.Default.Block, onClick = onSkipClick))
                            add(MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDeleteClick))
                        }
                    )
                }

                // Статусы
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isSkipped -> StatusPill("Пропущено", cs.onErrorContainer, cs.errorContainer, icon = Icons.Default.Block)
                        isToday -> StatusPill("Сегодня", cs.onPrimary, cs.primary)
                        isFuture -> StatusPill(
                            "Через $days ${pluralRu(days, "день", "дня", "дней")}",
                            cs.onTertiaryContainer,
                            cs.tertiaryContainer
                        )
                        else -> StatusPill("Завершён", cs.onSurfaceVariant, cs.surfaceContainerHigh)
                    }
                    if (!competition.placement.isNullOrBlank()) {
                        StatusPill(
                            text = competition.placement,
                            contentColor = extra.onGoldContainer,
                            containerColor = extra.goldContainer,
                            icon = Icons.Default.EmojiEvents
                        )
                    }
                    if (competition.hasQualification) {
                        StatusPill(
                            text = "Квалификация",
                            contentColor = cs.onSecondaryContainer,
                            containerColor = cs.secondaryContainer
                        )
                    }
                }

                if (isSkipped && !competition.skipReason.isNullOrBlank()) {
                    Text(
                        text = "Причина: ${competition.skipReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!fullAddr.isNullOrBlank()) {
                        CompetitionInfoRow(icon = Icons.Default.Place, text = fullAddr)
                    }
                    if (!competition.eventTime.isNullOrBlank()) {
                        CompetitionInfoRow(icon = Icons.Default.Schedule, text = "Время вида: ${competition.eventTime}")
                    }
                    if (competition.hasQualification && !competition.qualificationStandard.isNullOrBlank()) {
                        CompetitionInfoRow(icon = Icons.Default.CheckCircleOutline, text = "Норматив: ${competition.qualificationStandard}")
                    }
                }

                if (!competition.attemptsJson.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = cs.surfaceContainerLow,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Попытки: ${competition.attemptsJson}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = cs.onSurface
                            )
                        }
                    }
                }

                val runUpLines = runUpSummary(RunUp.decode(competition.runUpJson))
                if (runUpLines.isNotEmpty()) {
                    Text(
                        text = "Разбег:\n" + runUpLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    }
}

/** Заполненные попытки с их настоящими номерами. */
private fun runUpSummary(attempts: List<RunUpAttempt>): List<String> =
    attempts.mapIndexedNotNull { i, a -> if (a.isEmpty) null else "${i + 1}. ${RunUp.describe(a)}" }

@Composable
private fun CompetitionInfoRow(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Dialog Form: Add or Edit Competition in Styled Card
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditCompetitionDialog(
    competitionToEdit: Competition?,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        country: String,
        city: String,
        streetAndNumber: String,
        eventTime: String,
        hasQualification: Boolean,
        qualificationStandard: String,
        didNotQualifyForFinal: Boolean,
        placement: String,
        attemptsJson: String,
        date: Long,
        runUpJson: String?
    ) -> Unit
) {
    var nameText by remember { mutableStateOf(competitionToEdit?.name ?: "") }

    // Country Fixed to Россия
    val selectedCountry = "Россия"

    // Expanded Russian Cities List + Custom City Entry
    val citiesList = listOf(
        "Москва", "Санкт-Петербург", "Казань", "Сочи", "Екатеринбург",
        "Краснодар", "Новосибирск", "Нижний Новгород", "Челябинск",
        "Самара", "Уфа", "Ростов-на-Дону", "Омск", "Воронеж", "Пермь", "Волгоград",
        "Другой город..."
    )
    var selectedCity by remember { mutableStateOf(competitionToEdit?.city ?: "Москва") }
    var isCustomCity by remember { mutableStateOf(competitionToEdit?.city != null && !citiesList.contains(competitionToEdit.city)) }
    var customCityText by remember { mutableStateOf(if (isCustomCity) competitionToEdit?.city ?: "" else "") }
    var expandedCityDropdown by remember { mutableStateOf(false) }

    // Street & Number Combined Field
    var streetAndNumberText by remember { mutableStateOf(competitionToEdit?.streetAndNumber ?: "") }

    // Дата выбирается в календаре, а не тремя выпадающими списками
    var dateMillis by remember { mutableLongStateOf(competitionToEdit?.date ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var timeText by remember { mutableStateOf(competitionToEdit?.eventTime ?: "") }

    var hasQual by remember { mutableStateOf(competitionToEdit?.hasQualification ?: false) }
    var qualStandardText by remember { mutableStateOf(competitionToEdit?.qualificationStandard ?: "") }

    var didNotMakeTop8 by remember { mutableStateOf(competitionToEdit?.didNotQualifyForFinal ?: false) }
    var placementText by remember { mutableStateOf(competitionToEdit?.placement ?: "") }

    // Шесть попыток финала. При редактировании подставляются сохранённые — раньше они терялись при повторном сохранении
    val savedAttempts = remember(competitionToEdit) {
        competitionToEdit?.attemptsJson?.split(",")?.map { it.trim() } ?: emptyList()
    }
    val finalAttemptsDistList = remember(competitionToEdit) {
        mutableStateListOf<String>().apply {
            repeat(6) { i -> add(savedAttempts.getOrNull(i)?.takeIf { it.toDoubleOrNull() != null } ?: "") }
        }
    }
    val finalAttemptsFoulList = remember(competitionToEdit) {
        mutableStateListOf<Boolean>().apply {
            repeat(6) { i -> add(savedAttempts.getOrNull(i)?.let { it.equals("X", true) || it.equals("Х", true) } == true) }
        }
    }

    var runUpAttempts by remember { mutableStateOf(RunUp.decode(competitionToEdit?.runUpJson)) }
    var showRunUpDialog by remember { mutableStateOf(false) }

    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (competitionToEdit != null) "Соревнование" else "Новое соревнование",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        nameText = it
                        if (it.isNotBlank()) showNameError = false
                    },
                    label = { Text("Название") },
                    placeholder = { Text("Например, первенство города") },
                    isError = showNameError && nameText.isBlank(),
                    supportingText = if (showNameError && nameText.isBlank()) {
                        { Text("Введите название") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                FormSectionTitle("Где и когда")

                ExposedDropdownMenuBox(
                    expanded = expandedCityDropdown,
                    onExpandedChange = { expandedCityDropdown = !expandedCityDropdown }
                ) {
                    OutlinedTextField(
                        value = if (isCustomCity) "Другой город" else selectedCity,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Город") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCityDropdown) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCityDropdown,
                        onDismissRequest = { expandedCityDropdown = false }
                    ) {
                        citiesList.forEach { cityItem ->
                            DropdownMenuItem(
                                text = { Text(cityItem) },
                                onClick = {
                                    if (cityItem == "Другой город...") {
                                        isCustomCity = true
                                    } else {
                                        isCustomCity = false
                                        selectedCity = cityItem
                                    }
                                    expandedCityDropdown = false
                                }
                            )
                        }
                    }
                }

                if (isCustomCity) {
                    OutlinedTextField(
                        value = customCityText,
                        onValueChange = { customCityText = it },
                        label = { Text("Название города") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = streetAndNumberText,
                    onValueChange = { streetAndNumberText = it },
                    label = { Text("Место проведения") },
                    placeholder = { Text("Стадион или адрес") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DateField(
                        label = "Дата",
                        millis = dateMillis,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { v -> timeText = v.filter { it.isDigit() || it == ':' }.take(5) },
                        label = { Text("Время") },
                        placeholder = { Text("14:30") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(110.dp)
                    )
                }

                FormSectionTitle("Результат")

                OutlinedTextField(
                    value = placementText,
                    onValueChange = { placementText = it },
                    label = { Text("Место") },
                    placeholder = { Text("Например, 3") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                FormSwitchRow(
                    title = "Была квалификация",
                    subtitle = "Норматив выхода в финал",
                    checked = hasQual,
                    onChange = { hasQual = it }
                )
                if (hasQual) {
                    OutlinedTextField(
                        value = qualStandardText,
                        onValueChange = { qualStandardText = it },
                        label = { Text("Норматив") },
                        placeholder = { Text("15.20") },
                        suffix = { Text("м") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Попытки 1–3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (i in 0 until 3) {
                    AttemptRow(
                        number = i + 1,
                        distance = finalAttemptsDistList[i],
                        foul = finalAttemptsFoulList[i],
                        onDistance = { finalAttemptsDistList[i] = it },
                        onFoul = { finalAttemptsFoulList[i] = it }
                    )
                }

                FormSwitchRow(
                    title = "Не вошёл в ТОП-8",
                    subtitle = "Попытки 4–6 недоступны",
                    checked = didNotMakeTop8,
                    onChange = { didNotMakeTop8 = it }
                )
                if (!didNotMakeTop8) {
                    Text("Попытки 4–6", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (i in 3 until 6) {
                        AttemptRow(
                            number = i + 1,
                            distance = finalAttemptsDistList[i],
                            foul = finalAttemptsFoulList[i],
                            onDistance = { finalAttemptsDistList[i] = it },
                            onFoul = { finalAttemptsFoulList[i] = it }
                        )
                    }
                }

                FormSectionTitle("Разбег")
                runUpSummary(runUpAttempts).forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { showRunUpDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (runUpAttempts.all { it.isEmpty }) "Указать разбег по попыткам" else "Изменить разбег по попыткам")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        val finalCityName = if (isCustomCity) customCityText.ifBlank { "Москва" } else selectedCity

                        val finalAttemptsStr = (0 until 6).joinToString(", ") { i ->
                            if (i >= 3 && didNotMakeTop8) "-"
                            else if (finalAttemptsFoulList[i]) "X"
                            else finalAttemptsDistList[i].ifBlank { "-" }
                        }

                        onConfirm(
                            nameText.trim(),
                            selectedCountry,
                            finalCityName,
                            streetAndNumberText,
                            timeText,
                            hasQual,
                            qualStandardText,
                            didNotMakeTop8,
                            placementText,
                            finalAttemptsStr,
                            dateMillis,
                            runUpAttempts.takeIf { list -> list.any { !it.isEmpty } }?.let { RunUp.encodeAll(it) }
                        )
                    } else {
                        showNameError = true
                    }
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showRunUpDialog) {
        RunUpResultsDialog(
            exerciseName = nameText.ifBlank { "Попытки старта" },
            initial = runUpAttempts,
            fixedCount = if (didNotMakeTop8) 3 else 6,
            onDismiss = { showRunUpDialog = false },
            onConfirm = {
                runUpAttempts = it
                showRunUpDialog = false
            }
        )
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialMillis = dateMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateMillis = it
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun FormSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun FormSwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Попытка: номер в кружке, дальность в метрах и отметка «Заступ» (поле дальности при этом отключается). */
@Composable
private fun AttemptRow(
    number: Int,
    distance: String,
    foul: Boolean,
    onDistance: (String) -> Unit,
    onFoul: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (foul) cs.errorContainer else cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = if (foul) cs.onErrorContainer else cs.onSurfaceVariant)
        }
        OutlinedTextField(
            value = if (foul) "" else distance,
            onValueChange = { v -> onDistance(v.replace(',', '.').filter { it.isDigit() || it == '.' }) },
            enabled = !foul,
            placeholder = { Text(if (foul) "заступ" else "—") },
            suffix = { Text("м") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = foul,
            onClick = { onFoul(!foul) },
            label = { Text("Заступ") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = cs.errorContainer,
                selectedLabelColor = cs.onErrorContainer
            )
        )
    }
}

// Dialog: Skip Competition Reason Input
@Composable
fun SkipCompetitionReasonDialog(
    competition: Competition,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reasonText by remember { mutableStateOf(competition.skipReason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Пропуск соревнования", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Укажите причину пропуска соревнования '${competition.name}':", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Причина пропуска") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(reasonText.ifBlank { "Пропуск по решению атлета" })
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
