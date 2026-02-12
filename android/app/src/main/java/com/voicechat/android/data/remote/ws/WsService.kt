package com.voicechat.android.data.remote.ws

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WsService(private val okHttpClient: OkHttpClient) {
    
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
            val json = Json.encodeToString(WsMessage.serializer(), message)
            webSocket?.send(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun sendBytes(bytes: ByteArray) {
        webSocket?.send(okhttp3.internal.http2.Http2Stream.FrameHolder(bytes).byteString().utf8())
    }
    
    fun observeMessages(): Flow<String> = messageFlow
    
    fun disconnect() {
        webSocket?.close(1000, "Closing connection")
        webSocket = null
    }
}
