package com.pabloufor.voiceflow.data.transcription

import com.pabloufor.voiceflow.data.toAppError
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import javax.inject.Inject

class TranscriptionRepositoryImpl @Inject constructor(
    private val remoteDataSource: TranscriptionRemoteDataSource
) : TranscriptionRepository {

    override suspend fun transcribe(filePath: String): Resource<String> =
        runCatching { remoteDataSource.transcribe(filePath) }
            .fold(
                onSuccess = { transcription ->
                    if (transcription.isBlank()) {
                        Resource.Error(error = AppError.EmptyTranscription)
                    } else {
                        Resource.Success(transcription)
                    }
                },
                onFailure = { error ->
                    Resource.Error(
                        cause = error,
                        error = error.toAppError(),
                    )
                },
            )
}
