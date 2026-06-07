package com.pabloufor.voiceflow.domain.usecase.recording

import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecordingStateUseCase @Inject constructor(
    private val repository: AudioRecorderRepository,
) {
    operator fun invoke(): Flow<AudioRecordingState> = repository.recordingState
}
