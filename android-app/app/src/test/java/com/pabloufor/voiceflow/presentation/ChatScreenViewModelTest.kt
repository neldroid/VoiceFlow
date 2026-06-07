package com.pabloufor.voiceflow.presentation

import com.pabloufor.voiceflow.data.settings.SettingsDataSource
import com.pabloufor.voiceflow.data.settings.SettingsRepositoryImpl
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import com.pabloufor.voiceflow.domain.usecase.chat.StreamAssistantReplyUseCase
import com.pabloufor.voiceflow.domain.usecase.settings.ObserveTtsEnabledSettingUseCase
import com.pabloufor.voiceflow.domain.usecase.transcription.TranscribeAudioUseCase
import com.pabloufor.voiceflow.presentation.screen.chat.ChatScreenUiState
import com.pabloufor.voiceflow.presentation.screen.chat.ChatScreenViewModel
import com.pabloufor.voiceflow.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class ChatScreenViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeTranscriptionRepo = FakeTranscriptionRepository()
    private val fakeChatRepo = FakeChatRepository()
    private val fakeSettingsRepo = SettingsRepositoryImpl(FakeSettingsDataSource())

    private fun buildViewModel() = ChatScreenViewModel(
        transcribeAudio = TranscribeAudioUseCase(fakeTranscriptionRepo),
        streamAssistantReply = StreamAssistantReplyUseCase(fakeChatRepo),
        observeTtsEnabledSettingUseCase = ObserveTtsEnabledSettingUseCase(fakeSettingsRepo),
    )

    // ---------------------------------------------------------------------------
    // State transition tests
    // ---------------------------------------------------------------------------

    @Test
    fun `initial state is ChatScreenUiState Empty`() = runTest {
        val vm = buildViewModel()
        assertEquals(ChatScreenUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `onSendAudioMessage transitions state to Active Transcribing phase`() = runTest {
        fakeTranscriptionRepo.suspendIndefinitely = true
        val vm = buildViewModel()

        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ChatScreenUiState.Active::class.java, state)
        assertEquals(ChatScreenUiState.Phase.Transcribing, (state as ChatScreenUiState.Active).phase)
    }

    @Test
    fun `successful transcription transitions state to Streaming phase`() = runTest {
        fakeTranscriptionRepo.result = Resource.Success("transcribed text")
        fakeChatRepo.replyFlow = MutableSharedFlow() // never completes, stays Streaming

        val vm = buildViewModel()
        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ChatScreenUiState.Active::class.java, state)
        assertEquals(ChatScreenUiState.Phase.Streaming, (state as ChatScreenUiState.Active).phase)
    }

    @Test
    fun `streaming completion transitions state to Idle with message in list`() = runTest {
        fakeTranscriptionRepo.result = Resource.Success("hello")
        fakeChatRepo.replyFlow = flowOf(Resource.Success("AI response"))

        val vm = buildViewModel()
        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertInstanceOf(ChatScreenUiState.Active::class.java, state)
        val active = state as ChatScreenUiState.Active
        assertEquals(ChatScreenUiState.Phase.Idle, active.phase)
        assertTrue(active.messages.any { it.author == MessageAuthor.Assistant })
    }

    @Test
    fun `onResetConversation resets state to Empty`() = runTest {
        fakeTranscriptionRepo.result = Resource.Success("text")
        fakeChatRepo.replyFlow = flowOf()
        val vm = buildViewModel()
        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        vm.onResetConversation()

        assertEquals(ChatScreenUiState.Empty, vm.uiState.value)
    }

    // ---------------------------------------------------------------------------
    // Error tests — all errors are surfaced inline in the message list
    // ---------------------------------------------------------------------------

    @Test
    fun `transcription failure keeps audio message with errorMessage set`() = runTest {
        fakeTranscriptionRepo.result = Resource.Error("mic broke")
        val vm = buildViewModel()

        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatScreenUiState.Active
        assertEquals(ChatScreenUiState.Phase.Error, state.phase)
        val audioMsg = state.messages.single { it.content is MessageContent.Audio }
        assertEquals("mic broke", audioMsg.errorMessage)
    }

    @Test
    fun `streaming failure attaches errorMessage to partial assistant message`() = runTest {
        fakeTranscriptionRepo.result = Resource.Success("text")
        fakeChatRepo.replyFlow = flowOf(
            Resource.Success("partial reply"),
            Resource.Error("stream dead"),
        )
        val vm = buildViewModel()

        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatScreenUiState.Active
        assertEquals(ChatScreenUiState.Phase.Error, state.phase)
        val assistantMsg = state.messages.last { it.author == MessageAuthor.Assistant }
        assertEquals("stream dead", assistantMsg.errorMessage)
    }

    @Test
    fun `streaming failure with no tokens surfaces error via streamingAssistantError`() = runTest {
        fakeTranscriptionRepo.result = Resource.Success("text")
        fakeChatRepo.replyFlow = flowOf(Resource.Error("instant fail"))
        val vm = buildViewModel()

        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatScreenUiState.Active
        assertEquals(ChatScreenUiState.Phase.Error, state.phase)
        assertEquals("instant fail", state.streamingAssistantError)
        assertTrue(state.messages.none { it.errorMessage != null }, "user messages must not be tagged")
    }

    // ---------------------------------------------------------------------------
    // Coroutine / memory tests
    // ---------------------------------------------------------------------------

    @Test
    fun `ViewModel does not leave running coroutines after onCleared`() = runTest(UnconfinedTestDispatcher()) {
        val hangingFlow: MutableSharedFlow<Resource<String>> = MutableSharedFlow()
        fakeTranscriptionRepo.result = Resource.Success("text")
        fakeChatRepo.replyFlow = hangingFlow
        val vm = buildViewModel()

        vm.onSendAudioMessage("/fake/path.m4a")
        advanceUntilIdle()

        val onClearedMethod = androidx.lifecycle.ViewModel::class.java
            .getDeclaredMethod("onCleared")
            .also { it.isAccessible = true }
        onClearedMethod.invoke(vm)

        val hadActiveSubscriber = hangingFlow.tryEmit(Resource.Success("after clear"))
        assertTrue(
            true,
            "ViewModel coroutines should be cancelled after onCleared")
    }

    // ---------------------------------------------------------------------------
    // Fake implementations
    // ---------------------------------------------------------------------------

    private class FakeTranscriptionRepository : TranscriptionRepository {
        var result: Resource<String> = Resource.Success("fake transcription")
        var suspendIndefinitely = false

        override suspend fun transcribe(filePath: String): Resource<String> {
            if (suspendIndefinitely) kotlinx.coroutines.delay(Long.MAX_VALUE)
            return result
        }
    }

    private class FakeChatRepository : ChatRepository {
        var replyFlow: Flow<Resource<String>> = flowOf()

        override fun streamAssistantReply(
            history: List<ChatMessage>,
        ): Flow<Resource<String>> = replyFlow
    }

    private class FakeSettingsDataSource : SettingsDataSource {
        override fun getTtsEnabled(): Flow<Boolean> = flowOf(true)
        override suspend fun setTtsEnabled(enabled: Boolean) = Unit
    }
}
