package com.example.diarytripeljump

import com.example.diarytripeljump.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Свои текстовые модели (research/assistant/train.py): Kotlin считает признаки и вероятности так же, как Python
 * при обучении — сверка по контрольным парам; разбор заметок и ответы помощника на простых данных.
 */
class AssistantModelTest {

    private val day = 86_400_000L
    private val now = 3_000 * day

    private fun vectors(): List<List<String>> =
        javaClass.getResourceAsStream("/ai/assistant_vectors.txt")!!.bufferedReader(Charsets.UTF_8)
            .readLines().filter { it.isNotBlank() }.map { it.split('\t') }

    @Test
    fun features_matchPython() {
        assertEquals("как мой присед 30 м", TextFeatures.normalize("Как мой Присёд, 30 м?"))
        // FNV-1a: контрольные значения стандарта
        assertEquals(0x811C9DC5L, TextFeatures.fnv(""))
        assertEquals(0xE40C292CL, TextFeatures.fnv("a"))
    }

    @Test
    fun intentModel_sameAsPython() {
        val model = LinearTextModel.fromResource("/ai/intent_model.txt")!!
        val rows = vectors().filter { it[0] == "intent" }
        assertTrue(rows.size > 50)
        rows.forEach { (_, text, cls, prob) ->
            val p = model.predict(text)
            val best = p.indices.maxBy { p[it] }
            assertEquals(text, cls, model.classes[best])
            assertEquals(text, prob.toDouble(), p[best], 1e-5)
        }
    }

    @Test
    fun notesModel_sameAsPython() {
        val model = LinearTextModel.fromResource("/ai/notes_model.txt")!!
        val rows = vectors().filter { it[0] == "notes" }
        assertTrue(rows.size > 20)
        rows.forEach { r ->
            val expected = r[2].split(' ').map { it.toDouble() }
            val p = model.predict(r[1])
            expected.forEachIndexed { i, e -> assertTrue("${r[1]} [$i] $e vs ${p[i]}", abs(e - p[i]) < 1e-5) }
        }
    }

    @Test
    fun noteTagger_understandsWordsWithoutKeywords() {
        assertTrue(NoteTag.PAIN in NoteTagger.tags("колено чет побаливает"))
        assertTrue(NoteTag.SLEEP in NoteTagger.tags("не выспался совсем"))
        assertTrue(NoteTagger.tags("Сделал больше подходов").isEmpty())
        assertTrue(NoteTagger.tags("больше скорости").isEmpty())
    }

    @Test
    fun painAdvice_fromModelOnly() {
        // ни одного ключевого слова: «побаливает», «неприятно» — находит только модель
        fun t(id: Long, daysAgo: Int, note: String) = TrainingWithExercises(TrainingSession(id = id, date = now - daysAgo * day, notes = note), emptyList())
        val advice = AdviceEngine.build(
            AdviceInput(listOf(t(1, 1, "колено чет побаливает"), t(2, 3, "неприятно в стопе"), t(3, 5, "все хорошо")), emptyList(), emptyList(), emptyList(), emptyList(), now)
        )
        assertTrue(advice.any { it.id.startsWith("pain") })
    }

    private fun record(name: String, unit: ResultUnit, points: List<RecordPoint>, lower: Boolean = false) = RecordSummary(
        key = "ex|$name", name = name, displayName = name, unit = unit, isSingleLeg = false, legSide = null,
        isAnti = false, isCompetition = false, category = "Силовые", isIndoor = false, weather = null,
        inventoryLabel = null, levelLabel = "PB", best = if (lower) points.minOf { it.value } else points.maxOf { it.value },
        bestDate = points.last().date, lowerIsBetter = lower, deletableResultId = null, attempts = points.size,
        history = emptyList(), points = points
    )

    private val squat = record("Приседания со штангой", ResultUnit.KG, (0 until 10).map { RecordPoint(now - (60 - it * 6) * day, 100.0 + it * 2, false) })
    private val sprint = record("Бег 30 м", ResultUnit.SECONDS, (0 until 6).map { RecordPoint(now - (50 - it * 8) * day, 4.3 - it * 0.02, false) }, lower = true)
    private val input = AssistantInput(
        records = listOf(squat, sprint), insights = InsightsResult(emptyList(), emptyList()), advice = emptyList(),
        trainings = listOf(TrainingWithExercises(TrainingSession(id = 1, date = now - day, durationMinutes = 90, notes = "колено ноет"), emptyList())),
        competitions = listOf(Competition(id = 1, name = "Первенство города", date = now + 10 * day)), goals = emptyList(), now = now
    )

    @Test
    fun classify_typicalQuestions() {
        assertEquals(AssistantIntent.WEEK, DiaryAssistant.classify("итоги недели").first)
        assertEquals(AssistantIntent.FORECAST, DiaryAssistant.classify("сколько я прыгну на старте").first)
        assertEquals(AssistantIntent.EXERCISE, DiaryAssistant.classify("как мой присед").first)
        assertEquals(AssistantIntent.OTHER, DiaryAssistant.classify("какая погода завтра").first)
    }

    @Test
    fun exerciseAnswer_findsExerciseByWord() {
        assertEquals(squat, DiaryAssistant.findRecord("как мой присед", input.records))
        assertEquals(sprint, DiaryAssistant.findRecord("что с бегом 30", input.records))
        val a = DiaryAssistant.answer("как мой присед", input)
        assertEquals(AssistantIntent.EXERCISE, a.intent)
        assertTrue(a.text, a.text.contains("118 кг"))
    }

    @Test
    fun answers_useDiaryData() {
        assertTrue(DiaryAssistant.answer("когда ближайший старт", input).text.contains("Первенство города"))
        assertTrue(DiaryAssistant.answer("итоги недели", input).text.contains("1 тренировка"))
        val health = DiaryAssistant.answer("что в заметках", input)
        assertEquals(AssistantIntent.HEALTH, health.intent)
        assertTrue(health.text, health.text.contains("боль"))
        assertTrue(DiaryAssistant.answer("какая погода завтра", input).text.startsWith("Я отвечаю только"))
    }
}
