package com.pabloufor.voiceflow.domain.model

sealed class AudioRecordingState {
    data object Idle : AudioRecordingState()
    data object Recording : AudioRecordingState()
    data class Stopped(val filePath: String) : AudioRecordingState()
    data class Error(val error: AppError) : AudioRecordingState()
}
