package com.pabloufor.voiceflow.domain.usecase

import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.StopRecordingUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StopRecordingUseCaseTest {

    private val repository: AudioRecorderRepository = mockk()
    private lateinit var useCase: StopRecordingUseCase

    @BeforeEach
    fun setUp() {
        useCase = StopRecordingUseCase(repository)
    }

    @Test
    fun `delegates to repository stopRecording on invocation`() = runTest {
        coJustRun { repository.stopRecording() }

        useCase()

        coVerify(exactly = 1) { repository.stopRecording() }
    }

    @Test
    fun `propagates exception from repository without wrapping`() = runTest {
        val cause = RuntimeException("stop failed")
        io.mockk.coEvery { repository.stopRecording() } throws cause

        val thrown = runCatching { useCase() }.exceptionOrNull()

        assert(thrown === cause)
    }
}
