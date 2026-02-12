package com.voicechat.android.domain.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null
)

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
