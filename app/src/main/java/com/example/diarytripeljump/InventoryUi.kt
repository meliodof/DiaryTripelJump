package com.example.diarytripeljump

import com.example.diarytripeljump.data.HelpTopic
import com.example.diarytripeljump.ui.components.LocalConfirm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.components.destructiveButtonColors

/** Действия со справочником инвентаря: одни и те же в каталоге, тренировке и рекордах. */
data class InventoryActions(
    val add: (name: String, unit: ResultUnit?) -> Unit = { _, _ -> },
    val update: (item: InventoryItem, name: String, unit: ResultUnit?) -> Unit = { _, _, _ -> },
    val delete: (item: InventoryItem) -> Unit = {}
)

private val InventoryUnits = listOf(null, ResultUnit.CM, ResultUnit.KG)

/** Название для списка: «Барьер, см» или просто «Скакалка», если единицы нет. */
private val InventoryItem.label: String get() = unit?.let { "$name, ${getUnitTitle(it)}" } ?: name

/** Создание и изменение инвентаря: название и необязательная единица измерения. */
@Composable
fun CreateInventoryDialog(
    existing: List<InventoryItem>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: ResultUnit?) -> Unit,
    /** Изменяемый инвентарь; null — новый. */
    initial: InventoryItem? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var unit by remember { mutableStateOf(initial?.unit) }
    var showError by remember { mutableStateOf(false) }
    val error = EntryRules.inventoryNameError(name, unit, existing, initial)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_INVENTORY) { Text(text = if (initial == null) "Новый инвентарь" else "Изменить инвентарь", fontWeight = FontWeight.Bold) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название (например, Барьер, Гиря)") },
                    isError = showError && error != null,
                    supportingText = if (showError && error != null) {
                        { Text(error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "В чём измеряется",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedControl(
                    options = listOf("Без единицы", "см", "кг"),
                    selectedIndex = InventoryUnits.indexOf(unit),
                    onSelect = { unit = InventoryUnits[it] }
                )
                if (initial != null) {
                    Text(
                        text = "Упражнения в справочнике получат новые параметры. Прошлые тренировки и рекорды не меняются." +
                            if (unit != initial.unit) " Вес или размер снаряда в упражнениях будет сброшен." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (error != null) showError = true else onConfirm(name.trim(), unit)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Список инвентаря: добавить, изменить и удалить. */
@Composable
fun InventoryManagerDialog(
    items: List<InventoryItem>,
    actions: InventoryActions,
    onDismiss: () -> Unit,
    /** Инвентарь изменён: открытая форма может подхватить новое название. */
    onChanged: (old: InventoryItem, name: String, unit: ResultUnit?) -> Unit = { _, _, _ -> }
) {
    val confirm = LocalConfirm.current
    val cs = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var deleting by remember { mutableStateOf<InventoryItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { FormHelpRow(HelpTopic.F_INVENTORY) { Text(text = "Инвентарь", fontWeight = FontWeight.Bold) } },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (items.isEmpty()) {
                    Text("Инвентаря пока нет", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editing = item }) {
                            Icon(Icons.Default.Edit, contentDescription = "Изменить ${item.name}", tint = cs.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить ${item.name}", tint = cs.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Новый инвентарь")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )

    if (creating) {
        CreateInventoryDialog(
            existing = items,
            onDismiss = { creating = false },
            onConfirm = { name, unit ->
                actions.add(name, unit)
                creating = false
            }
        )
    }

    editing?.let { item ->
        CreateInventoryDialog(
            existing = items,
            initial = item,
            onDismiss = { editing = null },
            onConfirm = { name, unit ->
                confirm.save("Инвентарь «${item.name}»") {
                    actions.update(item, name, unit)
                    onChanged(item, name, unit)
                    editing = null
                }
            }
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(text = "Удалить инвентарь?", fontWeight = FontWeight.Bold) },
            text = { Text("«${item.name}» исчезнет из списка. Упражнения, тренировки и рекорды с ним сохранятся.") },
            confirmButton = {
                Button(
                    onClick = {
                        actions.delete(item)
                        deleting = null
                    },
                    colors = destructiveButtonColors()
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }
        )
    }
}
