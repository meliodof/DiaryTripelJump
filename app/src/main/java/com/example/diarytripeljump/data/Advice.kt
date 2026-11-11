package com.example.diarytripeljump.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Насколько совет важен: риск показывается первым, хорошие новости — последними. */
enum class AdviceKind { RISK, CHANCE, GOOD }

/**
 * Совет по данным дневника.
 * [id] меняется вместе с ситуацией (неделя, рекорд, старт): скрытый совет вернётся, если ситуация новая.
 * [because] — на каких данных он основан: совет должен быть проверяемым, а не «просто так».
 * [route] — какой раздел открыть по нажатию; [recordKey] — какой рекорд открыть.
 */
data class Advice(
    val id: String,
    val kind: AdviceKind,
    val title: String,
    val text: String,
    val because: String,
    val route: String? = null,
    val recordKey: String? = null
)

/** Всё, по чему строятся советы. */
data class AdviceInput(
    val trainings: List<TrainingWithExercises>,
    val competitions: List<Competition>,
    val records: List<RecordSummary>,
    val goals: List<ExerciseGoal>,
    val body: List<BodyMeasurement>,
    val now: Long = System.currentTimeMillis()
)

/**
 * Советы по правилам: без интернета и нейросети, каждый можно проверить по своим данным.
 * Пороги собраны в [Limits], чтобы их было легко подправить. Тексты осторожные — это подсказка
 * по цифрам дневника, а не замена тренеру.
 */
object AdviceEngine {
    private const val DAY = 86_400_000L

    object Limits {
        /** Сколько последних попыток разбега смотреть и сколько их нужно для вывода. */
        const val RUN_UP_ATTEMPTS = 10
        const val RUN_UP_MIN = 4
        const val RUN_UP_DAYS = 30
        /** Доля заступов или недоступов, после которой стоит переносить отметку. */
        const val RUN_UP_SHARE = 0.5
        /** Доля точных попаданий, при которой разбег стабилен. */
        const val RUN_UP_STABLE = 0.7

        /** Рекорд в тройном держится столько дней, хотя прыжки были, — застой. */
        const val PLATEAU_DAYS = 42
        const val PLATEAU_MIN_JUMPS = 3

        /** Рост объёма за неделю против среднего трёх прошлых недель. */
        const val LOAD_GROWTH = 1.4
        const val LOAD_MIN_DELTA = 20

        /** Перерыв в тренировках, о котором стоит напомнить. */
        const val BREAK_DAYS = 10

        /** За сколько дней до старта говорить о подводке. */
        const val TAPER_DAYS = 7

        /** Пропусков за 30 дней. */
        const val SKIPS = 3

        /** Нужный темп к цели больше фактического во столько раз — цель под угрозой. */
        const val GOAL_PACE_RATIO = 2.0
        const val GOAL_LOOKBACK_WEEKS = 8

        /** Изменение веса за 30 дней, % от веса. */
        const val WEIGHT_SHARE = 0.03

        /** Сколько последних тренировок смотреть на жалобы в заметках и сколько упоминаний нужно. */
        const val NOTES_TRAININGS = 3
        const val NOTES_MENTIONS = 2

        /** Новый рекорд считается свежим столько дней. */
        const val FRESH_RECORD_DAYS = 7
    }

    /** Жалобы в заметках (текст в нижнем регистре); «больше» не путается с болью благодаря границам слова. */
    private val PAIN = Regex(
        "(?<![а-яё])(болит|болят|болело|болела|боль|болью|больно|травм[а-яё]*|тянет|ноет|растяж[а-яё]*|ушиб[а-яё]*|надрыв[а-яё]*)(?![а-яё])"
    )

