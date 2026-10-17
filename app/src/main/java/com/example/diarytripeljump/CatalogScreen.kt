package com.example.diarytripeljump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diarytripeljump.data.Exercise
import com.example.diarytripeljump.data.RecordsEngine
import com.example.diarytripeljump.ui.components.AppCard
import com.example.diarytripeljump.ui.components.AppScreenScaffold
import com.example.diarytripeljump.ui.components.DropdownFilterChip
import com.example.diarytripeljump.ui.components.EmptyState
import com.example.diarytripeljump.ui.components.IconBadge
import com.example.diarytripeljump.ui.components.MenuAction
import com.example.diarytripeljump.ui.components.OverflowMenu
import com.example.diarytripeljump.ui.components.SegmentedControl
import com.example.diarytripeljump.ui.components.StatusPill
import com.example.diarytripeljump.ui.components.ToggleChip
import com.example.diarytripeljump.ui.components.destructiveButtonColors
import com.example.diarytripeljump.ui.components.pluralRu
import com.example.diarytripeljump.ui.components.screenContentPadding

private val CategoryFilterOptions = listOf("Все виды", "Беговые", "Техника", "Силовые", "Прыжковые", "ОФП")

private fun matchesCategory(ex: Exercise, filter: Int): Boolean {
    val c = ex.categoryName?.lowercase() ?: ""
    return when (filter) {
        1 -> c.contains("бег")
        2 -> c.contains("техник") || c.contains("тройн")
        3 -> c.contains("силов")
        4 -> c.contains("прыжк")
        5 -> c.contains("офп")
        else -> true
    }
}

/** Вкладка «Каталог»: переключатель «Упражнения / Комплексы». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel,
    onOpenProfile: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val rawExercises by viewModel.allExercises.collectAsState()
    val complexes by viewModel.complexes.collectAsState()
    val athleteProfile by viewModel.athleteProfile.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()

    val exercises = remember(rawExercises) { rawExercises.distinctBy { it.name.trim().lowercase() } }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var categoryFilter by rememberSaveable { mutableIntStateOf(0) }

    var showExerciseEditor by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var exerciseToDelete by remember { mutableStateOf<Exercise?>(null) }
    var showInventoryDialog by remember { mutableStateOf(false) }

    var showComplexEditor by remember { mutableStateOf(false) }
    var editingComplex by remember { mutableStateOf<ComplexUi?>(null) }
    var complexToDelete by remember { mutableStateOf<ComplexUi?>(null) }

    val filtered = remember(exercises, categoryFilter) { exercises.filter { matchesCategory(it, categoryFilter) } }

    AppScreenScaffold(
        modifier = modifier,
        title = "Каталог",
        avatarUri = athleteProfile?.avatarUriString,
        onOpenProfile = onOpenProfile,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (tab == 0) {
                        editingExercise = null
                        showExerciseEditor = true
                    } else {
                        editingComplex = null
                        showComplexEditor = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (tab == 0) "Упражнение" else "Комплекс") },
                containerColor = cs.primary,
                contentColor = cs.onPrimary
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(inner, extraBottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SegmentedControl(
                    options = listOf("Упражнения (${exercises.size})", "Комплексы (${complexes.size})"),
                    selectedIndex = tab,
                    onSelect = { tab = it }
                )
            }

            if (tab == 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DropdownFilterChip(
                            title = "Все виды",
                            options = CategoryFilterOptions,
                            selectedIndex = categoryFilter,
                            onSelect = { categoryFilter = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${filtered.size} ${pluralRu(filtered.size, "упражнение", "упражнения", "упражнений")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.FitnessCenter,
                            title = "Упражнений нет",
                            message = "Добавьте упражнения — их можно выбирать в тренировках и собирать в комплексы.",
                            actionLabel = "Добавить упражнение",
                            onAction = { editingExercise = null; showExerciseEditor = true }
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { ex ->
                        ExerciseCatalogRow(
                            exercise = ex,
                            onEdit = { editingExercise = ex; showExerciseEditor = true },
                            onDelete = { exerciseToDelete = ex }
                        )
                    }
                }
            } else {
                if (complexes.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Layers,
                            title = "Комплексов пока нет",
                            message = "Объедините несколько упражнений в комплекс и добавляйте его в тренировку одним нажатием.",
                            actionLabel = "Создать комплекс",
                            onAction = { editingComplex = null; showComplexEditor = true }
                        )
                    }
                } else {
                    items(complexes, key = { it.complex.id }) { c ->
                        ComplexCard(
                            complex = c,
                            onEdit = { editingComplex = c; showComplexEditor = true },
                            onDelete = { complexToDelete = c }
                        )
                    }
                }
            }
        }
    }

    if (showExerciseEditor) {
        val target = editingExercise
        ExerciseEditorDialog(
            initial = target,
            inventoryItems = inventoryItems,
            onCreateInventory = { showInventoryDialog = true },
            onDismiss = { showExerciseEditor = false },
            onConfirm = { cfg ->
                if (target == null) {
                    viewModel.saveExerciseToDatabase(
                        name = cfg.name,
                        categoryName = cfg.category,
                        defaultUnit = getInferredUnitForExercise(cfg.name),
                        isSingleLeg = cfg.isSingleLeg,
                        legSide = cfg.legSide,
                        inventoryName = cfg.inventoryName,
                        inventoryUnit = cfg.inventoryUnit,
                        inventoryValue = cfg.inventoryValue
                    )
                } else {
                    viewModel.updateExercise(
                        exercise = target,
                        name = cfg.name,
                        categoryName = cfg.category,
                        isSingleLeg = cfg.isSingleLeg,
                        legSide = cfg.legSide,
                        inventoryName = cfg.inventoryName,
                        inventoryUnit = cfg.inventoryUnit,
                        inventoryValue = cfg.inventoryValue
                    )
                }
                showExerciseEditor = false
            }
        )
    }

    if (showInventoryDialog) {
        CreateInventoryDialog(
            onDismiss = { showInventoryDialog = false },
            onConfirm = { name, unit ->
                viewModel.addInventory(name, unit)
                showInventoryDialog = false
            }
        )
    }

    exerciseToDelete?.let { target ->
        DeleteExerciseDialog(
            name = target.name,
            onDismiss = { exerciseToDelete = null },
            onConfirm = {
                viewModel.deleteExerciseFromCatalog(target)
                exerciseToDelete = null
            }
        )
    }

    if (showComplexEditor) {
        ComplexEditorDialog(
            initial = editingComplex,
            allExercises = exercises,
            onDismiss = { showComplexEditor = false },
            onSave = { name, description, ids ->
                viewModel.saveComplex(editingComplex?.complex?.id, name, description, ids)
                showComplexEditor = false
            }
        )
    }

    complexToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { complexToDelete = null },
            title = { Text("Удалить комплекс?", fontWeight = FontWeight.Bold) },
            text = { Text("Комплекс «${target.complex.name}» будет удалён. Сами упражнения и прошлые тренировки не изменятся.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteComplex(target.complex)
                        complexToDelete = null
                    },
                    colors = destructiveButtonColors()
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { complexToDelete = null }) { Text("Отмена") } }
        )
    }
}

/** Подтверждение удаления упражнения из справочника. */
@Composable
fun DeleteExerciseDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить упражнение?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "«$name» будет удалено из справочника и из всех комплексов. " +
                    "Результаты в прошлых тренировках и рекорды сохранятся."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = destructiveButtonColors()) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun ExerciseCatalogRow(exercise: Exercise, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Default.FitnessCenter, tint = cs.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val tags = buildList {
                    exercise.categoryName?.let { add(it) }
                    if (exercise.isSingleLeg) add(RecordsEngine.legSuffix(true, exercise.legSide).removePrefix(" · "))
                    RecordsEngine.inventoryLabel(exercise.inventoryName, exercise.inventoryUnit, exercise.inventoryValue)?.let { add(it) }
                }
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OverflowMenu(
                actions = listOf(
                    MenuAction("Редактировать", Icons.Default.Edit, onClick = onEdit),
                    MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDelete)
                )
            )
        }
    }
}

