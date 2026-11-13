package com.example.diarytripeljump.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** О чём вопрос: классы своей модели (research/assistant/dataset.py). */
enum class AssistantIntent(val key: String, val label: String) {
    FORECAST("FORECAST", "прогноз"),
    WEEK("WEEK", "итоги периода"),
    EXERCISE("EXERCISE", "упражнение"),
    RECORDS("RECORDS", "рекорды"),
    LINKS("LINKS", "что влияет на результат"),
    READY("READY", "готовность к старту"),
    LOAD("LOAD", "нагрузка"),
    HEALTH("HEALTH", "самочувствие по заметкам"),
    NEXT_START("NEXT_START", "ближайшие старты"),
    GOALS("GOALS", "цели"),
    ADVICE("ADVICE", "советы"),
    RUNUP("RUNUP", "разбег"),
    HELP("HELP", "помощь"),
    OTHER("OTHER", "не о дневнике");

    companion object {
        fun of(key: String): AssistantIntent? = entries.firstOrNull { it.key == key }
    }
}

/** Всё, по чему помощник отвечает: только данные дневника и результаты проверенных моделей. */
data class AssistantInput(
    val records: List<RecordSummary>,
    val insights: InsightsResult,
    val advice: List<Advice>,
    val trainings: List<TrainingWithExercises>,
    val competitions: List<Competition>,
    val goals: List<ExerciseGoal>,
    val now: Long = System.currentTimeMillis()
)

/** Сообщение в чате помощника: вопрос пользователя или ответ. */
data class AssistantMessage(val id: Long, val fromUser: Boolean, val text: String, val answer: AssistantAnswer? = null)

/** Ответ: текст, как понят вопрос и с какой уверенностью, куда можно перейти. */
data class AssistantAnswer(
    val text: String,
    val intent: AssistantIntent?,
    val confidence: Double,
    val route: String? = null,
    val routeLabel: String? = null
)

/**
 * Помощник «Спросить дневник». Тип вопроса определяет своя модель (логистическая регрессия на буквенных
 * n-граммах, обучена на собственных примерах), ответ собирается из данных дневника и уже проверенных моделей
 * (прогноз, связи, советы). Генеративной языковой модели нет — поэтому числа в ответах не выдумываются.
 */
object DiaryAssistant {
    private const val DAY = 86_400_000L
    private val model: LinearTextModel? by lazy { LinearTextModel.fromResource("/ai/intent_model.txt") }

    val examples = listOf(
        "Какой прогноз на старт?", "Итоги недели", "Как мой присед?", "Что влияет на тройной?",
        "Готов ли я к старту?", "Не перегружаюсь ли я?", "Что в заметках?", "Успею ли к цели?"
    )

    /** Тип вопроса и уверенность; ниже порога модели — null («не понял»). */
    fun classify(question: String): Pair<AssistantIntent?, Double> {
        val m = model ?: return null to 0.0
        val p = m.predict(question)
        val best = p.indices.maxBy { p[it] }
        val intent = AssistantIntent.of(m.classes[best])
        return (if (p[best] < m.threshold) null else intent) to p[best]
    }

    fun answer(question: String, input: AssistantInput): AssistantAnswer {
        val q = question.trim().take(500)
        if (q.isEmpty()) return AssistantAnswer(help(), AssistantIntent.HELP, 1.0)
        val (intent, conf) = classify(q)
        if (model == null) return AssistantAnswer("Модель помощника не загрузилась. Советы и прогноз по-прежнему в разделе «Советы и прогноз».", null, 0.0)
        val text = when (intent) {
            null -> "Не уверен, что понял вопрос. " + help()
            AssistantIntent.FORECAST -> forecast(q, input)
            AssistantIntent.WEEK -> period(q, input)
            AssistantIntent.EXERCISE -> exercise(q, input)
            AssistantIntent.RECORDS -> records(input)
            AssistantIntent.LINKS -> links(input)
            AssistantIntent.READY -> ready(input)
            AssistantIntent.LOAD -> load(input)
            AssistantIntent.HEALTH -> health(input)
            AssistantIntent.NEXT_START -> nextStarts(input)
            AssistantIntent.GOALS -> goals(input)
            AssistantIntent.ADVICE -> advice(input)
            AssistantIntent.RUNUP -> runUp(input)
            AssistantIntent.HELP -> help()
            AssistantIntent.OTHER -> "Я отвечаю только на вопросы о вашем дневнике и подготовке. " + help()
        }
        val (route, label) = when (intent) {
            AssistantIntent.RECORDS, AssistantIntent.EXERCISE, AssistantIntent.GOALS -> "records" to "Открыть рекорды"
            AssistantIntent.NEXT_START, AssistantIntent.READY -> "competitions" to "Открыть старты"
            AssistantIntent.WEEK, AssistantIntent.LOAD, AssistantIntent.HEALTH, AssistantIntent.RUNUP -> "trainings" to "Открыть тренировки"
            else -> null to null
        }
        return AssistantAnswer(text, intent, conf, route, label)
    }

