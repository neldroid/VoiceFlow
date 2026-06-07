package com.pabloufor.voiceflow.domain.usecase.chat

import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.Persona
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StreamAssistantReplyUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    operator fun invoke(history: List<ChatMessage>, persona: Persona): Flow<Resource<String>> =
        chatRepository.streamAssistantReply(history, persona)
}