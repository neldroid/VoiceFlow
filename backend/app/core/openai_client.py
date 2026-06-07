"""Shared AsyncOpenAI client singleton."""

from functools import lru_cache

from openai import AsyncOpenAI

from core.config import get_settings


@lru_cache(maxsize=1)
def get_openai_client() -> AsyncOpenAI:
    settings = get_settings()
    return AsyncOpenAI(api_key=settings.openai_api_key.get_secret_value())
