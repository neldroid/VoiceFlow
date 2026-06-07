package com.pabloufor.voiceflow.domain.repository

import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import kotlinx.coroutines.flow.Flow

interface AudioRecorderRepository {
    val recordingState: Flow<AudioRecordingState>
    suspend fun startRecording()
    suspend fun cancelRecording()
    suspend fun stopRecording()
    suspend fun resetRecorder()
}
