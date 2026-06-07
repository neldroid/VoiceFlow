package com.pabloufor.voiceflow.domain.usecase.recording

import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import javax.inject.Inject

class CancelRecordingUseCase @Inject constructor(
    private val repository: AudioRecorderRepository,
) {
    suspend operator fun invoke() = repository.cancelRecording()
}
