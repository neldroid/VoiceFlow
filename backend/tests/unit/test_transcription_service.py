"""Unit tests for TranscriptionService.

Tests the service in isolation with a mocked AsyncOpenAI client. We assert:
- the request shape we send to OpenAI
- the response shape we return to the caller
- that typed errors propagate cleanly without being swallowed
"""

from io import BytesIO
from unittest.mock import ANY, AsyncMock

import httpx
import pytest
from exceptions.errors import AudioTooLargeError, TranscriptionError
from openai import APIConnectionError, AuthenticationError, RateLimitError
from services.transcription import TranscriptionService


def _service(
    mock_openai: AsyncMock, max_bytes: int = 25 * 1024 * 1024
) -> TranscriptionService:
    return TranscriptionService(
        client=mock_openai, model="whisper-1", max_audio_bytes=max_bytes
    )


class TestTranscriptionService:
    async def test_passes_filename_and_bytes_to_openai_with_correct_model(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.return_value = "Hello world"
        service = _service(mock_openai)

        await service.transcribe(
            b"audio-bytes", filename="recording.mp4", content_type="audio/mp4"
        )

        mock_openai.audio.transcriptions.create.assert_awaited_once_with(
            model="whisper-1",
            file=("recording.mp4", ANY, "audio/mp4"),
            response_format="text",
        )
        # Verify the BytesIO actually wraps our bytes.
        call = mock_openai.audio.transcriptions.create.await_args
        _, buf, _ = call.kwargs["file"]
        assert isinstance(buf, BytesIO)
        assert buf.getvalue() == b"audio-bytes"

    async def test_returns_text_field_from_openai_response(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.return_value = "  the quick brown fox  "
        service = _service(mock_openai)

        text = await service.transcribe(
            b"x", filename="r.mp4", content_type="audio/mp4"
        )

        # Service strips whitespace.
        assert text == "the quick brown fox"

    async def test_propagates_authentication_error_without_swallowing(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.side_effect = AuthenticationError(
            message="bad key",
            response=httpx.Response(401, request=httpx.Request("POST", "http://x")),
            body=None,
        )
        service = _service(mock_openai)

        with pytest.raises(AuthenticationError):
            await service.transcribe(b"x", filename="r.mp4", content_type="audio/mp4")

    async def test_propagates_rate_limit_error_without_swallowing(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.side_effect = RateLimitError(
            message="slow down",
            response=httpx.Response(429, request=httpx.Request("POST", "http://x")),
            body=None,
        )
        service = _service(mock_openai)

        with pytest.raises(RateLimitError):
            await service.transcribe(b"x", filename="r.mp4", content_type="audio/mp4")

    async def test_propagates_network_timeout_without_swallowing(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.side_effect = APIConnectionError(
            request=httpx.Request("POST", "http://x")
        )
        service = _service(mock_openai)

        with pytest.raises(APIConnectionError):
            await service.transcribe(b"x", filename="r.mp4", content_type="audio/mp4")

    async def test_unexpected_error_is_wrapped_as_transcription_error(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.audio.transcriptions.create.side_effect = RuntimeError("kaboom")
        service = _service(mock_openai)

        with pytest.raises(TranscriptionError):
            await service.transcribe(b"x", filename="r.mp4", content_type="audio/mp4")

    async def test_rejects_oversize_audio_before_calling_openai(
        self, mock_openai: AsyncMock
    ) -> None:
        service = _service(mock_openai, max_bytes=10)

        with pytest.raises(AudioTooLargeError):
            await service.transcribe(
                b"x" * 11, filename="big.mp4", content_type="audio/mp4"
            )
        mock_openai.audio.transcriptions.create.assert_not_awaited()
