package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.data.HelpTopic
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.BodyMeasurement
import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.formatNumber
import com.example.diarytripeljump.ui.components.AppDatePickerDialog
import com.example.diarytripeljump.ui.components.DateField
import com.example.diarytripeljump.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Что отслеживается: вес или рост. */
enum class BodyKind(val title: String, val unit: String, val resultUnit: ResultUnit) {
    WEIGHT("Вес", "кг", ResultUnit.KG),
    HEIGHT("Рост", "см", ResultUnit.CM);

    fun of(m: BodyMeasurement): Double? = if (this == WEIGHT) m.weightKg else m.heightCm
}

/** История веса или роста: новый замер с датой, список с изменениями и мини-график. */
@Composable
fun BodyHistoryDialog(
    kind: BodyKind,
    history: List<BodyMeasurement>,
    onAdd: (value: Double, date: Long) -> Unit,
    onDelete: (BodyMeasurement) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val entries = remember(history, kind) { history.filter { kind.of(it) != null }.sortedBy { it.date } }
    var valueText by remember { mutableStateOf("") }
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pickingDate by remember { mutableStateOf(false) }
    val error = if (valueText.isBlank()) null else EntryRules.resultError(valueText, kind.resultUnit)
    val dateError = EntryRules.dateError(date, hasResult = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_BODY) { Text(kind.title) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entries.size > 1) {
                    Sparkline(
                        values = entries.map { kind.of(it)!! },
                        lowerIsBetter = false,
                        color = cs.primary,
                        modifier = Modifier.fillMaxWidth().height(64.dp)
                    )
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = EntryRules.cleanNumber(it) },
                    label = { Text("Новое значение") },
                    suffix = { Text(kind.unit) },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().coachTarget("body.value")
                )
                DateField(label = "Дата замера", millis = date, onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth())
                if (dateError != null) Text(dateError, style = MaterialTheme.typography.bodySmall, color = cs.error)

                if (entries.isNotEmpty()) {
                    HorizontalDivider(color = cs.outlineVariant)
                    Text("История", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Column(modifier = Modifier.heightIn(max = 320.dp)) {
                        entries.reversed().forEachIndexed { i, m ->
                            val value = kind.of(m)!!
                            val previous = entries.reversed().getOrNull(i + 1)?.let { kind.of(it) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${formatNumber(value)} ${kind.unit}", style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
                                    Text(
                                        SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(m.date)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                if (previous != null && previous != value) {
                                    val diff = value - previous
                                    StatusPill(
                                        text = (if (diff > 0) "+" else "−") + formatNumber(Math.abs(diff)),
                                        contentColor = cs.onSurfaceVariant,
                                        containerColor = cs.surfaceContainerHigh
                                    )
                                }
                                IconButton(onClick = { onDelete(m) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить замер", tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valueText.isNotBlank() && error == null && dateError == null,
                onClick = {
                    valueText.toDoubleOrNull()?.let { onAdd(it, date) }
                    valueText = ""
                }
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )

    if (pickingDate) {
        AppDatePickerDialog(initialMillis = date, onDismiss = { pickingDate = false }, onConfirm = {
            date = it
            pickingDate = false
        })
    }
}
