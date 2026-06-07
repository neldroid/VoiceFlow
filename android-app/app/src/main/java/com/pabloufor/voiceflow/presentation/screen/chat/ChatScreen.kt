package com.pabloufor.voiceflow.presentation.screen.chat

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Persona
import com.pabloufor.voiceflow.presentation.component.AudioRecorder
import com.pabloufor.voiceflow.presentation.component.ChatMessagesList
import com.pabloufor.voiceflow.presentation.component.ChatTopBar
import com.pabloufor.voiceflow.presentation.component.ResetConversationDialog
import com.pabloufor.voiceflow.presentation.component.WelcomeMessage
import com.pabloufor.voiceflow.presentation.theme.VoiceFlowTheme

@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatScreenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ttsEnabled by viewModel.ttsEnabled.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()

    ChatScreenContent(
        uiState = uiState,
        ttsEnabled = ttsEnabled,
        selectedPersona = selectedPersona,
        onPersonaSelected = viewModel::onPersonaSelected,
        onAudioMessageSent = viewModel::onSendAudioMessage,
        onResetConversation = viewModel::onResetConversation,
        onRetry = viewModel::onRetry,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

@Composable
internal fun ChatScreenContent(
    uiState: ChatScreenUiState,
    ttsEnabled: Boolean,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    onAudioMessageSent: (String) -> Unit,
    onResetConversation: () -> Unit,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val active = uiState as? ChatScreenUiState.Active
    val messages = active?.messages.orEmpty()
    val phase = active?.phase
    val streamingText = active?.streamingAssistantText.orEmpty()
    val streamingError = active?.streamingAssistantError
    val streamingErrorCode = active?.streamingAssistantErrorCode

    val hasStreamingBubble = streamingText.isNotEmpty() || streamingError != null
    LaunchedEffect(messages.size, hasStreamingBubble, streamingText.length) {
        val targetIndex = messages.size - 1 + (if (hasStreamingBubble) 1 else 0)
        if (targetIndex >= 0) listState.animateScrollToItem(targetIndex)
    }

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        ResetConversationDialog(
            onConfirm = {
                onResetConversation()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopBar(
                showResetAction = uiState is ChatScreenUiState.Active,
                onResetClick = { showResetDialog = true },
                onSettingsClick = onNavigateToSettings,
                selectedPersona = selectedPersona,
                onPersonaSelected = onPersonaSelected,
            )
        },
    ) { innerPadding ->
        val layoutModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding()

        val inputBlocked = (uiState as? ChatScreenUiState.Active)?.isInputBlocked == true

        if (isLandscape) {
            Row(
                modifier = layoutModifier,
                verticalAlignment = Alignment.Bottom,
            ) {
                MessagesArea(
                    messages = messages,
                    phase = phase,
                    streamingAssistantText = streamingText,
                    streamingAssistantError = streamingError,
                    streamingAssistantErrorCode = streamingErrorCode,
                    ttsEnabled = ttsEnabled,
                    listState = listState,
                    onRetry = onRetry,
                    modifier = Modifier.weight(0.7f),
                )
                MessageInputBar(
                    enabled = !inputBlocked,
                    onAudioMessageSent = onAudioMessageSent,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.3f),
                )
            }
        } else {
            Column(
                modifier = layoutModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MessagesArea(
                    messages = messages,
                    phase = phase,
                    streamingAssistantText = streamingText,
                    streamingAssistantError = streamingError,
                    streamingAssistantErrorCode = streamingErrorCode,
                    ttsEnabled = ttsEnabled,
                    listState = listState,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
                MessageInputBar(
                    enabled = !inputBlocked,
                    onAudioMessageSent = onAudioMessageSent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MessagesArea(
    messages: List<ChatMessage>,
    phase: ChatScreenUiState.Phase?,
    streamingAssistantText: String,
    streamingAssistantError: String?,
    streamingAssistantErrorCode: AppError?,
    ttsEnabled: Boolean,
    listState: LazyListState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (messages.isEmpty() && streamingAssistantText.isEmpty() && streamingAssistantError == null) {
            WelcomeMessage()
        } else {
            ChatMessagesList(
                messages = messages,
                phase = phase ?: ChatScreenUiState.Phase.Idle,
                streamingAssistantText = streamingAssistantText,
                streamingAssistantError = streamingAssistantError,
                streamingAssistantErrorCode = streamingAssistantErrorCode,
                ttsEnabled = ttsEnabled,
                listState = listState,
                onRetry = onRetry,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    enabled: Boolean,
    onAudioMessageSent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10),
        shadowElevation = 4.dp,
        modifier = modifier.padding(8.dp),
    ) {
        AudioRecorder(
            enabled = enabled,
            onAudioSent = onAudioMessageSent,
        )
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun ChatScreenEmptyPreview() {
    VoiceFlowTheme {
        ChatScreenContent(
            uiState = ChatScreenUiState.Empty,
            ttsEnabled = true,
            selectedPersona = Persona.Friendly,
            onPersonaSelected = {},
            onAudioMessageSent = {},
            onResetConversation = {},
            onRetry = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenTranscribingPreview() {
    VoiceFlowTheme {
        ChatScreenContent(
            uiState = ChatScreenUiState.Active(
                messages = listOf(
                    ChatMessage(
                        content = MessageContent.Audio("/cache/recording_001.mp4"),
                        author = MessageAuthor.User,
                    ),
                ),
                phase = ChatScreenUiState.Phase.Transcribing,
            ),
            ttsEnabled = true,
            selectedPersona = Persona.Friendly,
            onPersonaSelected = {},
            onAudioMessageSent = {},
            onResetConversation = {},
            onRetry = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenWithMessagesPreview() {
    VoiceFlowTheme {
        ChatScreenContent(
            uiState = ChatScreenUiState.Active(
                messages = listOf(
                    ChatMessage(
                        content = MessageContent.Text("Hey, how can I help you?"),
                        author = MessageAuthor.Assistant,
                    ),
                    ChatMessage(
                        content = MessageContent.Text("Can you summarise this audio?"),
                        author = MessageAuthor.User,
                    ),
                    ChatMessage(
                        content = MessageContent.Audio("/cache/recording_001.mp4"),
                        author = MessageAuthor.User,
                    ),
                ),
                phase = ChatScreenUiState.Phase.Streaming,
                streamingAssistantText = "Sure, summarising now…",
            ),
            ttsEnabled = true,
            selectedPersona = Persona.Friendly,
            onPersonaSelected = {},
            onAudioMessageSent = {},
            onResetConversation = {},
            onRetry = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenStreamErrorWithPartialPreview() {
    VoiceFlowTheme {
        ChatScreenContent(
            uiState = ChatScreenUiState.Active(
                messages = listOf(
                    ChatMessage(
                        content = MessageContent.Text("Can you summarise this audio?"),
                        author = MessageAuthor.User,
                    ),
                    ChatMessage(
                        content = MessageContent.Text("Sure, here are the first few words…"),
                        author = MessageAuthor.Assistant,
                        errorMessage = "Upstream rate limit reached. Please retry.",
                    ),
                ),
                phase = ChatScreenUiState.Phase.Error,
            ),
            ttsEnabled = false,
            selectedPersona = Persona.Friendly,
            onPersonaSelected = {},
            onAudioMessageSent = {},
            onResetConversation = {},
            onRetry = {},
            onNavigateToSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenStreamErrorNoTokensPreview() {
    VoiceFlowTheme {
        ChatScreenContent(
            uiState = ChatScreenUiState.Active(
                messages = listOf(
                    ChatMessage(
                        content = MessageContent.Text("Can you summarise this audio?"),
                        author = MessageAuthor.User,
                    ),
                ),
                phase = ChatScreenUiState.Phase.Error,
                streamingAssistantError = "Upstream rate limit reached. Please retry.",
            ),
            ttsEnabled = false,
            selectedPersona = Persona.Friendly,
            onPersonaSelected = {},
            onAudioMessageSent = {},
            onResetConversation = {},
            onRetry = {},
            onNavigateToSettings = {},
        )
    }
}

// endregion
