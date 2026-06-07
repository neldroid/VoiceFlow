"""POST /api/v1/chat endpoint – SSE streaming."""

import logging

from fastapi import APIRouter, Depends, status
from fastapi.responses import StreamingResponse

from dependencies import get_chat_service
from models.chat import ChatRequest
from services.chat import ChatService

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post(
    "/chat",
    status_code=status.HTTP_200_OK,
    summary="Stream a chat completion via Server-Sent Events",
    response_description="SSE stream: data: <token>\\n\\n … data: [DONE]\\n\\n",
)
async def chat_completion(
    body: ChatRequest,
    service: ChatService = Depends(get_chat_service),
) -> StreamingResponse:
    """Forward the conversation to the LLM and stream the response as SSE."""
    return StreamingResponse(
        service.stream_completion(body.messages, body.persona),
        media_type="text/event-stream",
        headers={
            "X-Accel-Buffering": "no",
            "Cache-Control": "no-cache",
        },
    )
