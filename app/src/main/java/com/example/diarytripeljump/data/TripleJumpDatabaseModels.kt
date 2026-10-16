package com.example.diarytripeljump.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "athlete_profile")
data class AthleteProfile(
    @PrimaryKey val id: Int = 1,
    val firstName: String? = null,
    val lastName: String? = null,
    val nickname: String? = null,
    val avatarUriString: String? = null,
    val greetingPreference: String? = "NAME",
    val specialization: String? = "Тройной прыжок",
    val birthDate: Long? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val customPbMeters: Double? = null,
    val notes: String? = null
)

data class SeasonStats(
    val year: Int,
    val totalCompetitions: Int,
    val totalTrainings: Int,
    val personalRecords: Int,
    val seasonalRecords: Int,
    val bestTripleJump: Double,
    val averageTripleJump: Double
)

@Entity(tableName = "competitions")
data class Competition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val country: String? = "Россия",
    val city: String? = "Москва",
    val streetAndNumber: String? = null,
    val location: String? = null,
    val address: String? = null,
    val date: Long = System.currentTimeMillis(),
    val eventTime: String? = null,
    val hasQualification: Boolean = false,
    val qualificationStandard: String? = null,
    val didNotQualifyForFinal: Boolean = false,
    val placement: String? = null,
    val attemptsJson: String? = null,
    val eventType: String = "Тройной прыжок",
    val status: CompetitionStatus = CompetitionStatus.UPCOMING,
    val skipReason: String? = null,
    val notes: String? = null
)

enum class CompetitionStatus { ONGOING, UPCOMING, COMPLETED, SKIPPED, CANCELLED }

@Entity(
    tableName = "triple_jump_results",
    foreignKeys = [ForeignKey(
        entity = Competition::class,
        parentColumns = ["id"],
        childColumns = ["competitionId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("competitionId"), Index("date")]
)
data class TripleJumpResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long? = null,
    val eventName: String = "Тройной прыжок",
    val result: Double,
    val resultUnit: ResultUnit = ResultUnit.M,
    val date: Long = System.currentTimeMillis(),
    val isIndoor: Boolean = false,
    val isAntiRecord: Boolean = false,
    val recordLevel: String = "PB",
    val exerciseCategory: String = "Прыжковые",
    val isCompetition: Boolean = true,
    val weatherConditions: String? = null,
    val notes: String? = null
)

@Entity(
    tableName = "jump_attempts",
    foreignKeys = [ForeignKey(
        entity = TripleJumpResult::class,
        parentColumns = ["id"],
        childColumns = ["tripleJumpResultId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripleJumpResultId")]
)
data class JumpAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripleJumpResultId: Long,
    val attemptNumber: Int,
    val distanceMeters: Double? = null,
    val isFoul: Boolean = false,
    val isPassed: Boolean = false
)

@Entity(tableName = "training_sessions")
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val trainingType: TrainingType = TrainingType.HYBRID,
    val intensity: Int? = null,
    val durationMinutes: Int? = null,
    val notes: String? = null,
    val isSkipped: Boolean = false,
    val skipReason: String? = null
)

enum class TrainingType {
    STRENGTH,
    TECHNICAL,
    JUMP,
    CONDITIONING,
    RECOVERY,
    TECHNICAL_STRENGTH,
    TECHNICAL_RECOVERY,
    STRENGTH_CONDITIONING,
    HYBRID
}

enum class ExerciseStage { WARMUP, MAIN, COOLDOWN }

@Entity(tableName = "exercise_categories")
data class CustomExerciseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#1E4D3B"
)

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryName: String? = null,
    val categoryColorHex: String = "#1E4D3B",
    val defaultUnit: ResultUnit = ResultUnit.KG,
    val isSingleLeg: Boolean = false,
    val legSide: String? = null,
    val inventoryName: String? = null,
    val inventoryUnit: ResultUnit? = null,
    val inventoryValue: Double? = null,
    val equipmentName: String? = null
)

