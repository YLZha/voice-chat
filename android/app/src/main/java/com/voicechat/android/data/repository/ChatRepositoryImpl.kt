package com.voicechat.android.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException
import kotlin.math.pow

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val wsService: WsService,
    private val gson: Gson,
    @Named("wsUrl") private val wsUrl: String
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepo"
    }

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
                // Actually establish the WebSocket connection
                Log.d(TAG, "Connecting to $wsUrl")
                suspendCancellableCoroutine { cont ->
                    wsService.connect(
                        url = wsUrl,
                        onReady = {
                            if (cont.isActive) {
                                cont.resume(Unit) {}
                            }
                        },
                        onError = { error ->
                            if (cont.isActive) {
                                cont.resumeWithException(Exception(error))
                            } else {
                                // Post-connect failure — update state for reconnect
                                _connectionState.value = ConnectionState.Error(error)
                            }
                        }
                    )
                    cont.invokeOnCancellation { wsService.disconnect() }
                }

                Log.d(TAG, "WebSocket connected, sending auth")

                // Authenticate
                val token = tokenManager.getAccessToken()
                if (token != null) {
                    wsService.sendMessage(WsMessage.Auth(token))
                } else {
                    _connectionState.value = ConnectionState.Error("No access token available")
                    return@launch
                }

                _connectionState.value = ConnectionState.Connected
                reconnectAttempts = 0
                Log.d(TAG, "Connected and authenticated")

                // Start listening for messages
                listenForMessages()

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
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
                Log.e(TAG, "Error parsing message: ${e.message}")
            }
        }
    }

    private fun parseMessage(json: String): WsMessage {
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        val type = jsonObject.get("type")?.asString ?: ""
        return when (type) {
            "connected" -> gson.fromJson(json, WsMessage.ConnectionAck::class.java)
            "transcription" -> gson.fromJson(json, WsMessage.Transcription::class.java)
            "response" -> gson.fromJson(json, WsMessage.Response::class.java)
            "buffering" -> gson.fromJson(json, WsMessage.Buffering::class.java)
            "tts" -> gson.fromJson(json, WsMessage.TtsResponse::class.java)
            "error" -> gson.fromJson(json, WsMessage.Error::class.java)
            "pong" -> WsMessage.Pong
            "closed" -> WsMessage.Error(
                code = "closed",
                message = jsonObject.get("reason")?.asString ?: "Connection closed"
            )
            "info" -> WsMessage.Pong // ignore info messages
            else -> {
                Log.w(TAG, "Unknown message type: $type")
                WsMessage.Pong
            }
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
                // User's speech was transcribed — show what they said
                Log.d(TAG, "Transcription: ${message.text}")
                val userMessage = ChatMessage(
                    content = message.text,
                    isUser = true,
                    isLoading = false
                )
                _messages.update { it + userMessage }
            }

            is WsMessage.Response -> {
                // AI response with text and optional audio
                Log.d(TAG, "Response: ${message.text.take(80)}, audio=${message.audio != null}")
                val assistantMessage = ChatMessage(
                    content = message.text,
                    isUser = false,
                    isLoading = false,
                    audioUrl = message.audio
                )
                _messages.update { it + assistantMessage }
            }

            is WsMessage.Buffering -> {
                Log.d(TAG, "Buffering: ${message.bufferedSeconds}/${message.targetSeconds}s")
            }

            is WsMessage.TtsResponse -> {
                // Legacy TTS response handling
                _messages.update { currentMessages ->
                    currentMessages.map { msg ->
                        if (msg.id == message.messageId) {
                            msg.copy(audioUrl = message.audio)
                        } else {
                            msg
                        }
                    }
                }
            }

            is WsMessage.Error -> {
                Log.e(TAG, "Server error: ${message.code} - ${message.message}")
                if (message.code == "closed" || message.code == "connection_lost") {
                    _connectionState.value = ConnectionState.Error(message.message)
                }
            }

            is WsMessage.Pong -> { /* heartbeat */ }

            else -> { }
        }
    }

    override suspend fun disconnect() {
        isManuallyDisconnected = true
        connectionJob?.cancel()
        connectionJob = null
        wsService.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun sendMessage(text: String) {
        if (_connectionState.value !is ConnectionState.Connected) {
            throw IllegalStateException("Not connected to WebSocket")
        }

        val message = ChatMessage(content = text, isUser = true, isLoading = true)
        _messages.update { it + message }

        try {
            val wsMessage = WsMessage.TextMessage(content = text, messageId = message.id)
            wsService.sendMessage(wsMessage)

            _messages.update { messages ->
                messages + ChatMessage(content = "", isUser = false, id = message.id, isLoading = true)
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
        if (_connectionState.value !is ConnectionState.Connected) return
        // Send raw PCM audio as a binary WebSocket frame
        wsService.sendRawBytes(audioData)
    }

    override suspend fun notifyEndOfAudio() {
        if (_connectionState.value !is ConnectionState.Connected) return
        wsService.sendMessage("{\"type\":\"end_audio\"}")
    }

    override suspend fun clearHistory() {
        _messages.value = emptyList()
    }

    private suspend fun handleConnectionError(e: Exception) {
        if (isManuallyDisconnected) return

        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            val delayMs = baseReconnectDelay * (2.0.pow(reconnectAttempts - 1)).toLong()
                .coerceAtMost(30000L)

            _connectionState.value = ConnectionState.Reconnecting(reconnectAttempts)
            delay(delayMs)
            connect()
        } else {
            _connectionState.value = ConnectionState.Error("Max reconnection attempts reached")
        }
    }
}
