"""
TranscriptionService: wraps the OpenAI Whisper API.

Design decisions:
  - Accepts raw bytes + filename so callers don't need to handle IO.
  - Validates audio size before touching the network (fail fast).
  - Typed OpenAI errors bubble up; only unexpected errors become TranscriptionError.
"""

import logging
from io import BytesIO

from openai import APIConnectionError, AsyncOpenAI, AuthenticationError, RateLimitError

from exceptions.errors import AudioTooLargeError, TranscriptionError

logger = logging.getLogger(__name__)


class TranscriptionService:
    """Proxies audio to OpenAI Whisper and returns the transcribed text."""

    def __init__(
        self,
        client: AsyncOpenAI,
        model: str,
        max_audio_bytes: int,
    ) -> None:
        self._client = client
        self._model = model
        self._max_audio_bytes = max_audio_bytes

    async def transcribe(
        self,
        audio_bytes: bytes,
        filename: str,
        content_type: str,
    ) -> str:
        """Transcribe audio bytes using Whisper."""
        if len(audio_bytes) > self._max_audio_bytes:
            limit_mb = self._max_audio_bytes // (1024 * 1024)
            raise AudioTooLargeError(
                f"Audio is {len(audio_bytes) // 1024} KB, "
                f"but the limit is {limit_mb} MB."
            )

        logger.info(
            "Transcribing audio file=%s size=%d bytes model=%s",
            filename,
            len(audio_bytes),
            self._model,
        )

        try:
            audio_file = (filename, BytesIO(audio_bytes), content_type)
            response = await self._client.audio.transcriptions.create(
                model=self._model,
                file=audio_file,  # type: ignore[arg-type]
                response_format="text",
            )
        except (AuthenticationError, RateLimitError, APIConnectionError):
            raise
        except Exception as exc:
            logger.exception("Whisper API call failed")
            raise TranscriptionError(f"Whisper failed: {exc}") from exc

        text = response if isinstance(response, str) else ""
        logger.info("Transcription complete: %d chars", len(text))
        return text.strip()
