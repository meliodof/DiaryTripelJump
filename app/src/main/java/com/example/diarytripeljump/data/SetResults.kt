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

    /** «3 × 5» или «5 + 5 + 3», если повторений в подходах поровну не было. */
    fun label(plan: List<Int>): String =
        if (plan.distinct().size == 1) "${plan.size} × ${plan.first()}" else plan.joinToString(" + ")

    /** Коротко для карточки: «3 × 5 по 100», «5 + 3: 100, 110»; иначе все попытки. */
    fun summary(results: List<Double>, plan: List<Int>, format: (Double) -> String): String {
        val groups = split(results, plan)
        if (groups != null) {
            if (results.distinct().size == 1) return "${label(plan)} по ${format(results.first())}"
            if (groups.all { it.distinct().size == 1 }) return "${label(plan)}: " + groups.joinToString(", ") { format(it.first()) }
        }
        return results.joinToString(" · ") { format(it) }
    }
}
