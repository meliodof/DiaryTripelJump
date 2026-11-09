package com.example.diarytripeljump

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.DiaryBackup
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.LocalConfirm
import com.example.diarytripeljump.ui.components.SectionHeader
import com.example.diarytripeljump.ui.components.destructiveButtonColors
import kotlinx.coroutines.launch

/** Настройки: оформление, обучение, подсказки и резервная копия. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DiaryViewModel,
    isDemo: Boolean,
    onBack: () -> Unit,
    onStartTutorial: () -> Unit,
    onOpenTopic: (HelpTopic) -> Unit,
    onLeaveDemo: () -> Unit
) {
    val confirm = LocalConfirm.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val customPalette by viewModel.customPalette.collectAsState()
    val demoLeftovers by viewModel.demoLeftovers.collectAsState()
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showHelpCenter by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isDemo) { if (!isDemo) viewModel.scanDemoLeftovers() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            scope.launch {
                val app = context.applicationContext as TripleJumpApplication
                val ok = DiaryBackup.export(context, app.database, uri)
                backupMessage = if (ok) "Копия сохранена. Храните файл в облаке или на компьютере." else "Не удалось сохранить копию."
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = DiaryBackup.import(context, uri)) {
                    DiaryBackup.ImportResult.Success -> {
                        // после восстановления открываем свой дневник, а не демо
                        (context.applicationContext as TripleJumpApplication).preferences.useDemoData = false
                        restartApp(context)
                    }
                    is DiaryBackup.ImportResult.Error -> backupMessage = result.message
                }
            }
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isDemo) {
                Surface(shape = MaterialTheme.shapes.large, color = cs.tertiaryContainer) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Открыт демо-дневник", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer)
                        Text("Это пример для обучения. Ваши данные хранятся отдельно.", style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
                        Button(onClick = onLeaveDemo) { Text("Вернуться к своему дневнику") }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Оформление")
                AppCard {
                    ProfileOptionRow(
                        icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        title = "Тёмная тема",
                        subtitle = if (isDarkTheme) "Включена" else "Выключена",
                        onClick = { viewModel.toggleTheme() },
                        trailing = { Switch(checked = isDarkTheme, onCheckedChange = { viewModel.toggleTheme() }) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                    ProfileOptionRow(
                        icon = Icons.Default.Palette,
                        title = "Своя расцветка",
                        subtitle = if (customPalette == null) "Стандартные цвета" else "Свои цвета",
                        onClick = { showPaletteDialog = true }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Обучение")
                AppCard {
                    ProfileOptionRow(
                        icon = Icons.Default.School,
                        title = "Пройти обучение",
                        subtitle = "На примере демо-дневника, ваши данные не изменятся",
                        onClick = onStartTutorial
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                    ProfileOptionRow(
                        icon = Icons.Default.Lightbulb,
                        title = "Подсказки по разделам",
                        subtitle = "Подсветка кнопок и пояснения на нужном экране",
                        onClick = { showHelpCenter = true }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Резервная копия",
                    subtitle = if (isDemo) "Доступна в своём дневнике" else "Дневник также сохраняется в облачный бэкап Google"
                )
                if (!isDemo) AppCard {
                    ProfileOptionRow(
                        icon = Icons.Default.Upload,
                        title = "Сохранить копию",
                        subtitle = "Файл со всеми тренировками, стартами и рекордами",
                        onClick = { exportLauncher.launch(DiaryBackup.suggestedFileName()) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 70.dp), color = cs.outlineVariant)
                    ProfileOptionRow(
                        icon = Icons.Default.Download,
                        title = "Восстановить из копии",
                        subtitle = "Текущие данные будут заменены",
                        onClick = { showRestoreConfirm = true }
                    )
                }
            }

            // в своём дневнике могли остаться записи старой автозагрузки демо — предлагаем убрать их
            demoLeftovers?.let { found ->
                AppCard {
                    ProfileOptionRow(
                        icon = Icons.Default.Science,
                        title = "Убрать старые демо-записи",
                        subtitle = found.describe(),
                        onClick = {
                            confirm.delete(
                                "демо-записи",
                                "Будут удалены только записи демо-набора: ${found.describe()}. Всё, что вы вводили сами, останется."
                            ) { viewModel.removeDemoLeftovers { backupMessage = "Демо-записи удалены: ${it.describe()}." } }
                        }
                    )
                }
            }
        }
    }

    if (showHelpCenter) {
        HelpCenterDialog(
            onOpenTopic = { topic ->
                showHelpCenter = false
                onOpenTopic(topic)
            },
            onStartTour = {
                showHelpCenter = false
                onStartTutorial()
            },
            onDismiss = { showHelpCenter = false }
        )
    }

    if (showPaletteDialog) {
        PaletteEditorDialog(
            initial = customPalette,
            onDismiss = { showPaletteDialog = false },
            onSave = { palette ->
                confirm.save("Расцветка приложения") {
                    viewModel.setCustomPalette(palette)
                    showPaletteDialog = false
                }
            },
            onReset = {
                confirm.ask("Вернуть стандартные цвета?", "Своя расцветка будет сброшена.", "Вернуть", false) {
                    viewModel.setCustomPalette(null)
                    showPaletteDialog = false
                }
            }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Восстановить дневник?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Все текущие записи будут заменены данными из файла, после чего приложение перезапустится. " +
                        "Перед восстановлением рекомендуем сохранить копию текущих данных."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirm = false
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    colors = destructiveButtonColors()
                ) { Text("Выбрать файл") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Отмена") } }
        )
    }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("Резервная копия", fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { backupMessage = null }) { Text("OK") } }
        )
    }
}
