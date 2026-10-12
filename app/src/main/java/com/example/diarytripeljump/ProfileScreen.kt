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

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val rawResultsList by viewModel.rawTripleJumpResults.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "ЛИЧНЫЙ КАБИНЕТ",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "Профиль Атлета",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
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

        // Profile Card Header with Avatar Picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
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
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Добавить фото",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = fullName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = nicknameText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Специализация: ${athleteProfile?.specialization ?: "Тройной прыжок"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Physical Parameters & Age Card (With Edit Button placed under Age)
        Text(
            text = "АНТРОПОМЕТРИЯ И ВОЗРАСТ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileParamColumn(
                        value = if (athleteProfile?.height != null && athleteProfile!!.height!! > 0) "${athleteProfile!!.height!!.toInt()} см" else "— см",
                        label = "Рост"
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(36.dp))
                    ProfileParamColumn(
                        value = if (athleteProfile?.weight != null && athleteProfile!!.weight!! > 0) "${athleteProfile!!.weight!!.toInt()} кг" else "— кг",
                        label = "Вес"
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(36.dp))
                    ProfileParamColumn(
                        value = calculatedPbMeters,
                        label = "Рекорд (PB)"
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Age in LARGE Bold Numbers, Birth Date Subtitle, and Edit Button Underneath!
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = ageText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 28.sp
                    )
                    Text(
                        text = birthDateFormattedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Редактировать профиль", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // App Settings & Preferences
        Text(
            text = "НАСТРОЙКИ ПРИЛОЖЕНИЯ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ProfileOptionRow(
                    icon = if (uiState.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    title = "Тема оформления",
                    subtitle = if (uiState.isDarkTheme) "Тёмная тема" else "Светлая тема",
                    onClick = { viewModel.toggleTheme() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ProfileOptionRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Обращение в приложении",
                    subtitle = if (athleteProfile?.greetingPreference == "NICKNAME") "По никнейму ($nicknameText)" else "По имени ($fullName)",
                    onClick = { showEditDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
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
            fontSize = 10.sp
        )
    }
}

@Composable
fun ProfileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
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
