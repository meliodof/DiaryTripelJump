package com.example.diarytripeljump.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCompetitionStatus(status: CompetitionStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toCompetitionStatus(status: String?): CompetitionStatus? {
        return status?.let {
            try { CompetitionStatus.valueOf(it) } catch (_: Exception) { CompetitionStatus.UPCOMING }
        }
    }

    @TypeConverter
    fun fromTrainingType(type: TrainingType?): String? {
        return type?.name
    }

    @TypeConverter
    fun toTrainingType(type: String?): TrainingType? {
        return type?.let {
            try { TrainingType.valueOf(it) } catch (_: Exception) { TrainingType.HYBRID }
        }
    }

    @TypeConverter
    fun fromExerciseStage(stage: ExerciseStage?): String? {
        return stage?.name
    }

    @TypeConverter
    fun toExerciseStage(stage: String?): ExerciseStage? {
        return stage?.let {
            try { ExerciseStage.valueOf(it) } catch (_: Exception) { ExerciseStage.MAIN }
        }
    }

    @TypeConverter
    fun fromResultUnit(unit: ResultUnit?): String? {
        return unit?.name
    }

    @TypeConverter
    fun toResultUnit(unit: String?): ResultUnit? {
        return unit?.let {
            try { ResultUnit.valueOf(it) } catch (_: Exception) { ResultUnit.M }
        }
    }
}
