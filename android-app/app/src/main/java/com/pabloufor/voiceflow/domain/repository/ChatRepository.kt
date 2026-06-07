package com.pabloufor.voiceflow.domain.repository

import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.Persona
import com.pabloufor.voiceflow.domain.model.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun streamAssistantReply(history: List<ChatMessage>, persona: Persona): Flow<Resource<String>>
}
