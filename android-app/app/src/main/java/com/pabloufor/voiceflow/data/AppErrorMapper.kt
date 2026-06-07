package com.pabloufor.voiceflow.data

import com.pabloufor.voiceflow.domain.model.AppError
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.toAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout
    is UnknownHostException, is ConnectException -> AppError.Offline
    is HttpException -> code().toHttpAppError(retryAfterSeconds = null)
    is IOException -> AppError.Offline
    else -> AppError.Unknown(rawMessage = message)
}

fun Int.toHttpAppError(retryAfterSeconds: Long?): AppError = when {
    this == 429 -> AppError.RateLimited(retryAfterSeconds)
    this in 500..599 -> AppError.Server(this)
    this in 400..499 -> AppError.BadRequest(this)
    else -> AppError.Server(this)
}
