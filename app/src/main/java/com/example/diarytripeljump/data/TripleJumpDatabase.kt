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
        InventoryItem::class
    ],
    version = 12,
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
                    .addMigrations(MIGRATION_11_12)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
