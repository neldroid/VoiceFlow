"""API v1 router: aggregates all endpoint sub-routers."""

from fastapi import APIRouter

from api.v1.endpoints.chat import router as chat_router
from api.v1.endpoints.transcribe import router as transcribe_router
from core.config import get_settings

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(chat_router, tags=["Chat"])
api_router.include_router(transcribe_router, tags=["Transcription"])

if get_settings().app_env == "development":
    from api.v1.endpoints.chatfake import router as chatfake_router

    api_router.include_router(chatfake_router, tags=["Chat (Fake)"])
