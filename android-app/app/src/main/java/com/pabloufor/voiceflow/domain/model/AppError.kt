package com.pabloufor.voiceflow.domain.model

sealed class AppError {
    data object Offline : AppError()
    data object Timeout : AppError()
    data class RateLimited(val retryAfterSeconds: Long?) : AppError()
    data class Server(val httpCode: Int) : AppError()
    data class BadRequest(val httpCode: Int) : AppError()
    data object EmptyTranscription : AppError()
    data object ErrorTranscription : AppError()
    data object RecordingFailed : AppError()
    data class Unknown(val rawMessage: String? = null) : AppError()
}
