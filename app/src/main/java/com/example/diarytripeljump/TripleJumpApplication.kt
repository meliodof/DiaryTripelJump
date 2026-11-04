package com.example.diarytripeljump

import android.app.Application
import com.example.diarytripeljump.data.AppPreferences
import com.example.diarytripeljump.data.DiaryBackup
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
    lateinit var preferences: AppPreferences
        private set

    /** Открыт демо-дневник (отдельная база) вместо дневника пользователя. */
    var isDemo: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        // Восстановление из резервной копии применяется до открытия базы
        DiaryBackup.applyPendingRestore(this)

        preferences = AppPreferences(this)
        openDiary(preferences.useDemoData)
    }

    /**
     * Подключает дневник: свой или демо. Базы разные, поэтому демо-записи никогда не попадают в данные пользователя.
     * После вызова экран пересоздаётся с новой ViewModel (см. [MainActivity.switchDiary]).
     */
    fun openDiary(demo: Boolean) {
        isDemo = demo
        database = TripleJumpDatabase.getDatabase(
            this,
            if (demo) TripleJumpDatabase.DEMO_DATABASE_NAME else TripleJumpDatabase.DATABASE_NAME
        )
        repository = TripleJumpRepository(
            database = database,
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
            controlIndicatorDao = database.controlIndicatorDao(),
            inventoryDao = database.inventoryDao(),
            trainingPeriodDao = database.trainingPeriodDao(),
            recordOverrideDao = database.recordOverrideDao(),
            exerciseComplexDao = database.exerciseComplexDao(),
            exerciseGoalDao = database.exerciseGoalDao(),
            runUpMeasurementDao = database.runUpMeasurementDao()
        )
        if (demo) {
            val seed = SeedDatabase(repository)
            applicationScope.launch { seed.seedIfEmpty() }
        }
    }
}
