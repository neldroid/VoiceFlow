package com.pabloufor.voiceflow.presentation

import app.cash.turbine.test
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.CancelRecordingUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.ObserveRecordingStateUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.ResetRecorderUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.StartRecordingUseCase
import com.pabloufor.voiceflow.domain.usecase.recording.StopRecordingUseCase
import com.pabloufor.voiceflow.presentation.component.AudioRecorderViewModel
import com.pabloufor.voiceflow.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class AudioRecorderViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeAudioRecorderRepository()
    private lateinit var vm: AudioRecorderViewModel

    @BeforeEach
    fun setUp() {
        vm = AudioRecorderViewModel(
            observeRecordingState = ObserveRecordingStateUseCase(fakeRepo),
            startRecording = StartRecordingUseCase(fakeRepo),
            cancelRecording = CancelRecordingUseCase(fakeRepo),
            stopRecording = StopRecordingUseCase(fakeRepo),
            resetRecorder = ResetRecorderUseCase(fakeRepo),
        )
    }

    @Test
    fun `initial state is Idle`() = runTest {
        // Collect the state flow to activate WhileSubscribed sharing
        vm.state.test {
            assertEquals(AudioRecordingState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStartRecording transitions state to Recording`() = runTest {
        vm.state.test {
            assertEquals(AudioRecordingState.Idle, awaitItem()) // initial value

            vm.onStartRecording()
            advanceUntilIdle()

            assertEquals(AudioRecordingState.Recording, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onCancelRecording transitions state back to Idle`() = runTest {
        vm.state.test {
            awaitItem() // Idle

            vm.onStartRecording()
            advanceUntilIdle()
            awaitItem() // Recording

            vm.onCancelRecording()
            advanceUntilIdle()
            assertEquals(AudioRecordingState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSendRecording transitions state to Stopped with file path`() = runTest {
        vm.state.test {
            awaitItem() // Idle

            vm.onStartRecording()
            advanceUntilIdle()
            awaitItem() // Recording

            vm.onSendRecording()
            advanceUntilIdle()
            assertEquals(AudioRecordingState.Stopped("fake/path.m4a"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onResetRecorder transitions state to Idle from Stopped`() = runTest {
        vm.state.test {
            awaitItem() // Idle
            vm.onStartRecording(); advanceUntilIdle(); awaitItem() // Recording
            vm.onSendRecording(); advanceUntilIdle(); awaitItem()  // Stopped

            vm.onResetRecorder()
            advanceUntilIdle()
            assertEquals(AudioRecordingState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAudioRecorderRepository : AudioRecorderRepository {
        private val _state = MutableStateFlow<AudioRecordingState>(AudioRecordingState.Idle)
        override val recordingState: StateFlow<AudioRecordingState> = _state.asStateFlow()

        override suspend fun startRecording() { _state.value = AudioRecordingState.Recording }
        override suspend fun cancelRecording() { _state.value = AudioRecordingState.Idle }
        override suspend fun stopRecording() { _state.value = AudioRecordingState.Stopped("fake/path.m4a") }
        override suspend fun resetRecorder() { _state.value = AudioRecordingState.Idle }
    }
}
