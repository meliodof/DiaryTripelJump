package com.example.diarytripeljump.data

import androidx.room.Embedded
import androidx.room.Relation

data class TripleJumpResultWithAttempts(
    @Embedded val tripleJumpResult: TripleJumpResult,
    @Relation(
        parentColumn = "id",
        entityColumn = "tripleJumpResultId"
    )
    val attempts: List<JumpAttempt>
)

data class ExerciseResultWithExercise(
    @Embedded val exerciseResult: ExerciseResult,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id"
    )
    val exercise: Exercise
)

data class TrainingWithExercises(
    @Embedded val training: TrainingSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "trainingSessionId"
    )
    val exerciseResults: List<ExerciseResult>
)

data class AnalyticsWithDetails(
    @Embedded val analytics: Analytics,
    @Relation(
        parentColumn = "id",
        entityColumn = "analyticsId"
    )
    val indicators: List<AnalyticsIndicator>,
    @Relation(
        parentColumn = "id",
        entityColumn = "analyticsId"
    )
    val results: List<AnalyticsResult>,
    @Relation(
        parentColumn = "id",
        entityColumn = "analyticsId"
    )
    val hints: List<CorrectionHint>
)

data class CorrectionHintWithControls(
    @Embedded val correctionHint: CorrectionHint,
    @Relation(
        parentColumn = "id",
        entityColumn = "hintId"
    )
    val controlIndicators: List<ControlIndicator>
)
