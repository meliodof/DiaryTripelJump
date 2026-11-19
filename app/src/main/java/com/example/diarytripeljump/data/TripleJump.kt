package com.example.diarytripeljump.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Вариант тройного прыжка в форме тренировки. */
data class TripleVariant(val name: String, val inSpikes: Boolean) {
    val label: String get() = if (name == TripleJump.SHORT) (if (inSpikes) "С короткого в шиповках" else "С короткого без шиповок") else "С полного разбега"
}

/**
 * Тройной прыжок — отдельная категория, как «По разбегу»: в каждой попытке вводятся и результат, и попадание
 * в брусок с переносом отметки. Результаты попыток лежат в setResultsJson, разбег — в runUpJson с тем же
 * номером попытки (0 в результате — незачёт). Варианты не берутся из каталога, а встроены в форму тренировки.
 */
object TripleJump {
    const val SHORT = "Тройной с короткого разбега"
    const val FULL = "Тройной с полного разбега"
    const val CATEGORY = "Тройной прыжок"

    /** С полного разбега прыгают в шиповках всегда, поэтому вариант один и в названии шиповки не пишутся. */
    val variants = listOf(TripleVariant(SHORT, false), TripleVariant(SHORT, true), TripleVariant(FULL, false))

    fun isExercise(name: String?): Boolean = name?.trim().let { it.equals(SHORT, true) || it.equals(FULL, true) }

    /** Тройной с короткого разбега: «с короткого», «с 6 шагов», «с 8 беговых шагов». */
    fun isShort(name: String): Boolean {
        val n = name.lowercase()
        return "коротк" in n || Regex("\\d+\\s*(бегов\\S*\\s*)?шаг").containsMatchIn(n)
    }

    /**
     * Прежнее упражнение «тройной» из каталога или тренировки, которое переносится в новую категорию:
     * результат в метрах, на двух ногах, без снаряда. «Тройной на одной ноге» и упражнения со снарядом —
     * другие упражнения и остаются как есть.
     */
    fun isLegacy(name: String, unit: String?, singleLeg: Boolean, inventory: String?): Boolean =
        !isExercise(name) && "тройн" in name.lowercase() && unit == ResultUnit.M.name && !singleLeg && inventory.isNullOrBlank()

    fun canonical(name: String): String = if (isShort(name)) SHORT else FULL

    /** С полного разбега — всегда один вариант без отдельного признака шиповок. */
    fun spikesFor(canonical: String, inSpikes: Boolean): Boolean = canonical == SHORT && inSpikes

