package com.example.diarytripeljump

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

    // Categorize competitions
    val nowMillis = System.currentTimeMillis()
    val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }

    val ongoingList = competitionsList.filter { comp ->
        val calComp = Calendar.getInstance().apply { timeInMillis = comp.date }
        comp.status == CompetitionStatus.ONGOING ||
                (calComp.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) && calComp.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR) && comp.status != CompetitionStatus.SKIPPED)
    }

    val upcomingList = competitionsList.filter { comp ->
        !ongoingList.contains(comp) && comp.date > nowMillis && comp.status != CompetitionStatus.SKIPPED && comp.status != CompetitionStatus.COMPLETED
    }

    val completedOrSkippedList = competitionsList.filter { comp ->
        !ongoingList.contains(comp) && !upcomingList.contains(comp)
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
                        "Предстоящие (${ongoingList.size + upcomingList.size})",
                        "Архив (${completedOrSkippedList.size})"
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            if (selectedTab == 0) {
                if (ongoingList.isNotEmpty()) {
                    item { SectionHeader(title = "Сегодня") }
                    items(ongoingList, key = { it.id }) { competition ->
                        CompetitionCardItem(
                            competition = competition,
                            onEditClick = { editingCompetition = competition },
                            onDeleteClick = { itemToDelete = competition },
                            onSkipClick = { skippingCompetition = competition }
                        )
                    }
                }

                if (upcomingList.isNotEmpty()) {
                    item { SectionHeader(title = "Впереди") }
                    items(upcomingList, key = { it.id }) { competition ->
                        CompetitionCardItem(
                            competition = competition,
                            onEditClick = { editingCompetition = competition },
                            onDeleteClick = { itemToDelete = competition },
                            onSkipClick = { skippingCompetition = competition }
                        )
                    }
                }

                if (ongoingList.isEmpty() && upcomingList.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.EmojiEvents,
                            title = "Нет запланированных стартов",
                            message = "Добавьте соревнование, и оно появится здесь с обратным отсчётом.",
                            actionLabel = "Добавить старт",
                            onAction = { showAddDialog = true }
                        )
                    }
                }
            } else {
                if (completedOrSkippedList.isNotEmpty()) {
                    items(completedOrSkippedList, key = { it.id }) { competition ->
                        CompetitionCardItem(
                            competition = competition,
                            onEditClick = { editingCompetition = competition },
                            onDeleteClick = { itemToDelete = competition },
                            onSkipClick = { skippingCompetition = competition }
                        )
                    }
                } else {
                    item {
                        EmptyState(
                            icon = Icons.Default.Flag,
                            title = "Архив пуст",
                            message = "Прошедшие и пропущенные соревнования появятся здесь."
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
            onConfirm = { name, country, city, streetAndNumber, eventTime, hasQual, qualStandard, didNotQual, placement, attemptsJson, date ->
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
                    eventType = "Тройной прыжок"
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
            onConfirm = { name, country, city, streetAndNumber, eventTime, hasQual, qualStandard, didNotQual, placement, attemptsJson, date ->
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
                    date = date
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
    val isOngoing = competition.status == CompetitionStatus.ONGOING
    val days = compDaysUntil(competition.date)
    val isToday = isOngoing || (days == 0 && !isSkipped)
    val isFuture = days > 0 && !isSkipped && competition.status != CompetitionStatus.COMPLETED

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
            }
        }
    }
}

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
        date: Long
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

    // Date Selectors (3 Blocks: Year, Month, Day)
    val calInitial = Calendar.getInstance().apply { timeInMillis = competitionToEdit?.date ?: System.currentTimeMillis() }
    var selYear by remember { mutableIntStateOf(calInitial.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(calInitial.get(Calendar.MONTH)) }
    var selDay by remember { mutableIntStateOf(calInitial.get(Calendar.DAY_OF_MONTH)) }

    val monthsList = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    var expandedYearDrop by remember { mutableStateOf(false) }
    var expandedMonthDrop by remember { mutableStateOf(false) }
    var expandedDayDrop by remember { mutableStateOf(false) }

    var timeText by remember { mutableStateOf(competitionToEdit?.eventTime ?: "14:30") }

    // Qualification vs Final Split
    var hasQual by remember { mutableStateOf(competitionToEdit?.hasQualification ?: true) }
    var qualStandardText by remember { mutableStateOf(competitionToEdit?.qualificationStandard ?: "15.20 м") }

    var didNotMakeTop8 by remember { mutableStateOf(competitionToEdit?.didNotQualifyForFinal ?: false) }
    var placementText by remember { mutableStateOf(competitionToEdit?.placement ?: "") }

    // Qualification Attempts (3 attempts)
    val qualAttemptsDistList = remember(competitionToEdit) {
        mutableStateListOf<String>().apply { repeat(3) { add("") } }
    }
    val qualAttemptsFoulList = remember(competitionToEdit) {
        mutableStateListOf<Boolean>().apply { repeat(3) { add(false) } }
    }

    // Final Attempts (6 attempts)
    val finalAttemptsDistList = remember(competitionToEdit) {
        mutableStateListOf<String>().apply { repeat(6) { add("") } }
    }
    val finalAttemptsFoulList = remember(competitionToEdit) {
        mutableStateListOf<Boolean>().apply { repeat(6) { add(false) } }
    }

    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (competitionToEdit != null) "Редактировать соревнование" else "Новое соревнование",
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Field 1: Название соревнования
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            if (it.isNotBlank()) showNameError = false
                        },
                        label = { Text("Название соревнования") },
                        isError = showNameError && nameText.isBlank(),
                        supportingText = if (showNameError && nameText.isBlank()) {
                            { Text("Это поле обязательно для заполнения", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Country Field (Fixed Russia)
                    OutlinedTextField(
                        value = "Россия 🇷🇺",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Страна") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // City Dropdown + Custom City Input
                    ExposedDropdownMenuBox(
                        expanded = expandedCityDropdown,
                        onExpandedChange = { expandedCityDropdown = !expandedCityDropdown }
                    ) {
                        OutlinedTextField(
                            value = if (isCustomCity) "Другой город..." else selectedCity,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Город") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCityDropdown) },
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
                            label = { Text("Введите название города") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Field: Улица и номер дома
                    OutlinedTextField(
                        value = streetAndNumberText,
                        onValueChange = { streetAndNumberText = it },
                        label = { Text("Улица и номер дома (например: ул. Лужники, д. 24)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date Selectors: 3 Blocks (Year, Month, Day)
                    Text(
                        text = "Дата проведения соревнования:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Block 1: Year Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedYearDrop,
                            onExpandedChange = { expandedYearDrop = !expandedYearDrop },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = selYear.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Год", fontSize = 9.sp) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedYearDrop,
                                onDismissRequest = { expandedYearDrop = false }
                            ) {
                                (2024..2028).forEach { yr ->
                                    DropdownMenuItem(
                                        text = { Text(yr.toString()) },
                                        onClick = {
                                            selYear = yr
                                            expandedYearDrop = false
                                        }
                                    )
                                }
                            }
                        }

                        // Block 2: Month Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedMonthDrop,
                            onExpandedChange = { expandedMonthDrop = !expandedMonthDrop },
                            modifier = Modifier.weight(1.3f)
                        ) {
                            OutlinedTextField(
                                value = monthsList.getOrElse(selMonth) { "" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Месяц", fontSize = 9.sp) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMonthDrop,
                                onDismissRequest = { expandedMonthDrop = false }
                            ) {
                                monthsList.forEachIndexed { index, mName ->
                                    DropdownMenuItem(
                                        text = { Text(mName) },
                                        onClick = {
                                            selMonth = index
                                            expandedMonthDrop = false
                                        }
                                    )
                                }
                            }
                        }

                        // Block 3: Day Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedDayDrop,
                            onExpandedChange = { expandedDayDrop = !expandedDayDrop },
                            modifier = Modifier.weight(0.9f)
                        ) {
                            OutlinedTextField(
                                value = selDay.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("День", fontSize = 9.sp) },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDayDrop,
                                onDismissRequest = { expandedDayDrop = false }
                            ) {
                                (1..31).forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d.toString()) },
                                        onClick = {
                                            selDay = d
                                            expandedDayDrop = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Field: Время вида
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("Время вида (например: 14:30)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider()

                    // SECTION: Квалификационные попытки
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Есть квалификация:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Switch(checked = hasQual, onCheckedChange = { hasQual = it })
                    }

                    if (hasQual) {
                        OutlinedTextField(
                            value = qualStandardText,
                            onValueChange = { qualStandardText = it },
                            label = { Text("Норматив для попадания в финал (например: 15.20 м)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(text = "📐 Квалификационные попытки (1-3):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        for (i in 0 until 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = qualAttemptsDistList.getOrElse(i) { "" },
                                    onValueChange = { newValue ->
                                        val cleanVal = newValue.filter { it.isDigit() || it == '.' }
                                        if (i < qualAttemptsDistList.size) qualAttemptsDistList[i] = cleanVal
                                    },
                                    label = { Text("Квал. Попытка #${i + 1} (м)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = qualAttemptsFoulList.getOrElse(i) { false },
                                        onCheckedChange = { isChecked ->
                                            if (i < qualAttemptsFoulList.size) qualAttemptsFoulList[i] = isChecked
                                        }
                                    )
                                    Text("Заступ (X)", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // SECTION: Финальные основные соревнования (6 попыток)
                    Text(text = "🏆 Финальные соревнования (6 попыток):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = placementText,
                        onValueChange = { placementText = it },
                        label = { Text("Место на соревновании (например: 1-е место)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Attempts 1-3
                    Text(text = "Основные финальные попытки (1-3):", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    for (i in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = finalAttemptsDistList.getOrElse(i) { "" },
                                onValueChange = { newValue ->
                                    val cleanVal = newValue.filter { it.isDigit() || it == '.' }
                                    if (i < finalAttemptsDistList.size) finalAttemptsDistList[i] = cleanVal
                                },
                                label = { Text("Финал Попытка #${i + 1} (м)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = finalAttemptsFoulList.getOrElse(i) { false },
                                    onCheckedChange = { isChecked ->
                                        if (i < finalAttemptsFoulList.size) finalAttemptsFoulList[i] = isChecked
                                    }
                                )
                                Text("Заступ (X)", fontSize = 10.sp)
                            }
                        }
                    }

                    // Rule Switch: Не вошёл в ТОП-8
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Не вошёл в ТОП-8 (без попыток 4-6):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Switch(checked = didNotMakeTop8, onCheckedChange = { didNotMakeTop8 = it })
                    }

                    if (didNotMakeTop8) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Не попал в ТОП-8 (финальные попытки 4-6 недоступны)",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(text = "Дополнительные финальные попытки для ТОП-8 (4-6):", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        for (i in 3 until 6) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = finalAttemptsDistList.getOrElse(i) { "" },
                                    onValueChange = { newValue ->
                                        val cleanVal = newValue.filter { it.isDigit() || it == '.' }
                                        if (i < finalAttemptsDistList.size) finalAttemptsDistList[i] = cleanVal
                                    },
                                    label = { Text("Финал Попытка #${i + 1} (м)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = finalAttemptsFoulList.getOrElse(i) { false },
                                        onCheckedChange = { isChecked ->
                                            if (i < finalAttemptsFoulList.size) finalAttemptsFoulList[i] = isChecked
                                        }
                                    )
                                    Text("Заступ (X)", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        val finalCityName = if (isCustomCity) customCityText.ifBlank { "Москва" } else selectedCity

                        // Construct date from 3 blocks
                        val newCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selYear)
                            set(Calendar.MONTH, selMonth)
                            set(Calendar.DAY_OF_MONTH, selDay)
                        }

                        // Construct attempts string
                        val finalAttemptsStr = (0 until 6).joinToString(", ") { i ->
                            if (i >= 3 && didNotMakeTop8) "-"
                            else if (finalAttemptsFoulList.getOrElse(i) { false }) "X"
                            else finalAttemptsDistList.getOrElse(i) { "" }.ifBlank { "-" }
                        }

                        onConfirm(
                            nameText,
                            selectedCountry,
                            finalCityName,
                            streetAndNumberText,
                            timeText,
                            hasQual,
                            qualStandardText,
                            didNotMakeTop8,
                            placementText,
                            finalAttemptsStr,
                            newCal.timeInMillis
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
