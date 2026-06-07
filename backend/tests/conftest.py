"""Shared pytest fixtures: env, app factory, async HTTP client, mock OpenAI.

All test imports use the SAME module paths the application uses internally
(bare `from services.x import ...` rather than `from app.services.x import ...`).
This ensures a single module identity, so that exception types raised by the
service compare equal to the exception types caught by the tests.
"""

import os

# Set env vars BEFORE any app import so Settings construction succeeds.
os.environ.setdefault("OPENAI_API_KEY", "sk-test-fake-key-for-tests")
os.environ.setdefault("APP_ENV", "test")

from collections.abc import AsyncGenerator, AsyncIterator
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest
import pytest_asyncio
from core.config import get_settings
from dependencies import get_chat_service, get_transcription_service
from httpx import ASGITransport, AsyncClient
from main import create_app
from services.chat import ChatService
from services.transcription import TranscriptionService


@pytest.fixture(autouse=True)
def _patch_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """Ensure every test sees a deterministic, valid env."""
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test-fake-key-for-tests")
    monkeypatch.setenv("APP_ENV", "test")
    get_settings.cache_clear()


def async_chunk_stream(tokens: list[str | None]) -> AsyncIterator[Any]:
    """Build an async iterator mimicking OpenAI streaming chunks.

    Each yielded chunk has chunk.choices[0].delta.content set to the token
    (or None / "" to model OpenAI's "no content this chunk" signals).
    """

    async def _gen() -> AsyncIterator[Any]:
        for token in tokens:
            chunk = MagicMock()
            chunk.choices = [MagicMock()]
            chunk.choices[0].delta.content = token
            yield chunk

    return _gen()


class _StreamCtxManager:
    """Async context manager wrapping an async iterator (mirrors openai's stream)."""

    def __init__(self, iterator: AsyncIterator[Any]) -> None:
        self._iterator = iterator

    async def __aenter__(self) -> AsyncIterator[Any]:
        return self._iterator

    async def __aexit__(self, *exc: Any) -> None:
        return None


def make_stream_response(tokens: list[str | None]) -> _StreamCtxManager:
    """Wrap async_chunk_stream in the async-context-manager OpenAI's client returns."""
    return _StreamCtxManager(async_chunk_stream(tokens))


@pytest.fixture
def mock_openai() -> AsyncMock:
    """A pre-wired AsyncMock for the OpenAI AsyncClient surface we depend on."""
    client = AsyncMock()
    client.audio = MagicMock()
    client.audio.transcriptions = MagicMock()
    client.audio.transcriptions.create = AsyncMock()
    client.chat = MagicMock()
    client.chat.completions = MagicMock()
    client.chat.completions.create = AsyncMock()
    return client


@pytest.fixture
def mock_transcription_service() -> AsyncMock:
    service = AsyncMock(spec=TranscriptionService)
    service.transcribe.return_value = "Hello world"
    return service


@pytest.fixture
def mock_chat_service() -> AsyncMock:
    service = AsyncMock(spec=ChatService)

    async def _stream(_messages: Any) -> AsyncIterator[str]:
        yield "data: Hello\n\n"
        yield "data:  world\n\n"
        yield "data: [DONE]\n\n"

    service.stream_completion = _stream
    return service


@pytest_asyncio.fixture
async def client(
    mock_transcription_service: AsyncMock,
    mock_chat_service: AsyncMock,
) -> AsyncGenerator[AsyncClient, None]:
    app = create_app()
    app.dependency_overrides[get_transcription_service] = (
        lambda: mock_transcription_service
    )
    app.dependency_overrides[get_chat_service] = lambda: mock_chat_service
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()
