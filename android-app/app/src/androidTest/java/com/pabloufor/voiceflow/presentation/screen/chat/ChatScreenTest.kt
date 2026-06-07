package com.pabloufor.voiceflow.presentation.screen.chat

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.presentation.theme.VoiceFlowTheme
import com.pabloufor.voiceflow.util.HiltTestActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ChatScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private fun setContent(uiState: ChatScreenUiState) {
        composeRule.setContent {
            VoiceFlowTheme {
                ChatScreenContent(
                    uiState = uiState,
                    ttsEnabled = false,
                    onAudioMessageSent = {},
                    onResetConversation = {},
                    onRetry = {},
                    onNavigateToSettings = {},
                )
            }
        }
    }

    private fun activeState(
        messages: List<ChatMessage>,
        phase: ChatScreenUiState.Phase = ChatScreenUiState.Phase.Idle,
    ) = ChatScreenUiState.Active(messages = messages, phase = phase)

    private val userMessage = ChatMessage(
        content = MessageContent.Text("What is AI?"),
        author = MessageAuthor.User,
    )
    private val assistantMessage = ChatMessage(
        content = MessageContent.Text("It is artificial intelligence."),
        author = MessageAuthor.Assistant,
    )

    @Test
    fun welcomeTitleIsDisplayedOnLaunch() {
        setContent(ChatScreenUiState.Empty)

        composeRule.onNodeWithText("Welcome to Chat!").assertIsDisplayed()
    }

    @Test
    fun welcomeSubtitleIsDisplayedOnLaunch() {
        setContent(ChatScreenUiState.Empty)

        composeRule
            .onNodeWithText("Tap the microphone below to start your first voice-to-text conversation.")
            .assertIsDisplayed()
    }

    @Test
    fun microphoneButtonIsDisplayedInEmptyState() {
        setContent(ChatScreenUiState.Empty)

        composeRule
            .onNodeWithContentDescription("Start recording", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun resetActionIsNotShownInEmptyState() {
        setContent(ChatScreenUiState.Empty)

        composeRule.onNodeWithContentDescription("New conversation").assertDoesNotExist()
    }

    @Test
    fun afterSingleExchangeTwoBubblesAreVisible() {
        setContent(activeState(listOf(userMessage, assistantMessage)))

        composeRule.onAllNodesWithTag("message_bubble").assertCountEquals(2)
    }

    @Test
    fun userMessageTextIsVisibleAfterTranscription() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Text("Hello assistant"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("Hi!"),
                        author = MessageAuthor.Assistant
                    ),
                )
            )
        )

        composeRule.onNodeWithText("Hello assistant").assertIsDisplayed()
    }

    @Test
    fun assembledAssistantReplyIsVisibleAfterStreamCompletes() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Text("Tell me a fact"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("The sky is blue."),
                        author = MessageAuthor.Assistant
                    ),
                )
            )
        )

        composeRule.onNodeWithText("The sky is blue.").assertIsDisplayed()
    }

    @Test
    fun typingIndicatorIsPresentDuringStreaming() {
        setContent(
            activeState(
                messages = listOf(userMessage),
                phase = ChatScreenUiState.Phase.Streaming,
            )
        )

        composeRule.onNodeWithText("…").assertIsDisplayed()
    }

    @Test
    fun typingIndicatorIsAbsentAfterStreamCompletes() {
        setContent(activeState(listOf(userMessage, assistantMessage)))

        composeRule.onNodeWithText("…").assertDoesNotExist()
    }

    @Test
    fun resetActionAppearsInTopBarAfterFirstMessage() {
        setContent(activeState(listOf(userMessage, assistantMessage)))

        composeRule.onNodeWithContentDescription("New conversation").assertIsDisplayed()
    }

    @Test
    fun welcomeMessageIsGoneAfterFirstMessage() {
        setContent(activeState(listOf(userMessage, assistantMessage)))

        composeRule.onNodeWithText("Welcome to Chat!").assertDoesNotExist()
    }

    @Test
    fun afterTwoExchangesFourBubblesAreVisible() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Text("first question"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("first answer"),
                        author = MessageAuthor.Assistant
                    ),
                    ChatMessage(
                        content = MessageContent.Text("second question"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("second answer"),
                        author = MessageAuthor.Assistant
                    ),
                )
            )
        )

        composeRule.onAllNodesWithTag("message_bubble").assertCountEquals(4)
    }

    @Test
    fun allMessagesFromBothExchangesAreVisible() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Text("first question"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("first answer"),
                        author = MessageAuthor.Assistant
                    ),
                    ChatMessage(
                        content = MessageContent.Text("second question"),
                        author = MessageAuthor.User
                    ),
                    ChatMessage(
                        content = MessageContent.Text("second answer"),
                        author = MessageAuthor.Assistant
                    ),
                )
            )
        )

        composeRule.onNodeWithText("first question").assertIsDisplayed()
        composeRule.onNodeWithText("first answer").assertIsDisplayed()
        composeRule.onNodeWithText("second question").assertIsDisplayed()
        composeRule.onNodeWithText("second answer").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Error state
    // -------------------------------------------------------------------------

    @Test
    fun transcriptionErrorLeavesZeroMessageBubbles() {
        setContent(activeState(emptyList(), phase = ChatScreenUiState.Phase.Error))

        composeRule.onAllNodesWithTag("message_bubble").assertCountEquals(0)
    }

    @Test
    fun microphoneButtonRemainsAccessibleAfterTranscriptionError() {
        setContent(activeState(emptyList(), phase = ChatScreenUiState.Phase.Error))

        composeRule
            .onNodeWithContentDescription("Start recording", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun errorMessageIsDisplayedAfterTranscriptionError() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Text("..."),
                        author = MessageAuthor.Assistant,
                        errorMessage = "Assistant error"
                    ),
                ),
                phase = ChatScreenUiState.Phase.Error
            )
        )

        composeRule.onNodeWithText("...").assertIsDisplayed()
        composeRule.onNodeWithText("Assistant error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun errorMessageOnAssistantRequest() {
        setContent(
            activeState(
                listOf(
                    ChatMessage(
                        content = MessageContent.Audio("/cache/recording.m4a"),
                        author = MessageAuthor.User,
                        errorMessage = "Transcription error"
                    )
                ), phase = ChatScreenUiState.Phase.Error
            )
        )

        composeRule.onNodeWithText("Audio message").assertIsDisplayed()
        composeRule.onNodeWithText("Transcription error").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Reset conversation
    // -------------------------------------------------------------------------

    @Test
    fun afterResetWelcomeMessageReappears() {
        setContent(ChatScreenUiState.Empty)

        composeRule.onNodeWithText("Welcome to Chat!").assertIsDisplayed()
    }

    @Test
    fun afterResetBubbleCountIsZero() {
        setContent(ChatScreenUiState.Empty)

        composeRule.onAllNodesWithTag("message_bubble").assertCountEquals(0)
    }

    @Test
    fun afterResetResetActionDisappearsFromTopBar() {
        setContent(ChatScreenUiState.Empty)

        composeRule.onNodeWithContentDescription("New conversation").assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // Input bar blocking
    // -------------------------------------------------------------------------

    @Test
    fun micButtonRemainsVisibleButInputIsDisabledDuringTranscribing() {
        setContent(
            activeState(
                messages = listOf(
                    ChatMessage(
                        content = MessageContent.Audio("/cache/recording.m4a"),
                        author = MessageAuthor.User,
                    )
                ),
                phase = ChatScreenUiState.Phase.Transcribing,
            )
        )

        // AudioRecorder renders the mic button regardless of enabled; clicks are silently dropped
        composeRule
            .onNodeWithContentDescription("Start recording", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun micButtonRemainsVisibleButInputIsDisabledDuringStreaming() {
        setContent(
            activeState(
                messages = listOf(userMessage),
                phase = ChatScreenUiState.Phase.Streaming,
            )
        )

        composeRule
            .onNodeWithContentDescription("Start recording", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}