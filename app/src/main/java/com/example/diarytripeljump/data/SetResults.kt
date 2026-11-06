package com.example.diarytripeljump.data

/**
 * Результаты упражнения идут плоским списком: сначала все повторения первого подхода, затем второго.
 * В подходах может быть разное число повторений, поэтому границы задаёт план [repsBySet]: 5, 5, 3.
 * Если весь подход или все подходы выполнены с одним весом, значение просто повторяется,
 * поэтому рекорды и история работают так же, как при вводе каждого раза отдельно.
 */
object SetResults {
    const val EACH = 0
    const val PER_SET = 1
    const val ALL = 2

    /** Повторения по подходам из строки «5,5,3»; null — во всех подходах поровну. */
    fun parseReps(text: String?): List<Int>? =
        text?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it > 0 }?.takeIf { it.isNotEmpty() }

    /** План подходов записи; пустой — подходы не указаны. */
    fun repsBySet(sets: Int?, reps: Int?, perSet: String?): List<Int> =
        parseReps(perSet) ?: if (sets != null && reps != null && sets > 0 && reps > 0) List(sets) { reps } else emptyList()

    private fun split(results: List<Double>, plan: List<Int>): List<List<Double>>? {
        if (plan.isEmpty() || plan.any { it < 1 } || results.size != plan.sum()) return null
        var start = 0
        return plan.map { count -> results.subList(start, start + count).also { start += count } }
    }

    /** Как были введены результаты: отдельно, по одному на подход или один на все подходы. */
    fun sameValueMode(results: List<Double>, plan: List<Int>): Int {
        val groups = split(results, plan) ?: return EACH
        return when {
            results.any { it <= 0 } -> EACH
            results.size > 1 && results.distinct().size == 1 -> ALL
            plan.any { it > 1 } && groups.all { it.distinct().size == 1 } -> PER_SET
            else -> EACH
        }
    }

    /** По каждому подходу: все ли его повторения выполнены с одним значением. Подход из одного повторения — нет. */
    fun sameBySet(results: List<Double>, plan: List<Int>): List<Boolean> {
        val groups = split(results, plan) ?: return List(plan.size) { false }
        return groups.map { it.size > 1 && it.first() > 0 && it.distinct().size == 1 }
    }

    /** Один результат на подход: при переходе от повторений к секундам берётся первый заполненный. */
    fun toPerSet(results: List<Double>, plan: List<Int>): List<Double> =
        split(results, plan)?.map { group -> group.firstOrNull { it > 0 } ?: 0.0 } ?: results

    /** Результат подхода на каждое повторение: при переходе от секунд к повторениям. */
    fun toPerRep(results: List<Double>, plan: List<Int>): List<Double> =
        if (results.size == plan.size) results.flatMapIndexed { i, value -> List(plan[i].coerceAtLeast(1)) { value } } else results

    /** «3 × 5» или «5 + 5 + 3», если повторений в подходах поровну не было; с [timed] значения — секунды. */
    fun label(plan: List<Int>, timed: Boolean = false): String {
        val text = if (plan.distinct().size == 1) "${plan.size} × ${plan.first()}" else plan.joinToString(" + ")
        return if (timed) "$text сек" else text
    }

    /** Полная расшифровка для окна подробностей: «Подход 1: 2 × 80 кг», «Подход 2: 30 сек, 100 кг». */
    fun setLines(results: List<Double>, plan: List<Int>, format: (Double) -> String, timed: Boolean = false): List<String> {
        if (results.none { it > 0 }) {
            return plan.mapIndexed { i, count -> "Подход ${i + 1}: $count ${if (timed) "сек" else "раз"}" }
        }
        if (timed && results.size == plan.size) {
            return plan.mapIndexed { i, seconds ->
                "Подход ${i + 1}: $seconds сек" + results[i].takeIf { it > 0 }?.let { ", ${format(it)}" }.orEmpty()
            }
        }
        val groups = split(results, plan)
            ?: return results.filter { it > 0 }.mapIndexed { i, value -> "Попытка ${i + 1}: ${format(value)}" }
        return groups.mapIndexed { i, group ->
            val filled = group.filter { it > 0 }
            val text = when {
                filled.isEmpty() -> "не заполнен"
                filled.size > 1 && filled.distinct().size == 1 -> "${filled.size} × ${format(filled.first())}"
                else -> filled.joinToString(", ") { format(it) }
            }
            "Подход ${i + 1}: $text"
        }
    }

    /** Коротко для карточки: «3 × 5 по 100», «5 + 3: 100, 110», «3 × 100, 105 · 110»; иначе все попытки. */
    fun summary(results: List<Double>, plan: List<Int>, format: (Double) -> String, timed: Boolean = false): String {
        if (timed) {
            return when {
                results.size != plan.size -> results.joinToString(" · ") { format(it) }
                results.distinct().size == 1 -> "${label(plan, true)} по ${format(results.first())}"
                else -> "${label(plan, true)}: " + results.joinToString(", ") { format(it) }
            }
        }
        val groups = split(results, plan)
        if (groups != null) {
            if (results.distinct().size == 1) return "${label(plan)} по ${format(results.first())}"
            if (groups.all { it.distinct().size == 1 }) return "${label(plan)}: " + groups.joinToString(", ") { format(it.first()) }
            if (groups.any { it.size > 1 && it.distinct().size == 1 }) {
                return groups.joinToString(", ") { group ->
                    if (group.size > 1 && group.distinct().size == 1) "${group.size} × ${format(group.first())}"
                    else group.joinToString(" · ") { format(it) }
                }
            }
        }
        return results.joinToString(" · ") { format(it) }
    }
}
