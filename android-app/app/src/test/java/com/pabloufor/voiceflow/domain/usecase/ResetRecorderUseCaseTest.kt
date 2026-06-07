package com.pabloufor.voiceflow.domain.usecase

import com.pabloufor.voiceflow.domain.repository.AudioRecorderRepository
import com.pabloufor.voiceflow.domain.usecase.recording.ResetRecorderUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResetRecorderUseCaseTest {

    private val repository: AudioRecorderRepository = mockk()
    private lateinit var useCase: ResetRecorderUseCase

    @BeforeEach
    fun setUp() {
        useCase = ResetRecorderUseCase(repository)
    }

    @Test
    fun `delegates to repository resetRecorder on invocation`() = runTest {
        coJustRun { repository.resetRecorder() }

        useCase()

        coVerify(exactly = 1) { repository.resetRecorder() }
    }

    @Test
    fun `propagates exception from repository without wrapping`() = runTest {
        val cause = IllegalStateException("reset failed")
        io.mockk.coEvery { repository.resetRecorder() } throws cause

        val thrown = runCatching { useCase() }.exceptionOrNull()

        assert(thrown === cause)
    }
}
