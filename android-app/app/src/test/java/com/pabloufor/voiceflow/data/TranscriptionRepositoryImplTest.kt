package com.pabloufor.voiceflow.data

import com.pabloufor.voiceflow.data.transcription.TranscriptionRemoteDataSource
import com.pabloufor.voiceflow.data.transcription.TranscriptionRepositoryImpl
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.Resource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionRepositoryImplTest {

    private val remoteDataSource: TranscriptionRemoteDataSource = mockk()
    private lateinit var repository: TranscriptionRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = TranscriptionRepositoryImpl(remoteDataSource)
    }

    @Test
    fun `returns transcribed text from successful response`() = runTest {
        coEvery { remoteDataSource.transcribe(any()) } returns "Hello, this is a test"

        val result = repository.transcribe("/path/to/audio.m4a")

        assertEquals(Resource.Success("Hello, this is a test"), result)
    }

    @Test
    fun `returns Resource Error EmptyTranscription when remote returns blank`() = runTest {
        coEvery { remoteDataSource.transcribe(any()) } returns "   "

        val result = repository.transcribe("/path/to/audio.m4a")

        assertInstanceOf(Resource.Error::class.java, result)
        assertEquals(AppError.EmptyTranscription, (result as Resource.Error).error)
    }

    @Test
    fun `returns Resource Error Offline when remote throws IOException`() = runTest {
        val cause = IOException("network failure")
        coEvery { remoteDataSource.transcribe(any()) } throws cause

        val result = repository.transcribe("/path/to/audio.m4a")

        assertInstanceOf(Resource.Error::class.java, result)
        assertEquals(AppError.Offline, (result as Resource.Error).error)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `returns Resource Error Unknown when remote throws RuntimeException`() = runTest {
        val cause = RuntimeException("HTTP 500")
        coEvery { remoteDataSource.transcribe(any()) } throws cause

        val result = repository.transcribe("/path/to/audio.m4a")

        assertInstanceOf(Resource.Error::class.java, result)
        assertInstanceOf(AppError.Unknown::class.java, (result as Resource.Error).error)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `passes file path to remote data source unchanged`() = runTest {
        val path = "/data/data/app/cache/recording_1234.m4a"
        coEvery { remoteDataSource.transcribe(path) } returns "text"

        repository.transcribe(path)

        coVerify(exactly = 1) { remoteDataSource.transcribe(path) }
    }
}