    // ---------------------------------------------------------------- ответы

    private fun help() = "Я отвечаю по данным вашего дневника. Можно спросить, например: " +
        examples.joinToString(", ") { "«$it»" } + "."

    private fun forecast(q: String, input: AssistantInput): String {
        val f = input.insights.forecasts.firstOrNull { !it.custom }
            ?: return "Прогноза пока нет: добавьте хотя бы один результат в тройном или личный рекорд в профиле."
        val basis = when (f.method) {
            ForecastMethod.OWN_DATA -> "Он построен по вашим последним ${f.points} ${days(f.points)} с результатами: свежие весят больше, " +
                "а один случайный результат прогноз не сбивает."
            ForecastMethod.TYPICAL_GROWTH -> "Своих результатов пока мало, поэтому прогноз — от последнего результата (или рекорда из профиля) " +
                "с обычным для вашего возраста приростом."
            ForecastMethod.LAST_RESULT -> "Своих результатов пока мало, поэтому ожидается примерно последний результат."
        }
        val why = if (Regex("почему|упал|низк|ниже|вырос").containsMatchIn(TextFeatures.normalize(q))) {
            " Прогноз — это ваш текущий уровень, а не рекорд: если последние результаты ниже рекорда, прогноз тоже ниже. " +
                "Обычный разброс результата от старта к старту — 2–3 %, то есть примерно ±${f.format(f.value * Insights.Limits.TYPICAL_SPREAD)}."
        } else ""
        val goalsNote = input.insights.forecasts.count { it.goal != null }.takeIf { it > 0 }?.let { " Прогнозы к срокам целей — в разделе «Советы и прогноз»." } ?: ""
        return "Прогноз ${f.targetLabel}: ${f.format(f.value)}. Надёжность — ${f.reliability.label}. $basis$why$goalsNote"
    }

    private fun period(q: String, input: AssistantInput): String {
        val n = TextFeatures.normalize(q)
        val days = when {
            "месяц" in n -> 30
            Regex("(2|две|двух) недел").containsMatchIn(n) -> 14
            else -> 7
        }
        val to = input.now + 1
        val from = input.now - days * DAY
        val inRange = input.trainings.filter { it.training.date in from until to }
        val done = inRange.filter { !it.training.isSkipped }
        val skipped = inRange.count { it.training.isSkipped }
        val minutes = done.sumOf { it.training.durationMinutes ?: 0 }
        val volume = AdviceEngine.volume(input.trainings, from, to)
        val prevVolume = AdviceEngine.volume(input.trainings, from - days * DAY, from)
        val starts = input.competitions.filter { it.date in from until to && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED }
        val fresh = freshRecords(input, from)
        val what = if (days == 7) "За неделю" else "За $days дней"
        if (done.isEmpty() && starts.isEmpty()) return "$what тренировок и стартов в дневнике нет" + (if (skipped > 0) ", пропусков — $skipped." else ".")
        val parts = mutableListOf("$what ${done.size} ${plural(done.size, "тренировка", "тренировки", "тренировок")}")
        if (minutes > 0) parts[0] += " (${formatTrainingDuration(minutes)})"
        if (skipped > 0) parts += "пропусков — $skipped"
        if (volume > 0) {
            val change = if (prevVolume > 0) ((volume.toDouble() / prevVolume - 1) * 100).roundToInt() else null
            parts += "объём $volume повторений" + (change?.let { if (abs(it) < 10) ", как и раньше" else if (it > 0) ", на $it % больше, чем в прошлый период" else ", на ${-it} % меньше, чем в прошлый период" } ?: "")
        }
        if (starts.isNotEmpty()) parts += "старты: " + starts.joinToString { it.name }
        val text = StringBuilder(parts.joinToString("; ") + ".")
        if (fresh.isNotEmpty()) text.append(" Новые рекорды: ").append(fresh.take(3).joinToString { "${it.displayName} — ${formatRecordValue(it.best, it.unit)}" }).append(".")
        val tags = noteTags(input, from)
        if (tags.isNotEmpty()) text.append(" В заметках: ").append(tags.entries.joinToString { "${it.key.label} (${it.value.size})" }).append(".")
        return text.toString()
    }

