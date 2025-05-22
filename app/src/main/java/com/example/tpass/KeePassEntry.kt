package com.example.tpass

data class KeePassEntry(
    val id: Int = 0,
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = ""
)