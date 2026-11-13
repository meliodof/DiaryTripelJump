package com.example.diarytripeljump

import com.example.diarytripeljump.data.Aggregation
import com.example.diarytripeljump.data.BodyMeasurement
import com.example.diarytripeljump.data.ChartConfig
import com.example.diarytripeljump.data.ChartData
import com.example.diarytripeljump.data.ChartMode
import com.example.diarytripeljump.data.ChartRange
import com.example.diarytripeljump.data.ExerciseResult
import com.example.diarytripeljump.data.LineStyle
import com.example.diarytripeljump.data.RecordPoint
import com.example.diarytripeljump.data.ResultUnit
import com.example.diarytripeljump.data.SeriesSample
import com.example.diarytripeljump.data.SeriesStyle
import com.example.diarytripeljump.data.VisualizationConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class AnalyticsTest {
    private fun points(vararg values: Double) = values.mapIndexed { i, v -> RecordPoint(i * 1000L, v, false) }
    private fun day(month: Int, dayOfMonth: Int) = Calendar.getInstance().apply { clear(); set(2026, month, dayOfMonth, 12, 0) }.timeInMillis

    @Test
    fun normalized_spreadsFromZeroToHundred() {
        val out = ChartData.transform(points(10.0, 15.0, 20.0), ChartMode.NORMALIZED, smoothing = false)
        assertEquals(listOf(0.0, 50.0, 100.0), out.map { it.value })
        assertEquals(listOf(10.0, 15.0, 20.0), out.map { it.raw })
    }

    @Test
    fun change_isPercentFromFirstPoint() {
        val out = ChartData.transform(points(10.0, 11.0, 9.0), ChartMode.CHANGE, smoothing = false)
        assertEquals(10.0, out[1].value, 1e-9)
        assertEquals(-10.0, out[2].value, 1e-9)
    }

    @Test
    fun flatLine_getsReadableScale() {
        assertEquals(0.0 to 100.0, ChartData.yBounds(listOf(50.0, 50.0), ChartMode.NORMALIZED, false))
        val (low, high) = ChartData.yBounds(listOf(8.8, 8.8), ChartMode.ABSOLUTE, false)
        assertEquals(7.8, low, 1e-9)
        assertEquals(9.8, high, 1e-9)
    }

    @Test
    fun axisPrecision_growsWithZoom() {
        assertEquals(0, ChartData.axisDecimals(20.0, 2))
        assertEquals(1, ChartData.axisDecimals(1.0, 2))
        assertEquals(2, ChartData.axisDecimals(0.1, 2))
        // килограммы — до граммов, секунды — до сотых, не мельче
        assertEquals(3, ChartData.axisDecimals(0.004, ChartData.maxDecimals("кг")))
        assertEquals(2, ChartData.axisDecimals(0.004, ChartData.maxDecimals("сек")))
        assertEquals("8.8", ChartData.formatTrim(8.80, 2).replace(',', '.'))
        assertEquals("102.505", ChartData.formatTrim(102.505, 3).replace(',', '.'))
        assertEquals("14", ChartData.formatTrim(14.0, 2).replace(',', '.'))
    }

    @Test
    fun smoothing_averagesNeighbours() {
        assertEquals(listOf(1.5, 2.0, 2.5), ChartData.smooth(listOf(1.0, 2.0, 3.0)))
    }

    @Test
    fun aggregation_byAttemptTrainingAndWeek() {
        // 2 и 4 ноября 2026 — одна неделя, 10 ноября — следующая
        val samples = listOf(
            SeriesSample(day(10, 2), listOf(13.0, 13.4)),
            SeriesSample(day(10, 4), listOf(13.2)),
            SeriesSample(day(10, 10), listOf(13.6, 13.0))
        )
        assertEquals(5, ChartData.aggregate(samples, Aggregation.EACH, false).size)
        assertEquals(listOf(13.4, 13.2, 13.6), ChartData.aggregate(samples, Aggregation.SESSION_BEST, false).map { it.value })
        assertEquals(listOf(13.4, 13.6), ChartData.aggregate(samples, Aggregation.WEEK_BEST, false).map { it.value })
        assertEquals(listOf(3.0, 2.0), ChartData.aggregate(samples, Aggregation.WEEK_COUNT, false).map { it.value })
        assertEquals(listOf(13.0, 13.0), ChartData.aggregate(samples, Aggregation.WEEK_BEST, true).map { it.value })
    }

    @Test
    fun records_onlyNewPersonalBests() {
        val samples = listOf(
            SeriesSample(day(10, 2), listOf(13.0, 13.4)),
            SeriesSample(day(10, 4), listOf(13.2)),
            SeriesSample(day(10, 10), listOf(13.6, 13.0)),
            SeriesSample(day(10, 12), listOf(13.6))
        )
        assertEquals(listOf(13.4, 13.6), ChartData.aggregate(samples, Aggregation.RECORDS, false).map { it.value })
        // бег: меньше — лучше
        val sprint = listOf(SeriesSample(1L, listOf(4.2)), SeriesSample(2L, listOf(4.3)), SeriesSample(3L, listOf(4.1, 4.4)))
        assertEquals(listOf(4.2, 4.1), ChartData.aggregate(sprint, Aggregation.RECORDS, true).map { it.value })
    }

    @Test
    fun positions_byTimeOrByOrder() {
        val dates = listOf(0L, 10L, 100L)
        assertEquals(mapOf(0L to 0f, 10L to 0.1f, 100L to 1f), ChartData.positions(dates, byOrder = false))
        assertEquals(mapOf(0L to 0f, 10L to 0.5f, 100L to 1f), ChartData.positions(dates, byOrder = true))
        assertEquals(mapOf(5L to 0.5f), ChartData.positions(listOf(5L, 5L), byOrder = true))
    }

    @Test
    fun eachAttempt_keepsOrderWithinDay() {
        val points = ChartData.aggregate(listOf(SeriesSample(1_000L, listOf(13.0, 13.4, 13.2))), Aggregation.EACH, false)
        assertEquals(listOf(13.0, 13.4, 13.2), points.map { it.value })
        assertEquals(3, points.map { it.date }.distinct().size)
    }

    @Test
    fun attempts_comeFromSetsOrResult() {
        val withSets = ExerciseResult(exerciseId = 1, trainingSessionId = 1, date = 1, result = 100.0, resultUnit = ResultUnit.KG, setResultsJson = "80,0,90")
        assertEquals(listOf(80.0, 90.0), ChartData.attemptsOf(withSets))
        assertEquals(listOf(100.0), ChartData.attemptsOf(withSets.copy(setResultsJson = null)))
        assertEquals(listOf(15.12, 15.4), ChartData.attemptsOf("15.12, X, 15,40"))
    }

    @Test
    fun config_survivesSaving() {
        val config = VisualizationConfig(
            charts = listOf(
                ChartConfig(
                    id = 7, title = "Прыжки", mode = ChartMode.CHANGE, aggregation = Aggregation.WEEK_AVG, smoothing = true, fromZero = true, byOrder = true,
                    series = listOf(SeriesStyle("rec:ex|тройной|BOTH|M", -16776961, LineStyle.LINE, 4f, false))
                ),
                ChartConfig(id = 8, title = "Вес")
            ),
            range = ChartRange.QUARTER
        )
        assertEquals(config, VisualizationConfig.decode(config.encode()))
        assertEquals(VisualizationConfig(), VisualizationConfig.decode("сломано"))
    }

    @Test
    fun series_includeStartsExercisesAndBody() {
        val exercise = ExerciseResult(exerciseId = 1, exerciseName = "Присед", trainingSessionId = 1, date = 1, result = 100.0, resultUnit = ResultUnit.KG)
        val body = listOf(BodyMeasurement(date = 1, weightKg = 77.0), BodyMeasurement(date = 2, heightCm = 187.0))
        val series = ChartData.buildSeries(listOf(exercise), emptyList(), listOf(SeriesSample(5, listOf(14.2))), body, emptyList())
        assertEquals(listOf("start:tj", "rec:ex|присед|BOTH|KG", "body:weight", "body:height"), series.map { it.id })
    }
}
