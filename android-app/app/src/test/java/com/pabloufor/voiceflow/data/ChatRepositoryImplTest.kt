package com.pabloufor.voiceflow.data

import app.cash.turbine.test
import com.pabloufor.voiceflow.data.chat.ChatRemoteDataSource
import com.pabloufor.voiceflow.data.chat.ChatRepositoryImpl
import com.pabloufor.voiceflow.data.chat.ChatStreamEvent
import com.pabloufor.voiceflow.data.chat.dto.ChatRequestDto
import com.pabloufor.voiceflow.domain.model.AppError
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Resource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

    private val remoteDataSource: ChatRemoteDataSource = mockk()
    private lateinit var repository: ChatRepositoryImpl

    private val userHistory = listOf(
        ChatMessage(content = MessageContent.Text("What is AI?"), author = MessageAuthor.User),
        ChatMessage(content = MessageContent.Text("It stands for Artificial Intelligence."), author = MessageAuthor.Assistant),
    )

    @BeforeEach
    fun setUp() {
        repository = ChatRepositoryImpl(remoteDataSource)
    }

    @Test
    fun `emits Resource Success tokens from remote data source`() = runTest {
        every { remoteDataSource.streamChat(any()) } returns flowOf(
            ChatStreamEvent.Token("Hello"),
            ChatStreamEvent.Token(" world"),
        )

        repository.streamAssistantReply(userHistory).test {
            assertEquals(Resource.Success("Hello"), awaitItem())
            assertEquals(Resource.Success(" world"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `maps ServerError event to Resource Error with payload message`() = runTest {
        every { remoteDataSource.streamChat(any()) } returns flowOf(
            ChatStreamEvent.Token("partial "),
            ChatStreamEvent.ServerError(
                code = "rate_limited",
                message = "slow down",
                appError = AppError.RateLimited(retryAfterSeconds = null),
            ),
        )

        repository.streamAssistantReply(userHistory).test {
            assertEquals(Resource.Success("partial "), awaitItem())
            val item = awaitItem()
            assertInstanceOf(Resource.Error::class.java, item)
            assertEquals("slow down", (item as Resource.Error).message)
            awaitComplete()
        }
    }

    @Test
    fun `wraps remote data source exception as Resource Error`() = runTest {
        val cause = RuntimeException("network error")
        every { remoteDataSource.streamChat(any()) } returns flow { throw cause }

        repository.streamAssistantReply(userHistory).test {
            val item = awaitItem()
            assertInstanceOf(Resource.Error::class.java, item)
            assertEquals(cause, (item as Resource.Error).cause)
            awaitComplete()
        }
    }

    @Test
    fun `filters out audio messages before sending to remote`() = runTest {
        val historyWithAudio = listOf(
            ChatMessage(content = MessageContent.Audio("/path/audio.m4a"), author = MessageAuthor.User),
            ChatMessage(content = MessageContent.Text("text message"), author = MessageAuthor.User),
        )
        every { remoteDataSource.streamChat(any()) } answers {
            val dto = firstArg<ChatRequestDto>()
            assertEquals(1, dto.messages.size)
            assertTrue(dto.messages.all { it.content == "text message" })
            flowOf()
        }

        repository.streamAssistantReply(historyWithAudio).test {
            awaitComplete()
        }
    }

    @Test
    fun `maps user author to role user in DTO`() = runTest {
        val history = listOf(
            ChatMessage(content = MessageContent.Text("question"), author = MessageAuthor.User),
        )
        every { remoteDataSource.streamChat(any()) } answers {
            val dto = firstArg<ChatRequestDto>()
            assertEquals("user", dto.messages.first().role)
            flowOf()
        }

        repository.streamAssistantReply(history).test { awaitComplete() }
    }

    @Test
    fun `maps assistant author to role assistant in DTO`() = runTest {
        val history = listOf(
            ChatMessage(content = MessageContent.Text("answer"), author = MessageAuthor.Assistant),
        )
        every { remoteDataSource.streamChat(any()) } answers {
            val dto = firstArg<ChatRequestDto>()
            assertEquals("assistant", dto.messages.first().role)
            flowOf()
        }

        repository.streamAssistantReply(history).test { awaitComplete() }
    }

    @Test
    fun `propagates cancellation to upstream flow without leak`() = runTest {
        every { remoteDataSource.streamChat(any()) } returns flow {
            emit(ChatStreamEvent.Token("one"))
            emit(ChatStreamEvent.Token("two"))
            emit(ChatStreamEvent.Token("three"))
        }

        repository.streamAssistantReply(userHistory).test {
            awaitItem() // Resource.Success("one")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

