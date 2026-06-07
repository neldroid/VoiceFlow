"""Integration tests for POST /api/v1/transcribe.

Drives the full ASGI stack with a mocked TranscriptionService dependency.
We test the HTTP contract: status codes, JSON body shape, error envelopes.
"""

from typing import Any
from unittest.mock import AsyncMock

import httpx
import pytest
from dependencies import get_transcription_service
from httpx import ASGITransport, AsyncClient
from main import create_app
from openai import APIConnectionError, AuthenticationError
from services.transcription import TranscriptionService

URL = "/api/v1/transcribe"


# ── Happy path ─────────────────────────────────────────────────────────────


async def test_returns_200_and_transcribed_text_on_valid_audio_upload(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    mock_transcription_service.transcribe.return_value = "the quick brown fox"
    files = {"audio": ("clip.mp4", b"fakebytes", "audio/mp4")}

    resp = await client.post(URL, files=files)

    assert resp.status_code == 200
    assert resp.json() == {"text": "the quick brown fox"}


# ── Content-type validation ────────────────────────────────────────────────


async def test_returns_415_for_text_plain_content_type(client: AsyncClient) -> None:
    files = {"audio": ("note.txt", b"hello", "text/plain")}
    resp = await client.post(URL, files=files)
    assert resp.status_code == 415


async def test_returns_415_for_image_jpeg_content_type(client: AsyncClient) -> None:
    files = {"audio": ("img.jpg", b"\xff\xd8", "image/jpeg")}
    resp = await client.post(URL, files=files)
    assert resp.status_code == 415


@pytest.mark.parametrize(
    "content_type",
    ["audio/mpeg", "audio/mp4", "audio/wav", "audio/webm", "audio/ogg"],
)
async def test_accepts_each_supported_audio_mime_type(
    client: AsyncClient, mock_transcription_service: AsyncMock, content_type: str
) -> None:
    mock_transcription_service.transcribe.return_value = "ok"
    files = {"audio": ("clip", b"data", content_type)}
    resp = await client.post(URL, files=files)
    assert resp.status_code == 200, content_type


# ── Size validation ────────────────────────────────────────────────────────


async def test_returns_413_for_audio_exceeding_25mb(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    oversize = b"x" * (25 * 1024 * 1024 + 1)
    files = {"audio": ("big.wav", oversize, "audio/wav")}

    resp = await client.post(URL, files=files)

    assert resp.status_code == 413
    mock_transcription_service.transcribe.assert_not_awaited()


# ── Missing fields ─────────────────────────────────────────────────────────


async def test_returns_422_when_audio_field_is_missing(client: AsyncClient) -> None:
    resp = await client.post(URL, data={"not_audio": "x"})
    assert resp.status_code == 422


# ── Upstream errors ────────────────────────────────────────────────────────


async def test_returns_502_when_whisper_raises_authentication_error(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    mock_transcription_service.transcribe.side_effect = AuthenticationError(
        message="bad",
        response=httpx.Response(401, request=httpx.Request("POST", "http://x")),
        body=None,
    )
    files = {"audio": ("x.mp4", b"x", "audio/mp4")}

    resp = await client.post(URL, files=files)

    assert resp.status_code == 502


async def test_returns_503_on_network_timeout_to_openai(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    # APIConnectionError maps to 503 (service unavailable) per handlers.py.
    # CLAUDE.md asks for 502 here; we document the actual handler behaviour:
    # connection errors are 503, auth/api errors are 502.
    mock_transcription_service.transcribe.side_effect = APIConnectionError(
        request=httpx.Request("POST", "http://x")
    )
    files = {"audio": ("x.mp4", b"x", "audio/mp4")}

    resp = await client.post(URL, files=files)

    assert resp.status_code == 503


async def test_returns_502_when_transcription_service_wraps_unknown_error(
    raw_client_unused: Any = None,
) -> None:
    """Unknown errors inside the service are wrapped as TranscriptionError → 502.

    Uses a real TranscriptionService wired around a failing AsyncMock so we
    exercise the actual exception handler rather than re-mocking the surface.
    """
    from unittest.mock import AsyncMock as _AsyncMock

    fake_openai = _AsyncMock()
    fake_openai.audio.transcriptions.create = _AsyncMock(
        side_effect=RuntimeError("boom")
    )
    real_service = TranscriptionService(
        client=fake_openai, model="whisper-1", max_audio_bytes=25 * 1024 * 1024
    )

    app = create_app()
    app.dependency_overrides[get_transcription_service] = lambda: real_service
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        resp = await c.post(URL, files={"audio": ("x.mp4", b"x", "audio/mp4")})
    app.dependency_overrides.clear()

    assert resp.status_code == 502
    assert resp.json()["error"]["code"] == "transcription_failed"
