package com.voicechat.android.data.repository

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    private var cachedUser: User? = null

    override val currentUser: User? get() = cachedUser

    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        return try {
            _authState.value = AuthState.Loading
            
            val response = authApiService.authenticateWithGoogle(AuthRequest(idToken))
            
            if (response.isSuccessful) {
                val tokenResponse = response.body()!!
                val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                val tokens = com.voicechat.android.domain.model.AuthTokens(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken,
                    expiresAt = expiresAt
                )
                tokenManager.saveTokens(tokens)
                
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
                Result.success(user)
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                _authState.value = AuthState.Error(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        tokenManager.clearTokens()
        cachedUser = null
        _authState.value = AuthState.Unauthenticated
        googleSignInClient.signOut().await()
    }

    override suspend fun refreshToken(): Result<String> {
        return try {
            val refreshToken = tokenManager.getRefreshToken()
                ?: return Result.failure(Exception("No refresh token available"))

            val response = authApiService.refreshToken(RefreshTokenRequest(refreshToken))
            
            if (response.isSuccessful) {
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
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        return tokenManager.getAccessToken() != null && !tokenManager.isTokenExpired()
    }

    fun getSignInIntent(): Task<GoogleSignInAccount> {
        return googleSignInClient.signInIntent
    }

    fun getSignOutIntent(): Task<Void> {
        return googleSignInClient.signOut()
    }

    fun requiresAuthScope(account: GoogleSignInAccount): Boolean {
        return !GoogleSignIn.hasPermissions(account, *AUTH_SCOPES)
    }

    companion object {
        private val AUTH_SCOPES = arrayOf(
            Scope("https://www.googleapis.com/auth/userinfo.profile"),
            Scope("https://www.googleapis.com/auth/userinfo.email")
        )
    }
}
