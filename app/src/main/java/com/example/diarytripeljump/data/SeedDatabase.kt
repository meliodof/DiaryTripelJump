package com.example.diarytripeljump.data

class SeedDatabase(private val repository: TripleJumpRepository) {

    suspend fun seedInitialData() {
        repository.clearAllDatabaseData()
    }
}
