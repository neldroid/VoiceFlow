"""POST /api/v1/transcribe endpoint."""

import logging

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status

from core.config import Settings, get_settings
from dependencies import get_transcription_service
from models.transcription import TranscriptionResponse
from services.transcription import TranscriptionService

logger = logging.getLogger(__name__)
router = APIRouter()


@router.post(
    "/transcribe",
    response_model=TranscriptionResponse,
    status_code=status.HTTP_200_OK,
    summary="Transcribe an audio file using OpenAI Whisper",
)
async def transcribe_audio(
    audio: UploadFile = File(..., description="The recorded audio file (m4a, wav, etc.)"),
    settings: Settings = Depends(get_settings),
    service: TranscriptionService = Depends(get_transcription_service),
) -> TranscriptionResponse:
    """
    Accept a multipart audio upload, proxy it to Whisper, return transcribed text.

    Validates MIME type and declared size at the boundary before reading
    the body into memory, to fail fast on malicious or malformed uploads.
    """
    if audio.content_type not in settings.allowed_audio_content_types:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Unsupported audio format: {audio.content_type}",
        )

    if audio.size is not None and audio.size > settings.max_audio_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Audio exceeds {settings.max_audio_size_mb} MB limit.",
        )

    audio_bytes = await audio.read()
    if len(audio_bytes) > settings.max_audio_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Audio exceeds {settings.max_audio_size_mb} MB limit.",
        )

    text = await service.transcribe(
        audio_bytes=audio_bytes,
        filename=audio.filename or "audio.m4a",
        content_type=audio.content_type or "audio/m4a",
    )

    return TranscriptionResponse(text=text)