    private fun exercise(q: String, input: AssistantInput): String {
        val rec = findRecord(q, input.records)
            ?: return "Не нашёл такое упражнение среди ваших результатов. Есть, например: " +
                input.records.filter { !it.isAnti }.sortedByDescending { it.points.size }.take(5).joinToString { it.displayName } + "."
        val pts = rec.points.sortedBy { it.date }
        val last = pts.lastOrNull()
        val sb = StringBuilder("${rec.displayName}: лучший результат ${formatRecordValue(rec.best, rec.unit)} (${date(rec.bestDate)})")
        if (last != null) sb.append(", последний — ${formatRecordValue(last.value, rec.unit)} (${date(last.date)})")
        sb.append(".")
        val recent = pts.filter { it.date >= input.now - 28 * DAY }.map { it.value }
        val before = pts.filter { it.date in (input.now - 56 * DAY) until (input.now - 28 * DAY) }.map { it.value }
        if (recent.isNotEmpty() && before.isNotEmpty()) {
            val a = if (rec.lowerIsBetter) recent.min() else recent.max()
            val b = if (rec.lowerIsBetter) before.min() else before.max()
            val diff = a - b
            val better = if (rec.lowerIsBetter) diff < 0 else diff > 0
            sb.append(
                when {
                    abs(diff) < 1e-9 || abs(diff) / abs(b) < 0.005 -> " За последние 4 недели — на том же уровне, что и в предыдущие 4."
                    better -> " За последние 4 недели лучше, чем в предыдущие 4, на ${formatRecordValue(abs(diff), rec.unit)}."
                    else -> " За последние 4 недели хуже, чем в предыдущие 4, на ${formatRecordValue(abs(diff), rec.unit)}."
                }
            )
        } else if (recent.isEmpty()) {
            sb.append(" За последние 4 недели результатов нет.")
        }
        input.insights.influences.firstOrNull { it.key == rec.key }?.let { inf ->
            sb.append(" По вашим данным это упражнение связано с тройным: ${strength(inf.r)} связь ${lag(inf.lagWeeks)}.")
        }
        return sb.toString()
    }

    private fun records(input: AssistantInput): String {
        val (comp, train) = RecordInsights.mainJumpSplit(input.records)
        val sb = StringBuilder()
        val main = listOfNotNull(comp, train).maxByOrNull { it.best }
        if (main != null) sb.append("Рекорд в тройном — ${formatRecordValue(main.best, main.unit)} (${date(main.bestDate)}). ")
        val fresh = freshRecords(input, input.now - 30 * DAY)
        if (fresh.isEmpty()) {
            val latest = input.records.filter { !it.isAnti }.maxByOrNull { it.bestDate }
            sb.append("За месяц новых рекордов нет")
            if (latest != null) sb.append("; последний — ${latest.displayName}, ${formatRecordValue(latest.best, latest.unit)} (${date(latest.bestDate)})")
            sb.append(".")
        } else {
            sb.append("За месяц ${fresh.size} ${plural(fresh.size, "новый рекорд", "новых рекорда", "новых рекордов")}: ")
            sb.append(fresh.take(5).joinToString { "${it.displayName} — ${formatRecordValue(it.best, it.unit)}" }).append(".")
        }
        return sb.toString().ifBlank { "Рекордов в дневнике пока нет." }
    }

