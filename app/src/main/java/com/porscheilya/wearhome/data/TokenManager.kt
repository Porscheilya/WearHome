package com.porscheilya.wearhome.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "YandexAuth"
        private const val TOKEN_KEY = "yandex_token"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _tokenState = MutableStateFlow<String?>(getSavedToken())
    val tokenState: StateFlow<String?> = _tokenState.asStateFlow()

    fun getSavedToken(): String? {
        return prefs.getString(TOKEN_KEY, null)
    }

    fun saveToken(token: String) {
        prefs.edit().putString(TOKEN_KEY, token).apply()
        _tokenState.value = token
    }

    fun clearToken() {
        prefs.edit().remove(TOKEN_KEY).apply()
        _tokenState.value = null
    }

    fun isTokenAvailable(): Boolean {
        return getSavedToken() != null
    }
}
