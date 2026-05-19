"""DeepSeek API client for PR review — stdlib only, no third-party deps."""
from __future__ import annotations

import json
import urllib.error
import urllib.request

_DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"
_MODEL = "deepseek-chat"
_MAX_TOKENS = 2000   # PR review needs more headroom than log analysis
_TEMPERATURE = 0.1


def call(system_prompt: str, user_prompt: str, api_key: str) -> str:
    """Send prompts to DeepSeek. Returns raw response content string.

    Raises urllib.error.URLError / OSError on network failure.
    """
    body = json.dumps({
        "model": _MODEL,
        "max_tokens": _MAX_TOKENS,
        "temperature": _TEMPERATURE,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }).encode("utf-8")

    req = urllib.request.Request(
        _DEEPSEEK_API_URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    return data["choices"][0]["message"]["content"]
