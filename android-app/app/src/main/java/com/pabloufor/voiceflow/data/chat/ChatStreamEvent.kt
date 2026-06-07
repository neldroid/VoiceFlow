package com.pabloufor.voiceflow.data.chat

import com.pabloufor.voiceflow.domain.model.AppError

sealed class ChatStreamEvent {
    data class Token(val text: String) : ChatStreamEvent()
    data class ServerError(
        val code: String,
        val message: String,
        val appError: AppError,
    ) : ChatStreamEvent()
}
