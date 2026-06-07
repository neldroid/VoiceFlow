"""POST /api/v1/chat/fake endpoint – SSE streaming with mocked responses.

Drop-in replacement for the real /chat endpoint to test the Android SSE
client without an OpenAI key or network dependency.

This router is only registered when APP_ENV=development.
"""

import asyncio
import logging
import random
from collections.abc import AsyncGenerator

from fastapi import APIRouter, status
from fastapi.responses import StreamingResponse

from utils.sse import SSE_DONE, sse_frame

logger = logging.getLogger(__name__)
router = APIRouter()

_MOCK_RESPONSES = [
    "Sure, I can help you with that. Let me think for a moment.",
    "That's a great question. The answer depends on a few factors, but generally speaking you should consider the context carefully.",
    "I understand what you're asking. Here is a straightforward explanation: things work best when kept simple.",
    "Of course! I'm happy to assist. Just let me know if you need more details.",
    "Interesting point. Based on what you've told me, I would recommend taking a step back and evaluating your options.",
    "I hear you. Let me walk you through the process step by step so it's easy to follow.",
]

_TOKEN_DELAY = 0.1


async def _fake_stream() -> AsyncGenerator[str, None]:
    """Yield SSE frames simulating a token-by-token LLM response."""
    logger.info("Fake stream started")

    response = random.choice(_MOCK_RESPONSES)
    tokens = [word + " " for word in response.split()]
    if tokens:
        tokens[-1] = tokens[-1].rstrip()

    for token in tokens:
        yield sse_frame(token)
        await asyncio.sleep(_TOKEN_DELAY)

    logger.info("Fake stream complete: %d tokens emitted", len(tokens))
    yield SSE_DONE


@router.post(
    "/chat/fake",
    status_code=status.HTTP_200_OK,
    summary="Stream a mocked chat completion via Server-Sent Events",
    response_description="SSE stream: data: <token>\\n\\n … data: [DONE]\\n\\n",
)
async def fake_chat_completion() -> StreamingResponse:
    """Return a pre-canned response as an SSE stream, token by token."""
    return StreamingResponse(
        _fake_stream(),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",
            "Cache-Control": "no-cache",
        },
    )
