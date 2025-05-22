package com.example.tpass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PasswordViewModel(application: Application, private val keepPassManager: KeePassManager) : AndroidViewModel(application) {

    private val _entries = MutableLiveData<List<KeePassEntry>>()
    val entries: LiveData<List<KeePassEntry>> = _entries

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var isDatabaseOpen = false
    private val historyViewModel = HistoryViewModel(application)

    fun isDatabaseOpen(): Boolean = isDatabaseOpen

    fun openDatabase(password: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                keepPassManager.openDatabase(password)
                isDatabaseOpen = true
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to open database"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addEntry(entry: KeePassEntry) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val newEntry = keepPassManager.addEntry(entry)
                historyViewModel.addHistoryItem(
                    serviceName = entry.title,
                    username = entry.username,
                    oldPassword = "",
                    newPassword = entry.password,
                    action = "Создан"
                )
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to add entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateEntry(entry: KeePassEntry) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val oldEntry = keepPassManager.getEntryByTitle(entry.title)
                keepPassManager.updateEntry(entry)
                historyViewModel.addHistoryItem(
                    serviceName = entry.title,
                    username = entry.username,
                    oldPassword = oldEntry?.password ?: "",
                    newPassword = entry.password,
                    action = "Изменен"
                )
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEntry(entry: KeePassEntry) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                keepPassManager.deleteEntry(entry)
                historyViewModel.addHistoryItem(
                    serviceName = entry.title,
                    username = entry.username,
                    oldPassword = entry.password,
                    newPassword = "",
                    action = "Удален"
                )
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete entry"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadEntries() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val loadedEntries = keepPassManager.getEntries()
                _entries.value = loadedEntries
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load entries"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cleanup() {
        viewModelScope.launch {
            try {
                keepPassManager.closeDatabase()
                isDatabaseOpen = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to close database"
            }
        }
    }

    fun reloadDatabase() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                loadEntries()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to reload database"
            } finally {
                _isLoading.value = false
            }
        }
    }
}