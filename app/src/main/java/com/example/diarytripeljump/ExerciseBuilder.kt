package com.example.diarytripeljump

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.formatNumber
import kotlin.math.roundToInt

/** Блок конструктора: свойство, которое уточняет упражнение (и разделяет его рекорды). */
private enum class BuilderBlock(val label: String, val hint: String, val icon: ImageVector) {
    SPIKES("Шиповки", "выполняется в шиповках", Icons.AutoMirrored.Filled.DirectionsRun),
    INVENTORY("Инвентарь", "барьер, тумба, ядро…", Icons.Default.FitnessCenter),
    SINGLE_LEG("Одна нога", "левая или правая", Icons.AutoMirrored.Filled.DirectionsWalk)
}

private val EXERCISE_CATEGORIES = listOf(
    "Беговое упражнение",
    "Упражнение на технику тройного прыжка",
    "Силовое упражнение",
    "Прыжковое упражнение",
    "Упражнение ОФП"
)

/**
 * Конструктор упражнения: название + блоки «Шиповки», «Инвентарь», «Одна нога».
 * Блоки перетаскиваются в поле упражнения (или добавляются нажатием); у каждого сочетания свои рекорды.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorDialog(
    initial: Exercise?,
    inventoryItems: List<InventoryItem>,
    onCreateInventory: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (ExerciseConfig) -> Unit,
    /** Параметры выполнения из тренировки — открыть конструктор для правки упражнения прямо в тренировке. */
    initialConfig: ExerciseConfig? = null
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val start = initialConfig ?: initial?.let {
        ExerciseConfig(
            name = it.name, category = it.categoryName ?: "Силовое упражнение", isSingleLeg = it.isSingleLeg,
            legSide = it.legSide, inventoryName = it.inventoryName, inventoryUnit = it.inventoryUnit,
            inventoryValue = it.inventoryValue, inSpikes = it.inSpikes, unit = it.defaultUnit
        )
    }

    var name by remember { mutableStateOf(start?.name ?: "") }
    var category by remember { mutableStateOf(start?.category ?: "Силовое упражнение") }
    var unit by remember { mutableStateOf(start?.unit ?: ResultUnit.KG) }
    // пока единицу не выбрали вручную, она подсказывается по названию (бег — секунды, присед — кг…)
    var unitTouched by remember { mutableStateOf(start != null) }
    var inSpikes by remember { mutableStateOf(start?.inSpikes ?: false) }
    var singleLeg by remember { mutableStateOf(start?.isSingleLeg ?: false) }
    var legSide by remember { mutableStateOf(start?.legSide) }
    var hasInventory by remember { mutableStateOf(!start?.inventoryName.isNullOrBlank()) }
    var inventoryName by remember { mutableStateOf(start?.inventoryName) }
    var inventoryUnit by remember { mutableStateOf(start?.inventoryUnit) }
    var inventoryValueText by remember { mutableStateOf(start?.inventoryValue?.let { formatNumber(it).replace(',', '.') } ?: "") }
    var showErrors by remember { mutableStateOf(false) }
    // штанга при результате в кг: вес снаряда и есть результат — отдельно не вводится
    val loadedImplement = hasInventory && RecordsEngine.isLoadedImplement(inventoryUnit, unit)

    fun isAdded(b: BuilderBlock) = when (b) {
        BuilderBlock.SPIKES -> inSpikes
        BuilderBlock.INVENTORY -> hasInventory
        BuilderBlock.SINGLE_LEG -> singleLeg
    }

    fun setAdded(b: BuilderBlock, added: Boolean) {
        when (b) {
            BuilderBlock.SPIKES -> inSpikes = added
            BuilderBlock.INVENTORY -> {
                hasInventory = added
                if (!added) { inventoryName = null; inventoryUnit = null; inventoryValueText = "" }
            }
            BuilderBlock.SINGLE_LEG -> {
                singleLeg = added
                if (!added) legSide = null
            }
        }
    }

    // --- перетаскивание: координаты в пределах окна диалога ---
    var dragBlock by remember { mutableStateOf<BuilderBlock?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dropZone by remember { mutableStateOf(Rect.Zero) }
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    val overZone = dragBlock != null && dropZone.contains(dragPos)

    fun drop() {
        val b = dragBlock
        if (b != null && dropZone.contains(dragPos) && !isAdded(b)) {
            setAdded(b, true)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        dragBlock = null
    }

    val inventoryMissing = hasInventory && inventoryName.isNullOrBlank()
    val canSave = name.isNotBlank() && !inventoryMissing
    val inventoryText = if (hasInventory) RecordsEngine.variantInventory(inventoryName, inventoryUnit, inventoryValueText.toDoubleOrNull(), unit) else null
    val preview = RecordsEngine.exerciseTitle(
        name.ifBlank { "Название" }, singleLeg, legSide, inSpikes,
        if (hasInventory) inventoryName else null, inventoryUnit, inventoryValueText.toDoubleOrNull(), unit
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }
            ) {
                Column(modifier = Modifier.fillMaxSize().imePadding()) {
                    // Шапка: закрыть · заголовок · сохранить
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                        Text(
                            text = if (start == null) "Новое упражнение" else "Упражнение",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (!canSave) {
                                    showErrors = true
                                } else {
                                    onConfirm(
                                        ExerciseConfig(
                                            name = name.trim(),
                                            category = category,
                                            isSingleLeg = singleLeg,
                                            legSide = if (singleLeg) legSide else null,
                                            inventoryName = if (hasInventory) inventoryName else null,
                                            inventoryUnit = if (hasInventory) inventoryUnit else null,
                                            inventoryValue = if (hasInventory && !loadedImplement) inventoryValueText.toDoubleOrNull() else null,
                                            inSpikes = inSpikes,
                                            // у разбега своя форма результата, единица не выбирается
                                            unit = if (RunUp.isExercise(name)) ResultUnit.CM else unit
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text("Сохранить") }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (!unitTouched) unit = getInferredUnitForExercise(it, unit)
                            },
                            label = { Text("Название") },
                            placeholder = { Text("Например, прыжки через барьеры") },
                            isError = showErrors && name.isBlank(),
                            supportingText = if (showErrors && name.isBlank()) {
                                { Text("Введите название") }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            textStyle = MaterialTheme.typography.titleMedium,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // --- Поле упражнения: сюда перетаскиваются блоки ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuilderLabel("Упражнение")
                            val zoneBorder by animateColorAsState(
                                targetValue = when {
                                    overZone -> cs.primary
                                    dragBlock != null -> cs.primary.copy(alpha = 0.5f)
                                    else -> cs.outlineVariant
                                },
                                label = "zoneBorder"
                            )
                            val zoneFill by animateColorAsState(
                                targetValue = if (overZone) cs.primaryContainer.copy(alpha = 0.5f) else cs.surfaceContainerLow,
                                label = "zoneFill"
                            )
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 76.dp)
                                    .onGloballyPositioned { dropZone = it.boundsInRoot() }
                                    .drawBehind {
                                        val r = CornerRadius(16.dp.toPx())
                                        drawRoundRect(color = zoneFill, cornerRadius = r)
                                        drawRoundRect(
                                            color = zoneBorder,
                                            cornerRadius = r,
                                            style = Stroke(
                                                width = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                                            )
                                        )
                                    }
                                    .animateContentSize()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                itemVerticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(10.dp), color = cs.primary) {
                                    Text(
                                        text = name.trim().ifBlank { "Название" },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cs.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                                BuilderBlock.entries.filter { isAdded(it) }.forEach { b ->
                                    Text("+", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                                    InputChip(
                                        selected = true,
                                        onClick = { setAdded(b, false) },
                                        label = {
                                            Text(
                                                when (b) {
                                                    BuilderBlock.SPIKES -> "В шиповках"
                                                    BuilderBlock.INVENTORY -> inventoryText ?: "Инвентарь"
                                                    BuilderBlock.SINGLE_LEG -> RecordsEngine.legSuffix(true, legSide).trim()
                                                        .replaceFirstChar { it.uppercase() }
                                                }
                                            )
                                        },
                                        leadingIcon = { Icon(b.icon, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize)) },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(InputChipDefaults.IconSize)) }
                                    )
                                }
                                if (BuilderBlock.entries.none { isAdded(it) }) {
                                    Text(
                                        text = if (dragBlock != null) "Отпустите здесь" else "Перетащите сюда блоки снизу",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // --- Палитра блоков ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuilderLabel("Блоки — перетащите или нажмите")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BuilderBlock.entries.forEach { b ->
                                    PaletteBlock(
                                        block = b,
                                        added = isAdded(b),
                                        dragging = dragBlock == b,
                                        onTap = { setAdded(b, !isAdded(b)) },
                                        onDragStart = { pos -> if (!isAdded(b)) { dragBlock = b; dragPos = pos } },
                                        onDrag = { delta -> if (dragBlock != null) dragPos += delta },
                                        onDragEnd = { drop() },
                                        onDragCancel = { dragBlock = null }
                                    )
                                }
                            }
                        }

                        // --- Настройки добавленных блоков ---
                        if (singleLeg) {
                            BlockSettings(title = "Какая нога", icon = BuilderBlock.SINGLE_LEG.icon) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = legSide == null, onClick = { legSide = null }, label = { Text("Любая") })
                                    FilterChip(selected = legSide == "LEFT", onClick = { legSide = "LEFT" }, label = { Text("Левая") })
                                    FilterChip(selected = legSide == "RIGHT", onClick = { legSide = "RIGHT" }, label = { Text("Правая") })
                                }
                            }
                        }
                        if (hasInventory) {
                            BlockSettings(title = "Инвентарь", icon = BuilderBlock.INVENTORY.icon) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    inventoryItems.forEach { item ->
                                        FilterChip(
                                            selected = inventoryName.equals(item.name, ignoreCase = true) && inventoryUnit == item.unit,
                                            onClick = { inventoryName = item.name; inventoryUnit = item.unit },
                                            label = { Text(item.name) }
                                        )
                                    }
                                    AssistChip(
                                        onClick = onCreateInventory,
                                        label = { Text("Новый") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                                    )
                                }
                                if (showErrors && inventoryMissing) {
                                    Text("Выберите инвентарь или уберите блок", style = MaterialTheme.typography.bodySmall, color = cs.error)
                                }
                                if (loadedImplement) {
                                    // штанга, гиря и т.п. при результате в кг: рекорд — это вес снаряда
                                    Text(
                                        text = "Вес снаряда — это и есть результат: вводится в попытках и всегда равен рекорду.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant
                                    )
                                } else if (!inventoryName.isNullOrBlank()) {
                                    OutlinedTextField(
                                        value = inventoryValueText,
                                        onValueChange = { v -> inventoryValueText = v.replace(',', '.').filter { it.isDigit() || it == '.' } },
                                        label = { Text("Высота / вес (необязательно)") },
                                        suffix = { Text(getUnitTitle(inventoryUnit ?: ResultUnit.CM)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // --- Вид упражнения ---
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuilderLabel("Вид")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EXERCISE_CATEGORIES.forEach { cat ->
                                    FilterChip(
                                        selected = category == cat,
                                        onClick = {
                                            category = cat
                                            // силовые измеряются весом — подставляем кг, если единицу ещё не выбирали
                                            if (!unitTouched && cat == "Силовое упражнение") unit = ResultUnit.KG
                                        },
                                        label = { Text(cat.removeSuffix(" упражнение").removePrefix("Упражнение ").replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                        }

                        // --- В чём измеряется результат ---
                        if (RunUp.isExercise(name)) {
                            Text(
                                text = "Вместо результата в тренировке открывается окно попыток: попал или нет, сколько стоп, на сколько отнёс или поднёс отметку.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BuilderLabel("Результат измеряется в")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    ResultUnit.KG to "кг", ResultUnit.M to "метрах", ResultUnit.CM to "см",
                                    ResultUnit.SECONDS to "секундах", ResultUnit.REPS to "повторениях"
                                ).forEach { (u, label) ->
                                    FilterChip(
                                        selected = unit == u,
                                        onClick = { unit = u; unitTouched = true },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = cs.outlineVariant)

                        // --- Как будет выглядеть ---
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            BuilderLabel("В тренировках и рекордах")
                            Text(styledTitle(preview, name.trim().ifBlank { "Название" }), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                            Text(
                                text = "Шиповки, инвентарь и одна нога ведут отдельные рекорды — результаты с ними не смешиваются с обычными.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.size(24.dp))
                    }
                }

                // Блок «в руке» во время перетаскивания
                dragBlock?.let { b ->
                    val halfW = with(density) { 60.dp.toPx() }
                    val halfH = with(density) { 22.dp.toPx() }
                    BlockChip(
                        block = b,
                        highlighted = overZone,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (dragPos.x - rootTopLeft.x - halfW).roundToInt(),
                                    (dragPos.y - rootTopLeft.y - halfH).roundToInt()
                                )
                            }
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun BuilderLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Блок в палитре: перетаскивается в поле упражнения; нажатие — добавить или убрать. */
@Composable
private fun PaletteBlock(
    block: BuilderBlock,
    added: Boolean,
    dragging: Boolean,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    BlockChip(
        block = block,
        added = added,
        faded = dragging,
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .pointerInput(block) {
                detectDragGestures(
                    onDragStart = { local -> onDragStart(bounds.topLeft + local) },
                    onDrag = { change, delta ->
                        change.consume()
                        onDrag(delta)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
            .clickable(onClick = onTap)
    )
}

@Composable
private fun BlockChip(
    block: BuilderBlock,
    modifier: Modifier = Modifier,
    added: Boolean = false,
    faded: Boolean = false,
    highlighted: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val container = when {
        highlighted -> cs.primary
        added -> cs.surfaceContainerHigh
        else -> cs.secondaryContainer
    }
    val content = when {
        highlighted -> cs.onPrimary
        added -> cs.onSurfaceVariant
        else -> cs.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = container.copy(alpha = if (faded) 0.4f else 1f),
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.DragIndicator, contentDescription = null, modifier = Modifier.size(16.dp))
            Icon(block.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Column {
                Text(block.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = if (added) "добавлен · нажмите, чтобы убрать" else block.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun BlockSettings(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerLow) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(cs.secondaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = cs.onSecondaryContainer, modifier = Modifier.size(16.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(4.dp))
            }
            content()
        }
    }
}
