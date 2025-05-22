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
}