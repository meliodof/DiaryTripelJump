package com.example.diarytripeljump.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCompetitionStatus(status: CompetitionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toCompetitionStatus(status: String): CompetitionStatus {
        return CompetitionStatus.valueOf(status)
    }

    @TypeConverter
    fun fromAttemptType(type: AttemptType): String {
        return type.name
    }

    @TypeConverter
    fun toAttemptType(type: String): AttemptType {
        return AttemptType.valueOf(type)
    }

    @TypeConverter
    fun fromTrainingType(type: TrainingType): String {
        return type.name
    }

    @TypeConverter
    fun toTrainingType(type: String): TrainingType {
        return TrainingType.valueOf(type)
    }

    @TypeConverter
    fun fromExerciseStage(stage: ExerciseStage): String {
        return stage.name
    }

    @TypeConverter
    fun toExerciseStage(stage: String): ExerciseStage {
        return ExerciseStage.valueOf(stage)
    }

    @TypeConverter
    fun fromResultUnit(unit: ResultUnit): String {
        return unit.name
    }

    @TypeConverter
    fun toResultUnit(unit: String): ResultUnit {
        return ResultUnit.valueOf(unit)
    }
}
