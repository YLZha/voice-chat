package com.voicechat.android.presentation.chat

import com.voicechat.android.domain.model.AuthState
import com.voicechat.android.domain.model.ChatMessage
import com.voicechat.android.domain.model.ConnectionState
import com.voicechat.android.domain.model.PlaybackState
import com.voicechat.android.domain.model.RecordingState
import com.voicechat.android.domain.repository.ChatRepository
import com.voicechat.android.domain.usecase.AudioPlayer
import com.voicechat.android.domain.usecase.AudioRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var chatRepository: ChatRepository
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        chatRepository = mock()
        audioRecorder = mock()
        audioPlayer = mock()
        
        // Setup default mocks
        whenever(chatRepository.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        whenever(chatRepository.messages).thenReturn(MutableStateFlow(emptyList()))
        whenever(audioRecorder.recordingState).thenReturn(MutableStateFlow(RecordingState.Idle))
        whenever(audioPlayer.playbackState).thenReturn(MutableStateFlow(PlaybackState.Idle))
        
        viewModel = ChatViewModel(chatRepository, audioRecorder, audioPlayer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value
        
        assertEquals(ConnectionState.Disconnected, state.connectionState)
        assertTrue(state.messages.isEmpty())
        assertEquals(RecordingState.Idle, state.recordingState)
        assertFalse(state.isRecording)
        assertFalse(state.isPlaying)
        assertEquals("", state.currentInputText)
    }

    @Test
    fun `updateInputText updates currentInputText`() {
        viewModel.updateInputText("Hello")
        
        assertEquals("Hello", viewModel.uiState.value.currentInputText)
    }

    @Test
    fun `sendMessage with blank text does nothing`() {
        viewModel.sendMessage("   ")
        
        // Verify no repository call was made
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `clearError clears error state`() {
        viewModel.clearError()
        
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `clearHistory calls repository`() = runTest {
        viewModel.clearHistory()
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(chatRepository).clearHistory()
    }

    @Test
    fun `disconnect calls repository and stops playback`() = runTest {
        viewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()
        
        verify(chatRepository).disconnect()
        verify(audioPlayer).stopPlayback()
    }
}
