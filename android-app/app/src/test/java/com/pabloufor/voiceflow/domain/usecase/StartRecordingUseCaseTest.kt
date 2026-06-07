package com.pabloufor.voiceflow.domain.usecase

import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.StartRecordingUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartRecordingUseCaseTest {

    private val repository: AudioRecorderRepository = mockk()
    private lateinit var useCase: StartRecordingUseCase

    @BeforeEach
    fun setUp() {
        useCase = StartRecordingUseCase(repository)
    }

    @Test
    fun `delegates to repository startRecording on invocation`() = runTest {
        coJustRun { repository.startRecording() }

        useCase()

        coVerify(exactly = 1) { repository.startRecording() }
    }

    @Test
    fun `propagates exception from repository without wrapping`() = runTest {
        val cause = IllegalStateException("MediaRecorder not initialized")
        io.mockk.coEvery { repository.startRecording() } throws cause

        val thrown = runCatching { useCase() }.exceptionOrNull()

        assert(thrown === cause) { "Expected the original exception to propagate" }
    }
}
