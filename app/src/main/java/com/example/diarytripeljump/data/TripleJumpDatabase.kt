package com.example.diarytripeljump.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AthleteProfile::class,
        Competition::class,
        TripleJumpResult::class,
        JumpAttempt::class,
        TrainingSession::class,
        CustomExerciseCategory::class,
        Exercise::class,
        ExerciseResult::class,
        TrainingJumpResult::class,
        PreparationPeriod::class,
        Analytics::class,
        AnalyticsIndicator::class,
        AnalyticsResult::class,
        CorrectionHint::class,
        ControlIndicator::class,
        InventoryItem::class,
        TrainingPeriod::class,
        RecordOverride::class,
        ExerciseComplex::class,
        ComplexExercise::class,
        ExerciseGoal::class
    ],
    version = 15,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TripleJumpDatabase : RoomDatabase() {
    abstract fun athleteProfileDao(): AthleteProfileDao
    abstract fun competitionDao(): CompetitionDao
    abstract fun tripleJumpResultDao(): TripleJumpResultDao
    abstract fun jumpAttemptDao(): JumpAttemptDao
    abstract fun trainingSessionDao(): TrainingSessionDao
    abstract fun customExerciseCategoryDao(): CustomExerciseCategoryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseResultDao(): ExerciseResultDao
    abstract fun trainingJumpResultDao(): TrainingJumpResultDao
    abstract fun preparationPeriodDao(): PreparationPeriodDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun analyticsIndicatorDao(): AnalyticsIndicatorDao
    abstract fun analyticsResultDao(): AnalyticsResultDao
    abstract fun correctionHintDao(): CorrectionHintDao
    abstract fun controlIndicatorDao(): ControlIndicatorDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun trainingPeriodDao(): TrainingPeriodDao
    abstract fun recordOverrideDao(): RecordOverrideDao
    abstract fun exerciseComplexDao(): ExerciseComplexDao
    abstract fun exerciseGoalDao(): ExerciseGoalDao

    companion object {
        /** 11 → 12: одна нога и инвентарь в результатах упражнений + таблица инвентаря. Данные сохраняются. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN isSingleLeg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN legSide TEXT")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN inventoryName TEXT")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN inventoryUnit TEXT")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN inventoryValue REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS inventory_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "unit TEXT NOT NULL)"
                )
            }
        }

        /** 12 → 13: параметры упражнения, подготовительные периоды календаря, ручные правки рекордов. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN isSingleLeg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercises ADD COLUMN legSide TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN inventoryName TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN inventoryUnit TEXT")
                db.execSQL("ALTER TABLE exercises ADD COLUMN inventoryValue REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS training_periods (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "startDate INTEGER NOT NULL, " +
                        "endDate INTEGER NOT NULL, " +
                        "colorHex TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS record_overrides (" +
                        "recordKey TEXT NOT NULL, " +
                        "baseBest REAL NOT NULL, " +
                        "value REAL NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "isIndoor INTEGER NOT NULL, " +
                        "weather TEXT, " +
                        "notes TEXT, " +
                        "PRIMARY KEY(recordKey))"
                )
            }
        }

        /** 13 → 14: комплексы упражнений. Данные сохраняются. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_complexes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "description TEXT, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS complex_exercises (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "complexId INTEGER NOT NULL, " +
                        "exerciseId INTEGER NOT NULL, " +
                        "position INTEGER NOT NULL, " +
                        "FOREIGN KEY(complexId) REFERENCES exercise_complexes(id) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_complex_exercises_complexId ON complex_exercises (complexId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_complex_exercises_exerciseId ON complex_exercises (exerciseId)")
            }
        }

        /** 14 → 15: нога и инвентарь у рекордов, избранные упражнения, цели. Данные сохраняются. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN isSingleLeg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN legSide TEXT")
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN inventoryName TEXT")
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN inventoryUnit TEXT")
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN inventoryValue REAL")
                db.execSQL("ALTER TABLE exercises ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS exercise_goals (" +
                        "recordKey TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "displayName TEXT NOT NULL, " +
                        "unit TEXT NOT NULL, " +
                        "targetValue REAL NOT NULL, " +
                        "lowerIsBetter INTEGER NOT NULL, " +
                        "deadline INTEGER, " +
                        "createdAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(recordKey))"
                )
            }
        }

        @Volatile
        private var INSTANCE: TripleJumpDatabase? = null

        fun getDatabase(context: Context): TripleJumpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripleJumpDatabase::class.java,
                    "triple_jump_database"
                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
