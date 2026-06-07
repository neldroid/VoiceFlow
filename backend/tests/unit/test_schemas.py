"""Boundary tests for the Pydantic request/response schemas.

We test our own validation rules — not Pydantic's coercion machinery.
"""

import pytest
from models.chat import ChatRequest, Message
from models.transcription import TranscriptionResponse
from pydantic import ValidationError


class TestChatRequest:
    def test_valid_user_message_is_accepted(self) -> None:
        req = ChatRequest(messages=[Message(role="user", content="hello")])
        assert req.messages[0].role == "user"
        assert req.messages[0].content == "hello"

    def test_valid_assistant_message_is_accepted(self) -> None:
        req = ChatRequest(messages=[Message(role="assistant", content="hi back")])
        assert req.messages[0].role == "assistant"

    def test_valid_system_message_is_accepted(self) -> None:
        req = ChatRequest(messages=[Message(role="system", content="be helpful")])
        assert req.messages[0].role == "system"

    def test_unknown_role_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest(messages=[{"role": "hacker", "content": "hi"}])
    def test_whitespace_only_content_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest(messages=[{"role": "user", "content": "   \t\n  "}])
    def test_empty_string_content_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest(messages=[{"role": "user", "content": ""}])
    def test_empty_messages_list_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest(messages=[])

    def test_missing_messages_field_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            ChatRequest()
    def test_extra_fields_are_ignored_by_default(self) -> None:
        # Pydantic v2 default behaviour: extra="ignore"
        req = ChatRequest.model_validate(
            {
                "messages": [{"role": "user", "content": "hi"}],
                "unexpected_field": "should be silently dropped",
            }
        )
        assert len(req.messages) == 1
        assert not hasattr(req, "unexpected_field")


class TestTranscriptionResponse:
    def test_serialises_text_field_correctly(self) -> None:
        resp = TranscriptionResponse(text="hello world")
        assert resp.model_dump() == {"text": "hello world"}

    def test_missing_text_field_raises_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            TranscriptionResponse()