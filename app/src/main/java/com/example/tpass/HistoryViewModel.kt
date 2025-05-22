package com.example.tpass

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Date

data class HistoryItem(
    val id: Long,
    val serviceName: String,
    val username: String,
    val oldPassword: String,
    val newPassword: String,
    val action: String,
    val timestamp: Date
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HistoryDatabase.getDatabase(application)
    private val _historyItems = MutableLiveData<List<HistoryItem>>()
    val historyItems: LiveData<List<HistoryItem>> = _historyItems

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val historyEntities = database.passwordHistoryDao().getAllHistory()
            _historyItems.value = historyEntities.map { entity ->
                HistoryItem(
                    id = entity.id,
                    serviceName = entity.serviceName,
                    username = entity.username,
                    oldPassword = entity.oldPassword,
                    newPassword = entity.newPassword,
                    action = entity.action,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    fun addHistoryItem(serviceName: String, username: String, oldPassword: String, newPassword: String, action: String) {
        viewModelScope.launch {
            val historyEntity = PasswordHistoryEntity(
                serviceName = serviceName,
                username = username,
                oldPassword = oldPassword,
                newPassword = newPassword,
                action = action,
                timestamp = Date()
            )
            database.passwordHistoryDao().insertHistory(historyEntity)
            loadHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            database.passwordHistoryDao().clearHistory()
            loadHistory()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            database.passwordHistoryDao().deleteHistoryItem(id)
            loadHistory()
        }
    }
} 