package com.example.tpass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PasswordViewModel(application: Application, private val keepPassManager: KeePassManager) : AndroidViewModel(application) {

    private val _entries = MutableLiveData<List<KeePassEntry>>()
    val entries: LiveData<List<KeePassEntry>> = _entries

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _newEntryAdded = MutableLiveData<KeePassEntry>()
    val newEntryAdded: LiveData<KeePassEntry> = _newEntryAdded

    private var isDatabaseOpen = false
    private val historyViewModel = HistoryViewModel(application)

    // StateFlow для поискового запроса
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // StateFlow для результатов поиска
    private val _searchResults = MutableStateFlow<List<KeePassEntry>>(emptyList())
    val searchResults: StateFlow<List<KeePassEntry>> = _searchResults.asStateFlow()

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
                _newEntryAdded.value = newEntry
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

    // Установить поисковый запрос
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchEntries(query)
    }

    // Поиск по всем полям (реактивно)
    private fun searchEntries(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = withContext(Dispatchers.IO) {
                    if (query.isBlank()) keepPassManager.getEntries()
                    else {
                        val lower = query.lowercase()
                        keepPassManager.getEntries().filter {
                            it.title.lowercase().contains(lower) ||
                            it.url.lowercase().contains(lower) ||
                            it.category.lowercase().contains(lower) ||
                            it.tags.any { tag -> tag.lowercase().contains(lower) }
                        }
                    }
                }
                _searchResults.value = results
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Получить все уникальные категории
    fun getAllCategories(): List<String> {
        return keepPassManager.getEntries().map { it.category }.distinct()
    }

    // Получить все уникальные теги
    fun getAllTags(): List<String> {
        return keepPassManager.getEntries().flatMap { it.tags }.distinct()
    }

    // Получить записи по категории
    fun getEntriesByCategory(category: String): List<KeePassEntry> {
        return keepPassManager.getEntries().filter { it.category == category }
    }

    // Получить записи по тегу
    fun getEntriesByTag(tag: String): List<KeePassEntry> {
        return keepPassManager.getEntries().filter { it.tags.contains(tag) }
    }
}