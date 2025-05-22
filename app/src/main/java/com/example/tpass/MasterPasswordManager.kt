package com.example.tpass

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MasterPasswordManager(private val context: Context) {
    private val masterKey: MasterKey
    private val sharedPreferences: SharedPreferences

    init {
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
            "master_password_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun setMasterPassword(password: String) {
        val hashedPassword = hashPassword(password)
        sharedPreferences.edit().apply {
            putString(MASTER_PASSWORD_KEY, hashedPassword)
            putInt(ATTEMPTS_REMAINING_KEY, MAX_ATTEMPTS)
            putLong(LAST_ATTEMPT_TIME_KEY, 0)
            apply()
        }
    }

    fun verifyMasterPassword(password: String): Boolean {
        if (isInCooldown()) {
            return false
        }

        val storedHash = sharedPreferences.getString(MASTER_PASSWORD_KEY, null)
        val isCorrect = storedHash != null && storedHash == hashPassword(password)

        if (!isCorrect) {
            decrementAttempts()
        } else {
            resetAttempts()
        }

        return isCorrect
    }

    fun hasMasterPassword(): Boolean {
        return sharedPreferences.contains(MASTER_PASSWORD_KEY)
    }

    fun getMasterPassword(): String? {
        return sharedPreferences.getString(MASTER_PASSWORD_KEY, null)
    }

    fun getRemainingAttempts(): Int {
        return sharedPreferences.getInt(ATTEMPTS_REMAINING_KEY, MAX_ATTEMPTS)
    }

    fun getCooldownTimeRemaining(): Long {
        val lastAttemptTime = sharedPreferences.getLong(LAST_ATTEMPT_TIME_KEY, 0)
        if (lastAttemptTime == 0L) return 0

        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - lastAttemptTime
        val cooldownTime = TimeUnit.MINUTES.toMillis(1)

        return if (timeElapsed >= cooldownTime) 0 else cooldownTime - timeElapsed
    }

    fun isInCooldown(): Boolean {
        return getCooldownTimeRemaining() > 0
    }

    fun decrementAttempts() {
        val currentAttempts = sharedPreferences.getInt(ATTEMPTS_REMAINING_KEY, MAX_ATTEMPTS)
        if (currentAttempts > 0) {
            sharedPreferences.edit().apply {
                putInt(ATTEMPTS_REMAINING_KEY, currentAttempts - 1)
                if (currentAttempts - 1 == 0) {
                    putLong(LAST_ATTEMPT_TIME_KEY, System.currentTimeMillis())
                }
                apply()
            }
        }
    }

    fun resetAttempts() {
        sharedPreferences.edit().apply {
            putInt(ATTEMPTS_REMAINING_KEY, MAX_ATTEMPTS)
            putLong(LAST_ATTEMPT_TIME_KEY, 0)
            apply()
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    companion object {
        private const val MASTER_PASSWORD_KEY = "master_password"
        private const val ATTEMPTS_REMAINING_KEY = "attempts_remaining"
        private const val LAST_ATTEMPT_TIME_KEY = "last_attempt_time"
        private const val MAX_ATTEMPTS = 3
    }
} 