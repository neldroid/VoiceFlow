package com.pabloufor.voiceflow.data.chat.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
)

@Serializable
data class ChatRequestDto(
    @SerialName("messages") val messages: List<MessageDto>,
    @SerialName("persona") val persona: String? = null,
)
