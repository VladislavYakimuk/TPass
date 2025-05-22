package com.example.tpass

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.TimeUnit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.os.Handler
import android.os.Looper

class AppLockManager(private val context: Context) {
    private val masterKey: MasterKey
    private val sharedPreferences: SharedPreferences
    private var lastActiveTime: Long = 0
    private var isLocked: Boolean = false
    private var inactivityHandler: Handler? = null
    private var inactivityRunnable: Runnable? = null

    init {
        Log.d(TAG, "Инициализация AppLockManager")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MasterKey.DEFAULT_MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        masterKey = MasterKey.Builder(context)
            .setKeyGenParameterSpec(keyGenParameterSpec)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "app_lock_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        lastActiveTime = System.currentTimeMillis()
        setupInactivityTimer()
        Log.d(TAG, "AppLockManager успешно инициализирован")
    }

    private fun setupInactivityTimer() {
        inactivityHandler = Handler(Looper.getMainLooper())
        inactivityRunnable = Runnable {
            if (shouldLock()) {
                Log.d(TAG, "Таймер бездействия: требуется блокировка")
                lock()
            }
        }
    }

    fun startInactivityTimer() {
        Log.d(TAG, "Запуск таймера бездействия")
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
        inactivityHandler?.postDelayed(inactivityRunnable!!, TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS))
    }

    fun stopInactivityTimer() {
        Log.d(TAG, "Остановка таймера бездействия")
        inactivityHandler?.removeCallbacks(inactivityRunnable!!)
    }

    fun updateLastActiveTime() {
        val oldTime = lastActiveTime
        lastActiveTime = System.currentTimeMillis()
        isLocked = false
        startInactivityTimer()
        Log.d(TAG, "Время последней активности обновлено: ${lastActiveTime} (прошло ${(lastActiveTime - oldTime) / 1000} секунд)")
    }

    fun shouldLock(): Boolean {
        if (isLocked) {
            Log.d(TAG, "Приложение уже заблокировано")
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastActive = currentTime - lastActiveTime
        val shouldLock = timeSinceLastActive >= TimeUnit.SECONDS.toMillis(LOCK_TIMEOUT_SECONDS)
        
        if (shouldLock) {
            Log.d(TAG, "Требуется блокировка: прошло ${timeSinceLastActive / 1000} секунд с последней активности")
        } else {
            Log.d(TAG, "Блокировка не требуется: прошло ${timeSinceLastActive / 1000} секунд с последней активности")
        }
        
        return shouldLock
    }

    fun lock() {
        if (!isLocked) {
            isLocked = true
            stopInactivityTimer()
            Log.d(TAG, "Приложение заблокировано (прошло ${(System.currentTimeMillis() - lastActiveTime) / 1000} секунд с последней активности)")
        } else {
            Log.d(TAG, "Приложение уже заблокировано, повторная блокировка не требуется")
        }
    }

    fun unlock() {
        if (isLocked) {
            isLocked = false
            updateLastActiveTime()
            Log.d(TAG, "Приложение разблокировано")
        } else {
            Log.d(TAG, "Приложение не было заблокировано, разблокировка не требуется")
        }
    }

    fun isLocked(): Boolean = isLocked

    companion object {
        private const val LOCK_TIMEOUT_SECONDS = 30L
        private const val TAG = "AppLockManager"
    }
} 