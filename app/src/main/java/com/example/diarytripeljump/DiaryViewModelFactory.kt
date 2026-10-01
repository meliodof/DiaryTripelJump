package com.example.diarytripeljump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.diarytripeljump.data.TripleJumpRepository

class DiaryViewModelFactory(
    private val repository: TripleJumpRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DiaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DiaryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
