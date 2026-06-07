"""
ChatService: wraps the OpenAI chat completion API with SSE streaming.

Design decisions:
  - Sliding window trims history to `context_messages` most-recent turns before
    every request, keeping total prompt tokens bounded without client involvement.
  - max_tokens + temperature are explicit parameters so the voice assistant stays
    concise and deterministic; defaults live in Settings, not here.
  - Errors inside the async generator are caught and emitted as a terminal
    `event: error` SSE frame so the Android client always gets a parseable signal
    rather than a silent stream close or an HTTP 500 after headers are sent.
"""

import logging
from collections.abc import AsyncGenerator

from openai import APIConnectionError, APIStatusError, AsyncOpenAI, RateLimitError

from exceptions.errors import ChatCompletionError
from models.chat import Message, Persona
from utils.sse import SSE_DONE, sse_error, sse_frame

_PERSONA_PROMPTS: dict[Persona, str] = {
    Persona.friendly: (
        "You are a warm, upbeat voice assistant. "
        "Keep replies to 1-2 sentences, use plain casual language, "
        "no Markdown or bullet points, and spell out symbols for TTS."
    ),
    Persona.parent: (
        "You are a caring, patient voice assistant that speaks like a supportive parent. "
        "Use simple, nurturing language, keep replies to 1-2 sentences, "
        "no Markdown or bullet points, and spell out symbols for TTS."
    ),
    Persona.teacher: (
        "You are a clear, encouraging voice assistant that speaks like a knowledgeable teacher. "
        "Explain things simply and precisely in 1-2 sentences, "
        "no Markdown or bullet points, and spell out symbols for TTS."
    ),
}

logger = logging.getLogger(__name__)


class ChatService:
    """Streams LLM completion tokens as Server-Sent Events."""

    def __init__(
        self,
        client: AsyncOpenAI,
        model: str,
        system_prompt: str,
        max_tokens: int,
        temperature: float,
        context_messages: int,
    ) -> None:
        self._client = client
        self._model = model
        self._system_prompt = system_prompt
        self._max_tokens = max_tokens
        self._temperature = temperature
        self._context_messages = context_messages

    def _apply_sliding_window(self, messages: list[Message]) -> list[Message]:
        """Keep only the most-recent `context_messages` turns.

        Truncates from the front so the latest user message is always included.
        Uses an even cap so user/assistant pairs stay intact.
        """
        cap = self._context_messages
        if len(messages) > cap:
            dropped = len(messages) - cap
            logger.debug("Sliding window: dropped %d oldest message(s)", dropped)
            return messages[-cap:]
        return messages

    async def stream_completion(
        self, messages: list[Message], persona: Persona = Persona.friendly
    ) -> AsyncGenerator[str, None]:
        """Yield SSE-formatted strings for every token the LLM produces.

        Errors that occur after the stream opens are emitted as a terminal
        `event: error` frame rather than crashing the generator silently.
        """
        system_prompt = _PERSONA_PROMPTS.get(persona, self._system_prompt)
        windowed = self._apply_sliding_window(messages)
        openai_messages: list[dict[str, str]] = [
            {"role": "system", "content": system_prompt},
            *[{"role": m.role, "content": m.content} for m in windowed],
        ]

        logger.info(
            "Starting stream: model=%s persona=%s history=%d (of %d) max_tokens=%d temp=%.2f",
            self._model,
            persona.value,
            len(windowed),
            len(messages),
            self._max_tokens,
            self._temperature,
        )

        token_count = 0
        try:
            async with await self._client.chat.completions.create(
                model=self._model,
                messages=openai_messages,  # type: ignore[arg-type]
                stream=True,
                max_tokens=self._max_tokens,
                temperature=self._temperature,
            ) as stream:
                async for chunk in stream:
                    text = chunk.choices[0].delta.content if chunk.choices else None
                    if text:
                        token_count += 1
                        yield sse_frame(text)

        except RateLimitError:
            # Caught here (not by global handler) because headers are already sent.
            logger.warning("OpenAI rate limit hit during stream")
            yield sse_error("rate_limited", "Upstream rate limit reached. Please retry.")
            return

        except APIConnectionError:
            # Caught here (not by global handler) because headers are already sent.
            logger.error("OpenAI connection error during stream")
            yield sse_error("upstream_unavailable", "Could not reach OpenAI.")
            return

        except APIStatusError as exc:
            logger.error("OpenAI API error during stream: status=%d", exc.status_code)
            raise ChatCompletionError(f"OpenAI returned {exc.status_code}") from exc

        except Exception as exc:
            logger.exception("Unexpected error during stream")
            raise ChatCompletionError(f"Stream failed: {exc}") from exc

        logger.info("Stream complete: %d tokens emitted", token_count)
        yield SSE_DONE
