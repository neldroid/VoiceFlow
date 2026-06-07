package com.pabloufor.voiceflow.presentation.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Persona
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.usecase.settings.ObserveTtsEnabledSettingUseCase
import com.pabloufor.voiceflow.domain.usecase.chat.StreamAssistantReplyUseCase
import com.pabloufor.voiceflow.domain.usecase.transcription.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatScreenViewModel @Inject constructor(
    private val transcribeAudio: TranscribeAudioUseCase,
    private val streamAssistantReply: StreamAssistantReplyUseCase,
    private val observeTtsEnabledSettingUseCase: ObserveTtsEnabledSettingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatScreenUiState>(ChatScreenUiState.Empty)
    val uiState: StateFlow<ChatScreenUiState> = _uiState.asStateFlow()

    private val _selectedPersona = MutableStateFlow(Persona.Friendly)
    val selectedPersona: StateFlow<Persona> = _selectedPersona.asStateFlow()

    val ttsEnabled: StateFlow<Boolean> = observeTtsEnabledSettingUseCase.invoke().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    private var transcriptionJob: Job? = null
    private var assistantReplyJob: Job? = null

    fun onPersonaSelected(persona: Persona) {
        _selectedPersona.value = persona
    }

    fun onResetConversation() {
        cancelInFlight()
        _uiState.value = ChatScreenUiState.Empty
    }

    /**
     * Retries the most recent failure. If the last user message is an audio bubble
     * tagged with an error, re-runs transcription on its file. Otherwise (streaming
     * failed before/after partial tokens), drops the failed assistant remnant and
     * re-requests the assistant reply from the existing history.
     */
    fun onRetry() {
        val active = _uiState.value as? ChatScreenUiState.Active ?: return
        if (active.phase != ChatScreenUiState.Phase.Error) return

        cancelInFlight()

        val failedAudio = active.messages.lastOrNull {
            it.content is MessageContent.Audio && it.errorMessage != null
        }
        if (failedAudio != null) {
            val filePath = (failedAudio.content as MessageContent.Audio).filePath
            _uiState.update { current ->
                ChatScreenUiState.Active(
                    messages = current.messages().filter { it.id != failedAudio.id },
                    phase = ChatScreenUiState.Phase.Idle,
                )
            }
            onSendAudioMessage(filePath)
            return
        }

        val cleanedMessages = active.messages.let { messages ->
            val last = messages.lastOrNull()
            if (last?.author == MessageAuthor.Assistant && last.errorMessage != null) {
                messages.dropLast(1)
            } else messages
        }
        _uiState.update {
            ChatScreenUiState.Active(
                messages = cleanedMessages,
                phase = ChatScreenUiState.Phase.Idle,
            )
        }
        requestAssistantReply()
    }

    fun onSendAudioMessage(filePath: String) {
        cancelInFlight()

        val audioMessage = ChatMessage(
            content = MessageContent.Audio(filePath),
            author = MessageAuthor.User,
        )
        _uiState.update { current ->
            ChatScreenUiState.Active(
                messages = current.messages() + audioMessage,
                phase = ChatScreenUiState.Phase.Transcribing,
            )
        }

        transcriptionJob = viewModelScope.launch {
            when (val result = transcribeAudio(filePath)) {
                is Resource.Success -> {
                    val textMessage = ChatMessage(
                        content = MessageContent.Text(result.data),
                        author = MessageAuthor.User,
                    )
                    _uiState.update { current ->
                        ChatScreenUiState.Active(
                            messages = current.messages().map { msg ->
                                if (msg.id == audioMessage.id) textMessage else msg
                            },
                            phase = ChatScreenUiState.Phase.Idle,
                        )
                    }
                    requestAssistantReply()
                }

                is Resource.Error -> {
                    _uiState.update { current ->
                        ChatScreenUiState.Active(
                            messages = current.messages().map { msg ->
                                if (msg.id == audioMessage.id) {
                                    msg.copy(
                                        errorMessage = result.message,
                                        errorCode = result.error,
                                    )
                                } else msg
                            },
                            phase = ChatScreenUiState.Phase.Error,
                        )
                    }
                }
            }
        }
    }

    private fun requestAssistantReply() {
        val history = getTextHistory() ?: return
        val persona = _selectedPersona.value

        startStreamingState()

        assistantReplyJob = viewModelScope.launch {
            collectAssistantStream(history, persona).onSuccess(::commitFinalResult)
        }
    }

    private fun getTextHistory(): List<ChatMessage>? {
        val history = _uiState.value.messages()
            .filter { it.content is MessageContent.Text }

        return history.takeIf { it.isNotEmpty() }
    }

    private fun startStreamingState() {
        _uiState.update { current ->
            ChatScreenUiState.Active(
                messages = current.messages(),
                phase = ChatScreenUiState.Phase.Streaming,
                streamingAssistantText = "",
            )
        }
    }

    private suspend fun collectAssistantStream(
        history: List<ChatMessage>,
        persona: Persona,
    ): StreamResult {
        val buffer = StringBuilder()
        var failed = false

        streamAssistantReply(history, persona).collect { resource ->
            when (resource) {
                is Resource.Success -> {
                    buffer.append(resource.data)
                    updateStreamingText(buffer)
                }

                is Resource.Error -> {
                    failed = true
                    handlePartialOrError(buffer, resource.message, resource.error)
                }
            }
        }

        return StreamResult(
            text = buffer.toString(),
            failed = failed,
        )
    }

    private fun updateStreamingText(buffer: StringBuilder) {
        _uiState.update { current ->
            ChatScreenUiState.Active(
                messages = current.messages(),
                phase = ChatScreenUiState.Phase.Streaming,
                streamingAssistantText = buffer.toString(),
            )
        }
    }

    private fun handlePartialOrError(
        buffer: StringBuilder,
        errorMessage: String,
        errorCode: AppError?,
    ) {
        val partial = buffer.toString()

        _uiState.update { current ->
            if (partial.isNotEmpty()) {
                ChatScreenUiState.Active(
                    messages = current.messages() + ChatMessage(
                        content = MessageContent.Text(partial),
                        author = MessageAuthor.Assistant,
                        errorMessage = errorMessage,
                        errorCode = errorCode,
                    ),
                    phase = ChatScreenUiState.Phase.Error,
                )
            } else {
                ChatScreenUiState.Active(
                    messages = current.messages(),
                    phase = ChatScreenUiState.Phase.Error,
                    streamingAssistantError = errorMessage,
                    streamingAssistantErrorCode = errorCode,
                )
            }
        }
    }

    private fun commitFinalResult(finalText: String) {
        _uiState.update { current ->
            val committed = if (finalText.isNotEmpty()) {
                current.messages() + ChatMessage(
                    content = MessageContent.Text(finalText),
                    author = MessageAuthor.Assistant,
                )
            } else current.messages()

            ChatScreenUiState.Active(
                messages = committed,
                phase = ChatScreenUiState.Phase.Idle,
            )
        }
    }

    private fun cancelInFlight() {
        transcriptionJob?.cancel()
        assistantReplyJob?.cancel()
        transcriptionJob = null
        assistantReplyJob = null
    }

    private fun ChatScreenUiState.messages(): List<ChatMessage> =
        (this as? ChatScreenUiState.Active)?.messages ?: emptyList()
}

private data class StreamResult(
    val text: String,
    val failed: Boolean,
) {
    inline fun onSuccess(block: (String) -> Unit) {
        if (!failed && text.isNotEmpty()) {
            block(text)
        }
    }
}
