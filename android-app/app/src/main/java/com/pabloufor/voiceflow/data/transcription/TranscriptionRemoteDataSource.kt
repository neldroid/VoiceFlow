package com.pabloufor.voiceflow.data.transcription

import com.pabloufor.voiceflow.core.concurrency.DispatcherProvider
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class TranscriptionRemoteDataSource @Inject constructor(
    private val api: TranscriptionApi,
    private val dispatcher: DispatcherProvider
) {

    suspend fun transcribe(filePath: String): String = withContext(dispatcher.io) {
        val file = File(filePath)

        val part = MultipartBody.Part.createFormData(
            name = "audio",
            filename = file.name,
            body = file.asRequestBody("audio/m4a".toMediaType()),
        )
        api.transcribe(part).text
    }
}
