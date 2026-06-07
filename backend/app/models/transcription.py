"""Pydantic models for the /transcribe endpoint."""

from pydantic import BaseModel, Field


class TranscriptionResponse(BaseModel):
    """Response body returned to the Android client after Whisper transcription."""

    text: str = Field(..., description="The transcribed text from the audio input.")