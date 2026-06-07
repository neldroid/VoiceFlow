package com.pabloufor.voiceflow.data.chat

import com.pabloufor.voiceflow.data.chat.dto.ChatRequestDto
import com.pabloufor.voiceflow.data.chat.dto.MessageDto
import com.pabloufor.voiceflow.data.toAppError
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Persona
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val remoteDataSource: ChatRemoteDataSource
) : ChatRepository {

    override fun streamAssistantReply(history: List<ChatMessage>, persona: Persona): Flow<Resource<String>> {
        val dtos = history
            .filter { it.content is MessageContent.Text }
            .map { message ->
                val role = when (message.author) {
                    MessageAuthor.User -> "user"
                    MessageAuthor.Assistant -> "assistant"
                }
                val text = (message.content as MessageContent.Text).value
                MessageDto(role = role, content = text)
            }
        return remoteDataSource.streamChat(ChatRequestDto(messages = dtos, persona = persona.apiValue))
            .map { event ->
                when (event) {
                    is ChatStreamEvent.Token -> Resource.Success(event.text)
                    is ChatStreamEvent.ServerError -> Resource.Error(
                        message = event.message,
                        error = event.appError,
                    )
                }
            }
            .catch { error ->
                emit(
                    Resource.Error(
                        message = error.message ?: "",
                        cause = error,
                        error = error.toAppError(),
                    ),
                )
            }
    }
}
