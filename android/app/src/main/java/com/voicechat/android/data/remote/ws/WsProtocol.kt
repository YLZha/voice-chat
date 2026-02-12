package com.voicechat.android.data.remote.ws

import com.google.gson.annotations.SerializedName

sealed class WsMessage {
    abstract val type: String
    
    data class Auth(
        @SerializedName("token")
        val token: String
    ) : WsMessage() {
        override val type = "auth"
    }
    
    data class AudioChunk(
        @SerializedName("audio")
        val audio: String, // Base64 encoded PCM
        @SerializedName("timestamp")
        val timestamp: Long
    ) : WsMessage() {
        override val type = "audio"
    }
    
    data class TextMessage(
        @SerializedName("content")
        val content: String,
        @SerializedName("message_id")
        val messageId: String? = null
    ) : WsMessage() {
        override val type = "message"
    }
    
    data class TtsResponse(
        @SerializedName("audio")
        val audio: String, // Base64 encoded audio
        @SerializedName("message_id")
        val messageId: String
    ) : WsMessage() {
        override val type = "tts"
    }
    
    data class Transcription(
        @SerializedName("text")
        val text: String,
        @SerializedName("message_id")
        val messageId: String
    ) : WsMessage() {
        override val type = "transcription"
    }
    
    data class ConnectionAck(
        @SerializedName("status")
        val status: String,
        @SerializedName("session_id")
        val sessionId: String? = null
    ) : WsMessage() {
        override val type = "connected"
    }
    
    data class Error(
        @SerializedName("code")
        val code: String,
        @SerializedName("message")
        val message: String
    ) : WsMessage() {
        override val type = "error"
    }
    
    data object Ping : WsMessage() {
        override val type = "ping"
    }
    
    data object Pong : WsMessage() {
        override val type = "pong"
    }
}
