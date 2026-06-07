package com.pabloufor.voiceflow.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pabloufor.voiceflow.core.concurrency.DefaultDispatcherProvider
import com.pabloufor.voiceflow.data.transcription.TranscriptionApi
import com.pabloufor.voiceflow.data.transcription.TranscriptionRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionRemoteDataSourceTest {

    private val server = MockWebServer()
    private lateinit var api: TranscriptionApi
    private lateinit var dataSource: TranscriptionRemoteDataSource
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var tempFile: File

    private val dispatcher = DefaultDispatcherProvider()

    @BeforeEach
    fun setUp() {
        server.start()
        val client = OkHttpClient()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(TranscriptionApi::class.java)
        dataSource = TranscriptionRemoteDataSource(api, dispatcher)

        // Create a real temp file with audio-like content for multipart upload tests
        tempFile = File.createTempFile("test_audio_", ".m4a").apply {
            writeBytes(ByteArray(1024) { 0x42 })
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        tempFile.delete()
    }

    @Test
    fun `sends audio bytes as multipart field named audio`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text":"hello"}""")
        )

        dataSource.transcribe(tempFile.absolutePath)

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"audio\""), "Multipart field must be named 'audio'")
    }

    @Test
    fun `returns transcribed text from successful response`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text":"Hello, world"}""")
        )

        val result = dataSource.transcribe(tempFile.absolutePath)

        assertEquals("Hello, world", result)
    }

    @Test
    fun `throws on HTTP 500 response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val thrown = runCatching { dataSource.transcribe(tempFile.absolutePath) }.exceptionOrNull()

        assertTrue(thrown is HttpException || thrown is IOException || thrown is RuntimeException,
            "Expected an HTTP/network exception but got: $thrown")
    }

    @Test
    fun `throws on network failure`() = runTest {
        server.shutdown()
        val thrown = runCatching { dataSource.transcribe(tempFile.absolutePath) }.exceptionOrNull()

        assertTrue(thrown is IOException, "Expected IOException on network failure but got: $thrown")
    }

    @Test
    fun `sends correct Content-Type header for multipart upload`() = runTest {
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"text":"test"}""")
        )

        dataSource.transcribe(tempFile.absolutePath)

        val request = server.takeRequest()
        val contentType = request.getHeader("Content-Type") ?: ""
        assertTrue(contentType.startsWith("multipart/form-data"),
            "Expected multipart/form-data Content-Type but got: $contentType")
    }
}
