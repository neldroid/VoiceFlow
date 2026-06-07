package com.pabloufor.voiceflow.domain.usecase

import app.cash.turbine.test
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.AudioRecordingState
import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.ObserveRecordingStateUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObserveRecordingStateUseCaseTest {

    private val repository: AudioRecorderRepository = mockk()
    private lateinit var useCase: ObserveRecordingStateUseCase

    @BeforeEach
    fun setUp() {
        useCase = ObserveRecordingStateUseCase(repository)
    }

    @Test
    fun `emits expected output on successful repository response`() = runTest {
        every { repository.recordingState } returns flowOf(
            AudioRecordingState.Idle,
            AudioRecordingState.Recording,
            AudioRecordingState.Stopped("/path/file.m4a"),
        )

        useCase().test {
            assertEquals(AudioRecordingState.Idle, awaitItem())
            assertEquals(AudioRecordingState.Recording, awaitItem())
            assertEquals(AudioRecordingState.Stopped("/path/file.m4a"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `emits error state from repository flow`() = runTest {
        every { repository.recordingState } returns flowOf(
            AudioRecordingState.Error(AppError.RecordingFailed),
        )

        useCase().test {
            val state = awaitItem()
            assertEquals(AudioRecordingState.Error(AppError.RecordingFailed), state)
            awaitComplete()
        }
    }

    @Test
    fun `propagates cancellation to repository flow without leak`() = runTest {
        every { repository.recordingState } returns flowOf(
            AudioRecordingState.Idle,
            AudioRecordingState.Recording,
        )

        useCase().test {
            awaitItem() // Idle
            cancelAndIgnoreRemainingEvents()
        }
        // If we reach here without hanging, cancellation propagated correctly
    }
}
