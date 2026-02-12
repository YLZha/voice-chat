package com.voicechat.android.data.remote

import com.voicechat.android.data.remote.dto.AuthRequest
import com.voicechat.android.data.remote.dto.RefreshTokenRequest
import com.voicechat.android.data.remote.dto.TokenResponse
import com.voicechat.android.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/google")
    suspend fun authenticateWithGoogle(
        @Body request: AuthRequest
    ): Response<TokenResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<TokenResponse>

    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): Response<UserDto>
}
