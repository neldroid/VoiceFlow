"""
FastAPI lifespan context manager.

Centralises startup/shutdown logic.
"""

import logging
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from core.config import get_settings
from core.openai_client import get_openai_client

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan: runs startup, yields, then runs shutdown."""
    settings = get_settings()
    logger.info("Starting Voice AI backend (env=%s)", settings.app_env)

    client = get_openai_client()
    logger.info("OpenAI async client initialised")

    yield  # ── application runs here ──

    logger.info("Shutting down Voice AI backend")
    await client.close()
