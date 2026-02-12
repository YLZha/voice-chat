package com.voicechat.android.data.remote.ws

import com.github.tinder.scarlet.ws.Send
import com.github.tinder.scarlet.ws.TextSocket
import kotlinx.coroutines.flow.Flow

interface WsService {
    fun observeMessages(): Flow<String>
    
    @Send
    fun sendMessage(message: String)
    
    @Send
    fun sendMessage(message: WsMessage)
}
