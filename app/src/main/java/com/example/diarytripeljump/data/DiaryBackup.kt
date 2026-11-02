package com.example.diarytripeljump.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ручная резервная копия дневника: файл базы данных целиком.
 *
 * Журнал БД — TRUNCATE, поэтому все данные лежат в одном файле. Копирование выполняется
 * внутри транзакции: пока файл читается, другие записи ждут, и копия получается целостной.
 */
object DiaryBackup {

    sealed interface ImportResult {
        data object Success : ImportResult
        data class Error(val message: String) : ImportResult
    }

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun suggestedFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return "triple-jump-diary-$date.db"
    }

    /** Сохраняет копию базы в выбранный пользователем файл. */
    suspend fun export(context: Context, database: TripleJumpDatabase, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(TripleJumpDatabase.DATABASE_NAME)
            database.withTransaction {
                context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    dbFile.inputStream().use { it.copyTo(out) }
                } ?: error("Не удалось открыть файл для записи")
            }
        }.isSuccess
    }

    private fun pendingFile(context: Context) = File(context.filesDir, "pending_restore.db")

    /**
     * Проверяет файл и откладывает его до следующего запуска: открытую базу Room подменять нельзя,
     * поэтому после успеха приложение перезапускается, а [applyPendingRestore] ставит копию на место.
     */
    suspend fun import(context: Context, source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "import.db")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            } ?: return@withContext ImportResult.Error("Не удалось прочитать файл")

            validate(temp)?.let { return@withContext ImportResult.Error(it) }

            temp.copyTo(pendingFile(context), overwrite = true)
            ImportResult.Success
        } catch (e: Exception) {
            ImportResult.Error("Ошибка восстановления: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            temp.delete()
        }
    }

    /** Вызывается при старте до открытия базы. Текущая база сохраняется рядом как .bak на случай отката. */
    fun applyPendingRestore(context: Context) {
        val pending = pendingFile(context)
        if (!pending.exists()) return
        val dbFile = context.getDatabasePath(TripleJumpDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.copyTo(File(dbFile.path + ".bak"), overwrite = true)
        File(dbFile.path + "-journal").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        pending.copyTo(dbFile, overwrite = true)
        pending.delete()
    }

    /** null — файл годится; иначе текст ошибки для пользователя. */
    private fun validate(file: File): String? {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != header.size || !header.contentEquals(SQLITE_HEADER)) return "Это не файл резервной копии дневника"

        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.version
            val hasTables = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('training_sessions','exercise_results','triple_jump_results')",
                null
            ).use { c -> c.moveToFirst() && c.getInt(0) == 3 }
            when {
                !hasTables -> "В файле нет данных дневника"
                version < TripleJumpDatabase.MIN_MIGRATABLE_VERSION -> "Копия сделана слишком старой версией приложения"
                version > TripleJumpDatabase.CURRENT_VERSION -> "Копия сделана более новой версией приложения — обновите приложение"
                else -> null
            }
        }
    }
}
