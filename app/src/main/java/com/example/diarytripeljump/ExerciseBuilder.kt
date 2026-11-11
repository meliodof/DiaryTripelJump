package com.example.diarytripeljump

import com.example.diarytripeljump.ui.components.coachTarget
import com.example.diarytripeljump.data.HelpTopic
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import com.example.diarytripeljump.data.InventorySet
import com.example.diarytripeljump.data.InventoryUse
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
import com.example.diarytripeljump.data.EntryRules
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.InventoryItem
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.RunUp
import com.example.diarytripeljump.data.TitleGrammar
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
    inventoryActions: InventoryActions,
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
            inventoryValue = it.inventoryValue, inSpikes = it.inSpikes, unit = it.defaultUnit, titleLayout = it.titleLayout
        )
    }

    // название до свойств и его продолжение после них: «Прыжки» + в шиповках + «с разбега»
    val startSplit = remember { splitName(start?.name.orEmpty(), start?.titleLayout) }
    var name by remember { mutableStateOf(startSplit.first) }
    var nameAfter by remember { mutableStateOf(startSplit.second) }
    var category by remember { mutableStateOf(start?.category ?: "Силовое упражнение") }
    var unit by remember { mutableStateOf(start?.unit ?: ResultUnit.KG) }
    // пока единицу не выбрали вручную, она подсказывается по названию (бег — секунды, присед — кг…)
    var unitTouched by remember { mutableStateOf(start != null) }
    var inSpikes by remember { mutableStateOf(start?.inSpikes ?: false) }
    var singleLeg by remember { mutableStateOf(start?.isSingleLeg ?: false) }
    var legSide by remember { mutableStateOf(start?.legSide) }
    // снарядов в упражнении может быть несколько, у каждого свой вес или размер
    val picked = remember {
        mutableStateListOf<PickedInventory>().apply {
            InventorySet.decode(start?.inventoryName, start?.inventoryUnit, start?.inventoryValue).forEach {
                add(PickedInventory(it.name, it.unit, it.value?.let { v -> formatNumber(v).replace(',', '.') } ?: ""))
            }
        }
    }
    var hasInventory by remember { mutableStateOf(picked.isNotEmpty()) }
    var showErrors by remember { mutableStateOf(false) }
    var showCreateInventory by remember { mutableStateOf(false) }
    var showInventoryManager by remember { mutableStateOf(false) }
    var showTitleLayout by remember { mutableStateOf(false) }
    // только что созданный снаряд сразу выбирается, чтобы можно было указать его вес или размер
    var knownInventory by remember { mutableStateOf(inventoryItems.map { it.id }.toSet()) }
    LaunchedEffect(inventoryItems) {
        inventoryItems.firstOrNull { it.id !in knownInventory }?.let { fresh ->
            hasInventory = true
            if (picked.none { it.matches(fresh) }) picked.add(PickedInventory(fresh.name, fresh.unit, ""))
        }
        knownInventory = inventoryItems.map { it.id }.toSet()
    }
    // позиции ноги, снаряда и шиповок в названии
    var layout by remember { mutableStateOf(startSplit.third) }
    // штанга при результате в кг: вес снаряда и есть результат — отдельно не вводится
    fun isLoaded(p: PickedInventory) = RecordsEngine.isLoadedImplement(p.unit, unit)

    /** Выбранные снаряды в полях базы (см. [InventorySet]). */
    fun inventoryFields() = InventorySet.encode(
        if (!hasInventory) emptyList()
        else picked.map { InventoryUse(it.name, it.unit, if (isLoaded(it)) null else it.valueText.toDoubleOrNull()) }
    )

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
                if (!added) picked.clear()
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

    val inventoryMissing = hasInventory && picked.isEmpty()
    fun valueError(p: PickedInventory): String? = if (p.unit != null) EntryRules.inventoryValueError(p.valueText, p.unit) else null
    val inventoryValueError = if (hasInventory) picked.firstNotNullOfOrNull { valueError(it) } else null
    val canSave = name.isNotBlank() && !inventoryMissing && inventoryValueError == null
    val fields = inventoryFields()
    val inventoryText = RecordsEngine.variantInventory(fields.name, fields.unit, fields.value, unit)
    val anyBlock = inSpikes || singleLeg || hasInventory
    // продолжение имеет смысл, только когда есть свойства, после которых оно идёт
    val tail = if (anyBlock) nameAfter.trim() else ""
    val fullName = joinName(name, tail)
    val savedLayout = layoutWithContinuation(layout, name, tail)
    val preview = RecordsEngine.exerciseTitle(
        joinName(name.ifBlank { "Название" }, tail), singleLeg, legSide, inSpikes,
        fields.name, fields.unit, fields.value, unit, layoutWithContinuation(layout, name.ifBlank { "Название" }, tail)
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
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        HelpIconButton(onClick = rememberHelp(HelpTopic.F_BUILDER), id = "builder.help")
                        Button(
                            onClick = {
                                if (!canSave) {
                                    showErrors = true
                                } else {
                                    onConfirm(
                                        ExerciseConfig(
                                            name = fullName,
                                            category = category,
                                            isSingleLeg = singleLeg,
                                            legSide = if (singleLeg) legSide else null,
                                            inventoryName = fields.name,
                                            inventoryUnit = fields.unit,
                                            inventoryValue = fields.value,
                                            inSpikes = inSpikes,
                                            // у разбега своя форма результата, единица не выбирается
                                            unit = if (RunUp.isExercise(fullName)) ResultUnit.CM else unit,
                                            titleLayout = savedLayout.takeIf { it != TitleGrammar.DEFAULT_LAYOUT }
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
                            modifier = Modifier.fillMaxWidth().coachTarget("builder.name")
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
                                    .coachTarget("builder.zone")
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
                                } else {
                                    // название можно продолжить после свойств: «… в шиповках с разбега»
                                    Text("+", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = nameAfter,
                                        onValueChange = { nameAfter = it },
                                        placeholder = { Text("дальше в названии…") },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.labelLarge,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.widthIn(min = 160.dp).coachTarget("builder.after")
                                    )
                                }
                            }
                        }

                        // --- Палитра блоков ---
                        Column(modifier = Modifier.coachTarget("builder.blocks"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(selected = legSide == null, onClick = { legSide = null }, label = { Text("Любая") })
                                    FilterChip(selected = legSide == "LEFT", onClick = { legSide = "LEFT" }, label = { Text("Левая") })
                                    FilterChip(selected = legSide == "RIGHT", onClick = { legSide = "RIGHT" }, label = { Text("Правая") })
                                    FilterChip(
                                        selected = legSide == TitleGrammar.ALTERNATE,
                                        onClick = { legSide = TitleGrammar.ALTERNATE },
                                        label = { Text("По очереди") }
                                    )
                                }
                            }
                        }
                        if (hasInventory) {
                            BlockSettings(title = "Инвентарь", icon = BuilderBlock.INVENTORY.icon) {
                                Text(
                                    "Можно выбрать несколько",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    inventoryItems.forEach { item ->
                                        val index = picked.indexOfFirst { it.matches(item) }
                                        FilterChip(
                                            selected = index >= 0,
                                            onClick = {
                                                if (index >= 0) picked.removeAt(index)
                                                else picked.add(PickedInventory(item.name, item.unit, ""))
                                            },
                                            label = { Text(item.name) },
                                            leadingIcon = if (index >= 0) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                            } else null
                                        )
                                    }
                                    AssistChip(
                                        onClick = { showCreateInventory = true },
                                        label = { Text("Новый") },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                                    )
                                    AssistChip(
                                        onClick = { showInventoryManager = true },
                                        label = { Text("Изменить") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) }
                                    )
                                }
                                if (showErrors && inventoryMissing) {
                                    Text("Выберите инвентарь или уберите блок", style = MaterialTheme.typography.bodySmall, color = cs.error)
                                }
                                // у инвентаря без единицы нет веса или размера
                                picked.forEachIndexed { i, p ->
                                    if (p.unit == null) return@forEachIndexed
                                    val error = valueError(p)
                                    OutlinedTextField(
                                        value = p.valueText,
                                        onValueChange = { v ->
                                            val text = EntryRules.cleanNumber(v)
                                            // постоянный вес: снаряд не штанга, и результат считается не в кг
                                            if (isLoaded(p) && text.isNotEmpty()) {
                                                val low = name.lowercase()
                                                val throwing = listOf("метан", "брос", "толк").any { low.contains(it) }
                                                unit = if (throwing) ResultUnit.M else ResultUnit.REPS
                                                unitTouched = true
                                            }
                                            picked[i] = p.copy(valueText = text)
                                        },
                                        label = { Text("${p.name}: вес или размер") },
                                        suffix = { Text(getUnitTitle(p.unit)) },
                                        isError = showErrors && error != null,
                                        supportingText = when {
                                            showErrors && error != null -> {
                                                { Text(error) }
                                            }
                                            isLoaded(p) -> {
                                                { Text("Для штанги оставьте пустым: её вес вводится в попытках как результат") }
                                            }
                                            else -> null
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // --- Где в названии стоят свойства: редкая настройка, по умолчанию скрыта ---
                        if (BuilderBlock.entries.any { isAdded(it) }) {
                            TextButton(onClick = { showTitleLayout = !showTitleLayout }) {
                                Text(if (showTitleLayout) "Скрыть порядок слов" else "Порядок слов в названии")
                            }
                            if (showTitleLayout) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BuilderLabel("Место в названии")
                                    listOf(
                                        Triple(0, "Нога", BuilderBlock.SINGLE_LEG),
                                        Triple(1, "Снаряд", BuilderBlock.INVENTORY),
                                        Triple(2, "Шиповки", BuilderBlock.SPIKES)
                                    ).filter { isAdded(it.third) }.forEach { (index, label, _) ->
                                        Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            listOf(
                                                TitleGrammar.AUTO to "Авто", TitleGrammar.START to "В начале",
                                                TitleGrammar.MIDDLE to "В середине", TitleGrammar.END to "В конце"
                                            ).forEach { (code, text) ->
                                                FilterChip(
                                                    selected = layout.getOrElse(index) { TitleGrammar.AUTO } == code,
                                                    onClick = {
                                                        layout = layout.padEnd(3, TitleGrammar.AUTO).toCharArray()
                                                            .also { it[index] = code }.concatToString()
                                                    },
                                                    label = { Text(text) }
                                                )
                                            }
                                        }
                                    }
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

    if (showCreateInventory) {
        CreateInventoryDialog(
            existing = inventoryItems,
            onDismiss = { showCreateInventory = false },
            onConfirm = { newName, newUnit ->
                inventoryActions.add(newName, newUnit)
                showCreateInventory = false
            }
        )
    }

    if (showInventoryManager) {
        InventoryManagerDialog(
            items = inventoryItems,
            actions = inventoryActions,
            onDismiss = { showInventoryManager = false },
            onChanged = { old, newName, newUnit ->
                // изменён выбранный снаряд: форма показывает его новые параметры
                val index = picked.indexOfFirst { it.matches(old) }
                if (index >= 0) {
                    val p = picked[index]
                    picked[index] = PickedInventory(newName, newUnit, if (newUnit != old.unit) "" else p.valueText)
                }
            }
        )
    }
}

private fun joinName(before: String, after: String): String = listOf(before.trim(), after.trim()).filter { it.isNotEmpty() }.joinToString(" ")

/**
 * Позиции свойств с продолжением названия: свойства, оставленные на «Авто», встают после слов [before],
 * а [after] идёт за ними. Без продолжения позиции не меняются.
 */
private fun layoutWithContinuation(layout: String, before: String, after: String): String {
    if (after.isBlank()) return layout
    val split = TitleGrammar.afterWords(before.trim().split(Regex("\\s+")).count { it.isNotEmpty() })
    return layout.padEnd(3, TitleGrammar.AUTO).take(3).map { if (it == TitleGrammar.AUTO) split else it }.joinToString("")
}

/**
 * Обратно: сохранённое название и позиции — в название до свойств, продолжение и позиции для формы.
 * Позиции «после N слов» в форме снова показываются как «Авто».
 */
private fun splitName(name: String, layout: String?): Triple<String, String, String> {
    val positions = layout ?: TitleGrammar.DEFAULT_LAYOUT
    val count = TitleGrammar.continuationSplit(positions)
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (count == null || count >= words.size) {
        return Triple(name, "", positions.map { if (it in '1'..'9') TitleGrammar.END else it }.joinToString(""))
    }
    return Triple(
        words.take(count).joinToString(" "),
        words.drop(count).joinToString(" "),
        positions.map { if (it in '1'..'9') TitleGrammar.AUTO else it }.joinToString("")
    )
}

/** Снаряд, выбранный в конструкторе; значение — как введено в поле. */
private data class PickedInventory(val name: String, val unit: ResultUnit?, val valueText: String) {
    fun matches(item: InventoryItem) = name.equals(item.name, ignoreCase = true) && unit == item.unit
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
