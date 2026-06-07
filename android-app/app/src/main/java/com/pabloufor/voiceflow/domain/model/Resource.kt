package com.pabloufor.voiceflow.domain.model

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(
        val message: String = "",
        val cause: Throwable? = null,
        val error: AppError? = null,
    ) : Resource<Nothing>()
}
