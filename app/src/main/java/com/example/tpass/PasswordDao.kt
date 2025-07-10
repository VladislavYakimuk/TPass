package com.example.tpass

import androidx.room.*

@Dao
interface PasswordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntry): Long

    @Query("SELECT * FROM passwords ORDER BY id DESC")
    suspend fun getAllPasswords(): List<PasswordEntry>

    @Query("SELECT * FROM passwords WHERE serviceName = :serviceName LIMIT 1")
    suspend fun getEntryByServiceName(serviceName: String): PasswordEntry?

    @Delete
    suspend fun deletePassword(password: PasswordEntry): Int

    @Update
    suspend fun updatePassword(password: PasswordEntry): Int

    @Query("DELETE FROM passwords")
    suspend fun deleteAllPasswords()

    // Получить все уникальные категории
    @Query("SELECT DISTINCT category FROM passwords")
    suspend fun getAllCategories(): List<String>

    // Получить все уникальные теги (JSON-строки, преобразование в List<String> делать вручную через Converters)
    @Query("SELECT tags FROM passwords")
    suspend fun getAllTagsRaw(): List<String>

    // Получить записи по категории
    @Query("SELECT * FROM passwords WHERE category = :category ORDER BY id DESC")
    suspend fun getPasswordsByCategory(category: String): List<PasswordEntry>

    // Получить записи по тегу (LIKE, т.к. Room не поддерживает поиск по списку)
    @Query("SELECT * FROM passwords WHERE tags LIKE '%' || :tag || '%' ORDER BY id DESC")
    suspend fun getPasswordsByTag(tag: String): List<PasswordEntry>

    // Поиск по названию сервиса, URL, тегам, категориям
    @Query("SELECT * FROM passwords WHERE serviceName LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY id DESC")
    suspend fun searchPasswords(query: String): List<PasswordEntry>
}