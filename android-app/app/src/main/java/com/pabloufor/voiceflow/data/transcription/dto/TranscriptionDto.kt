package com.pabloufor.voiceflow.data.transcription.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptionResponseDto(
    @SerialName("text") val text: String,
)
