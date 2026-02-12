package com.voicechat.android.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voicechat.android.domain.model.ChatMessage
import com.voicechat.android.domain.model.ConnectionState
import com.voicechat.android.domain.model.PlaybackState
import com.voicechat.android.domain.model.RecordingState
import com.voicechat.android.domain.repository.ChatRepository
import com.voicechat.android.domain.usecase.AudioPlayer
import com.voicechat.android.domain.usecase.AudioRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val messages: List<ChatMessage> = emptyList(),
    val recordingState: RecordingState = RecordingState.Idle,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val currentInputText: String = "",
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var audioChunkJob: Job? = null

    init {
        observeConnectionState()
        observeMessages()
        observeRecordingState()
        observePlaybackState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            chatRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.messages.collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    private fun observeRecordingState() {
        viewModelScope.launch {
            audioRecorder.recordingState.collect { state ->
                _uiState.update { it.copy(recordingState = state) }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
                _uiState.update { it.copy(isPlaying = state is PlaybackState.Playing) }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                chatRepository.connect()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            chatRepository.disconnect()
            audioPlayer.stopPlayback()
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(text)
                _uiState.update { it.copy(currentInputText = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(currentInputText = text) }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        
        viewModelScope.launch {
            val result = audioRecorder.startRecording()
            result.fold(
                onSuccess = { audioFlow ->
                    _uiState.update { it.copy(isRecording = true) }
                    
                    audioChunkJob = viewModelScope.launch {
                        // Collect audio chunks and send them
                        audioFlow.collect { chunk ->
                            try {
                                chatRepository.sendAudio(chunk)
                            } catch (e: Exception) {
                                // Handle error silently or show to user
                            }
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        
        viewModelScope.launch {
            audioChunkJob?.cancel()
            audioChunkJob = null
            
            audioRecorder.stopRecording()
            _uiState.update { it.copy(isRecording = false) }
        }
    }

    fun cancelRecording() {
        audioRecorder.cancelRecording()
        audioChunkJob?.cancel()
        audioChunkJob = null
        _uiState.update { it.copy(isRecording = false) }
    }

    fun playAudio(audioUrl: String) {
        viewModelScope.launch {
            audioPlayer.playAudio(audioUrl)
        }
    }

    fun stopAudio() {
        viewModelScope.launch {
            audioPlayer.stopPlayback()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioChunkJob?.cancel()
        viewModelScope.launch {
            audioRecorder.stopRecording()
            audioPlayer.stopPlayback()
            chatRepository.disconnect()
        }
    }
}
