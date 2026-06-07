package com.pabloufor.voiceflow.domain.usecase.transcription

import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val transcriptionRepository: TranscriptionRepository,
) {
    suspend operator fun invoke(filePath: String): Resource<String> =
        transcriptionRepository.transcribe(filePath)
}