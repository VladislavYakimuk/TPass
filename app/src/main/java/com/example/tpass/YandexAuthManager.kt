package com.example.tpass

import android.content.Context
import android.content.SharedPreferences
import com.yandex.authsdk.YandexAuthToken
import org.json.JSONObject

class YandexAuthManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveAuthToken(token: YandexAuthToken) {
        sharedPreferences.edit()
            .putString(KEY_TOKEN, token.value)
            .putLong(KEY_EXPIRES_IN, token.expiresIn)
            .apply()
    }

    fun saveUserInfo(name: String, email: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserName(): String? {
        return sharedPreferences.getString(KEY_USER_NAME, null)
    }

    fun getUserEmail(): String? {
        return sharedPreferences.getString(KEY_USER_EMAIL, null)
    }

    fun getAuthToken(): YandexAuthToken? {
        val tokenValue = sharedPreferences.getString(KEY_TOKEN, null)
        val expiresIn = sharedPreferences.getLong(KEY_EXPIRES_IN, 0)
        
        return if (tokenValue != null) {
            YandexAuthToken(tokenValue, expiresIn)
        } else {
            null
        }
    }

    fun clearAuthToken() {
        sharedPreferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_EXPIRES_IN)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .apply()
    }

    fun isAuthenticated(): Boolean {
        return getAuthToken() != null
    }

    companion object {
        private const val PREFS_NAME = "yandex_auth_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EXPIRES_IN = "expires_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }
} 