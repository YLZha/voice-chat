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
        val audio: String,
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
        val audio: String,
        @SerializedName("message_id")
        val messageId: String? = null
    ) : WsMessage() {
        override val type = "tts"
    }

    data class Transcription(
        @SerializedName("text")
        val text: String,
        @SerializedName("message_id")
        val messageId: String? = null
    ) : WsMessage() {
        override val type = "transcription"
    }

    /** Full response from backend: transcription text + optional base64 WAV audio. */
    data class Response(
        @SerializedName("text")
        val text: String,
        @SerializedName("audio")
        val audio: String? = null,
        @SerializedName("processing_time")
        val processingTime: Double? = null
    ) : WsMessage() {
        override val type = "response"
    }

    /** Buffering progress while backend accumulates audio chunks. */
    data class Buffering(
        @SerializedName("buffered_seconds")
        val bufferedSeconds: Double = 0.0,
        @SerializedName("target_seconds")
        val targetSeconds: Double = 5.0
    ) : WsMessage() {
        override val type = "buffering"
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
