"""
Application configuration via Pydantic BaseSettings.

All settings are sourced from environment variables or a .env file.
This is the SINGLE source of truth – no magic strings elsewhere.
"""

from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

_DEFAULT_SYSTEM_PROMPT = (
    "You are a helpful, conversational voice assistant. "
    "Guidelines: "
    "1. Be extremely concise. Keep responses to 1-2 sentences when possible. "
    "2. Use plain text only. No Markdown, bolding, or lists (e.g., use 'and' instead of bullet points). "
    "3. Spell out abbreviations or symbols that might sound strange when read by TTS (e.g., use 'percent' instead of '%'). "
    "4. Avoid long technical explanations unless specifically asked. "
    "5. If the user's transcribed input is unclear or too short, ask a brief clarifying question."
)

_DEFAULT_AUDIO_CONTENT_TYPES = [
    "audio/mpeg",
    "audio/mp4",
    "audio/m4a",
    "audio/wav",
    "audio/webm",
    "audio/ogg",
    "audio/flac",
    "audio/x-m4a",
]


class Settings(BaseSettings):
    """Validated, typed application settings loaded from the environment."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
    )

    # ── OpenAI ──────────────────────────────────────────────────────────────
    openai_api_key: SecretStr = Field(..., description="OpenAI secret key")
    openai_model: str = Field(default="gpt-4o-mini", description="Chat completion model")
    whisper_model: str = Field(default="whisper-1", description="Transcription model")

    # ── Chat ────────────────────────────────────────────────────────────────
    chat_system_prompt: str = Field(
        default=_DEFAULT_SYSTEM_PROMPT,
        description="System prompt prepended to every chat completion.",
    )
    chat_max_tokens: int = Field(
        default=256,
        ge=64,
        le=2048,
        description="Hard cap on completion length. Keeps voice responses brief.",
    )
    chat_temperature: float = Field(
        default=0.4,
        ge=0.0,
        le=2.0,
        description="Sampling temperature. Lower = more deterministic, better for voice.",
    )
    chat_context_messages: int = Field(
        default=10,
        ge=2,
        le=50,
        description=(
            "Sliding-window size (user+assistant turns kept). "
            "Prevents unbounded context growth. Must be even so pairs stay intact."
        ),
    )

    # ── App ─────────────────────────────────────────────────────────────────
    app_env: str = Field(default="development")
    log_level: str = Field(default="INFO")
    cors_origins: list[str] = Field(
        default=["http://localhost:8080", "http://10.0.2.2:8080"],
        description="Allowed CORS origins. 10.0.2.2 is the Android emulator loopback.",
    )
    max_audio_size_mb: int = Field(default=25, ge=1, le=100)
    allowed_audio_content_types: list[str] = Field(
        default=_DEFAULT_AUDIO_CONTENT_TYPES,
        description="MIME types accepted by /transcribe.",
    )

    @field_validator("openai_api_key", mode="after")
    @classmethod
    def api_key_must_not_be_empty(cls, v: SecretStr) -> SecretStr:
        raw_key = v.get_secret_value()
        if not raw_key or raw_key.startswith("sk-..."):
            raise ValueError("OPENAI_API_KEY must be a valid key, not the placeholder.")
        return v

    @field_validator("chat_context_messages", mode="after")
    @classmethod
    def must_be_even(cls, v: int) -> int:
        if v % 2 != 0:
            raise ValueError(
                "chat_context_messages must be even to keep user/assistant pairs intact"
            )
        return v

    @property
    def max_audio_size_bytes(self) -> int:
        return self.max_audio_size_mb * 1024 * 1024


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return a cached singleton Settings instance."""
    return Settings()
