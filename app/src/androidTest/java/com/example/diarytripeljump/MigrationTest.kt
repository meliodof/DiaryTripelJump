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
}
