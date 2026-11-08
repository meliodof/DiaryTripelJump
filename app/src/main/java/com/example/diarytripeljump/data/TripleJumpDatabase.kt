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
        ExerciseGoal::class,
        RunUpMeasurement::class,
        BodyMeasurement::class
    ],
    version = TripleJumpDatabase.CURRENT_VERSION,
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
    abstract fun runUpMeasurementDao(): RunUpMeasurementDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao

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

        /** 15 → 16: выполнение упражнений и рекорды в шиповках. Данные сохраняются. */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN inSpikes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE triple_jump_results ADD COLUMN inSpikes INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 16 → 17: шиповки по умолчанию в справочнике упражнений, пол в профиле (для возрастной группы). */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN inSpikes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE athlete_profile ADD COLUMN gender TEXT")
            }
        }

        /** 17 → 18: разряд и толчковая нога в профиле, упражнения по разбегу, полная правка рекордов. Данные сохраняются. */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE athlete_profile ADD COLUMN sportRank TEXT")
                db.execSQL("ALTER TABLE athlete_profile ADD COLUMN sportRankDate INTEGER")
                db.execSQL("ALTER TABLE athlete_profile ADD COLUMN leadingLeg TEXT")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN runUpJson TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN eventName TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN unit TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN recordLevel TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN isCompetition INTEGER")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN isSingleLeg INTEGER")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN legSide TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN inSpikes INTEGER")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN inventoryName TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN inventoryUnit TEXT")
                db.execSQL("ALTER TABLE record_overrides ADD COLUMN inventoryValue REAL")
            }
        }

        /** 18 → 19: разбег в попытках старта, замеры размера разбега, вид «На разбег» убран. Данные сохраняются. */
        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE competitions ADD COLUMN runUpJson TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS run_up_measurements (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "lengthCm REAL, " +
                        "lengthFeet REAL)"
                )
                db.execSQL("UPDATE exercises SET categoryName = 'Прыжковое упражнение' WHERE categoryName = 'Упражнение на разбег'")
            }
        }

        /** 19 → 20: попытки квалификации и выход в финал. Данные сохраняются. */
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE competitions ADD COLUMN qualificationAttemptsJson TEXT")
                db.execSQL("ALTER TABLE competitions ADD COLUMN qualificationRunUpJson TEXT")
                db.execSQL("ALTER TABLE competitions ADD COLUMN qualifiedForFinal INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** 20 → 21: положение ноги, снаряда и шиповок в названии упражнения. Данные сохраняются. */
        internal val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN titleLayout TEXT")
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN titleLayout TEXT")
            }
        }

        /** 21 → 22: разное число повторений в подходах. Данные сохраняются. */
        internal val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN repsPerSetJson TEXT")
            }
        }

        /** 22 → 23: единица инвентаря необязательна, подход можно измерять временем. Данные сохраняются. */
        internal val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_results ADD COLUMN isTimed INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE inventory_items_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, unit TEXT)"
                )
                db.execSQL("INSERT INTO inventory_items_new (id, name, unit) SELECT id, name, unit FROM inventory_items")
                db.execSQL("DROP TABLE inventory_items")
                db.execSQL("ALTER TABLE inventory_items_new RENAME TO inventory_items")
            }
        }

        /** 23 → 24: история веса и роста; текущие значения из профиля становятся первым замером. */
        internal val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS body_measurements (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date INTEGER NOT NULL, heightCm REAL, weightKg REAL)"
                )
                db.execSQL(
                    "INSERT INTO body_measurements (date, heightCm, weightKg) " +
                        "SELECT CAST(strftime('%s','now') AS INTEGER) * 1000, height, weight FROM athlete_profile " +
                        "WHERE height IS NOT NULL OR weight IS NOT NULL"
                )
            }
        }

        internal val ALL_MIGRATIONS = arrayOf(
            MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
            MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24
        )

        const val DATABASE_NAME = "triple_jump_database"

        // Уже выпущенную версию не меняем: любое изменение схемы — новая версия и новая миграция
        const val CURRENT_VERSION = 24

        /** Самая старая версия, которую можно перенести без потери данных. */
        const val MIN_MIGRATABLE_VERSION = 11

        /** Отдельная база с демо-данными: переключение не смешивает их с дневником пользователя. */
        const val DEMO_DATABASE_NAME = "triple_jump_demo"

        private val instances = HashMap<String, TripleJumpDatabase>()

        fun getDatabase(context: Context, name: String = DATABASE_NAME): TripleJumpDatabase {
            return synchronized(this) {
                instances[name] ?: Room.databaseBuilder(
                    context.applicationContext,
                    TripleJumpDatabase::class.java,
                    name
                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .addMigrations(*ALL_MIGRATIONS)
                    // Пересоздаётся только база самых ранних тестовых версий (до 11), где миграций нет.
                    // Если для новой версии забыть миграцию, приложение упадёт, а не сотрёт дневник молча.
                    .fallbackToDestructiveMigrationFrom(true, *(1 until MIN_MIGRATABLE_VERSION).toList().toIntArray())
                    .build()
                    .also { instances[name] = it }
            }
        }
    }
}
