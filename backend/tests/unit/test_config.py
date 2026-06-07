"""Unit tests for the Settings (BaseSettings) configuration object.

Boundary conditions: required fields, defaults, secret handling.
"""


import pytest
from core.config import Settings, get_settings
from pydantic import ValidationError


def _settings(**overrides: object) -> Settings:
    """Construct Settings without picking up the dev .env file on disk.

    monkeypatch in tests already injects OPENAI_API_KEY; the .env in the repo
    is a developer convenience that would otherwise leak secrets into tests.
    """
    return Settings(_env_file=None, **overrides)


class TestSettingsLoading:
    def test_loads_openai_api_key_from_environment(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv("OPENAI_API_KEY", "sk-loaded-from-env")
        get_settings.cache_clear()

        s = _settings()

        assert s.openai_api_key.get_secret_value() == "sk-loaded-from-env"

    def test_raises_validation_error_when_openai_api_key_is_missing(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("OPENAI_API_KEY", raising=False)
        get_settings.cache_clear()

        with pytest.raises(ValidationError):
            _settings()

    def test_raises_validation_error_when_openai_api_key_is_placeholder(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv("OPENAI_API_KEY", "sk-...your-key-here")
        get_settings.cache_clear()

        with pytest.raises(ValidationError):
            _settings()


class TestSettingsDefaults:
    def test_default_openai_model_is_gpt_4o_mini(self) -> None:
        # NB: CLAUDE.md spec says gpt-4o; actual code default is gpt-4o-mini.
        s = _settings()
        assert s.openai_model == "gpt-4o-mini"

    def test_default_whisper_model_is_whisper_1(self) -> None:
        s = _settings()
        assert s.whisper_model == "whisper-1"

    def test_default_max_audio_size_is_25mb(self) -> None:
        s = _settings()
        assert s.max_audio_size_mb == 25
        assert s.max_audio_size_bytes == 25 * 1024 * 1024

    def test_default_chat_context_messages_is_even(self) -> None:
        # The validator rejects odd values; default must satisfy it.
        s = _settings()
        assert s.chat_context_messages % 2 == 0

    def test_chat_context_messages_must_be_even(self) -> None:
        with pytest.raises(ValidationError):
            _settings(chat_context_messages=11)


class TestSettingsSecretHandling:
    def test_api_key_does_not_appear_in_repr(self) -> None:
        s = _settings()
        assert "sk-test-fake-key-for-tests" not in repr(s)

    def test_api_key_does_not_appear_in_str(self) -> None:
        s = _settings()
        assert "sk-test-fake-key-for-tests" not in str(s)

    def test_api_key_does_not_appear_in_model_dump(self) -> None:
        # SecretStr serialises as "**********" by default.
        s = _settings()
        dumped = str(s.model_dump())
        assert "sk-test-fake-key-for-tests" not in dumped
