package com.pabloufor.voiceflow.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pabloufor.voiceflow.R
import com.pabloufor.voiceflow.domain.model.AppError

/**
 * Resolves an [AppError] to a human-readable, localized string.
 * Centralized here so the data layer never deals with strings.
 *
 * Falls back to [fallback] when [error] is null (legacy callers that still
 * pass raw `errorMessage` strings).
 */
@Composable
fun appErrorText(error: AppError?, fallback: String? = null): String = when (error) {
    AppError.Offline -> stringResource(R.string.error_offline)
    AppError.Timeout -> stringResource(R.string.error_timeout)
    is AppError.RateLimited -> error.retryAfterSeconds
        ?.let { stringResource(R.string.error_rate_limited_with_retry, it) }
        ?: stringResource(R.string.error_rate_limited)
    is AppError.Server -> stringResource(R.string.error_server)
    is AppError.BadRequest -> stringResource(R.string.error_bad_request)
    AppError.EmptyTranscription -> stringResource(R.string.error_empty_transcription)
    AppError.ErrorTranscription -> stringResource(R.string.error_transcription)
    AppError.RecordingFailed -> stringResource(R.string.error_recording_failed)
    is AppError.Unknown,
    null -> fallback ?: stringResource(R.string.error_unknown)
}