    /**
     * Миграция 27 → 28: прежние упражнения «тройной» удаляются из каталога, а их результаты остаются на своих
     * местах в тренировках и переходят в новые варианты — с ними переносятся рекорды, цели и ручные правки
     * рекордов (они привязаны к названию). Комплексы получают новый вариант вместо удалённого упражнения.
     */
    fun migrateLegacy(db: SupportSQLiteDatabase) {
        data class Old(val id: Long, val name: String, val spikes: Boolean, val favorite: Boolean)
        val old = mutableListOf<Old>()
        db.query("SELECT id, name, defaultUnit, isSingleLeg, inventoryName, inSpikes, isFavorite FROM exercises").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (isLegacy(name, c.getString(2), c.getInt(3) != 0, c.getString(4))) {
                    old += Old(c.getLong(0), name, c.getInt(5) != 0, c.getInt(6) != 0)
                }
            }
        }
        // вариант в каталоге — только если на него ссылается комплекс; в списке упражнений варианты не показываются
        val variantIds = HashMap<Pair<String, Boolean>, Long>()
        fun variantId(name: String, spikes: Boolean, favorite: Boolean): Long = variantIds.getOrPut(name to spikes) {
            db.query("SELECT id FROM exercises WHERE name = ? AND inSpikes = ?", arrayOf<Any>(name, if (spikes) 1 else 0)).use { c ->
                if (c.moveToFirst()) return@getOrPut c.getLong(0)
            }
            db.execSQL(
                "INSERT INTO exercises (name, categoryName, categoryColorHex, defaultUnit, isSingleLeg, isFavorite, inSpikes) VALUES (?, ?, '#1E4D3B', 'M', 0, ?, ?)",
                arrayOf<Any>(name, CATEGORY, if (favorite) 1 else 0, if (spikes) 1 else 0)
            )
            db.query("SELECT last_insert_rowid()").use { c -> c.moveToFirst(); c.getLong(0) }
        }
        for (o in old) {
            val name = canonical(o.name)
            val spikes = spikesFor(name, o.spikes)
            var inComplex = false
            db.query("SELECT COUNT(*) FROM complex_exercises WHERE exerciseId = ?", arrayOf<Any>(o.id)).use { c -> c.moveToFirst(); inComplex = c.getInt(0) > 0 }
            if (inComplex) {
                db.execSQL("UPDATE complex_exercises SET exerciseId = ? WHERE exerciseId = ?", arrayOf<Any>(variantId(name, spikes, o.favorite), o.id))
            }
            db.execSQL("DELETE FROM exercises WHERE id = ?", arrayOf<Any>(o.id))
        }

        // результаты тренировок: место в тренировке, попытки и заметки не меняются, меняется только упражнение
        data class Res(val id: Long, val name: String, val spikes: Boolean)
        val results = mutableListOf<Res>()
        db.query("SELECT id, exerciseName, resultUnit, isSingleLeg, inventoryName, inSpikes FROM exercise_results").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (isLegacy(name, c.getString(2), c.getInt(3) != 0, c.getString(4))) results += Res(c.getLong(0), name, c.getInt(5) != 0)
            }
        }
        val renamed = HashMap<String, String>()                      // старое название → новое (для ключей рекордов)
        for (r in results) {
            val name = canonical(r.name)
            val spikes = spikesFor(name, r.spikes)
            val id = variantIds[name to spikes] ?: 0L
            db.execSQL(
                "UPDATE exercise_results SET exerciseName = ?, exerciseId = ?, inSpikes = ? WHERE id = ?",
                arrayOf<Any>(name, id, if (spikes) 1 else 0, r.id)
            )
            renamed[r.name] = name
        }
        // ручные тренировочные записи рекордов (не старты и не антирекорды) сливаются с упражнением — тоже переносятся
        db.query("SELECT id, eventName, resultUnit, isSingleLeg, inventoryName, inSpikes FROM triple_jump_results WHERE isCompetition = 0 AND isAntiRecord = 0").use { c ->
            val rows = mutableListOf<Res>()
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (isLegacy(name, c.getString(2), c.getInt(3) != 0, c.getString(4))) rows += Res(c.getLong(0), name, c.getInt(5) != 0)
            }
            rows
        }.forEach { r ->
            val name = canonical(r.name)
            db.execSQL("UPDATE triple_jump_results SET eventName = ?, inSpikes = ? WHERE id = ?", arrayOf<Any>(name, if (spikesFor(name, r.spikes)) 1 else 0, r.id))
            renamed[r.name] = name
        }
        old.forEach { renamed[it.name] = canonical(it.name) }
        // цели и ручные правки рекордов: ключ «ex|название|нога|единица[|spikes]»
        for ((from, to) in renamed) {
            val a = "ex|${from.trim().lowercase()}|"
            val b = "ex|${to.lowercase()}|"
            for (table in listOf("exercise_goals", "record_overrides")) {
                val keys = mutableListOf<String>()
                db.query("SELECT recordKey FROM $table").use { c -> while (c.moveToNext()) c.getString(0)?.let { if (it.startsWith(a)) keys += it } }
                keys.forEach { k ->
                    var nk = b + k.removePrefix(a)
                    if (to == FULL) nk = nk.removeSuffix("|spikes")
                    db.execSQL("UPDATE OR REPLACE $table SET recordKey = ? WHERE recordKey = ?", arrayOf<Any>(nk, k))
                }
            }
            db.execSQL("UPDATE exercise_goals SET name = ?, displayName = ? WHERE name = ?", arrayOf<Any>(to, to, from))
        }
    }
}