@Entity(
    tableName = "exercise_results",
    foreignKeys = [
        ForeignKey(
            entity = TrainingSession::class,
            parentColumns = ["id"],
            childColumns = ["trainingSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("exerciseId"), Index("trainingSessionId"), Index("date")]
)
data class ExerciseResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val exerciseName: String = "",
    val trainingSessionId: Long?,
    val date: Long,
    val result: Double,
    val resultUnit: ResultUnit,
    val sets: Int? = null,
    val reps: Int? = null,
    val isSingleLeg: Boolean = false,
    val legSide: String? = null,
    val hasWeight: Boolean = true,
    val hasHurdles: Boolean = false,
    val hurdleHeightCm: Double? = null,
    val hurdleCount: Int? = null,
    val inventoryName: String? = null,
    val inventoryUnit: ResultUnit? = null,
    val inventoryValue: Double? = null,
    val setResultsJson: String? = null,
    val stage: ExerciseStage = ExerciseStage.MAIN,
    val notes: String? = null
)

@Entity(tableName = "training_periods")
data class TrainingPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val colorHex: String
)

@Entity(tableName = "record_overrides")
data class RecordOverride(
    @PrimaryKey val recordKey: String,
    val baseBest: Double,
    val value: Double,
    val date: Long,
    val isIndoor: Boolean,
    val weather: String?,
    val notes: String?
)

@Entity(tableName = "exercise_complexes")
data class ExerciseComplex(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val exercisesJson: String,
    val notes: String? = null
)

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: ResultUnit = ResultUnit.CM
)

enum class ResultUnit { KG, CM, M, REPS, SECONDS }

@Entity(
    tableName = "training_jump_results",
    foreignKeys = [ForeignKey(
        entity = TrainingSession::class,
        parentColumns = ["id"],
        childColumns = ["trainingSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("trainingSessionId")]
)
data class TrainingJumpResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainingSessionId: Long,
    val result: Double? = null,
    val runUpSpeed: Double? = null,
    val hopPhase: Double? = null,
    val stepPhase: Double? = null,
    val jumpPhase: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: String? = null,
    val attemptNumber: Int? = null,
    val notes: String? = null
)

@Entity(tableName = "preparation_periods")
data class PreparationPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long? = null,
    val startDate: Long,
    val endDate: Long,
    val name: String? = null,
    val colorHex: String = "#1E4D3B",
    val notes: String? = null
)

@Entity(
    tableName = "analytics",
    foreignKeys = [
        ForeignKey(
            entity = Competition::class,
            parentColumns = ["id"],
            childColumns = ["competitionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("competitionId")]
)
data class Analytics(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val competitionId: Long,
    val analysisDate: Long = System.currentTimeMillis(),
    val totalAttempts: Int = 0,
    val successfulAttempts: Int = 0,
    val bestResult: Double = 0.0,
    val averageResult: Double = 0.0,
    val consistencyIndex: Double = 0.0,
    val notes: String? = null
)

@Entity(
    tableName = "correction_hints",
    foreignKeys = [
        ForeignKey(
            entity = Analytics::class,
            parentColumns = ["id"],
            childColumns = ["analyticsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analyticsId")]
)
data class CorrectionHint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val category: String,
    val title: String,
    val description: String,
    val priority: Int = 0,
    val isApplied: Boolean = false
)

@Entity(
    tableName = "analytics_indicators",
    foreignKeys = [
        ForeignKey(
            entity = Analytics::class,
            parentColumns = ["id"],
            childColumns = ["analyticsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analyticsId")]
)
data class AnalyticsIndicator(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val name: String,
    val value: Double,
    val unit: String? = null,
    val category: String? = null
)

@Entity(
    tableName = "analytics_results",
    foreignKeys = [
        ForeignKey(
            entity = Analytics::class,
            parentColumns = ["id"],
            childColumns = ["analyticsId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("analyticsId")]
)
data class AnalyticsResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analyticsId: Long,
    val attemptNumber: Int,
    val result: Double,
    val isFoul: Boolean = false
)

@Entity(
    tableName = "control_indicators",
    foreignKeys = [
        ForeignKey(
            entity = CorrectionHint::class,
            parentColumns = ["id"],
            childColumns = ["hintId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("hintId")]
)
data class ControlIndicator(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hintId: Long,
    val name: String,
    val targetValue: Double,
    val currentValue: Double = 0.0,
    val unit: String? = null
)
