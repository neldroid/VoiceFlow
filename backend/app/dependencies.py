"""FastAPI Depends() providers for services and shared clients."""

from fastapi import Depends
from openai import AsyncOpenAI

from core.config import Settings, get_settings
from core.openai_client import get_openai_client
from services.chat import ChatService
from services.transcription import TranscriptionService


def get_chat_service(
    settings: Settings = Depends(get_settings),
    client: AsyncOpenAI = Depends(get_openai_client),
) -> ChatService:
    return ChatService(
        client=client,
        model=settings.openai_model,
        system_prompt=settings.chat_system_prompt,
        max_tokens=settings.chat_max_tokens,
        temperature=settings.chat_temperature,
        context_messages=settings.chat_context_messages,
    )


def get_transcription_service(
    settings: Settings = Depends(get_settings),
    client: AsyncOpenAI = Depends(get_openai_client),
) -> TranscriptionService:
    return TranscriptionService(
        client=client,
        model=settings.whisper_model,
        max_audio_bytes=settings.max_audio_size_bytes,
    )
