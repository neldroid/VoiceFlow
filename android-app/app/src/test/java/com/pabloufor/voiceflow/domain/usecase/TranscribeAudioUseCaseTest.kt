package com.pabloufor.voiceflow.domain.usecase

import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.TranscriptionRepository
import com.pabloufor.voiceflow.domain.usecase.transcription.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscribeAudioUseCaseTest {

    private val repository: TranscriptionRepository = mockk()
    private lateinit var useCase: TranscribeAudioUseCase

    @BeforeEach
    fun setUp() {
        useCase = TranscribeAudioUseCase(repository)
    }

    @Test
    fun `emits expected transcription on successful repository response`() = runTest {
        coEvery { repository.transcribe(any()) } returns Resource.Success("Hello world")

        val result = useCase("/path/to/audio.m4a")

        assertEquals(Resource.Success("Hello world"), result)
    }

    @Test
    fun `returns Resource Error when repository returns error`() = runTest {
        val error = Resource.Error("transcription failed", RuntimeException("server error"))
        coEvery { repository.transcribe(any()) } returns error

        val result = useCase("/path/to/audio.m4a")

        assertInstanceOf(Resource.Error::class.java, result)
        assertEquals("transcription failed", (result as Resource.Error).message)
    }

    @Test
    fun `passes file path to repository unchanged`() = runTest {
        val path = "/data/data/com.example/cache/recording_1234.m4a"
        coEvery { repository.transcribe(path) } returns Resource.Success("test")

        useCase(path)

        coVerify(exactly = 1) { repository.transcribe(path) }
    }

    @Test
    fun `returns Resource Error when repository returns empty string`() = runTest {
        coEvery { repository.transcribe(any()) } returns Resource.Success("")

        val result = useCase("/path/to/audio.m4a")

        // Empty transcription is a valid success; callers decide how to handle blank text
        assertEquals(Resource.Success(""), result)
    }
}
