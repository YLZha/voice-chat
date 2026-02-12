package com.voicechat.android.data.repository

import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.voicechat.android.data.local.TokenManager
import com.voicechat.android.data.remote.AuthApiService
import com.voicechat.android.data.remote.dto.AuthRequest
import com.voicechat.android.data.remote.dto.RefreshTokenRequest
import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.domain.model.User
import com.voicechat.android.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiService: AuthApiService,
    private val googleSignInClient: GoogleSignInClient
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var cachedUser: User? = null

    override val currentUser: User? get() = cachedUser

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            _authState.value = AuthState.Loading
            Log.d(TAG, "Starting Google authentication with backend...")
            
            val response = authApiService.authenticateWithGoogle(AuthRequest(idToken))
            Log.d(TAG, "Backend response: ${response.code()}")
            
            if (response.isSuccessful) {
                Log.d(TAG, "Authentication successful, saving tokens...")
                val tokenResponse = response.body()!!
                val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                val tokens = com.voicechat.android.domain.model.AuthTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresAt = expiresAt
                )
                tokenManager.saveTokens(tokens)
                Log.d(TAG, "Tokens saved, fetching user info...")
                
                val userResponse = authApiService.getCurrentUser("Bearer ${tokenResponse.accessToken}")
                val user = userResponse.body()?.let { dto ->
                    User(
                        id = dto.id,
                        email = dto.email,
                        displayName = dto.name,
                        photoUrl = dto.picture
                    )
                } ?: throw Exception("Failed to get user info")
                
                cachedUser = user
                _authState.value = AuthState.Authenticated(user)
                Log.d(TAG, "Authentication complete for user: ${user.email}")
                Result.success(user)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Backend error (${response.code()}): $error")
                _authState.value = AuthState.Error(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during authentication", e)
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            Log.d(TAG, "Signing out...")
            tokenManager.clearTokens()
            cachedUser = null
            _authState.value = AuthState.Unauthenticated
            googleSignInClient.signOut().await()
            Log.d(TAG, "Sign-out complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign-out", e)
        }
    }

    override suspend fun refreshToken(): Result<String> {
        return try {
            Log.d(TAG, "Refreshing token...")
            val refreshToken = tokenManager.getRefreshToken()
                ?: return Result.failure(Exception("No refresh token available")).also {
                    Log.e(TAG, "No refresh token found")
                }

            val response = authApiService.refreshToken(RefreshTokenRequest(refreshToken))
            Log.d(TAG, "Token refresh response: ${response.code()}")
            
            if (response.isSuccessful) {
                Log.d(TAG, "Token refreshed successfully")
                val tokenResponse = response.body()!!
                val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                val tokens = com.voicechat.android.domain.model.AuthTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresAt = expiresAt
                )
                tokenManager.saveTokens(tokens)
                Result.success(tokenResponse.accessToken)
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code()}")
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
            Result.failure(e)
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return tokenManager.getAccessToken() != null && !tokenManager.isTokenExpired()
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun requiresAuthScope(account: GoogleSignInAccount): Boolean {
        return !GoogleSignIn.hasPermissions(account, *AUTH_SCOPES)
    }

    companion object {
        private const val TAG = "AuthRepository"
        private val AUTH_SCOPES = arrayOf(
            Scope("https://www.googleapis.com/auth/userinfo.profile"),
            Scope("https://www.googleapis.com/auth/userinfo.email")
        )
    }
}
