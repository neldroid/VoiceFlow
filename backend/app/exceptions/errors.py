"""Domain exception classes. Kept free of FastAPI imports so services can use them."""


class AudioTooLargeError(Exception):
    """Raised when the uploaded audio file exceeds the configured size limit."""


class TranscriptionError(Exception):
    """Raised when the Whisper API call fails."""


class ChatCompletionError(Exception):
    """Raised when the LLM completion API call fails."""