    /** Как считается каждый совет — для раздела «Советы» в аналитике; пороги берутся из [Limits]. */
    val rules: List<Pair<String, String>>
        get() = listOf(
            "Разбег" to "Последние ${Limits.RUN_UP_ATTEMPTS} попыток за ${Limits.RUN_UP_DAYS} дней (нужно от ${Limits.RUN_UP_MIN}). " +
                "Заступ или недоступ в ${percent(Limits.RUN_UP_SHARE)} попыток и больше — перенести отметку; " +
                "${percent(Limits.RUN_UP_STABLE)} точных попаданий — разбег стабилен.",
            "Цели" to "Нужный темп в неделю сравнивается с фактическим за ${Limits.GOAL_LOOKBACK_WEEKS} недель: " +
                "если нужно в ${formatNumber(Limits.GOAL_PACE_RATIO)} раза больше — цель под угрозой.",
            "Застой" to "Рекорд в тройном на тренировках держится ${Limits.PLATEAU_DAYS / 7}+ недель, хотя прыжков было ${Limits.PLATEAU_MIN_JUMPS}+.",
            "Нагрузка" to "Повторения во всех подходах за неделю против среднего трёх прошлых недель: рост на " +
                "${percent(Limits.LOAD_GROWTH - 1)} и больше. Перерыв — ${Limits.BREAK_DAYS}+ дней без тренировок.",
            "Подводка к старту" to "За ${Limits.TAPER_DAYS} дней до старта: снижен ли объём, отдых в последние два дня.",
            "Пропуски" to "${Limits.SKIPS}+ пропуска за 30 дней, с самой частой причиной.",
            "Заметки" to "Слова «болит», «тянет», «травма» и похожие в ${Limits.NOTES_MENTIONS} из ${Limits.NOTES_TRAININGS} последних тренировок.",
            "Вес" to "Изменение на ${percent(Limits.WEIGHT_SHARE)} и больше за месяц.",
            "Рекорды" to "Рекорд, обновлённый за последние ${Limits.FRESH_RECORD_DAYS} дней."
        )

    private fun percent(share: Double): String = "${(share * 100).roundToInt()}%"

    fun build(input: AdviceInput): List<Advice> =
        listOfNotNull(
            painInNotes(input),
            loadJump(input),
            skips(input),
            runUp(input),
            taper(input),
            *goals(input).toTypedArray(),
            plateau(input),
            trainingBreak(input),
            weight(input),
            freshRecord(input)
        ).sortedBy { it.kind.ordinal }

    // --- Разбег ---

    private fun runUp(input: AdviceInput): Advice? {
        val from = input.now - Limits.RUN_UP_DAYS * DAY
        val fromTrainings = input.trainings.flatMap { t ->
            t.exerciseResults.filter { it.runUpJson != null && it.date >= from }.flatMap { r -> RunUp.decode(r.runUpJson).map { r.date to it } }
        }
        val fromStarts = input.competitions.filter { it.date in from..input.now }.flatMap { c ->
            (RunUp.decode(c.qualificationRunUpJson) + RunUp.decode(c.runUpJson)).map { c.date to it }
        }
        val attempts = (fromTrainings + fromStarts)
            .filter { it.second.boardFeet != null }
            .sortedByDescending { it.first }
            .take(Limits.RUN_UP_ATTEMPTS)
            .map { it.second.boardFeet!! }
        if (attempts.size < Limits.RUN_UP_MIN) return null

        val fouls = attempts.filter { it > 0 }
        val shorts = attempts.filter { it < 0 }
        val hits = attempts.count { it == 0.0 }
        val week = weekOf(input.now)
        val because = "Последние ${attempts.size} попыток разбега за ${Limits.RUN_UP_DAYS} дней: " +
            "заступов ${fouls.size}, недоступов ${shorts.size}, точно ${hits}."
        return when {
            fouls.size >= attempts.size * Limits.RUN_UP_SHARE -> Advice(
                id = "runup-foul-$week", kind = AdviceKind.RISK,
                title = "Частые заступы",
                text = "В ${fouls.size} из ${attempts.size} попыток заступ, в среднем на ${feet(fouls.average())}. " +
                    "Стоит попробовать отнести отметку примерно на столько же.",
                because = because, route = "trainings"
            )
            shorts.size >= attempts.size * Limits.RUN_UP_SHARE -> Advice(
                id = "runup-short-$week", kind = AdviceKind.CHANCE,
                title = "Недоступы до бруска",
                text = "В ${shorts.size} из ${attempts.size} попыток недоступ, в среднем на ${feet(abs(shorts.average()))}. " +
                    "Можно поднести отметку — так меньше теряется в длине прыжка.",
                because = because, route = "trainings"
            )
            hits >= attempts.size * Limits.RUN_UP_STABLE -> Advice(
                id = "runup-stable-$week", kind = AdviceKind.GOOD,
                title = "Разбег стабилен",
                text = "$hits из ${attempts.size} попыток точно в брусок. Отметку можно не трогать.",
                because = because, route = "trainings"
            )
            else -> null
        }
    }

