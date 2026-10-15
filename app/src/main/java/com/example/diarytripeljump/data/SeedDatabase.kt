package com.example.diarytripeljump.data

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        // Раньше здесь вызывался repository.clearAllDatabaseData(): все тренировки, старты и рекорды
        // стирались при каждом запуске приложения. Для работы истории рекордов данные должны сохраняться.
    }
}
