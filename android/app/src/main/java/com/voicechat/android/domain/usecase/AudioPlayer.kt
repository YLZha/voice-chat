package com.voicechat.android.domain.usecase

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import com.voicechat.android.domain.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AudioPlayer @Inject constructor() {
    
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private fun createAudioTrack(): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(2 * SAMPLE_RATE) // 2 seconds buffer
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    suspend fun playAudio(base64Audio: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isPlaying) {
                stopPlayback()
            }

            audioTrack = createAudioTrack()
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                _playbackState.value = PlaybackState.Error(message = "Failed to initialize AudioTrack")
                return@withContext Result.failure(Exception("AudioTrack initialization failed"))
            }

            _playbackState.value = PlaybackState.Playing
            audioTrack?.play()
            isPlaying = true

            val audioData = Base64.decode(base64Audio, Base64.NO_WRAP)
            val sampleSize = 2 // 16-bit samples
            val buffer = ByteArray(4096) // 4KB buffer
            var offset = 0

            while (isActive && isPlaying && offset < audioData.size) {
                val toRead = minOf(4096, audioData.size - offset)
                System.arraycopy(audioData, offset, buffer, 0, toRead)
                
                val written = audioTrack?.write(buffer, 0, toRead) ?: -1
                if (written > 0) {
                    offset += written
                } else if (written < 0) {
                    _playbackState.value = PlaybackState.Error(message = "Playback error: $written")
                    break
                }
                
                // Check if playback was paused/stopped during write
                if (!isPlaying) break
            }

            // Wait for playback to finish
            while (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                kotlinx.coroutines.delay(100)
            }

            stopPlayback()
            Result.success(Unit)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.Error(message = e.message ?: "Playback failed")
            stopPlayback()
            Result.failure(e)
        }
    }

    suspend fun stopPlayback(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            isPlaying = false
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
            audioTrack = null
            _playbackState.value = PlaybackState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.Error(message = e.message ?: "Failed to stop playback")
            Result.failure(e)
        }
    }

    fun pausePlayback() {
        audioTrack?.pause()
        _playbackState.value = PlaybackState.Paused
    }

    fun resumePlayback() {
        audioTrack?.play()
        _playbackState.value = PlaybackState.Playing
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
