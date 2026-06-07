package com.pabloufor.voiceflow.domain.usecase

import app.cash.turbine.test
import com.pabloufor.voiceflow.domain.model.ChatMessage
import com.pabloufor.voiceflow.domain.model.MessageAuthor
import com.pabloufor.voiceflow.domain.model.MessageContent
import com.pabloufor.voiceflow.domain.model.Resource
import com.pabloufor.voiceflow.domain.repository.ChatRepository
import com.pabloufor.voiceflow.domain.usecase.chat.StreamAssistantReplyUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamAssistantReplyUseCaseTest {

    private val history = listOf(
        ChatMessage(
            content = MessageContent.Text("hello"),
            author = MessageAuthor.User,
        ),
    )

    @Test
    fun `emits expected output on successful repository response`() = runTest {
        val useCase = StreamAssistantReplyUseCase(
            FakeChatRepository(
                flowOf(Resource.Success("Hi"), Resource.Success(" there"))
            )
        )

        useCase(history).test {
            assertEquals(Resource.Success("Hi"), awaitItem())
            assertEquals(Resource.Success(" there"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `wraps repository exception as Resource Error`() = runTest {
        val boom = IllegalStateException("boom")
        val useCase = StreamAssistantReplyUseCase(
            FakeChatRepository(
                flowOf(Resource.Success("partial"), Resource.Error("stream failed", boom))
            )
        )

        useCase(history).test {
            assertEquals(Resource.Success("partial"), awaitItem())
            val err = awaitItem() as Resource.Error
            assertEquals("stream failed", err.message)
            assertEquals(boom, err.cause)
            awaitComplete()
        }
    }

    @Test
    fun `propagates cancellation to repository without leak`() = runTest {
        useCase@ StreamAssistantReplyUseCase(
            FakeChatRepository(
            flow {
                emit(Resource.Success("one"))
                emit(Resource.Success("two"))
                emit(Resource.Success("three"))
            }
        )).also { useCase ->
            useCase(history).test {
                assertEquals(Resource.Success("one"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `completes flow when repository emits no items`() = runTest {
        val useCase = StreamAssistantReplyUseCase(FakeChatRepository(flowOf()))

        useCase(history).test {
            awaitComplete()
        }
    }

    @Test
    fun `emits all tokens in order for multi-token stream`() = runTest {
        val tokens = listOf("Hello", " ", "world", "!")
        val useCase = StreamAssistantReplyUseCase(
            FakeChatRepository(
                flowOf(*tokens.map { Resource.Success(it) }.toTypedArray())
            )
        )

        val results = useCase(history).toList()

        assertEquals(tokens.map { Resource.Success(it) }, results)
    }

    @Test
    fun `passes history list to repository unchanged`() = runTest {
        var capturedHistory: List<ChatMessage>? = null
        val capturingRepo = object : ChatRepository {
            override fun streamAssistantReply(history: List<ChatMessage>): Flow<Resource<String>> {
                capturedHistory = history
                return flowOf()
            }
        }
        val useCase = StreamAssistantReplyUseCase(capturingRepo)

        useCase(history).toList()

        assertEquals(history, capturedHistory)
    }

    private class FakeChatRepository(
        private val flow: Flow<Resource<String>>,
    ) : ChatRepository {
        override fun streamAssistantReply(history: List<ChatMessage>): Flow<Resource<String>> = flow
    }
}
