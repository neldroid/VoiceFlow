"""
Application entry point.

Wires together FastAPI, CORS, exception handlers, and the API router.
The create_app() factory pattern makes the app testable without side-effects.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from api.v1.router import api_router
from core.config import get_settings
from core.lifespan import lifespan
from core.logging import configure_logging
from exceptions.handlers import register_exception_handlers


def create_app() -> FastAPI:
    """Construct and configure the FastAPI application."""
    settings = get_settings()
    configure_logging(settings.log_level)

    app = FastAPI(
        title="Voice AI Backend",
        description="Transcription + LLM streaming backend for the Voice AI Android app.",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs" if settings.app_env == "development" else None,
        redoc_url=None,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=False,
        allow_methods=["POST", "OPTIONS"],
        allow_headers=["Content-Type", "Accept"],
    )

    register_exception_handlers(app)
    app.include_router(api_router)

    return app


app = create_app()
