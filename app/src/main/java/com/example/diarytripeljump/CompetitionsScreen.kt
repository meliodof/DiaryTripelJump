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

@Composable
fun CompetitionsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    val competitionsList by viewModel.allCompetitions.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCompetition by remember { mutableStateOf<Competition?>(null) }
    var itemToDelete by remember { mutableStateOf<Competition?>(null) }
    var skippingCompetition by remember { mutableStateOf<Competition?>(null) }

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
                text = "КАЛЕНДАРЬ СТАРТОВ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Предстоящие и прошедшие соревнования",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 2. Button 'Добавить соревнование' Directly Under Header
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Добавить соревнование", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        // 3. Categorized Competitions Lists
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // SECTION 1: Текущие соревнования
            if (ongoingList.isNotEmpty()) {
                item {
                    SectionHeaderTitle(title = "🏆 ТЕКУЩИЕ СОРЕВНОВАНИЯ (СЕГОДНЯ)")
                }
                items(ongoingList) { competition ->
                    CompetitionCardItem(
                        competition = competition,
                        onEditClick = { editingCompetition = competition },
                        onDeleteClick = { itemToDelete = competition },
                        onSkipClick = { skippingCompetition = competition }
                    )
                }
            }

            // SECTION 2: Предстоящие соревнования
            item {
                SectionHeaderTitle(title = "📅 ПРЕДСТОЯЩИЕ СОРЕВНОВАНИЯ (${upcomingList.size})")
            }
            if (upcomingList.isNotEmpty()) {
                items(upcomingList) { competition ->
                    CompetitionCardItem(
                        competition = competition,
                        onEditClick = { editingCompetition = competition },
                        onDeleteClick = { itemToDelete = competition },
                        onSkipClick = { skippingCompetition = competition }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "Нет запланированных предстоящих соревнований",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // SECTION 3: Прошедшие и Пропущенные соревнования
            if (completedOrSkippedList.isNotEmpty()) {
                item {
                    SectionHeaderTitle(title = "🏁 ПРОШЕДШИЕ И ПРОПУЩЕННЫЕ СОРЕВНОВАНИЯ (${completedOrSkippedList.size})")
                }
                items(completedOrSkippedList) { competition ->
                    CompetitionCardItem(
                        competition = competition,
                        onEditClick = { editingCompetition = competition },
                        onDeleteClick = { itemToDelete = competition },
                        onSkipClick = { skippingCompetition = competition }
                    )
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

@Composable
fun SectionHeaderTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun CompetitionCardItem(
    competition: Competition,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSkipClick: () -> Unit
) {
    val isSkipped = competition.status == CompetitionStatus.SKIPPED
    val isOngoing = competition.status == CompetitionStatus.ONGOING

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSkipped) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Status Badge & Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSkipped) Icons.Default.Block else Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = if (isSkipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isSkipped) "ПРОПУЩЕНО" else if (isOngoing) "СЕГОДНЯ" else "СОРЕВНОВАНИЕ",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSkipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!isSkipped) {
                        IconButton(onClick = onSkipClick, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Пропуск",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = competition.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (!competition.placement.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = competition.placement,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (isSkipped && !competition.skipReason.isNullOrBlank()) {
                Text(
                    text = "Причина пропуска: ${competition.skipReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Address, Date & Event Time Details
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val fullAddr = competition.address ?: competition.location
                if (!fullAddr.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Адрес: $fullAddr",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Дата: ${formatDate(competition.date)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!competition.eventTime.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Время вида: ${competition.eventTime}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (competition.hasQualification) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Квалификация: Есть" + if (!competition.qualificationStandard.isNullOrBlank()) " (Норматив: ${competition.qualificationStandard})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!competition.attemptsJson.isNullOrBlank()) {
                    Text(
                        text = "Попытки: ${competition.attemptsJson}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
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