    // --- Цели ---

    private fun goals(input: AdviceInput): List<Advice> = input.goals.mapNotNull { goal ->
        val record = input.records.firstOrNull { it.key == goal.recordKey } ?: return@mapNotNull null
        val lower = record.lowerIsBetter
        val reached = if (lower) record.best <= goal.targetValue else record.best >= goal.targetValue
        if (reached) {
            return@mapNotNull Advice(
                id = "goal-done-${goal.recordKey}-${goal.targetValue}", kind = AdviceKind.GOOD,
                title = "Цель достигнута",
                text = "${record.displayName}: ${formatRecordValue(record.best, record.unit)} — цель " +
                    "${formatRecordValue(goal.targetValue, record.unit)} выполнена. Можно поставить следующую.",
                because = "Рекорд ${formatRecordValue(record.best, record.unit)}, цель ${formatRecordValue(goal.targetValue, record.unit)}.",
                route = "records", recordKey = record.key
            )
        }
        val needed = RecordInsights.weeklyPace(record.best, goal.targetValue, goal.deadline, input.now) ?: return@mapNotNull null
        val actual = actualWeeklyPace(record, input.now)
        if (actual > 0 && needed <= actual * Limits.GOAL_PACE_RATIO) return@mapNotNull null
        val weeks = RecordInsights.weeksLeft(goal.deadline, input.now) ?: return@mapNotNull null
        Advice(
            id = "goal-risk-${goal.recordKey}-${weekOf(input.now)}", kind = AdviceKind.RISK,
            title = "Цель под угрозой",
            text = "${record.displayName}: чтобы успеть за $weeks нед., нужно улучшаться на " +
                "${RecordInsights.formatPace(needed, record.unit)} в неделю, а сейчас " +
                (if (actual > 0) RecordInsights.formatPace(actual, record.unit) else "прогресса нет") +
                ". Стоит пересмотреть срок или план.",
            because = "Рекорд ${formatRecordValue(record.best, record.unit)}, цель ${formatRecordValue(goal.targetValue, record.unit)}, " +
                "темп за ${Limits.GOAL_LOOKBACK_WEEKS} недель.",
            route = "records", recordKey = record.key
        )
    }

    /** Насколько улучшился лучший результат за последние недели, в среднем за неделю. */
    private fun actualWeeklyPace(record: RecordSummary, now: Long): Double {
        val from = now - Limits.GOAL_LOOKBACK_WEEKS * 7 * DAY
        val before = record.points.filter { it.date < from }.map { it.value }
        val recent = record.points.filter { it.date >= from }.map { it.value }
        if (before.isEmpty() || recent.isEmpty()) return 0.0
        val gain = if (record.lowerIsBetter) before.min() - recent.min() else recent.max() - before.max()
        return (gain / Limits.GOAL_LOOKBACK_WEEKS).coerceAtLeast(0.0)
    }

    // --- Застой в тройном ---

    private fun plateau(input: AdviceInput): Advice? {
        val jump = input.records.filter { RecordInsights.isMainJump(it) && !it.isCompetition }.maxByOrNull { it.best } ?: return null
        val held = RecordInsights.daysHeld(jump.bestDate, input.now)
        if (held < Limits.PLATEAU_DAYS) return null
        val recentJumps = jump.points.count { it.date >= input.now - Limits.PLATEAU_DAYS * DAY }
        if (recentJumps < Limits.PLATEAU_MIN_JUMPS) return null
        return Advice(
            id = "plateau-${jump.key}-${jump.best}", kind = AdviceKind.CHANCE,
            title = "Тройной стоит на месте",
            text = "Рекорд ${formatRecordValue(jump.best, jump.unit)} держится ${held / 7} нед., хотя прыжков было $recentJumps. " +
                "Возможно, стоит сменить акцент: прыжковые упражнения, скорость разбега, техника отдельных фаз.",
            because = "Рекорд от ${held} дн. назад, $recentJumps результатов за ${Limits.PLATEAU_DAYS} дней.",
            route = "records", recordKey = jump.key
        )
    }

    // --- Нагрузка ---