    private fun links(input: AssistantInput): String {
        val sb = StringBuilder()
        if (input.insights.influences.isEmpty()) {
            sb.append("По вашим данным надёжных связей с тройным пока нет: нужно хотя бы ${Insights.Limits.MIN_PAIRS} недель, " +
                "в которые записаны и упражнение, и тройной.")
        } else {
            sb.append("По вашим данным с тройным связаны: ")
            sb.append(input.insights.influences.joinToString { "${it.title} (${strength(it.r)} связь ${lag(it.lagWeeks)})" }).append(". ")
            sb.append("Совпадение во времени не доказывает причину — это повод обсудить с тренером.")
        }
        val known = Insights.trackedKnownLinks(input.records.map { it.displayName })
        val missing = known.filterValues { !it }.keys.map { it.title.lowercase() }
        sb.append(" По исследованиям с результатом в тройном связаны ").append(known.keys.joinToString { it.title.lowercase() }).append(".")
        if (missing.isNotEmpty()) sb.append(" Вы пока не записываете: ").append(missing.joinToString()).append(".")
        return sb.toString()
    }

    private fun ready(input: AssistantInput): String {
        val next = upcoming(input).firstOrNull() ?: return "Ближайших стартов в календаре нет. Добавьте старт — тогда подскажу про подводку."
        val days = daysTo(next.date, input.now)
        val sb = StringBuilder("До старта «${next.name}» ${if (days == 0) "— сегодня" else "$days ${plural(days, "день", "дня", "дней")}"}. ")
        val taper = input.advice.firstOrNull { it.id.startsWith("taper") }
        when {
            taper != null -> sb.append(taper.text).append(" ")
            days > 14 -> sb.append("Пока идёт основная работа. Снижать объём обычно начинают за 1–2 недели до старта, сохраняя скорость и прыжковые упражнения. ")
            else -> sb.append("Время подводки: объём уменьшают, скорость и короткие прыжковые упражнения сохраняют. ")
        }
        input.insights.forecasts.firstOrNull { !it.custom }?.let { sb.append("Прогноз ${it.targetLabel}: ${it.format(it.value)} (надёжность ${it.reliability.label}). ") }
        val risks = input.advice.filter { it.kind == AdviceKind.RISK && !it.id.startsWith("taper") }.take(2)
        if (risks.isNotEmpty()) sb.append("Обратите внимание: ").append(risks.joinToString { it.title.lowercase() }).append(".")
        return sb.toString().trim()
    }

    private fun load(input: AssistantInput): String {
        val now = input.now
        val week = AdviceEngine.volume(input.trainings, now - 7 * DAY, now + 1)
        val before = (1..3).map { w -> AdviceEngine.volume(input.trainings, now - (w + 1) * 7 * DAY, now - w * 7 * DAY) }
        val minutesWeek = input.trainings.filter { !it.training.isSkipped && it.training.date in (now - 7 * DAY)..now }.sumOf { it.training.durationMinutes ?: 0 }
        val minutesMonth = input.trainings.filter { !it.training.isSkipped && it.training.date in (now - 28 * DAY)..now }.sumOf { it.training.durationMinutes ?: 0 } / 4.0
        val sb = StringBuilder()
        if (week == 0 && minutesWeek == 0) sb.append("За неделю тренировок с объёмом нет. ")
        else {
            sb.append("За неделю $week повторений")
            if (before.any { it > 0 }) sb.append(" (в среднем раньше ${before.average().roundToInt()})")
            if (minutesWeek > 0) {
                sb.append(", время тренировок ${formatTrainingDuration(minutesWeek)}")
                if (minutesMonth > 0) sb.append(" при среднем ${formatTrainingDuration(minutesMonth.roundToInt())} в неделю за месяц")
            }
            sb.append(". ")
            val ratio = if (minutesMonth > 0) minutesWeek / minutesMonth else if (before.average() > 0) week / before.average() else 1.0
            sb.append(
                when {
                    ratio >= 1.5 -> "Нагрузка резко выросла — стоит добавить восстановление. "
                    ratio <= 0.6 -> "Нагрузка заметно ниже обычной. "
                    else -> "Нагрузка в обычных пределах. "
                }
            )
        }
        val related = input.advice.filter { it.id.startsWith("load-") || it.id.startsWith("acwr-") || it.id.startsWith("break-") }
        if (related.isNotEmpty()) sb.append(related.joinToString(" ") { "${it.title}: ${it.text}" })
        return sb.toString().trim()
    }

