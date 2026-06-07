"""Pydantic models for the /chat endpoint."""

from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field, field_validator


class Persona(str, Enum):
    friendly = "friendly"
    parent = "parent"
    teacher = "teacher"


class Message(BaseModel):
    """A single chat message following the OpenAI message format."""

    role: Literal["user", "assistant", "system"] = Field(
        ..., description="The speaker role."
    )
    content: str = Field(..., description="Message content.")

    @field_validator("content")
    @classmethod
    def content_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("Message content cannot be empty")
        return v


class ChatRequest(BaseModel):
    """Request body for the /chat endpoint."""

    messages: list[Message] = Field(
        ...,
        min_length=1,
        description="Conversation history, ending with the latest user message.",
    )
    persona: Persona = Field(
        default=Persona.friendly,
        description="Persona that shapes the assistant's tone and style.",
    )
