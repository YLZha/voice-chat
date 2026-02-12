package com.voicechat.android.domain.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val audioUrl: String? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

sealed class RecordingState {
    data object Idle : RecordingState()
    data object Recording : RecordingState()
    data object Processing : RecordingState()
    data class Error(val message: String) : RecordingState()
}

sealed class PlaybackState {
    data object Idle : PlaybackState()
    data object Playing : PlaybackState()
    data object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
