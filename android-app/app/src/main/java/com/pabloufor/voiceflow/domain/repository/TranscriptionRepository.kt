package com.pabloufor.voiceflow.domain.repository

import com.pabloufor.voiceflow.domain.model.Resource

interface TranscriptionRepository {
    suspend fun transcribe(filePath: String): Resource<String>
}
