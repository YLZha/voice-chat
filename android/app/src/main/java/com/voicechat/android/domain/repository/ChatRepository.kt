package com.voicechat.android.domain.repository

import com.voicechat.android.domain.model.ChatMessage
import com.voicechat.android.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val connectionState: StateFlow<ConnectionState>
    val messages: StateFlow<List<ChatMessage>>
    
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendMessage(text: String)
    suspend fun sendAudio(audioData: ByteArray)
    suspend fun clearHistory()
}
