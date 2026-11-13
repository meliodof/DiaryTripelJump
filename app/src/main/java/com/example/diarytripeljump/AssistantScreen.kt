package com.example.diarytripeljump

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.AssistantMessage
import com.example.diarytripeljump.data.DiaryAssistant
import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.coachTarget
import kotlin.math.roundToInt

/**
 * «Спросить дневник»: вопрос своими словами → своя модель определяет тип вопроса → ответ из данных дневника
 * ([DiaryAssistant]). Всё работает на телефоне, переписка не сохраняется.
 */
@Composable
fun AssistantScreen(viewModel: DiaryViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val messages by viewModel.assistantMessages.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }

    fun send(text: String) {
        if (text.isBlank()) return
        viewModel.askAssistant(text)
        input = ""
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            Text("Спросить дневник", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearAssistant() }) { Icon(Icons.Default.Delete, contentDescription = "Очистить переписку") }
            }
            HelpIconButton(onClick = rememberHelp(HelpTopic.F_ASSISTANT), id = "assistant.help")
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "intro") {
                AppCard {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        IconBadge(icon = Icons.Default.QuestionAnswer, tint = cs.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Спросите своими словами", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Своя модель дневника понимает, о чём вопрос, а ответ собирается только из ваших данных: " +
                                    "прогноза, советов, рекордов, тренировок и заметок. Всё считается на телефоне, переписка не сохраняется.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { m -> MessageBubble(m, onNavigate) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp).coachTarget("assistant.examples"),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DiaryAssistant.examples.forEach { q -> ToggleChip(label = q, selected = false, onClick = { send(q) }) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(500) },
                placeholder = { Text("Например: как прошла неделя?") },
                modifier = Modifier.weight(1f).coachTarget("assistant.input"),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) })
            )
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Спросить", tint = if (input.isNotBlank()) cs.primary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: AssistantMessage, onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (m.fromUser) cs.primaryContainer else cs.surfaceVariant,
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (m.fromUser) 16.dp else 4.dp, bottomEnd = if (m.fromUser) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                m.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (m.fromUser) cs.onPrimaryContainer else cs.onSurface
            )
            val a = m.answer
            if (a != null) {
                Text(
                    (a.intent?.let { "Понял как: ${it.label}" } ?: "Вопрос не распознан") + " · уверенность ${(a.confidence * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = cs.onSurfaceVariant
                )
                if (a.route != null && a.routeLabel != null) {
                    TextButton(onClick = { onNavigate(a.route) }, contentPadding = PaddingValues(0.dp)) { Text(a.routeLabel) }
                }
            }
        }
    }
}
