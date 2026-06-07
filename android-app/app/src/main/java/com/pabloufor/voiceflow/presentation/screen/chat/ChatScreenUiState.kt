package com.pabloufor.voiceflow.presentation.screen.chat

import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.ChatMessage

sealed class ChatScreenUiState {

    data object Empty : ChatScreenUiState()

    data class Active(
        val messages: List<ChatMessage>,
        val phase: Phase,
        val streamingAssistantText: String = "",
        val streamingAssistantError: String? = null,
        val streamingAssistantErrorCode: AppError? = null,
    ) : ChatScreenUiState() {

        val isInputBlocked: Boolean
            get() = phase == Phase.Transcribing || phase == Phase.Streaming
    }

    enum class Phase { Idle, Transcribing, Streaming, Error }
}
