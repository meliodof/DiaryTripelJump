package com.example.diarytripeljump

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.diarytripeljump.data.TripleJumpDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Проверяет, что миграции сохраняют данные и дают схему, совпадающую с экспортированной в app/schemas. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TripleJumpDatabase::class.java
    )

    @Test
    fun migrate15To16_keepsExerciseResults() {
        helper.createDatabase(dbName, 15).apply {
            execSQL("INSERT INTO training_sessions (id, date, trainingType, isSkipped) VALUES (1, 1000, 'HYBRID', 0)")
            execSQL(
                "INSERT INTO exercise_results (id, exerciseId, exerciseName, trainingSessionId, date, result, resultUnit, stage, isSingleLeg) " +
                    "VALUES (1, 1, 'Присед', 1, 1000, 120.0, 'KG', 'MAIN', 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 16, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT result, inSpikes FROM exercise_results WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(120.0, c.getDouble(0), 1e-9)
            assertEquals(0, c.getInt(1))
        }
    }

    @Test
    fun migrate16To17_keepsExercisesAndProfile() {
        helper.createDatabase(dbName, 16).apply {
            execSQL(
                "INSERT INTO exercises (id, name, categoryColorHex, defaultUnit, isSingleLeg, isFavorite) " +
                    "VALUES (1, 'Присед', '#1E4D3B', 'KG', 0, 1)"
            )
            execSQL("INSERT INTO athlete_profile (id, firstName) VALUES (1, 'Иван')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 17, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT name, isFavorite, inSpikes FROM exercises WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Присед", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT firstName, gender FROM athlete_profile WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Иван", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate24To25_clearsPlaceholderDuration() {
        helper.createDatabase(dbName, 24).apply {
            // заглушка 60 минут из старых версий и настоящая длительность из демо-данных
            execSQL("INSERT INTO training_sessions (id, date, trainingType, durationMinutes, isSkipped) VALUES (1, 1000, 'STRENGTH', 60, 0)")
            execSQL("INSERT INTO training_sessions (id, date, trainingType, durationMinutes, isSkipped) VALUES (2, 2000, 'STRENGTH', 90, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 25, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT durationMinutes FROM training_sessions ORDER BY id").use { c ->
            c.moveToFirst()
            assertTrue(c.isNull(0))
            c.moveToNext()
            assertEquals(90, c.getInt(0))
        }
    }

    @Test
    fun migrate23To24_profileBecomesFirstMeasurement() {
        helper.createDatabase(dbName, 23).apply {
            execSQL("INSERT INTO athlete_profile (id, firstName, lastName, height, weight) VALUES (1, 'Антон', 'Ларин', 187.0, 77.0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 24, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT heightCm, weightKg FROM body_measurements").use { c ->
            c.moveToFirst()
            assertEquals(187.0, c.getDouble(0), 1e-9)
            assertEquals(77.0, c.getDouble(1), 1e-9)
        }
    }

    @Test
    fun migrate22To23_keepsInventoryAndResults() {
        helper.createDatabase(dbName, 22).apply {
            execSQL("INSERT INTO inventory_items (id, name, unit) VALUES (1, 'Барьер', 'CM')")
            execSQL("INSERT INTO training_sessions (id, date, trainingType, isSkipped) VALUES (1, 1000, 'HYBRID', 0)")
            execSQL(
                "INSERT INTO exercise_results (id, exerciseId, exerciseName, trainingSessionId, date, result, resultUnit, stage, isSingleLeg, inSpikes) " +
                    "VALUES (1, 1, 'Присед', 1, 1000, 120.0, 'KG', 'MAIN', 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 23, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT name, unit FROM inventory_items WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Барьер", c.getString(0))
            assertEquals("CM", c.getString(1))
        }
        db.query("SELECT result, isTimed FROM exercise_results WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(120.0, c.getDouble(0), 1e-9)
            assertEquals(0, c.getInt(1))
        }
    }

    @Test
    fun migrate21To22_keepsExerciseResults() {
        helper.createDatabase(dbName, 21).apply {
            execSQL("INSERT INTO training_sessions (id, date, trainingType, isSkipped) VALUES (1, 1000, 'HYBRID', 0)")
            execSQL(
                "INSERT INTO exercise_results (id, exerciseId, exerciseName, trainingSessionId, date, result, resultUnit, stage, isSingleLeg, inSpikes) " +
                    "VALUES (1, 1, 'Присед', 1, 1000, 120.0, 'KG', 'MAIN', 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 22, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT result, repsPerSetJson FROM exercise_results WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals(120.0, c.getDouble(0), 1e-9)
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate20To21_keepsExercises() {
        helper.createDatabase(dbName, 20).apply {
            execSQL(
                "INSERT INTO exercises (id, name, categoryColorHex, defaultUnit, isSingleLeg, isFavorite, inSpikes) " +
                    "VALUES (1, 'Метание', '#1E4D3B', 'M', 0, 0, 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 21, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT name, inSpikes, titleLayout FROM exercises WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Метание", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun migrate19To20_oldCompetitionsCountAsReachedFinal() {
        helper.createDatabase(dbName, 19).apply {
            execSQL(
                "INSERT INTO competitions (id, name, date, hasQualification, didNotQualifyForFinal, eventType, status) " +
                    "VALUES (1, 'Первенство', 1000, 1, 0, 'Тройной прыжок', 'UPCOMING')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 20, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT name, qualifiedForFinal, qualificationAttemptsJson FROM competitions WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Первенство", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun migrate18To19_keepsCompetitionsAndResetsRunUpCategory() {
        helper.createDatabase(dbName, 18).apply {
            execSQL(
                "INSERT INTO exercises (id, name, categoryName, categoryColorHex, defaultUnit, isSingleLeg, isFavorite, inSpikes) " +
                    "VALUES (1, 'По разбегу', 'Упражнение на разбег', '#1E4D3B', 'CM', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO competitions (id, name, date, hasQualification, didNotQualifyForFinal, eventType, status) " +
                    "VALUES (1, 'Первенство', 1000, 0, 0, 'Тройной прыжок', 'UPCOMING')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 19, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT categoryName FROM exercises WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Прыжковое упражнение", c.getString(0))
        }
        db.query("SELECT name, runUpJson FROM competitions WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Первенство", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun migrate17To18_keepsProfileAndRecordOverrides() {
        helper.createDatabase(dbName, 17).apply {
            execSQL("INSERT INTO athlete_profile (id, firstName) VALUES (1, 'Иван')")
            execSQL(
                "INSERT INTO record_overrides (recordKey, baseBest, value, date, isIndoor) " +
                    "VALUES ('ex|присед|BOTH|KG', 100.0, 102.5, 1000, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 18, true, *TripleJumpDatabase.ALL_MIGRATIONS)
        db.query("SELECT firstName, sportRank, leadingLeg FROM athlete_profile WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("Иван", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT value, eventName FROM record_overrides").use { c ->
            c.moveToFirst()
            assertEquals(102.5, c.getDouble(0), 1e-9)
            assertTrue(c.isNull(1))
        }
    }
}
