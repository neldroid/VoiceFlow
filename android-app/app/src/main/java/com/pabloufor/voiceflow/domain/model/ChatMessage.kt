package com.pabloufor.voiceflow.domain.model

import java.time.Instant
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: MessageContent,
    val author: MessageAuthor,
    val timestamp: Instant = Instant.now(),
    val errorMessage: String? = null,
    val errorCode: AppError? = null,
)

enum class MessageAuthor { User, Assistant }

sealed class MessageContent {
    data class Text(val value: String) : MessageContent()
    data class Audio(val filePath: String) : MessageContent()
}
