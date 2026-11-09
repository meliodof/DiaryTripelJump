package com.example.diarytripeljump.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

/** Вопрос перед действием: что спросить и что сделать после «да». */
class ConfirmRequest(
    val title: String,
    val text: String,
    val confirmLabel: String,
    val destructive: Boolean,
    val action: () -> Unit
)

/** Одно окно подтверждения на всё приложение: любой экран может спросить перед удалением или изменением. */
class ConfirmController {
    var request by mutableStateOf<ConfirmRequest?>(null)
        internal set

    fun ask(title: String, text: String, confirmLabel: String, destructive: Boolean, action: () -> Unit) {
        request = ConfirmRequest(title, text, confirmLabel, destructive, action)
    }

    /** Сохранение изменений уже существующей записи. */
    fun save(what: String, action: () -> Unit) =
        ask("Сохранить изменения?", "$what — изменения будут сохранены.", "Сохранить", false, action)

    /** Удаление; [details] объясняет, что именно пропадёт. */
    fun delete(what: String, details: String, action: () -> Unit) =
        ask("Удалить $what?", details, "Удалить", true, action)
}

val LocalConfirm = staticCompositionLocalOf { ConfirmController() }

/** Подключает общее окно подтверждения; оборачивает весь интерфейс. */
@Composable
fun ConfirmHost(content: @Composable () -> Unit) {
    val controller = remember { ConfirmController() }
    CompositionLocalProvider(LocalConfirm provides controller) {
        content()
    }
    controller.request?.let { request ->
        AlertDialog(
            onDismissRequest = { controller.request = null },
            title = { Text(request.title, fontWeight = FontWeight.Bold) },
            text = { Text(request.text) },
            confirmButton = {
                Button(
                    onClick = {
                        controller.request = null
                        request.action()
                    },
                    colors = if (request.destructive) destructiveButtonColors() else androidx.compose.material3.ButtonDefaults.buttonColors()
                ) { Text(request.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = { controller.request = null }) { Text("Отмена") } }
        )
    }
}
