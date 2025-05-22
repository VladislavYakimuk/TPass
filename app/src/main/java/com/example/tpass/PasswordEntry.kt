package com.example.tpass

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serviceName: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = ""
)