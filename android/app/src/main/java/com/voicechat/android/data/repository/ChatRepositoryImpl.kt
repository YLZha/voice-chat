package com.voicechat.android.data.repository

import android.util.Base64
import com.google.gson.Gson
import com.voicechat.android.data.local.TokenManager
import com.voicechat.android.data.remote.ws.WsMessage
import com.voicechat.android.data.remote.ws.WsService
import com.voicechat.android.domain.model.ChatMessage
import com.voicechat.android.domain.model.ConnectionState
import com.voicechat.android.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.pow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val wsService: WsService,
    private val gson: Gson
) : ChatRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private var reconnectAttempts = 0
    private var isManuallyDisconnected = false
    private val maxReconnectAttempts = 5
    private val baseReconnectDelay = 1000L

    override suspend fun connect() {
        if (connectionJob?.isActive == true) return
        
        isManuallyDisconnected = false
        connectionJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting
            
            try {
                // Wait for WebSocket connection ( Scarlet handles this)
                delay(500)
                
                // Send authentication
                val token = tokenManager.getAccessToken()
                if (token != null) {
                    wsService.sendMessage(WsMessage.Auth(token))
                } else {
                    _connectionState.value = ConnectionState.Error("No access token available")
                    return@launch
                }
                
                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
                
                // Start listening for messages
                listenForMessages()
                
            } catch (e: Exception) {
                handleConnectionError(e)
            }
        }
    }

    private suspend fun listenForMessages() {
        wsService.observeMessages().collect { messageJson ->
            try {
                val message = parseMessage(messageJson)
                handleIncomingMessage(message)
            } catch (e: Exception) {
                // Log error but don't crash
            }
        }
    }

    private fun parseMessage(json: String): WsMessage {
        val type = gson.fromJson(json, WsMessage::class.java).type
        return when (type) {
            "connected" -> gson.fromJson(json, WsMessage.ConnectionAck::class.java)
            "transcription" -> gson.fromJson(json, WsMessage.Transcription::class.java)
            "tts" -> gson.fromJson(json, WsMessage.TtsResponse::class.java)
            "error" -> gson.fromJson(json, WsMessage.Error::class.java)
            "pong" -> WsMessage.Pong
            else -> gson.fromJson(json, WsMessage::class.java)
        }
    }

    private fun handleIncomingMessage(message: WsMessage) {
        when (message) {
            is WsMessage.ConnectionAck -> {
                if (message.status == "ok") {
                    _connectionState.value = ConnectionState.Connected
                    reconnectAttempts = 0
                }
            }
            
            is WsMessage.Transcription -> {
                _messages.update { currentMessages ->
                    currentMessages.map { msg ->
                        if (msg.id == message.id && msg.isLoading) {
                            msg.copy(content = message.text, isLoading = false)
                        } else {
                            msg
                        }
                    }
                }
            }
            
            is WsMessage.TtsResponse -> {
                _messages.update { currentMessages ->
                    currentMessages.map { msg ->
                        if (msg.id == message.id) {
                            msg.copy(audioUrl = message.audio)
                        } else {
                            msg
                        }
                    }
                }
            }
            
            is WsMessage.Error -> {
                _connectionState.value = ConnectionState.Error(message.message)
            }
            
            is WsMessage.Pong -> {
                // Heartbeat response, connection is alive
            }
            
            else -> { /* Ignore other message types */ }
        }
    }

    override suspend fun disconnect() {
        isManuallyDisconnected = true
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(text: String) {
        if (_connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Not connected to WebSocket")
        }

        val message = ChatMessage(
            content = text,
            isUser = true,
            isLoading = true
        )
        
        _messages.update { it + message }
        
        try {
            val wsMessage = WsMessage.TextMessage(
                content = text,
                messageId = message.id
            )
            wsService.sendMessage(wsMessage)
            
            // Add assistant placeholder
            _messages.update { messages ->
                messages + ChatMessage(
                    content = "",
                    isUser = false,
                    messageId = message.id,
                    isLoading = true
                )
            }
        } catch (e: Exception) {
            _messages.update { messages ->
                messages.map { msg ->
                    if (msg.id == message.id) msg.copy(isError = true, isLoading = false)
                    else msg
                }
            }
        }
    }

    override suspend fun sendAudio(audioData: ByteArray) {
        if (_connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Not connected to WebSocket")
        }

        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        val messageId = java.util.UUID.randomUUID().toString()
        
        // Add user message placeholder
        val message = ChatMessage(
            content = "Voice message",
            isUser = true,
            messageId = messageId,
            isLoading = true
        )
        _messages.update { it + message }
        
        // Add assistant placeholder
        _messages.update { messages ->
            messages + ChatMessage(
                content = "",
                isUser = false,
                messageId = messageId,
                isLoading = true
            )
        }
        
        try {
            wsService.sendMessage(
                WsMessage.AudioChunk(
                    audio = base64Audio,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            _messages.update { messages ->
                messages.map { msg ->
                    if (msg.id == messageId) msg.copy(isError = true, isLoading = false)
                    else msg
                }
            }
        }
    }

    override suspend fun clearHistory() {
        _messages.value = emptyList()
    }

    private suspend fun handleConnectionError(e: Exception) {
        if (isManuallyDisconnected) return
        
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            val delay = baseReconnectDelay * (2.0.pow(reconnectAttempts - 1)).toLong()
                .coerceAtMost(30000L) // Max 30 seconds
            
            _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts)
            delay(delay)
            
            // Try to refresh token if needed
            if (tokenManager.shouldRefreshToken()) {
                val refreshResult = refreshToken()
                if (refreshResult.isFailure) {
                    // Can't refresh, stay disconnected
                    return
                }
            }
            
            connect()
        } else {
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
        }
    }

    private fun refreshToken(): Result<String> {
        // This is called from the repository, but actual refresh logic
        // should be in AuthRepository. For now, we'll throw an exception
        // that will be caught and handled by the auth layer
        return Result.failure(Exception("Token refresh not implemented in ChatRepository"))
    }
}
