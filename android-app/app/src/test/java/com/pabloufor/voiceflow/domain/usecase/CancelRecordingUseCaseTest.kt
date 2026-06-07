package com.pabloufor.voiceflow.domain.usecase

import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.CancelRecordingUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CancelRecordingUseCaseTest {

    private val repository: AudioRecorderRepository = mockk()
    private lateinit var useCase: CancelRecordingUseCase

    @BeforeEach
    fun setUp() {
        useCase = CancelRecordingUseCase(repository)
    }

    @Test
    fun `delegates to repository cancelRecording on invocation`() = runTest {
        coJustRun { repository.cancelRecording() }

        useCase()

        coVerify(exactly = 1) { repository.cancelRecording() }
    }

    @Test
    fun `propagates exception from repository without wrapping`() = runTest {
        val cause = RuntimeException("cancel failed")
        io.mockk.coEvery { repository.cancelRecording() } throws cause

        val thrown = runCatching { useCase() }.exceptionOrNull()

        assert(thrown === cause)
    }
}