    /** Объём недели: повторения во всех подходах (при работе на время — подходы). */
    fun volume(trainings: List<TrainingWithExercises>, from: Long, to: Long): Int =
        trainings.filter { !it.training.isSkipped && it.training.date >= from && it.training.date < to }
            .sumOf { t ->
                t.exerciseResults.sumOf { r ->
                    val sets = (r.sets ?: 1).coerceAtLeast(1)
                    val perSet = SetResults.parseReps(r.repsPerSetJson)
                    when {
                        r.runUpJson != null -> RunUp.decode(r.runUpJson).size
                        r.isTimed -> sets
                        perSet != null -> perSet.sum()
                        else -> sets * (r.reps ?: 1).coerceAtLeast(1)
                    }
                }
            }

    private fun loadJump(input: AdviceInput): Advice? {
        val now = input.now
        val thisWeek = volume(input.trainings, now - 7 * DAY, now + 1)
        val before = (1..3).map { w -> volume(input.trainings, now - (w + 1) * 7 * DAY, now - w * 7 * DAY) }
        if (before.count { it > 0 } < 2) return null
        val average = before.average()
        if (thisWeek < average * Limits.LOAD_GROWTH || thisWeek - average < Limits.LOAD_MIN_DELTA) return null
        val growth = ((thisWeek / average - 1) * 100).roundToInt()
        return Advice(
            id = "load-${weekOf(now)}", kind = AdviceKind.RISK,
            title = "Нагрузка выросла на $growth%",
            text = "За неделю $thisWeek повторений против ${average.roundToInt()} в среднем раньше. " +
                "Резкий рост повышает риск перегрузки — добавь восстановление или распредели объём.",
            because = "Объём: повторения во всех подходах за 7 дней и за три недели до этого.",
            route = "trainings"
        )
    }

    private fun trainingBreak(input: AdviceInput): Advice? {
        val done = input.trainings.filter { !it.training.isSkipped && it.training.date <= input.now }
        val last = done.maxOfOrNull { it.training.date } ?: return null
        val days = ((input.now - last) / DAY).toInt()
        if (days < Limits.BREAK_DAYS || done.size < 3) return null
        return Advice(
            id = "break-$last", kind = AdviceKind.CHANCE,
            title = "Перерыв $days дн.",
            text = "Последняя тренировка была $days дней назад. После перерыва лучше начать с меньшего объёма и прыжков с короткого разбега.",
            because = "Дата последней записанной тренировки.",
            route = "trainings"
        )
    }

    // --- Подводка к старту ---

    private fun taper(input: AdviceInput): Advice? {
        val today = startOfDay(input.now)
        val next = input.competitions
            .filter { it.date >= today && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && it.status != CompetitionStatus.COMPLETED }
            .minByOrNull { it.date } ?: return null
        val days = ((startOfDay(next.date) - today) / DAY).toInt()
        if (days > Limits.TAPER_DAYS) return null
        val thisWeek = volume(input.trainings, input.now - 7 * DAY, input.now + 1)
        val before = (1..3).map { w -> volume(input.trainings, input.now - (w + 1) * 7 * DAY, input.now - w * 7 * DAY) }.average()
        val notReduced = before > 0 && thisWeek >= before
        val text = when {
            days == 0 -> "Старт сегодня. Разминка как обычно, без новых упражнений. Удачи!"
            days <= 2 -> "До старта $days дн. Время отдыха и короткой разминки с ускорениями, без тяжёлой силовой."
            notReduced -> "До старта $days дн., а объём не снижается ($thisWeek повторений за неделю). Обычно перед стартом объём уменьшают, сохраняя скорость."
            else -> "До старта $days дн., объём уже снижен — хорошо. Сохраняй скорость и короткие прыжковые упражнения."
        }
        return Advice(
            id = "taper-${next.id}-$days", kind = if (notReduced && days > 2) AdviceKind.RISK else AdviceKind.CHANCE,
            title = "Подводка: ${next.name}",
            text = text,
            because = "Дата старта и объём тренировок за последнюю неделю.",
            route = "competitions"
        )
    }

    // --- Пропуски ---

