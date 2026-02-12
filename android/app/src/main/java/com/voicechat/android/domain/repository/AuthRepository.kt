package com.voicechat.android.domain.repository

import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.domain.model.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: User?
    
    suspend fun signInWithGoogle(idToken: String): Result<User>
    suspend fun signOut()
    suspend fun refreshToken(): Result<String>
    suspend fun isAuthenticated(): Boolean
}
