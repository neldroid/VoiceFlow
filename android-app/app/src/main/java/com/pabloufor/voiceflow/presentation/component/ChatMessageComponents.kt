package com.pabloufor.voiceflow.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pabloufor.voiceflow.R
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.presentation.screen.chat.ChatScreenUiState

private const val STREAMING_BUBBLE_KEY = "streaming_assistant_bubble"

@Composable
fun ChatMessagesList(
    messages: List<ChatMessage>,
    phase: ChatScreenUiState.Phase,
    streamingAssistantText: String,
    streamingAssistantError: String?,
    streamingAssistantErrorCode: AppError?,
    ttsEnabled: Boolean,
    listState: LazyListState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tts = rememberTtsController()
    val isStreaming = phase == ChatScreenUiState.Phase.Streaming
    val hasStreamingBubble = isStreaming && streamingAssistantText.isNotEmpty()

    LaunchedEffect(hasStreamingBubble, streamingAssistantText) {
        if (ttsEnabled && hasStreamingBubble) {
            tts.speakChunk(STREAMING_BUBBLE_KEY, streamingAssistantText)
        }
    }

    // Flush trailing text when streaming finishes; stop immediately if TTS is toggled off mid-stream
    LaunchedEffect(isStreaming) {
        if (!isStreaming) tts.flush()
    }
    LaunchedEffect(ttsEnabled) {
        if (!ttsEnabled) tts.stop()
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Bottom),
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(
                message = message,
                isTranscribing = phase == ChatScreenUiState.Phase.Transcribing
                        && message == messages.lastOrNull(),
            )
        }

        when {
            hasStreamingBubble ->
                item(key = STREAMING_BUBBLE_KEY) {
                    StreamingAssistantBubble(text = streamingAssistantText)
                }
            streamingAssistantError != null ->
                item(key = STREAMING_BUBBLE_KEY) {
                    MessageBubble(
                        message = ChatMessage(
                            content = MessageContent.Text(stringResource(R.string.chat_typing_indicator)),
                            author = MessageAuthor.Assistant,
                            errorMessage = streamingAssistantError,
                            errorCode = streamingAssistantErrorCode,
                        ),
                        isTranscribing = false,
                    )
                }
            isStreaming ->
                item(key = "typing_indicator") { TypingIndicator() }
        }

        if (phase == ChatScreenUiState.Phase.Error) {
            item(key = "retry_action") { RetryAction(onClick = onRetry) }
        }
    }
}

@Composable
private fun RetryAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("retry_action"),
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(onClick = onClick) {
            Text(text = stringResource(R.string.chat_retry))
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
) {
    val isUser = message.author == MessageAuthor.User
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.75f).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("message_bubble"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = maxBubbleWidth),
        ) {
            when (val content = message.content) {
                is MessageContent.Text -> Text(
                    text = content.value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                is MessageContent.Audio -> AudioMessageBubble(
                    isUser = isUser,
                    isTranscribing = isTranscribing,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (message.errorMessage != null || message.errorCode != null) {
            Text(
                text = appErrorText(error = message.errorCode, fallback = message.errorMessage),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun AudioMessageBubble(
    isUser: Boolean,
    isTranscribing: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isTranscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(R.string.recorder_transcribing),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = stringResource(R.string.recorder_cd_audio_message),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.recorder_audio_message),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun StreamingAssistantBubble(text: String, modifier: Modifier = Modifier) {
    MessageBubble(
        message = ChatMessage(
            content = MessageContent.Text(text),
            author = MessageAuthor.Assistant,
        ),
        isTranscribing = false,
        modifier = modifier,
    )
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 4.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(R.string.chat_typing_indicator),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
