package com.pabloufor.voiceflow.data.chat

import com.pabloufor.voiceflow.core.concurrency.DispatcherProvider
import com.pabloufor.voiceflow.data.chat.dto.ChatRequestDto
import com.pabloufor.voiceflow.data.toHttpAppError
import com.pabloufor.voiceflow.domain.model.AppError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

private const val SSE_DATA_FIELD = "data:"
private const val SSE_EVENT_FIELD = "event:"
private const val SSE_DONE_SENTINEL = "[DONE]"
private const val SSE_EVENT_ERROR = "error"
private const val HEADER_RETRY_AFTER = "Retry-After"

class ChatRemoteDataSource(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val json: Json,
    private val dispatcher: DispatcherProvider
) {

    fun streamChat(requestDto: ChatRequestDto): Flow<ChatStreamEvent> = callbackFlow {
        val body = json.encodeToString(requestDto)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/v1/chat")
            .addHeader("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = httpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        trySend(buildHttpServerError(resp))
                        close()
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        close(IOException("Chat response has no body"))
                        return
                    }
                    try {
                        consumeStream(source) { event ->
                            trySend(event)
                        }
                        close()
                    } catch (t: StreamDoneException) {
                        close()
                    } catch (t: Throwable) {
                        close(t)
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(dispatcher.io)

    /**
     * Builds a typed [ChatStreamEvent.ServerError] from a non-2xx HTTP response.
     * Reads the response body once (best effort) to extract a server-supplied error
     * message; on 429, also extracts the `Retry-After` header so the UI can surface
     * how long the user should wait before retrying.
     */
    private fun buildHttpServerError(response: Response): ChatStreamEvent.ServerError {
        val httpCode = response.code
        val rawBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        val parsed = rawBody.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<SseErrorPayload>(it) }.getOrNull() }
        val retryAfter = response.header(HEADER_RETRY_AFTER)?.toLongOrNull()
        val appError = httpCode.toHttpAppError(retryAfterSeconds = retryAfter)
        return ChatStreamEvent.ServerError(
            code = parsed?.code ?: "http_$httpCode",
            message = parsed?.message ?: "HTTP $httpCode",
            appError = appError,
        )
    }

    /**
     * Reads the SSE stream block by block. Events are delimited by blank lines and
     * may carry an `event:` type plus one or more `data:` lines that are joined
     * with `\n` per the spec. Server emits `event: error` with a JSON payload to
     * signal mid-stream failures (rate limit, upstream unavailable); these are
     * surfaced as [ChatStreamEvent.ServerError] instead of being mistaken for tokens.
     */
    private fun consumeStream(
        source: BufferedSource,
        emit: (ChatStreamEvent) -> Unit,
    ) {
        var eventType: String? = null
        val dataBuffer = StringBuilder()

        while (!source.exhausted()) {
            val rawLine = source.readUtf8Line() ?: break
            val line = rawLine.trimEnd('\r')

            if (line.isEmpty()) {
                if (dataBuffer.isEmpty() && eventType == null) continue
                dispatchEvent(eventType, dataBuffer.toString(), emit)
                eventType = null
                dataBuffer.clear()
                continue
            }

            if (line.startsWith(":")) continue

            when {
                line.startsWith(SSE_EVENT_FIELD) ->
                    eventType = line.substring(SSE_EVENT_FIELD.length).trimStart()
                line.startsWith(SSE_DATA_FIELD) -> {
                    val payload = line.substring(SSE_DATA_FIELD.length)
                        .let { if (it.startsWith(" ")) it.substring(1) else it }
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payload)
                }
                else -> Unit
            }
        }

        if (dataBuffer.isNotEmpty() || eventType != null) {
            dispatchEvent(eventType, dataBuffer.toString(), emit)
        }
    }

    private fun dispatchEvent(
        eventType: String?,
        data: String,
        emit: (ChatStreamEvent) -> Unit,
    ) {
        if (eventType == SSE_EVENT_ERROR) {
            val parsed = runCatching { json.decodeFromString<SseErrorPayload>(data) }.getOrNull()
            val code = parsed?.code ?: "server_error"
            val message = parsed?.message ?: data.ifBlank { "Stream error" }
            emit(
                ChatStreamEvent.ServerError(
                    code = code,
                    message = message,
                    appError = AppError.Unknown(rawMessage = message),
                ),
            )
            throw StreamDoneException
        }
        if (data == SSE_DONE_SENTINEL) throw StreamDoneException
        if (data.isNotEmpty()) emit(ChatStreamEvent.Token(data))
    }
}

@Serializable
private data class SseErrorPayload(
    @SerialName("code") val code: String? = null,
    @SerialName("message") val message: String? = null,
)

private object StreamDoneException : RuntimeException() {
    private fun readResolve(): Any = StreamDoneException
    override fun fillInStackTrace(): Throwable = this
}
