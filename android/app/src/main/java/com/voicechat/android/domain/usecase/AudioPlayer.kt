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
        const val DEFAULT_SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(2 * sampleRate) // 2 seconds buffer
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * Parse WAV header if present, returning (sampleRate, pcmData).
     * Falls back to raw PCM at DEFAULT_SAMPLE_RATE if not a valid WAV.
     */
    private fun parseAudioData(data: ByteArray): Pair<Int, ByteArray> {
        if (data.size > 44 &&
            data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() &&
            data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()
        ) {
            // Read sample rate from WAV header (bytes 24-27, little-endian)
            val sampleRate = (data[24].toInt() and 0xFF) or
                ((data[25].toInt() and 0xFF) shl 8) or
                ((data[26].toInt() and 0xFF) shl 16) or
                ((data[27].toInt() and 0xFF) shl 24)

            // Find the "data" sub-chunk
            var offset = 12 // skip RIFF header
            while (offset < data.size - 8) {
                val chunkId = String(data, offset, 4, Charsets.US_ASCII)
                val chunkSize = (data[offset + 4].toInt() and 0xFF) or
                    ((data[offset + 5].toInt() and 0xFF) shl 8) or
                    ((data[offset + 6].toInt() and 0xFF) shl 16) or
                    ((data[offset + 7].toInt() and 0xFF) shl 24)

                if (chunkId == "data") {
                    val start = offset + 8
                    val end = minOf(start + chunkSize, data.size)
                    return Pair(sampleRate, data.copyOfRange(start, end))
                }
                offset += 8 + chunkSize
            }
            // Fallback: assume data starts at byte 44
            return Pair(sampleRate, data.copyOfRange(44, data.size))
        }
        return Pair(DEFAULT_SAMPLE_RATE, data)
    }

    suspend fun playAudio(base64Audio: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isPlaying) {
                stopPlayback()
            }

            val rawData = Base64.decode(base64Audio, Base64.NO_WRAP)
            val (sampleRate, pcmData) = parseAudioData(rawData)

            audioTrack = createAudioTrack(sampleRate)

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                _playbackState.value = PlaybackState.Error("Failed to initialize AudioTrack")
                return@withContext Result.failure(Exception("AudioTrack initialization failed"))
            }

            _playbackState.value = PlaybackState.Playing
            audioTrack?.play()
            isPlaying = true

            val buffer = ByteArray(4096)
            var offset = 0

            while (isActive && isPlaying && offset < pcmData.size) {
                val toRead = minOf(4096, pcmData.size - offset)
                System.arraycopy(pcmData, offset, buffer, 0, toRead)

                val written = audioTrack?.write(buffer, 0, toRead) ?: -1
                if (written > 0) {
                    offset += written
                } else if (written < 0) {
                    _playbackState.value = PlaybackState.Error("Playback error: $written")
                    break
                }

                if (!isPlaying) break
            }

            // Wait for playback to finish
            while (isPlaying && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                kotlinx.coroutines.delay(100)
            }

            stopPlayback()
            Result.success(Unit)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.Error(e.message ?: "Playback failed")
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
            _playbackState.value = PlaybackState.Error(e.message ?: "Failed to stop playback")
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
