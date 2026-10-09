package com.example.diarytripeljump

import android.app.Application
import android.content.Context
import com.example.diarytripeljump.data.SeedDatabase
import com.example.diarytripeljump.data.TripleJumpDatabase
import com.example.diarytripeljump.data.TripleJumpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TripleJumpApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: TripleJumpDatabase
        private set
    lateinit var repository: TripleJumpRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = TripleJumpDatabase.getDatabase(this)
        repository = TripleJumpRepository(
            athleteProfileDao = database.athleteProfileDao(),
            competitionDao = database.competitionDao(),
            tripleJumpResultDao = database.tripleJumpResultDao(),
            jumpAttemptDao = database.jumpAttemptDao(),
            trainingSessionDao = database.trainingSessionDao(),
            customExerciseCategoryDao = database.customExerciseCategoryDao(),
            exerciseDao = database.exerciseDao(),
            exerciseResultDao = database.exerciseResultDao(),
            trainingJumpResultDao = database.trainingJumpResultDao(),
            preparationPeriodDao = database.preparationPeriodDao(),
            analyticsDao = database.analyticsDao(),
            analyticsIndicatorDao = database.analyticsIndicatorDao(),
            analyticsResultDao = database.analyticsResultDao(),
            correctionHintDao = database.correctionHintDao(),
            controlIndicatorDao = database.controlIndicatorDao()
        )

        // Безопасный запуск первичного заполнения ТОЛЬКО 1 РАЗ при первом запуске приложения
        val prefs = getSharedPreferences("triple_jump_prefs", MODE_PRIVATE)
        val isSeeded = prefs.getBoolean("is_db_seeded", false)

        if (!isSeeded) {
            applicationScope.launch {
                val seedDatabase = SeedDatabase(repository)
                seedDatabase.seedInitialData()
                prefs.edit().putBoolean("is_db_seeded", true).apply()
            }
        }
    }
}
