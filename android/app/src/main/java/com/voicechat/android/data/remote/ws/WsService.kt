package com.voicechat.android.data.remote.ws

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

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
                    Log.d("WsService", "WebSocket opened")
                    onReady()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("WsService", "Text message: ${text.take(200)}")
                    messageFlow.tryEmit(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.d("WsService", "Binary message: ${bytes.size} bytes")
                    messageFlow.tryEmit(bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    Log.e("WsService", "WebSocket failure: ${t.message}")
                    onError(t.message ?: "Unknown error")
                    messageFlow.tryEmit(
                        "{\"type\":\"error\",\"code\":\"connection_lost\"," +
                            "\"message\":\"${t.message ?: "Connection lost"}\"}"
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d("WsService", "WebSocket closed: $code $reason")
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

    /** Send raw binary data (e.g. PCM audio) as a binary WebSocket frame. */
    fun sendRawBytes(bytes: ByteArray) {
        webSocket?.send(ByteString.of(*bytes))
    }

    fun observeMessages(): Flow<String> = messageFlow

    fun disconnect() {
        webSocket?.close(1000, "Closing connection")
        webSocket = null
    }
}