    private fun skips(input: AdviceInput): Advice? {
        val from = input.now - 30 * DAY
        val skipped = input.trainings.filter { it.training.isSkipped && it.training.date in from..input.now }
        if (skipped.size < Limits.SKIPS) return null
        val reason = skipped.mapNotNull { it.training.skipReason?.trim()?.takeIf { r -> r.isNotEmpty() } }
            .groupingBy { it.lowercase() }.eachCount().maxByOrNull { it.value }?.key
        return Advice(
            id = "skips-${weekOf(input.now)}", kind = AdviceKind.RISK,
            title = "${skipped.size} пропусков за месяц",
            text = (reason?.let { "Чаще всего причина — «$it». " } ?: "") +
                "Если пропуски из-за усталости или болезни, вернуться стоит с меньшей нагрузкой.",
            because = "Отметки о пропусках за 30 дней.",
            route = "trainings"
        )
    }

    // --- Заметки ---

    private fun painInNotes(input: AdviceInput): Advice? {
        val recent = input.trainings.filter { !it.training.isSkipped && it.training.date <= input.now }
            .sortedByDescending { it.training.date }
            .take(Limits.NOTES_TRAININGS)
        // текст приводится к нижнему регистру заранее: без UNICODE_CASE регистр кириллицы в шаблоне не учитывается
        fun notesOf(t: TrainingWithExercises) =
            (listOfNotNull(t.training.notes) + t.exerciseResults.mapNotNull { it.notes }).map { it.lowercase() }
        val mentions = recent.filter { t -> notesOf(t).any { PAIN.containsMatchIn(it) } }
        if (mentions.size < Limits.NOTES_MENTIONS) return null
        val word = mentions.flatMap { notesOf(it) }.firstNotNullOfOrNull { PAIN.find(it)?.value }
        return Advice(
            id = "pain-${mentions.first().training.id}", kind = AdviceKind.RISK,
            title = "Жалобы в заметках",
            text = "В ${mentions.size} из ${recent.size} последних тренировок в заметках есть «$word». " +
                "Стоит снизить прыжковую нагрузку и, если не проходит, показаться врачу.",
            because = "Заметки последних ${recent.size} тренировок.",
            route = "trainings"
        )
    }

    // --- Вес ---

    private fun weight(input: AdviceInput): Advice? {
        val weights = input.body.filter { it.weightKg != null }.sortedBy { it.date }
        val latest = weights.lastOrNull() ?: return null
        val month = weights.lastOrNull { it.date <= latest.date - 30 * DAY } ?: return null
        val diff = latest.weightKg!! - month.weightKg!!
        if (abs(diff) < month.weightKg * Limits.WEIGHT_SHARE) return null
        val sign = if (diff > 0) "+" else "−"
        return Advice(
            id = "weight-${latest.id}", kind = AdviceKind.CHANCE,
            title = "Вес $sign${formatNumber(abs(diff))} кг за месяц",
            text = "Вес изменился с ${formatNumber(month.weightKg)} до ${formatNumber(latest.weightKg)} кг. " +
                "Посмотри, как это сказалось на прыжках и силовых, — это видно в визуализации.",
            because = "Замеры веса в профиле.",
            route = "analytics"
        )
    }

    // --- Хорошие новости ---

    private fun freshRecord(input: AdviceInput): Advice? {
        val from = input.now - Limits.FRESH_RECORD_DAYS * DAY
        val record = input.records
            .filter { !it.isAnti && !it.isManuallyEdited && it.bestDate >= from && it.history.size > 1 }
            .maxByOrNull { it.bestDate } ?: return null
        val previous = record.history.last().previousValue ?: return null
        val gain = abs(record.best - previous)
        return Advice(
            id = "record-${record.key}-${record.best}", kind = AdviceKind.GOOD,
            title = "Новый рекорд!",
            text = "${record.displayName}: ${formatRecordValue(record.best, record.unit)}, " +
                "лучше прежнего на ${formatRecordValue(gain, record.unit)}.",
            because = "История рекорда.",
            route = "records", recordKey = record.key
        )
    }

    // --- Вспомогательное ---

    private fun feet(value: Double): String = String.format(Locale("ru"), "%.1f ст.", value).replace(",0 ", " ")

    /** Номер недели от начала эпохи: совет, скрытый на этой неделе, на следующей покажется снова. */
    private fun weekOf(now: Long): Long = now / (7 * DAY)
}
