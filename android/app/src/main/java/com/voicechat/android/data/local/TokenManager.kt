package com.voicechat.android.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.voicechat.android.domain.model.AuthTokens
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _tokens = MutableStateFlow<AuthTokens?>(null)
    val tokens: StateFlow<AuthTokens?> = _tokens.asStateFlow()

    init {
        loadTokens()
    }

    private fun loadTokens() {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0L)

        if (accessToken != null && refreshToken != null) {
            _tokens.value = AuthTokens(accessToken, refreshToken, expiresAt)
        }
    }

    fun saveTokens(tokens: AuthTokens) {
        try {
            Log.d(TAG, "Saving tokens to encrypted preferences...")
            encryptedPrefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, tokens.accessToken)
                putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
                putLong(KEY_EXPIRES_AT, tokens.expiresAt)
                apply()
            }
            _tokens.value = tokens
            Log.d(TAG, "Tokens saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tokens", e)
            throw e
        }
    }

    fun getAccessToken(): String? = _tokens.value?.accessToken

    fun getRefreshToken(): String? = _tokens.value?.refreshToken

    fun isTokenExpired(): Boolean {
        val expiresAt = _tokens.value?.expiresAt ?: return true
        return System.currentTimeMillis() >= expiresAt
    }

    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
        _tokens.value = null
    }

    fun shouldRefreshToken(): Boolean {
        val expiresAt = _tokens.value?.expiresAt ?: return true
        val bufferTime = 5 * 60 * 1000L // 5 minutes buffer
        return System.currentTimeMillis() >= (expiresAt - bufferTime)
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_FILE_NAME = "voice_chat_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
