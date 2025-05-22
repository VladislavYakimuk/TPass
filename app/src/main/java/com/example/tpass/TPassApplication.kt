package com.example.tpass

import android.app.Application

class TPassApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}