    private fun health(input: AssistantInput): String {
        val from = input.now - 14 * DAY
        val notes = notesSince(input, from)
        if (notes.isEmpty()) return "За две недели заметок к тренировкам нет. Записывайте самочувствие — я подскажу, если появятся тревожные сигналы."
        if (!NoteTagger.available) return "Модель разбора заметок не загрузилась."
        val tagged = notes.map { it to NoteTagger.tags(it.second) }
        val counts = NoteTag.entries.associateWith { t -> tagged.filter { t in it.second } }.filterValues { it.isNotEmpty() }
        val sb = StringBuilder("За две недели ${notes.size} ${plural(notes.size, "заметка", "заметки", "заметок")}. ")
        if (counts.isEmpty()) sb.append("Ни боли, ни усталости, ни плохого сна в них не видно.")
        else {
            sb.append("В них: ").append(counts.entries.joinToString { "${it.key.label} — ${it.value.size}" }).append(". ")
            (counts[NoteTag.PAIN] ?: counts[NoteTag.ILLNESS])?.firstOrNull()?.let { (d, text) ->
                sb.append("Например, ${date(d)}: «${text.take(60)}». ")
            }
            if (NoteTag.PAIN in counts || NoteTag.ILLNESS in counts) {
                sb.append("Стоит снизить прыжковую нагрузку; если боль или недомогание не проходят — к врачу. Это подсказка по заметкам, а не медицинская рекомендация.")
            } else if (NoteTag.FATIGUE in counts || NoteTag.SLEEP in counts) {
                sb.append("Усталость и плохой сон — повод добавить восстановление.")
            }
        }
        return sb.toString().trim()
    }

    private fun nextStarts(input: AssistantInput): String {
        val list = upcoming(input).take(3)
        if (list.isEmpty()) return "Ближайших стартов в календаре нет."
        val sb = StringBuilder("Ближайшие старты: ")
        sb.append(list.joinToString { c ->
            val d = daysTo(c.date, input.now)
            "«${c.name}» — ${date(c.date)}" + (c.city?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "") +
                if (d == 0) " (сегодня)" else " (через $d ${plural(d, "день", "дня", "дней")})"
        }).append(".")
        input.insights.forecasts.firstOrNull { !it.custom }?.let { sb.append(" Прогноз ${it.targetLabel}: ${it.format(it.value)}.") }
        return sb.toString()
    }

    private fun goals(input: AssistantInput): String {
        if (input.goals.isEmpty()) return "Целей пока нет. Цель ставится на карточке рекорда — тогда я подскажу, успеваете ли вы."
        return input.goals.joinToString(" ") { g ->
            val rec = input.records.firstOrNull { it.key == g.recordKey }
            val now = rec?.let { formatRecordValue(it.best, it.unit) } ?: "нет результатов"
            val f = input.insights.forecasts.firstOrNull { it.key == "goal-${g.recordKey}" }
            val reached = rec != null && (if (rec.lowerIsBetter) rec.best <= g.targetValue else rec.best >= g.targetValue)
            "${g.displayName}: цель ${formatRecordValue(g.targetValue, g.unit)}" + (g.deadline?.let { " к ${date(it)}" } ?: "") +
                ", сейчас $now. " + when {
                    reached -> "Цель уже достигнута!"
                    f?.reachesGoal == true -> "По прогнозу успеваете (${f.format(f.value)})."
                    f?.reachesGoal == false -> "По прогнозу к сроку будет около ${f.format(f.value)} — пока не успеваете."
                    else -> ""
                }
        }.trim()
    }

