package com.example.tpass

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PasswordViewModelFactory(
    private val application: Application,
    private val keepPassManager: KeePassManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PasswordViewModel(application, keepPassManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}