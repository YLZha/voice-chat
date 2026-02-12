package com.voicechat.android.domain.usecase

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.voicechat.android.domain.model.RecordingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
    }

    val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
    }

    fun hasRecordingPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startRecording(): Result<Flow<ByteArray>> = withContext(Dispatchers.IO) {
        try {
            if (!hasRecordingPermission()) {
                return@withContext Result.failure(SecurityException("Recording permission not granted"))
            }

            if (isRecording) {
                return@withContext Result.failure(IllegalStateException("Already recording"))
            }

            _recordingState.value = RecordingState.Recording
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Failed to initialize AudioRecord")
                return@withContext Result.failure(Exception("AudioRecord initialization failed"))
            }

            audioRecord?.startRecording()
            isRecording = true

            val audioFlow = flow {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readCount > 0) {
                        emit(buffer.copyOf(readCount))
                    } else if (readCount < 0) {
                        _recordingState.value = RecordingState.Error("Recording error: $readCount")
                        break
                    }
                }
            }

            Result.success(audioFlow)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    suspend fun stopRecording(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isRecording) {
                return@withContext Result.success(Unit)
            }

            isRecording = false
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            
            _recordingState.value = RecordingState.Idle
            Result.success(Unit)
        } catch (e: Exception) {
            _recordingState.value = RecordingState.Error(e.message ?: "Failed to stop recording")
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        _recordingState.value = RecordingState.Idle
    }
}