@Composable
private fun ComplexCard(complex: ComplexUi, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val n = complex.exercises.size
    AppCard {
        Column(modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = Icons.Default.Layers, tint = cs.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(complex.complex.name, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        text = "$n ${pluralRu(n, "упражнение", "упражнения", "упражнений")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                OverflowMenu(
                    actions = listOf(
                        MenuAction("Редактировать", Icons.Default.Edit, onClick = onEdit),
                        MenuAction("Удалить", Icons.Default.DeleteOutline, destructive = true, onClick = onDelete)
                    )
                )
            }
            if (!complex.complex.description.isNullOrBlank()) {
                Text(
                    text = complex.complex.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp)
                )
            }
            if (complex.exercises.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp, end = 12.dp), color = cs.outlineVariant)
                Column(
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    complex.exercises.forEachIndexed { i, ex ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(text = "${i + 1}", contentColor = cs.onSecondaryContainer, containerColor = cs.secondaryContainer)
                            Text(ex.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** Создание и редактирование комплекса: название, описание, состав и порядок упражнений. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplexEditorDialog(
    initial: ComplexUi?,
    allExercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String?, exerciseIds: List<Long>) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial?.complex?.name ?: "") }
    var description by remember { mutableStateOf(initial?.complex?.description ?: "") }
    var selected by remember { mutableStateOf<List<Long>>(initial?.exercises?.map { it.id } ?: emptyList()) }
    var showError by remember { mutableStateOf(false) }

    val byId = remember(allExercises) { allExercises.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый комплекс" else "Редактировать комплекс", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название комплекса") },
                    isError = showError && name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Состав (${selected.size})", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                if (selected.isEmpty()) {
                    Text(
                        "Выберите упражнения ниже — порядок можно менять стрелками.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                selected.forEachIndexed { i, id ->
                    val ex = byId[id] ?: return@forEachIndexed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(text = "${i + 1}", contentColor = cs.onSecondaryContainer, containerColor = cs.secondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ex.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            enabled = i > 0,
                            onClick = { selected = selected.toMutableList().also { java.util.Collections.swap(it, i, i - 1) } },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Выше") }
                        IconButton(
                            enabled = i < selected.lastIndex,
                            onClick = { selected = selected.toMutableList().also { java.util.Collections.swap(it, i, i + 1) } },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ниже") }
                        IconButton(
                            onClick = { selected = selected.filter { it != id } },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.Close, contentDescription = "Убрать") }
                    }
                }
                if (showError && selected.size < 2) {
                    Text("Добавьте минимум два упражнения", style = MaterialTheme.typography.bodySmall, color = cs.error)
                }

                HorizontalDivider(color = cs.outlineVariant)
                Text("Упражнения", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allExercises.forEach { ex ->
                        val isSel = ex.id in selected
                        ToggleChip(
                            label = ex.name,
                            selected = isSel,
                            onClick = { selected = if (isSel) selected.filter { it != ex.id } else selected + ex.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || selected.size < 2) showError = true
                else onSave(name.trim(), description.trim().ifBlank { null }, selected)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
