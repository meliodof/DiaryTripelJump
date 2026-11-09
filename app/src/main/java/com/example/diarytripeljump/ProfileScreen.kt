package com.example.diarytripeljump

import com.example.diarytripeljump.data.HelpTopic
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
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.diarytripeljump.data.AgeGroups
import com.example.diarytripeljump.data.AthleteProfile
import com.example.diarytripeljump.data.AutoRank
import com.example.diarytripeljump.data.RunUpHistoryEntry
import com.example.diarytripeljump.data.RunUpLength
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.data.DiaryBackup
import java.util.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.ui.text.style.TextOverflow
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.SportRanks
import com.example.diarytripeljump.data.formatRecordValue
import com.example.diarytripeljump.ui.components.*
import com.example.diarytripeljump.ui.theme.extraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    isDemo: Boolean = false,
    onSwitchDiary: (demo: Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val confirm = LocalConfirm.current
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val rawResultsList by viewModel.rawTripleJumpResults.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                // Копируем фото в память приложения — иначе доступ к нему пропадает после перезапуска
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { AvatarStorage.saveFromUri(context, uri, demo = isDemo) }
                    if (saved != null) viewModel.updateAthleteProfileAvatar(saved)
                }
            }
        }
    )

    val calculatedPbMeters = remember(rawResultsList, athleteProfile) {
        val customPb = athleteProfile?.customPbMeters
        if (customPb != null && customPb > 0) {
            String.format(Locale.getDefault(), "%.2f м", customPb)
        } else {
            // Только результаты в метрах: килограммы и секунды в рекорд прыжка не попадают
            val best = rawResultsList
                .filter { !it.isAntiRecord && it.resultUnit == ResultUnit.M }
                .maxOfOrNull { it.result }
            if (best != null && best > 0) String.format(Locale.getDefault(), "%.2f м", best) else "— м"
        }
    }

    val birthDateMillis = athleteProfile?.birthDate
    val isBirthday = isTodayBirthday(birthDateMillis)
    val ageText = calculateAgeText(birthDateMillis)
    val birthDateFormattedText = formatBirthDateFull(birthDateMillis)
    val ageGroup = remember(birthDateMillis, athleteProfile?.gender) { AgeGroups.of(birthDateMillis, athleteProfile?.gender) }
    val autoRank by viewModel.autoRank.collectAsState()
    val runUpHistory by viewModel.runUpHistory.collectAsState()
    var showRanksDialog by remember { mutableStateOf(false) }
    var showRunUpDialog by remember { mutableStateOf(false) }
    var bodyKind by remember { mutableStateOf<BodyKind?>(null) }
    val bodyMeasurements by viewModel.bodyMeasurements.collectAsState()
    val sportRank = autoRank?.rank

    val fullName = remember(athleteProfile) {
        val f = athleteProfile?.firstName ?: ""
        val l = athleteProfile?.lastName ?: ""
        val full = "$f $l".trim()
        if (full.isNotBlank()) full else "Ваше имя"
    }

    // Пустой никнейм не показываем, вместо выдуманного «@jumper_alex»
    val nicknameText = remember(athleteProfile) {
        val n = athleteProfile?.nickname?.trim() ?: ""
        if (n.isNotBlank()) (if (n.startsWith("@")) n else "@$n") else ""
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
                    LocalOpenSettings.current?.let { SettingsIconButton(onClick = it) }
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
                        val avatar = athleteProfile?.avatarUriString
                        var avatarFailed by remember(avatar) { mutableStateOf(false) }
                        if (!avatar.isNullOrBlank() && !avatarFailed) {
                            AsyncImage(
                                model = avatar,
                                contentDescription = "Аватар",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { avatarFailed = true }
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
                if (nicknameText.isNotBlank()) {
                    Text(text = nicknameText, style = MaterialTheme.typography.titleSmall, color = cs.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = athleteProfile?.specialization ?: "Тройной прыжок",
                        contentColor = cs.onPrimaryContainer,
                        containerColor = cs.primaryContainer
                    )
                    if (ageGroup != null) {
                        StatusPill(
                            text = ageGroup.code,
                            contentColor = cs.onTertiaryContainer,
                            containerColor = cs.tertiaryContainer,
                            icon = Icons.Default.Groups
                        )
                    }
                    if (sportRank != null) {
                        StatusPill(
                            text = sportRank.shortName,
                            contentColor = MaterialTheme.extraColors.onGoldContainer,
                            containerColor = MaterialTheme.extraColors.goldContainer,
                            icon = Icons.Default.MilitaryTech
                        )
                    }
                }
            }

            // Показатели
            Row(modifier = Modifier.coachTarget("profile.stats"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = if (athleteProfile?.height != null && athleteProfile!!.height!! > 0) "${athleteProfile!!.height!!.toInt()}" else "—",
                    label = "Рост, см",
                    icon = Icons.Default.Height,
                    tint = cs.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { bodyKind = BodyKind.HEIGHT }
                )
                StatTile(
                    value = if (athleteProfile?.weight != null && athleteProfile!!.weight!! > 0) "${athleteProfile!!.weight!!.toInt()}" else "—",
                    label = "Вес, кг",
                    icon = Icons.Default.MonitorWeight,
                    tint = cs.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { bodyKind = BodyKind.WEIGHT }
                )
                StatTile(
                    // тот же лучший результат, по которому считается разряд
                    value = autoRank?.bestResult?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: calculatedPbMeters.removeSuffix(" м"),
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
                        if (ageGroup != null) {
                            Text(
                                text = "Возрастная группа: ${ageGroup.code} — ${ageGroup.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.tertiary
                            )
                        }
                    }
                }
            }

            AppCard(modifier = Modifier.coachTarget("profile.rank")) {
                ProfileOptionRow(
                    icon = Icons.Default.MilitaryTech,
                    title = sportRank?.title ?: "Разряд пока не выполнен",
                    subtitle = rankSubtitle(autoRank, athleteProfile?.gender),
                    onClick = { showRanksDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                ProfileOptionRow(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    title = when (athleteProfile?.leadingLeg) {
                        "LEFT" -> "Ведущая нога: левая"
                        "RIGHT" -> "Ведущая нога: правая"
                        else -> "Ведущая нога не указана"
                    },
                    subtitle = "Нога отталкивания в тройном прыжке",
                    onClick = { showEditDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                val currentRunUp = runUpHistory.firstOrNull()
                ProfileOptionRow(
                    icon = Icons.Default.Straighten,
                    title = currentRunUp?.let { "Разбег: " + RunUpLength.label(it.lengthCm, it.lengthFeet) } ?: "Размер разбега не указан",
                    subtitle = if (currentRunUp == null) "Нажмите, чтобы указать" else "Меняется, когда отнёс или поднёс отметку",
                    onClick = { showRunUpDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                ProfileOptionRow(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Обращение в приложении",
                    subtitle = if (athleteProfile?.greetingPreference == "NICKNAME") "По никнейму · ${nicknameText.ifBlank { "не задан" }}"
                    else "По имени · $fullName",
                    onClick = { showEditDialog = true }
                )
            }

        }
    }

    if (showRanksDialog) {
        RanksDialog(autoRank = autoRank, gender = athleteProfile?.gender, onDismiss = { showRanksDialog = false })
    }

    bodyKind?.let { kind ->
        BodyHistoryDialog(
            kind = kind,
            history = bodyMeasurements,
            onAdd = { value, date -> viewModel.addBodyMeasurement(kind, value, date) },
            onDelete = { m ->
                confirm.delete("замер", "Замер «${formatNumber(kind.of(m) ?: 0.0)} ${kind.unit}» будет удалён.") { viewModel.deleteBodyMeasurement(m) }
            },
            onDismiss = { bodyKind = null }
        )
    }

    if (showRunUpDialog) {
        RunUpLengthDialog(
            history = runUpHistory,
            onDismiss = { showRunUpDialog = false },
            onSave = { cm, feet ->
                viewModel.saveRunUpLength(cm, feet)
                showRunUpDialog = false
            }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentProfile = athleteProfile,
            onDismiss = { showEditDialog = false },
            onConfirm = { firstName, lastName, nickname, greetingPref, birthDateMillis, height, weight, customPb, gender, leadingLeg ->
                confirm.save("Профиль") {
                    viewModel.updateAthleteProfile(
                        gender = gender,
                        leadingLeg = leadingLeg,
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
            }
        )
    }
}

private fun shortDate(millis: Long): String = java.text.SimpleDateFormat("d MMM yyyy", Locale("ru")).format(Date(millis))

/** Подпись к разряду: откуда он взялся и сколько осталось до следующего. */
private fun rankSubtitle(auto: AutoRank?, gender: String?): String {
    val best = auto?.bestResult
    return when {
        gender == null -> "Укажите пол в профиле, чтобы сверять результат с нормами"
        best == null -> "Определится по результату на соревнованиях"
        else -> {
            val next = SportRanks.next(auto?.rank)
            val limit = next?.let { SportRanks.norm(it, gender) }
            "Лучший результат ${formatRecordValue(best, ResultUnit.M)}" +
                if (next != null && limit != null && limit > best) {
                    ", до нормы ${next.shortName}: ${formatRecordValue(limit - best, ResultUnit.M)}"
                } else ""
        }
    }
}

/** Все разряды с нормами в тройном прыжке; выполненный выделен. */
@Composable
private fun RanksDialog(autoRank: AutoRank?, gender: String?, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_RANKS) { Text("Разряды в тройном прыжке") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SportRanks.all.forEach { rank ->
                    val current = rank == autoRank?.rank
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (current) cs.primaryContainer else cs.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = rank.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                text = SportRanks.norm(rank, gender)?.let { formatRecordValue(it, ResultUnit.M) }
                                    ?: "М ${formatRecordValue(rank.maleNorm, ResultUnit.M)}, Ж ${formatRecordValue(rank.femaleNorm, ResultUnit.M)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Text(
                    text = "Разряд определяется автоматически по лучшему результату на соревнованиях и рекорду из профиля. " +
                        "Норма засчитывается при попутном ветре не более 2 м/с на соревнованиях нужного статуса, " +
                        "а присваивает разряд уполномоченная организация. ЕВСК, приказ Минспорта России от 10.03.2026 № 201.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/** Размер разбега: ручной замер и история изменений. */
@Composable
private fun RunUpLengthDialog(
    history: List<RunUpHistoryEntry>,
    onDismiss: () -> Unit,
    onSave: (lengthCm: Double?, lengthFeet: Double?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val current = history.firstOrNull()
    fun plain(v: Double?) = v?.let { formatNumber(it).replace(',', '.') } ?: ""
    var cmText by remember { mutableStateOf(plain(current?.lengthCm)) }
    var feetText by remember { mutableStateOf(plain(current?.lengthFeet)) }
    fun numeric(v: String) = v.replace(',', '.').filter { it.isDigit() || it == '.' }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_RUNUP_LENGTH) { Text("Размер разбега") } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cmText,
                        onValueChange = { cmText = numeric(it) },
                        label = { Text("Сантиметры") },
                        suffix = { Text("см") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = feetText,
                        onValueChange = { feetText = numeric(it) },
                        label = { Text("Стопы") },
                        suffix = { Text("ст.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Можно указать одно или оба значения. Сантиметры меняются на сантиметры переноса, стопы — на стопы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                if (history.isNotEmpty()) {
                    Text("История", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    history.forEach { entry ->
                        Column {
                            Text(
                                text = RunUpLength.label(entry.lengthCm, entry.lengthFeet),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface
                            )
                            val cmDelta = entry.deltaCm?.takeIf { it != 0.0 }?.let { signedAmount(it, "см") }
                            val feetDelta = entry.deltaFeet?.takeIf { it != 0.0 }?.let { signedAmount(it, "ст.") }
                            Text(
                                text = listOfNotNull(shortDate(entry.date), entry.source, cmDelta, feetDelta).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(cmText.toDoubleOrNull(), feetText.toDoubleOrNull()) }) { Text("Сохранить замер") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun signedAmount(value: Double, unit: String): String =
    (if (value > 0) "+" else "−") + formatNumber(Math.abs(value)) + " " + unit

/** Перезапуск процесса: восстановленная база подключается при следующем старте приложения. */
internal fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

/** Редактирование профиля: без выдуманных значений по умолчанию, дата рождения — через календарь. */
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
        customPbMeters: Double?,
        gender: String?,
        leadingLeg: String?
    ) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var firstNameText by remember { mutableStateOf(currentProfile?.firstName ?: "") }
    var lastNameText by remember { mutableStateOf(currentProfile?.lastName ?: "") }
    var nicknameText by remember { mutableStateOf(currentProfile?.nickname?.removePrefix("@") ?: "") }
    var greetingPref by remember { mutableStateOf(currentProfile?.greetingPreference ?: "NAME") }
    var gender by remember { mutableStateOf(currentProfile?.gender) }
    var heightText by remember { mutableStateOf(currentProfile?.height?.toInt()?.toString() ?: "") }
    var weightText by remember { mutableStateOf(currentProfile?.weight?.toInt()?.toString() ?: "") }
    var pbText by remember { mutableStateOf(currentProfile?.customPbMeters?.toString() ?: "") }
    var birthMillis by remember { mutableStateOf(currentProfile?.birthDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var leadingLeg by remember { mutableStateOf(currentProfile?.leadingLeg) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_PROFILE_EDIT) { Text(text = "Профиль", style = MaterialTheme.typography.titleLarge) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstNameText,
                        onValueChange = { firstNameText = it },
                        label = { Text("Имя") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastNameText,
                        onValueChange = { lastNameText = it },
                        label = { Text("Фамилия") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = nicknameText,
                    onValueChange = { nicknameText = it.removePrefix("@").trim() },
                    label = { Text("Никнейм") },
                    prefix = { Text("@") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                ProfileFormLabel("Обращение")
                SegmentedControl(
                    options = listOf("По имени", "По никнейму"),
                    selectedIndex = if (greetingPref == "NICKNAME") 1 else 0,
                    onSelect = { greetingPref = if (it == 1) "NICKNAME" else "NAME" }
                )

                ProfileFormLabel("Пол — для возрастной группы")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == AgeGroups.GENDER_MALE,
                        onClick = { gender = AgeGroups.GENDER_MALE },
                        label = { Text("Мужской") }
                    )
                    FilterChip(
                        selected = gender == AgeGroups.GENDER_FEMALE,
                        onClick = { gender = AgeGroups.GENDER_FEMALE },
                        label = { Text("Женский") }
                    )
                }

                ProfileFormLabel("Дата рождения")
                val birth = birthMillis
                if (birth != null) {
                    DateField(label = "Дата рождения", millis = birth, onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Cake, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Указать дату рождения")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Рост") },
                        suffix = { Text("см") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Вес") },
                        suffix = { Text("кг") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = pbText,
                    onValueChange = { pbText = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Личный рекорд в тройном") },
                    suffix = { Text("м") },
                    supportingText = { Text("Если оставить пустым — берётся из рекордов", color = cs.onSurfaceVariant) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                ProfileFormLabel("Ведущая нога")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = leadingLeg == "LEFT",
                        onClick = { leadingLeg = if (leadingLeg == "LEFT") null else "LEFT" },
                        label = { Text("Левая") }
                    )
                    FilterChip(
                        selected = leadingLeg == "RIGHT",
                        onClick = { leadingLeg = if (leadingLeg == "RIGHT") null else "RIGHT" },
                        label = { Text("Правая") }
                    )
                }

            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    firstNameText.trim(),
                    lastNameText.trim(),
                    nicknameText.trim(),
                    greetingPref,
                    birthMillis,
                    heightText.toDoubleOrNull(),
                    weightText.toDoubleOrNull(),
                    pbText.toDoubleOrNull(),
                    gender,
                    leadingLeg
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showDatePicker) {
        AppDatePickerDialog(
            initialMillis = birthMillis ?: Calendar.getInstance().apply { add(Calendar.YEAR, -18) }.timeInMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                birthMillis = it
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun ProfileFormLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
