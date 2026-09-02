"""Optional DeepSeek summaries for grouped quality findings."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

from quality_backlog.models import FindingGroup

_API_URL = "https://api.deepseek.com/chat/completions"
_MODEL = "deepseek-chat"

_SYSTEM_PROMPT = """\
You summarize grouped static-analysis findings for a Java/Spring/Gradle repository.
Return ONLY valid JSON with keys: summary, impact, recommendedFix.
Keep each value concise and actionable. Do not invent evidence beyond the input.
"""


def summarize(group: FindingGroup, enabled: bool) -> dict[str, str] | None:
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not enabled or not api_key:
        return None

    examples = [
        {
            "path": issue.path,
            "line": issue.line,
            "message": issue.message,
            "severity": issue.severity,
            "type": issue.issue_type,
        }
        for issue in group.issues[:8]
    ]
    payload = json.dumps(
        {
            "component": group.component,
            "category": group.category,
            "theme": group.theme,
            "rule": group.rule,
            "priority": group.priority,
            "findingCount": group.count,
            "examples": examples,
            "metric": None
            if group.metric is None
            else {"key": group.metric.key, "value": group.metric.raw_value},
        },
        ensure_ascii=False,
    )
    request_body = json.dumps(
        {
            "model": _MODEL,
            "max_tokens": 700,
            "temperature": 0.1,
            "messages": [
                {"role": "system", "content": _SYSTEM_PROMPT},
                {"role": "user", "content": payload},
            ],
        },
    ).encode("utf-8")
    req = urllib.request.Request(
        _API_URL,
        data=request_body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            raw = response.read().decode("utf-8")
        content = json.loads(raw)["choices"][0]["message"]["content"].strip()
        if content.startswith("```"):
            lines = content.splitlines()[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            content = "\n".join(lines).strip()
        parsed = json.loads(content)
        if isinstance(parsed, dict):
            return {
                "summary": str(parsed.get("summary", "")).strip(),
                "impact": str(parsed.get("impact", "")).strip(),
                "recommendedFix": str(parsed.get("recommendedFix", "")).strip(),
            }
    except (KeyError, json.JSONDecodeError, urllib.error.URLError, TimeoutError, OSError) as exc:
        print(f"[quality-backlog] WARNING: DeepSeek summary unavailable for {group.fingerprint}: {exc}")
    return None
