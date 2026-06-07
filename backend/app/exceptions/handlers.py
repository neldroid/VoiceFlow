"""
Global exception handlers.

Registers FastAPI exception handlers so every unhandled error returns
a consistent JSON envelope instead of a raw 500 traceback.
"""

import logging

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from openai import APIConnectionError, AuthenticationError, RateLimitError

from exceptions.errors import AudioTooLargeError, ChatCompletionError, TranscriptionError

logger = logging.getLogger(__name__)


def _error_body(code: str, message: str) -> dict[str, object]:
    return {"error": {"code": code, "message": message}}


def register_exception_handlers(app: FastAPI) -> None:
    """Attach all global handlers to the FastAPI application."""

    @app.exception_handler(AudioTooLargeError)
    async def handle_audio_too_large(
        _: Request, exc: AudioTooLargeError
    ) -> JSONResponse:
        logger.warning("Audio too large: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            content=_error_body("audio_too_large", str(exc)),
        )

    @app.exception_handler(AuthenticationError)
    async def handle_auth_error(_: Request, exc: AuthenticationError) -> JSONResponse:
        logger.error("OpenAI authentication failed: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=_error_body("upstream_auth_error", "Invalid OpenAI credentials."),
        )

    @app.exception_handler(RateLimitError)
    async def handle_rate_limit(_: Request, exc: RateLimitError) -> JSONResponse:
        logger.warning("OpenAI rate limit hit: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content=_error_body("rate_limited", "Upstream rate limit reached."),
        )

    @app.exception_handler(APIConnectionError)
    async def handle_connection_error(
        _: Request, exc: APIConnectionError
    ) -> JSONResponse:
        logger.error("OpenAI connection error: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=_error_body("upstream_unavailable", "Could not reach OpenAI."),
        )

    @app.exception_handler(ChatCompletionError)
    async def handle_chat_completion_error(
        _: Request, exc: ChatCompletionError
    ) -> JSONResponse:
        logger.error("Chat completion failed: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=_error_body("completion_failed", str(exc)),
        )

    @app.exception_handler(TranscriptionError)
    async def handle_transcription_error(
        _: Request, exc: TranscriptionError
    ) -> JSONResponse:
        logger.error("Transcription failed: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=_error_body("transcription_failed", str(exc)),
        )

    @app.exception_handler(Exception)
    async def handle_generic(_: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled exception: %s", exc)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=_error_body("internal_error", "An unexpected error occurred."),
        )
