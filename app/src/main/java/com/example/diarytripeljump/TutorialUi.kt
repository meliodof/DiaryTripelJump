package com.example.diarytripeljump

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.LocalCoach
import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.ui.components.pluralRu

val HelpTopic.icon: ImageVector
    get() = when (this) {
        HelpTopic.F_TRAINING, HelpTopic.F_PICKER, HelpTopic.F_RESULTS, HelpTopic.F_RUNUP, HelpTopic.F_DETAILS,
        HelpTopic.F_PERIOD, HelpTopic.F_SKIP -> Icons.Default.FitnessCenter
        HelpTopic.F_COMPETITION, HelpTopic.F_SKIP_START -> Icons.Default.EmojiEvents
        HelpTopic.F_RECORD, HelpTopic.F_RECORD_DETAIL, HelpTopic.F_GOAL -> Icons.Default.MilitaryTech
        HelpTopic.F_VISUALIZATION, HelpTopic.F_CHART_SETTINGS, HelpTopic.F_ADVICE, HelpTopic.F_ASSISTANT -> Icons.AutoMirrored.Filled.ShowChart
        HelpTopic.F_BUILDER, HelpTopic.F_COMPLEX, HelpTopic.F_INVENTORY -> Icons.Default.Category
        HelpTopic.F_BODY, HelpTopic.F_PROFILE_EDIT, HelpTopic.F_RUNUP_LENGTH, HelpTopic.F_RANKS -> Icons.Default.Person
        HelpTopic.F_FAVORITES -> Icons.Default.Home
        HelpTopic.F_PALETTE -> Icons.Default.Settings
        HelpTopic.WELCOME -> Icons.Default.School
        HelpTopic.HOME -> Icons.Default.Home
        HelpTopic.TRAININGS -> Icons.Default.FitnessCenter
        HelpTopic.STARTS -> Icons.Default.EmojiEvents
        HelpTopic.RECORDS -> Icons.Default.MilitaryTech
        HelpTopic.ANALYTICS -> Icons.AutoMirrored.Filled.ShowChart
        HelpTopic.CATALOG -> Icons.Default.Category
        HelpTopic.PROFILE -> Icons.Default.Person
        HelpTopic.SETTINGS -> Icons.Default.Settings
    }

/** Подсказки по текущему экрану: подсветка его элементов стрелкой и текстом. */
@Composable
fun rememberHelp(topic: HelpTopic): () -> Unit {
    val coach = LocalCoach.current
    return { coach.show(topic.tourSteps) }
}

/** Заголовок окна с кнопкой «?»: подсказки подсвечивают элементы этого окна. */
@Composable
fun FormHelpRow(topic: HelpTopic, content: @Composable () -> Unit) {
    val help = rememberHelp(topic)
    Row(modifier = Modifier.fillMaxWidth().coachTarget("form.title"), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { content() }
        IconButton(onClick = help) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Подсказки по окну", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Список разделов подсказок: раздел открывает свой экран и подсвечивает его элементы. */
@Composable
fun HelpCenterDialog(onOpenTopic: (HelpTopic) -> Unit, onStartTour: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подсказки по разделам") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val sections = HelpTopic.entries.filter { it != HelpTopic.WELCOME && !it.isForm }
                val forms = HelpTopic.entries.filter { it.isForm }
                (sections + forms).forEachIndexed { index, t ->
                    if (index == 0 || index == sections.size) {
                        Text(
                            if (index == 0) "Экраны" else "Окна",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenTopic(t) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(icon = t.icon, tint = cs.primary, size = 36.dp, iconSize = 18.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                            Text(
                                "${t.steps.size} ${pluralRu(t.steps.size, "подсказка", "подсказки", "подсказок")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onStartTour) { Text("Пройти обучение") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

/** Кнопка «?» для верхней панели экрана. */
@Composable
fun HelpIconButton(onClick: () -> Unit, id: String = "top.help") {
    IconButton(onClick = onClick, modifier = Modifier.coachTarget(id)) {
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Подсказки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Кнопка настроек для верхней панели экрана. */
@Composable
fun SettingsIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.coachTarget("top.settings")) {
        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Предложение пройти обучение при первом входе. */
@Composable
fun WelcomeDialog(onStart: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = { Icon(Icons.Default.School, contentDescription = null) },
        title = { Text("Добро пожаловать!") },
        text = {
            Text(
                "Покажем, как всё устроено, на примере демо-дневника: подсветим кнопки и расскажем, что они делают. " +
                    "Ваши данные не изменятся. Обучение можно пройти позже в настройках."
            )
        },
        confirmButton = { Button(onClick = onStart) { Text("Начать") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Не сейчас") } }
    )
}
