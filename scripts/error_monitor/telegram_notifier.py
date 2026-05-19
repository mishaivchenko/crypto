"""Send daily error monitor report to a Telegram chat via Bot API (stdlib only).

Requires env vars:
  TELEGRAM_BOT_TOKEN       — bot token
  TELEGRAM_NOTIFICATION_CHAT_ID — target chat/channel ID
"""
from __future__ import annotations

import html
import json
import os
import urllib.error
import urllib.parse
import urllib.request

_TIMEOUT = 15
_MAX_MESSAGE_LENGTH = 4000  # Telegram hard limit is 4096; leave a small margin


def _send(token: str, chat_id: str, text: str) -> bool:
    """Send a single message. Returns True on success."""
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    payload = json.dumps({
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "HTML",
        "disable_web_page_preview": True,
    }).encode()
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            result = json.loads(resp.read().decode())
            return result.get("ok", False)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        print(f"[telegram] WARNING: HTTP {e.code}: {body[:200]}")
        return False
    except Exception as exc:
        print(f"[telegram] WARNING: send failed: {exc}")
        return False


def _chunk(text: str) -> list[str]:
    """Split text into ≤_MAX_MESSAGE_LENGTH chunks, breaking on newlines.

    Lines longer than the limit are hard-truncated to avoid exceeding Telegram's 4096-char cap.
    """
    if len(text) <= _MAX_MESSAGE_LENGTH:
        return [text]
    chunks, current = [], []
    current_len = 0
    for line in text.splitlines(keepends=True):
        if len(line) > _MAX_MESSAGE_LENGTH:
            line = line[:_MAX_MESSAGE_LENGTH - 4] + "...\n"
        if current_len + len(line) > _MAX_MESSAGE_LENGTH and current:
            chunks.append("".join(current))
            current, current_len = [], 0
        current.append(line)
        current_len += len(line)
    if current:
        chunks.append("".join(current))
    return chunks


def send_report(
    stats: dict,
    issues_created: list[dict],   # list of {"service", "title", "number", "url", "severity"}
    issues_updated: list[dict],   # list of {"service", "number", "url", "severity"}
    skipped: list[dict],          # list of {"service", "reason"}
    repo: str,
    create_errors: list[dict] | None = None,  # list of {"service", "severity"}
) -> None:
    """Build and send the daily error monitor report. Silently skips if not configured."""
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
    chat_id = os.environ.get("TELEGRAM_NOTIFICATION_CHAT_ID", "").strip()

    if not token or not chat_id:
        print("[telegram] TELEGRAM_BOT_TOKEN or TELEGRAM_NOTIFICATION_CHAT_ID not set — skipping report")
        return

    lines = [f"<b>🔍 Daily Error Monitor — {repo}</b>\n"]

    # Summary block
    total_issues = stats["issues_created"] + stats["issues_updated"]
    if total_issues == 0 and stats["error_blocks_found"] == 0:
        lines.append("✅ <b>All clean.</b> No errors found in Docker logs.")
    elif total_issues == 0:
        lines.append("✅ <b>No actionable issues.</b> Errors found but filtered by quality gate.")
    else:
        lines.append(f"⚠️ <b>{total_issues} issue(s) found</b>")

    lines.append(
        f"\n📊 <b>Stats:</b> "
        f"{stats['logs_scanned']} services scanned · "
        f"{stats['error_blocks_found']} error block(s) · "
        f"{stats['api_calls']} AI call(s)"
    )

    # New issues
    if issues_created:
        lines.append("\n🆕 <b>New issues created:</b>")
        for item in issues_created:
            sev_emoji = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡"}.get(item["severity"], "⚪")
            lines.append(
                f"{sev_emoji} <a href=\"{item['url']}\">#{item['number']}</a> "
                f"[{html.escape(item['service'])}] {html.escape(item['title'])}"
            )

    # Updated issues
    if issues_updated:
        lines.append("\n🔁 <b>Recurring issues updated:</b>")
        for item in issues_updated:
            sev_emoji = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡"}.get(item["severity"], "⚪")
            lines.append(
                f"{sev_emoji} <a href=\"{item['url']}\">#{item['number']}</a> "
                f"[{html.escape(item['service'])}] new occurrence added"
            )

    # Skipped (only if there were blocks)
    if skipped:
        lines.append("\n🔕 <b>Filtered out (quality gate):</b>")
        for item in skipped:
            lines.append(f"  • [{html.escape(item['service'])}] {html.escape(item['reason'])}")

    # Issue creation failures
    if create_errors:
        lines.append("\n⛔ <b>Failed to create issue:</b>")
        for item in create_errors:
            sev_emoji = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡"}.get(item["severity"], "⚪")
            lines.append(f"  {sev_emoji} [{html.escape(item['service'])}] GitHub API error — check runner logs")

    # Errors
    if stats["api_errors"] > 0 or stats["parse_errors"] > 0:
        lines.append(
            f"\n❌ <b>Errors:</b> "
            f"{stats['api_errors']} API error(s), "
            f"{stats['parse_errors']} parse error(s)"
        )

    text = "\n".join(lines)
    for chunk in _chunk(text):
        ok = _send(token, chat_id, chunk)
        if not ok:
            print("[telegram] WARNING: failed to send report chunk")
            return
    print("[telegram] Report sent successfully")