    private fun advice(input: AssistantInput): String {
        if (input.advice.isEmpty()) return "Сейчас советов нет: всё в порядке или данных пока мало."
        return "Главное сейчас: " + input.advice.take(3).joinToString(" ") { "${it.title} — ${it.text}" }
    }

    private fun runUp(input: AssistantInput): String =
        input.advice.firstOrNull { it.id.startsWith("runup-") }?.let { "${it.title}. ${it.text} (${it.because})" }
            ?: "Попыток с отметкой разбега за ${AdviceEngine.Limits.RUN_UP_DAYS} дней мало (нужно от ${AdviceEngine.Limits.RUN_UP_MIN}) " +
            "или разбег без явных проблем. Отмечайте заступы и недоступы в упражнении «Разбег» или на старте."

    // ---------------------------------------------------------------- вспомогательное

    /** Упражнение, о котором спрашивают: совпадение слов вопроса с названием по началу слова (5 букв) и числам. */
    fun findRecord(question: String, records: List<RecordSummary>): RecordSummary? {
        val stop = setOf("как", "мой", "моя", "мои", "что", "мне", "мой", "какой", "какая", "прогресс", "рекорд",
            "результат", "растет", "покажи", "динамика", "лучший", "последний", "упражнении", "упражнение", "было", "дела")
        val qWords = TextFeatures.words(question).filter { it !in stop && (it.length >= 3 || it.any(Char::isDigit)) }
        if (qWords.isEmpty()) return null
        fun stem(w: String) = if (w.any(Char::isDigit)) w else w.take(5)
        val qStems = qWords.map(::stem).toSet()
        return records.filter { !it.isAnti }
            .map { r -> r to TextFeatures.words(r.displayName).map(::stem).toSet().intersect(qStems).size }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<RecordSummary, Int>> { it.second }.thenBy { it.first.points.size })
            ?.first
    }

    private fun freshRecords(input: AssistantInput, from: Long) =
        input.records.filter { !it.isAnti && it.bestDate >= from && it.bestDate <= input.now && it.history.size > 1 }.sortedByDescending { it.bestDate }

    private fun upcoming(input: AssistantInput) = input.competitions
        .filter { startOfDay(it.date) >= startOfDay(input.now) && it.status != CompetitionStatus.SKIPPED && it.status != CompetitionStatus.CANCELLED && it.status != CompetitionStatus.COMPLETED }
        .sortedBy { it.date }

    /** Заметки тренировок и упражнений с даты [from]: дата и текст. */
    private fun notesSince(input: AssistantInput, from: Long): List<Pair<Long, String>> =
        input.trainings.filter { it.training.date in from..input.now }.flatMap { t ->
            (listOfNotNull(t.training.notes) + t.exerciseResults.mapNotNull { it.notes })
                .filter { it.isNotBlank() }.map { t.training.date to it }
        }.sortedByDescending { it.first }

    private fun noteTags(input: AssistantInput, from: Long): Map<NoteTag, List<Pair<Long, String>>> {
        if (!NoteTagger.available) return emptyMap()
        val notes = notesSince(input, from)
        return NoteTag.entries.filter { it != NoteTag.TECHNIQUE }
            .associateWith { t -> notes.filter { t in NoteTagger.tags(it.second) } }
            .filterValues { it.isNotEmpty() }
    }

    private fun daysTo(date: Long, now: Long) = ((startOfDay(date) - startOfDay(now)) / DAY).toInt()

    private fun strength(r: Double) = when {
        abs(r) >= 0.7 -> "сильная"
        abs(r) >= 0.5 -> "заметная"
        else -> "слабая"
    }

    private fun lag(weeks: Int) = if (weeks == 0) "в ту же неделю" else "через $weeks нед."

    private fun days(n: Int) = plural(n, "дню", "дням", "дням")

    private fun date(millis: Long) = SimpleDateFormat("d MMMM", Locale("ru")).format(Date(millis))

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
    }
}
