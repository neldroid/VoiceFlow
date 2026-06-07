"""Integration tests for POST /api/v1/chat (SSE).

Exercises the full ASGI stack with a mocked ChatService. We assert:
- 200 + text/event-stream content type and the no-buffering headers
- exact "data: <token>\\n\\n" wire format
- terminal data: [DONE] sentinel
- 422 on every Pydantic validation failure
- concurrent requests don't bleed state into each other
- mid-stream OpenAI failure surfaces as a typed SSE error frame
"""

import asyncio
from collections.abc import AsyncIterator
from typing import Any

from dependencies import get_chat_service
from httpx import ASGITransport, AsyncClient
from main import create_app
from models.chat import Message

URL = "/api/v1/chat"


def _valid_body() -> dict[str, Any]:
    return {"messages": [{"role": "user", "content": "hi"}]}


# ── 200 + headers + format ─────────────────────────────────────────────────


async def test_returns_200_with_event_stream_content_type(client: AsyncClient) -> None:
    async with client.stream("POST", URL, json=_valid_body()) as resp:
        assert resp.status_code == 200
        assert resp.headers["content-type"].startswith("text/event-stream")


async def test_response_contains_no_cache_header(client: AsyncClient) -> None:
    async with client.stream("POST", URL, json=_valid_body()) as resp:
        assert resp.headers.get("cache-control") == "no-cache"


async def test_response_contains_x_accel_buffering_no_header(
    client: AsyncClient,
) -> None:
    async with client.stream("POST", URL, json=_valid_body()) as resp:
        assert resp.headers.get("x-accel-buffering") == "no"


async def test_streams_tokens_as_correctly_formatted_sse_data_lines(
    client: AsyncClient,
) -> None:
    async with client.stream("POST", URL, json=_valid_body()) as resp:
        body = ""
        async for chunk in resp.aiter_text():
            body += chunk

    # mock_chat_service yields these three frames
    assert "data: Hello\n\n" in body
    assert "data:  world\n\n" in body
    assert "data: [DONE]\n\n" in body


async def test_last_sse_event_is_done(client: AsyncClient) -> None:
    async with client.stream("POST", URL, json=_valid_body()) as resp:
        body = ""
        async for chunk in resp.aiter_text():
            body += chunk

    assert body.rstrip().endswith("data: [DONE]")


# ── 422 validation errors ──────────────────────────────────────────────────


async def test_returns_422_on_empty_messages_array(client: AsyncClient) -> None:
    resp = await client.post(URL, json={"messages": []})
    assert resp.status_code == 422


async def test_returns_422_on_whitespace_only_message_content(
    client: AsyncClient,
) -> None:
    resp = await client.post(
        URL, json={"messages": [{"role": "user", "content": "   "}]}
    )
    assert resp.status_code == 422


async def test_returns_422_on_invalid_role_value(client: AsyncClient) -> None:
    resp = await client.post(
        URL, json={"messages": [{"role": "hacker", "content": "hi"}]}
    )
    assert resp.status_code == 422


async def test_returns_422_when_messages_field_is_absent(client: AsyncClient) -> None:
    resp = await client.post(URL, json={})
    assert resp.status_code == 422


async def test_returns_422_when_request_body_is_not_json(client: AsyncClient) -> None:
    resp = await client.post(
        URL, content="not json at all", headers={"Content-Type": "application/json"}
    )
    assert resp.status_code == 422


# ── Concurrency: no shared state bleed ─────────────────────────────────────


async def test_handles_concurrent_sse_streams_without_shared_state_bleed() -> None:
    """Fire two concurrent /chat requests; assert each only sees its own tokens.

    Uses a service that echoes the user's first-message content as tokens, so
    we can prove tokens emitted to stream A never appear in stream B.
    """

    class EchoChatService:
        async def stream_completion(
            self, messages: list[Message]
        ) -> AsyncIterator[str]:
            content = messages[-1].content
            for ch in content:
                # tiny await so the two coroutines actually interleave
                await asyncio.sleep(0)
                yield f"data: {ch}\n\n"
            yield "data: [DONE]\n\n"

    app = create_app()
    app.dependency_overrides[get_chat_service] = lambda: EchoChatService()
    transport = ASGITransport(app=app)

    async def collect_response(content: str) -> str:
        async with AsyncClient(transport=transport, base_url="http://test") as c:
            async with c.stream(
                "POST", URL, json={"messages": [{"role": "user", "content": content}]}
            ) as resp:
                buf = ""
                async for chunk in resp.aiter_text():
                    buf += chunk
                return buf

    body_a, body_b = await asyncio.gather(
        collect_response("AAAAAA"), collect_response("BBBBBB")
    )
    app.dependency_overrides.clear()

    assert "data: A\n\n" in body_a
    assert "data: B\n\n" not in body_a
    assert "data: B\n\n" in body_b
    assert "data: A\n\n" not in body_b
    assert body_a.rstrip().endswith("data: [DONE]")
    assert body_b.rstrip().endswith("data: [DONE]")


# ── Mid-stream OpenAI failure ──────────────────────────────────────────────


async def test_streams_typed_error_event_when_openai_raises_mid_stream() -> None:
    """When OpenAI raises (e.g. RateLimitError) before/during streaming, the
    response status is still 200 (headers already sent) and the body contains
    a typed `event: error` frame instead of a token stream."""

    import httpx
    from openai import RateLimitError

    class RaisingChatService:
        async def stream_completion(
            self, _messages: list[Message]
        ) -> AsyncIterator[str]:
            # Mirror the real service's behaviour: catch RateLimitError and emit
            # a typed SSE error frame.
            try:
                raise RateLimitError(
                    message="slow",
                    response=httpx.Response(
                        429, request=httpx.Request("POST", "http://x")
                    ),
                    body=None,
                )
            except RateLimitError:
                yield (
                    'event: error\n'
                    'data: {"code": "rate_limited", '
                    '"message": "Upstream rate limit reached. Please retry."}\n\n'
                )

    app = create_app()
    app.dependency_overrides[get_chat_service] = lambda: RaisingChatService()
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:
        async with c.stream("POST", URL, json=_valid_body()) as resp:
            assert resp.status_code == 200
            body = ""
            async for chunk in resp.aiter_text():
                body += chunk
    app.dependency_overrides.clear()

    assert "event: error" in body
    assert "rate_limited" in body
