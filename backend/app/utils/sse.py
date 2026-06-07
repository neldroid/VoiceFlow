"""SSE frame encoding helpers shared by real and fake chat endpoints."""

import json

SSE_DONE = "data: [DONE]\n\n"


def sse_frame(text: str) -> str:
    """Encode text as one SSE data event per the spec (one line per newline)."""
    lines = text.split("\n")
    return "".join(f"data: {line}\n" for line in lines) + "\n"


def sse_error(code: str, message: str) -> str:
    """Emit a typed SSE error event the client can pattern-match on."""
    payload = json.dumps({"code": code, "message": message})
    return f"event: error\ndata: {payload}\n\n"
