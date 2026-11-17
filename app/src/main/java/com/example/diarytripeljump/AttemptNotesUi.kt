package com.example.diarytripeljump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.AttemptLine

/** Окно комментариев ко всем попыткам записи: по полю на попытку; пустое поле — комментария нет. */
@Composable
fun AttemptNotesDialog(
    title: String,
    lines: List<AttemptLine>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val texts = remember(lines) { mutableStateMapOf<String, String>().apply { lines.forEach { put(it.key, it.note.orEmpty()) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lines, key = { it.key }) { line ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = texts[line.key].orEmpty(),
                            onValueChange = { texts[line.key] = it.take(300) },
                            placeholder = { Text("Комментарий") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(texts.toMap()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Комментарии к попыткам под карточкой: «Попытка 2 — смазал отталкивание». */
@Composable
fun AttemptNotesList(lines: List<AttemptLine>, modifier: Modifier = Modifier) {
    val withNotes = lines.filter { !it.note.isNullOrBlank() }
    if (withNotes.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        withNotes.forEach { line ->
            Text(
                "${line.label.substringBefore(':')} — ${line.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
