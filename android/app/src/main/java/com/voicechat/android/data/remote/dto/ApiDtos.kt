package com.voicechat.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("expires_in")
    val expiresIn: Long,
    @SerializedName("token_type")
    val tokenType: String = "Bearer"
)

data class AuthRequest(
    @SerializedName("id_token")
    val idToken: String
)

data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("picture")
    val picture: String?
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class ErrorResponse(
    @SerializedName("error")
    val error: String,
    @SerializedName("message")
    val message: String
)
