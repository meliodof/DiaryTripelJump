package com.example.diarytripeljump

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.diarytripeljump.data.AthleteProfile
import java.util.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextOverflow
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.ui.components.*
import com.example.diarytripeljump.ui.theme.extraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val rawResultsList by viewModel.rawTripleJumpResults.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDemoDialog by remember { mutableStateOf(false) }
    var showCreateInventory by remember { mutableStateOf(false) }
    val inventoryItems by viewModel.inventoryItems.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.updateAthleteProfileAvatar(uri.toString())
            }
        }
    )

    val calculatedPbMeters = remember(rawResultsList, athleteProfile) {
        val customPb = athleteProfile?.customPbMeters
        if (customPb != null && customPb > 0) {
            String.format(Locale.getDefault(), "%.2f м", customPb)
        } else {
            val best = rawResultsList.filter { !it.isAntiRecord }.maxOfOrNull { it.result }
            if (best != null && best > 0) String.format(Locale.getDefault(), "%.2f м", best) else "— м"
        }
    }

    val birthDateMillis = athleteProfile?.birthDate
    val isBirthday = isTodayBirthday(birthDateMillis)
    val ageText = calculateAgeText(birthDateMillis)
    val birthDateFormattedText = formatBirthDateFull(birthDateMillis)

    val fullName = remember(athleteProfile) {
        val f = athleteProfile?.firstName ?: ""
        val l = athleteProfile?.lastName ?: ""
        val full = "$f $l".trim()
        if (full.isNotBlank()) full else "Алексей Иванов"
    }

    val nicknameText = remember(athleteProfile) {
        val n = athleteProfile?.nickname ?: ""
        if (n.isNotBlank()) if (n.startsWith("@")) n else "@$n" else "@jumper_alex"
    }

    val cs = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(text = "Профиль", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Редактировать профиль")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 4.dp,
                    bottom = inner.calculateBottomPadding() + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isBirthday) {
                val extra = MaterialTheme.extraColors
                Surface(shape = MaterialTheme.shapes.large, color = extra.goldContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎉", style = MaterialTheme.typography.displaySmall)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("С днём рождения!", style = MaterialTheme.typography.titleMedium, color = extra.onGoldContainer)
                            Text(
                                "Желаем новых личных рекордов, высоких результатов и побед на всех стартах!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = extra.onGoldContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Шапка: большой аватар, имя, ник, специализация
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .background(cs.primaryContainer)
                            .clickable {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!athleteProfile?.avatarUriString.isNullOrBlank()) {
                            AsyncImage(
                                model = athleteProfile?.avatarUriString,
                                contentDescription = "Аватар",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Добавить фото",
                                tint = cs.onPrimaryContainer,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = fullName, style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
                Text(text = nicknameText, style = MaterialTheme.typography.titleSmall, color = cs.primary)
                StatusPill(
                    text = athleteProfile?.specialization ?: "Тройной прыжок",
                    contentColor = cs.onPrimaryContainer,
                    containerColor = cs.primaryContainer
                )
            }

            // Показатели
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (athleteProfile?.height != null && athleteProfile!!.height!! > 0) "${athleteProfile!!.height!!.toInt()}" else "—",
                    label = "Рост, см",
                    icon = Icons.Default.Height,
                    tint = cs.tertiary,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = if (athleteProfile?.weight != null && athleteProfile!!.weight!! > 0) "${athleteProfile!!.weight!!.toInt()}" else "—",
                    label = "Вес, кг",
                    icon = Icons.Default.MonitorWeight,
                    tint = cs.secondary,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = calculatedPbMeters.removeSuffix(" м"),
                    label = "Рекорд, м",
                    icon = Icons.Default.EmojiEvents,
                    tint = MaterialTheme.extraColors.gold,
                    modifier = Modifier.weight(1f)
                )
            }

            AppCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(icon = Icons.Default.Cake, tint = cs.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = ageText, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                        Text(text = birthDateFormattedText, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Инвентарь",
                    subtitle = "Выбирается в упражнениях тренировки",
                    actionLabel = "Добавить",
                    onAction = { showCreateInventory = true }
                )
                if (inventoryItems.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.FitnessCenter,
                        title = "Инвентаря пока нет",
                        message = "Добавьте барьеры, гири, штанги — и указывайте их размер или вес в тренировках.",
                        actionLabel = "Добавить инвентарь",
                        onAction = { showCreateInventory = true }
                    )
                } else {
                    AppCard {
                        inventoryItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconBadge(icon = Icons.Default.FitnessCenter, tint = cs.primary)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(text = item.name, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                                    Text(
                                        text = if (item.unit == ResultUnit.KG) "Измеряется в килограммах" else "Измеряется в сантиметрах",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteInventory(item) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Удалить ${item.name}",
                                        tint = cs.onSurfaceVariant
                                    )
                                }
                            }
                            if (index < inventoryItems.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Настройки")
                AppCard {
                    ProfileOptionRow(
                        icon = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "Тёмная тема",
                        subtitle = if (uiState.isDarkTheme) "Включена" else "Выключена",
                        onClick = { viewModel.toggleTheme() },
                        trailing = {
                            Switch(checked = uiState.isDarkTheme, onCheckedChange = { viewModel.toggleTheme() })
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                    ProfileOptionRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Обращение в приложении",
                        subtitle = if (athleteProfile?.greetingPreference == "NICKNAME") "По никнейму ($nicknameText)" else "По имени ($fullName)",
                        onClick = { showEditDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                    ProfileOptionRow(
                        icon = Icons.Default.Storage,
                        title = "Демо-данные",
                        subtitle = "Профиль, тренировки и рекорды за 3 месяца",
                        onClick = { showDemoDialog = true }
                    )
                }
            }
        }
    }

    if (showDemoDialog) {
        AlertDialog(
            onDismissRequest = { showDemoDialog = false },
            title = { Text("Загрузить демо-данные?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Будут добавлены тренировки, старты, рекорды, комплексы и подготовительные периоды за последние три месяца. " +
                        "Ваши записи не изменятся и не дублируются; заполненный профиль сохранится."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.loadDemoData()
                    showDemoDialog = false
                }) { Text("Загрузить") }
            },
            dismissButton = { TextButton(onClick = { showDemoDialog = false }) { Text("Отмена") } }
        )
    }

    if (showCreateInventory) {
        CreateInventoryDialog(
            onDismiss = { showCreateInventory = false },
            onConfirm = { name, unit ->
                viewModel.addInventory(name, unit)
                showCreateInventory = false
            }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentProfile = athleteProfile,
            onDismiss = { showEditDialog = false },
            onConfirm = { firstName, lastName, nickname, greetingPref, birthDateMillis, height, weight, customPb ->
                viewModel.updateAthleteProfile(
                    firstName = firstName,
                    lastName = lastName,
                    nickname = nickname,
                    avatarUriString = athleteProfile?.avatarUriString,
                    greetingPreference = greetingPref,
                    birthDate = birthDateMillis,
                    height = height,
                    weight = weight,
                    customPbMeters = customPb
                )
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileDialog(
    currentProfile: AthleteProfile?,
    onDismiss: () -> Unit,
    onConfirm: (
        firstName: String,
        lastName: String,
        nickname: String,
        greetingPref: String,
        birthDateMillis: Long?,
        height: Double?,
        weight: Double?,
        customPbMeters: Double?
    ) -> Unit
) {
    var firstNameText by remember { mutableStateOf(currentProfile?.firstName ?: "Алексей") }
    var lastNameText by remember { mutableStateOf(currentProfile?.lastName ?: "Иванов") }
    var nicknameText by remember { mutableStateOf(currentProfile?.nickname ?: "@jumper_alex") }
    var greetingPref by remember { mutableStateOf(currentProfile?.greetingPreference ?: "NAME") }

    var heightText by remember { mutableStateOf(currentProfile?.height?.toInt()?.toString() ?: "185") }
    var weightText by remember { mutableStateOf(currentProfile?.weight?.toInt()?.toString() ?: "75") }
    var pbText by remember { mutableStateOf(currentProfile?.customPbMeters?.toString() ?: "15.52") }

    val calInitial = Calendar.getInstance().apply {
        timeInMillis = currentProfile?.birthDate ?: Calendar.getInstance().apply { set(2000, Calendar.JANUARY, 15) }.timeInMillis
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Редактировать Профиль", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
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
                    OutlinedTextField(
                        value = firstNameText,
                        onValueChange = { firstNameText = it },
                        label = { Text("Имя") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = lastNameText,
                        onValueChange = { lastNameText = it },
                        label = { Text("Фамилия") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = nicknameText,
                        onValueChange = { nicknameText = it },
                        label = { Text("Никнейм (например: @jumper_alex)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(text = "Как к вам обращаться:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = greetingPref == "NAME",
                            onClick = { greetingPref = "NAME" },
                            label = { Text("По имени", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = greetingPref == "NICKNAME",
                            onClick = { greetingPref = "NICKNAME" },
                            label = { Text("По никнейму", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it.filter { char -> char.isDigit() } },
                            label = { Text("Рост (см)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it.filter { char -> char.isDigit() } },
                            label = { Text("Вес (кг)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = pbText,
                        onValueChange = { pbText = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Личный рекорд PB (в метрах)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    HorizontalDivider()

                    Text(text = "Дата рождения (для возраста и поздравлений):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = expandedYearDrop,
                            onExpandedChange = { expandedYearDrop = !expandedYearDrop },
                            modifier = Modifier.weight(1.1f)
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
                                (1960..2015).reversed().forEach { yr ->
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
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, selYear)
                        set(Calendar.MONTH, selMonth)
                        set(Calendar.DAY_OF_MONTH, selDay)
                    }

                    onConfirm(
                        firstNameText,
                        lastNameText,
                        nicknameText,
                        greetingPref,
                        newCal.timeInMillis,
                        heightText.toDoubleOrNull(),
                        weightText.toDoubleOrNull(),
                        pbText.toDoubleOrNull()
                    )
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

@Composable
fun ProfileParamColumn(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) trailing()
        }
    }
}

fun isTodayBirthday(birthDateMillis: Long?): Boolean {
    if (birthDateMillis == null) return false
    val calBirth = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
    val calToday = Calendar.getInstance()
    return calBirth.get(Calendar.MONTH) == calToday.get(Calendar.MONTH) &&
            calBirth.get(Calendar.DAY_OF_MONTH) == calToday.get(Calendar.DAY_OF_MONTH)
}

fun calculateAgeText(birthDateMillis: Long?): String {
    if (birthDateMillis == null) return "— лет"
    val calBirth = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
    val calToday = Calendar.getInstance()

    var age = calToday.get(Calendar.YEAR) - calBirth.get(Calendar.YEAR)
    if (calToday.get(Calendar.DAY_OF_YEAR) < calBirth.get(Calendar.DAY_OF_YEAR)) {
        age--
    }

    if (age < 0) return "— лет"

    val lastDigit = age % 10
    val lastTwoDigits = age % 100

    val suffix = when {
        lastTwoDigits in 11..14 -> "лет"
        lastDigit == 1 -> "год"
        lastDigit in 2..4 -> "года"
        else -> "лет"
    }

    return "$age $suffix"
}

fun formatBirthDateFull(birthDateMillis: Long?): String {
    if (birthDateMillis == null) return "Дата рождения не указана"
    val calendar = Calendar.getInstance().apply { timeInMillis = birthDateMillis }
    val day = calendar[Calendar.DAY_OF_MONTH]
    val month = when (calendar[Calendar.MONTH]) {
        Calendar.JANUARY -> "января"
        Calendar.FEBRUARY -> "февраля"
        Calendar.MARCH -> "марта"
        Calendar.APRIL -> "апреля"
        Calendar.MAY -> "мая"
        Calendar.JUNE -> "июня"
        Calendar.JULY -> "июля"
        Calendar.AUGUST -> "августа"
        Calendar.SEPTEMBER -> "сентября"
        Calendar.OCTOBER -> "октября"
        Calendar.NOVEMBER -> "ноября"
        Calendar.DECEMBER -> "декабря"
        else -> ""
    }
    val year = calendar[Calendar.YEAR]
    return "$day $month $year г."
}
