# VoiceFlow AI

A production-ready voice assistant prototype: an Android client captures audio, a FastAPI backend transcribes it via OpenAI Whisper and streams an LLM response back over Server-Sent Events (SSE), and the client speaks the reply using on-device TTS.

Built to explore real-world constraints around latency, streaming UX, and AI integration on mobile.

```
┌─────────────────┐   multipart audio    ┌──────────────────────┐
│                 │ ───────────────────▶ │                      │──▶ Whisper
│  Android client │                      │   FastAPI backend    │
│  Compose + Hilt │◀──── transcript ─────│   (uvicorn)          │──▶ GPT-4o-mini
│  + Retrofit     │◀─ SSE token stream ──│                      │   (stream)
└─────────────────┘                      └──────────────────────┘
```
---
## Screenshots
![](https://github.com/user-attachments/assets/da7614fb-209c-49f1-a97e-0aa2349f15a3)  |  ![](https://github.com/user-attachments/assets/0f727146-5015-4979-afea-c12419885cfd)
---

## Why I built this

Most voice AI demos stop at "it works on my machine." I wanted to validate the full stack under realistic constraints: network latency, interrupted streams, on-device TTS coordination, and a backend that doesn't fall over under concurrent requests. The result is a minimal but honest foundation for a production voice product.

---

## Architecture decisions

**SSE over WebSockets for streaming**
WebSockets add bidirectional complexity that isn't needed here. SSE gives us a simple, resumable, HTTP-native stream from backend to client. The Android client handles `data:` events directly via a custom OkHttp interceptor.

**Stateless backend, stateful client**
`/chat` receives the full rolling conversation history on every request. The backend trims it server-side via `CHAT_CONTEXT_MESSAGES` to bound LLM cost. This makes the backend trivially scalable — no session state, no sticky routing.

**On-device TTS**
Server-side speech synthesis (ElevenLabs, Google TTS) adds ~300–800ms of latency per turn. Android's platform TTS is instant and free. For a conversational product, that tradeoff is obvious until you have strong monetization to justify the quality upgrade.

**Hilt for DI, not manual wiring**
With three distinct layers (recording, networking, playback) all needing lifecycle awareness, manual DI becomes fragile fast. Hilt keeps scoping explicit and testable.

---

## Stack

| Layer | Technology |
|---|---|
| Android UI | Jetpack Compose |
| DI | Hilt |
| Networking | Retrofit + OkHttp (SSE) |
| Backend | FastAPI + uvicorn |
| Transcription | OpenAI Whisper |
| Chat | GPT-4o-mini (streaming) |
| TTS | Android platform TTS |
| Tests | pytest (backend, 85%+ coverage) · Robolectric (Android) |

---

## Repository layout

```
voiceflow-ai/
├── backend/
│   ├── app/
│   │   ├── api/          # Route handlers
│   │   ├── core/         # Config, settings (pydantic-settings)
│   │   └── services/     # Whisper + chat services
│   └── tests/
└── android-app/
    └── app/
        ├── ui/           # Compose screens
        ├── di/           # Hilt modules
        └── data/         # Retrofit services, SSE parser
```

---

## Running locally

### Backend

**Prerequisites:** Python 3.11+, an OpenAI API key with access to `whisper-1` and `gpt-4o-mini`.

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env   # add your OPENAI_API_KEY
fastapi dev
```

Key environment variables:

| Variable | Default | Notes |
|---|---|---|
| `OPENAI_API_KEY` | — | Required. Validated at startup. |
| `OPENAI_MODEL` | `gpt-4o-mini` | Chat model. |
| `WHISPER_MODEL` | `whisper-1` | Transcription model. |
| `CHAT_CONTEXT_MESSAGES` | `10` | Sliding window; keep even for user/assistant pairs. |
| `MAX_AUDIO_SIZE_MB` | `25` | Enforced at boundary and after read. |

Endpoints:
- `POST /api/v1/transcribe` — multipart audio → `{"text": "..."}`
- `POST /api/v1/chat` — `{messages: [...]}` → SSE token stream

Tests: `pytest` (coverage gate at 85%).

### Android

**Prerequisites:** Android Studio Koala+ or JDK 17 + Android SDK (platform 36).

```bash
cd android-app
cp local.properties.example local.properties
# Set sdk.dir and BASE_URL in local.properties
./gradlew :app:installDebug
```

`BASE_URL` by scenario:

| Scenario | Value |
|---|---|
| Emulator → host | `http://10.0.2.2:8000` |
| Physical device (same Wi-Fi) | `http://<host-LAN-IP>:8000` |
| Backend behind TLS | `https://your.host` |

---

## Known limitations and next steps

These aren't excuses — they're the honest backlog of a prototype built to validate a concept, not ship to production.

**Backend**
- Audio is buffered before forwarding to Whisper. Fine for short turns; needs streaming multipart for long-form.
- No auth or rate-limiting. Intended for local use only; needs a shared-secret header + per-IP token bucket before any public exposure.
- No retry/circuit-breaker around OpenAI. An upstream blip surfaces as a 5xx today.
- No request tracing. Adding `X-Request-ID` + OpenTelemetry would make a voice turn traceable across `/transcribe → /chat → OpenAI`.

**Android**
- Chat history is in-memory. Promoting to Room would survive process death and unlock multi-session features.
- SSE error UX is generic. Transport errors, partial completions, and upstream cancellations should be distinguished.
- No build flavors (dev/staging/prod). Currently relies on `local.properties` discipline.
- No accessibility pass. TalkBack labels, reduced-motion handling, and large-text layouts are missing.

**Cross-cutting**
- No CI. Backend `pytest + ruff + mypy` and Android `lint + testDebugUnitTest` should run on every PR.
- No contract tests. An OpenAPI schema consumed by both sides would catch API drift at CI time.

---

## License

MIT
