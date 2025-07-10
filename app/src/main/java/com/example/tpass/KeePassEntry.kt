package com.example.tpass

data class KeePassEntry(
    val id: Int = 0,
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    // Категория записи (например, "PIN-коды", "Wi-Fi")
    val category: String = "PIN-коды",
    // Теги для фильтрации и поиска
    val tags: List<String> = emptyList()
)