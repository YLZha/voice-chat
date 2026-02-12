package com.voicechat.android.data.remote.ws

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WsService(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    
    private var webSocket: WebSocket? = null
    private val messageFlow = MutableSharedFlow<String>(replay = 10)
    
    fun connect(url: String, onReady: () -> Unit, onError: (String) -> Unit) {
        try {
            val request = Request.Builder().url(url).build()
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    onReady()
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    messageFlow.tryEmit(text)
                }
                
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    messageFlow.tryEmit(bytes.utf8())
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    onError(t.message ?: "Unknown error")
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    messageFlow.tryEmit("{\"type\":\"closed\",\"reason\":\"$reason\"}")
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Connection failed")
        }
    }
    
    fun sendMessage(message: String) {
        webSocket?.send(message)
    }
    
    fun sendMessage(message: WsMessage) {
        try {
            val json = gson.toJson(message)
            webSocket?.send(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun sendBytes(bytes: ByteArray) {
        try {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val audioMessage = mapOf(
                "type" to "audio",
                "data" to base64
            )
            val json = gson.toJson(audioMessage)
            webSocket?.send(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun observeMessages(): Flow<String> = messageFlow
    
    fun disconnect() {
        webSocket?.close(1000, "Closing connection")
        webSocket = null
    }
}
