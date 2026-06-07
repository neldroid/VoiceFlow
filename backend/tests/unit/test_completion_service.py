"""Unit tests for ChatService streaming.

Mocks the AsyncOpenAI streaming context manager and asserts:
- exact SSE wire format ("data: <token>\\n\\n")
- DONE sentinel is always the last event on a successful stream
- chunks with null/empty content are skipped
- typed mid-stream errors emit a typed SSE error frame instead of raising
"""

import json
from unittest.mock import AsyncMock

import httpx
import pytest
from exceptions.errors import ChatCompletionError
from models.chat import Message
from openai import APIConnectionError, APIStatusError, RateLimitError
from services.chat import ChatService

from tests.conftest import make_stream_response


def _service(mock_openai: AsyncMock) -> ChatService:
    return ChatService(
        client=mock_openai,
        model="gpt-4o-mini",
        system_prompt="be helpful",
        max_tokens=256,
        temperature=0.4,
        context_messages=10,
    )


async def _collect(service: ChatService, messages: list[Message]) -> list[str]:
    return [frame async for frame in service.stream_completion(messages)]


class TestCompletionService:
    async def test_yields_one_sse_data_line_per_chunk_token(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(
            ["Hi", "!"]
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hello")])

        assert frames[0] == "data: Hi\n\n"
        assert frames[1] == "data: !\n\n"

    async def test_skips_chunks_where_delta_content_is_none(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(
            [None, "Hi", None, "!"]
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        # 2 token frames + DONE
        assert len(frames) == 3
        assert frames[0] == "data: Hi\n\n"
        assert frames[1] == "data: !\n\n"

    async def test_skips_chunks_where_delta_content_is_empty_string(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(
            ["", "Hi", "", "!", ""]
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        token_frames = [f for f in frames if f != "data: [DONE]\n\n"]
        assert token_frames == ["data: Hi\n\n", "data: !\n\n"]

    async def test_always_emits_done_sentinel_as_last_event(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(
            ["A", "B", "C"]
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        assert frames[-1] == "data: [DONE]\n\n"

    async def test_emits_done_sentinel_even_on_empty_stream(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response([])
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        assert frames == ["data: [DONE]\n\n"]

    async def test_passes_all_messages_with_system_prompt_prepended(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(["x"])
        service = _service(mock_openai)

        await _collect(
            service,
            [
                Message(role="user", content="Q1"),
                Message(role="assistant", content="A1"),
                Message(role="user", content="Q2"),
            ],
        )

        mock_openai.chat.completions.create.assert_awaited_once()
        kwargs = mock_openai.chat.completions.create.await_args.kwargs
        assert kwargs["model"] == "gpt-4o-mini"
        assert kwargs["stream"] is True
        assert kwargs["messages"] == [
            {"role": "system", "content": "be helpful"},
            {"role": "user", "content": "Q1"},
            {"role": "assistant", "content": "A1"},
            {"role": "user", "content": "Q2"},
        ]

    async def test_applies_sliding_window_to_old_history(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.return_value = make_stream_response(["x"])
        service = _service(mock_openai)  # context_messages=10

        long_history = [
            Message(role="user" if i % 2 == 0 else "assistant", content=f"m{i}")
            for i in range(20)
        ]
        await _collect(service, long_history)

        kwargs = mock_openai.chat.completions.create.await_args.kwargs
        # 1 system + the last 10 of 20 messages
        assert len(kwargs["messages"]) == 11
        assert kwargs["messages"][0]["role"] == "system"
        assert kwargs["messages"][1]["content"] == "m10"
        assert kwargs["messages"][-1]["content"] == "m19"

    async def test_rate_limit_mid_stream_emits_sse_error_frame(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.side_effect = RateLimitError(
            message="slow",
            response=httpx.Response(429, request=httpx.Request("POST", "http://x")),
            body=None,
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        assert len(frames) == 1
        assert frames[0].startswith("event: error\n")
        assert "rate_limited" in frames[0]

    async def test_connection_error_mid_stream_emits_sse_error_frame(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.side_effect = APIConnectionError(
            request=httpx.Request("POST", "http://x")
        )
        service = _service(mock_openai)

        frames = await _collect(service, [Message(role="user", content="hi")])

        assert len(frames) == 1
        assert frames[0].startswith("event: error\n")
        payload = frames[0].split("data: ")[1].split("\n\n")[0]
        assert json.loads(payload)["code"] == "upstream_unavailable"

    async def test_api_status_error_mid_stream_raises_chat_completion_error(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.side_effect = APIStatusError(
            message="boom",
            response=httpx.Response(500, request=httpx.Request("POST", "http://x")),
            body=None,
        )
        service = _service(mock_openai)

        with pytest.raises(ChatCompletionError):
            await _collect(service, [Message(role="user", content="hi")])

    async def test_unknown_exception_is_wrapped_as_chat_completion_error(
        self, mock_openai: AsyncMock
    ) -> None:
        mock_openai.chat.completions.create.side_effect = RuntimeError("kaboom")
        service = _service(mock_openai)

        with pytest.raises(ChatCompletionError):
            await _collect(service, [Message(role="user", content="hi")])
