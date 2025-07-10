package com.example.tpass

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "passwords")
@TypeConverters(ListStringConverter::class)
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
    // Категория записи (например, "PIN-коды", "Wi-Fi")
    val category: String = "PIN-коды",
    // Теги для фильтрации и поиска
    val tags: List<String> = emptyList()
)