"""Security tests: assert no sensitive data leaks via API responses.

These are belt-and-braces checks. The Settings.repr/str hides the key, but
we re-verify at the HTTP boundary because a future refactor that, say, dumps
settings into an error envelope would silently regress.
"""

from unittest.mock import AsyncMock

import httpx
from httpx import AsyncClient
from openai import AuthenticationError

REAL_KEY = "sk-test-fake-key-for-tests"


async def test_api_key_is_not_returned_in_transcribe_success_body(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    mock_transcription_service.transcribe.return_value = "ok"
    files = {"audio": ("c.mp4", b"x", "audio/mp4")}

    resp = await client.post("/api/v1/transcribe", files=files)

    assert REAL_KEY not in resp.text


async def test_api_key_is_not_returned_in_transcribe_error_body(
    client: AsyncClient, mock_transcription_service: AsyncMock
) -> None:
    mock_transcription_service.transcribe.side_effect = AuthenticationError(
        message="bad",
        response=httpx.Response(401, request=httpx.Request("POST", "http://x")),
        body=None,
    )
    files = {"audio": ("c.mp4", b"x", "audio/mp4")}

    resp = await client.post("/api/v1/transcribe", files=files)

    assert resp.status_code == 502
    assert REAL_KEY not in resp.text


async def test_413_response_does_not_echo_back_the_audio_bytes(
    client: AsyncClient,
) -> None:
    sentinel = b"SECRET-PAYLOAD-MARKER-XYZ"
    oversize = sentinel + b"x" * (25 * 1024 * 1024 + 1)
    files = {"audio": ("big.wav", oversize, "audio/wav")}

    resp = await client.post("/api/v1/transcribe", files=files)

    assert resp.status_code == 413
    assert b"SECRET-PAYLOAD-MARKER-XYZ" not in resp.content


async def test_api_key_is_not_returned_in_chat_response(
    client: AsyncClient,
) -> None:
    async with client.stream(
        "POST",
        "/api/v1/chat",
        json={"messages": [{"role": "user", "content": "hi"}]},
    ) as resp:
        body = ""
        async for chunk in resp.aiter_text():
            body += chunk

    assert REAL_KEY not in body


async def test_api_key_is_not_returned_in_validation_error_body(
    client: AsyncClient,
) -> None:
    # Pydantic error envelopes sometimes echo the failing input — make sure
    # nothing in the path tries to dump Settings into the response.
    resp = await client.post(
        "/api/v1/chat", json={"messages": [{"role": "hacker", "content": "hi"}]}
    )
    assert resp.status_code == 422
    assert REAL_KEY not in resp.text
