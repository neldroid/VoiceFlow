package com.pabloufor.voiceflow.data.transcription

import com.pabloufor.voiceflow.data.transcription.dto.TranscriptionResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TranscriptionApi {

    @Multipart
    @POST("api/v1/transcribe")
    suspend fun transcribe(
        @Part audio: MultipartBody.Part,
    ): TranscriptionResponseDto
}
