"""Collect logs from Docker containers running on the local host.

Returns empty strings for any container that cannot be reached — never raises.
"""
from __future__ import annotations

import subprocess
from typing import Dict

# ---------------------------------------------------------------------------
# Container mapping: logical service name → Docker container name
# ---------------------------------------------------------------------------

CONTAINERS: Dict[str, str] = {
    "monitor": "funding-monitor",
    "engine": "funding-engine",
    "telegram-bot": "funding-telegram-bot",
}

_DOCKER_TIMEOUT_SECONDS = 60


def _fetch_container_logs(container: str, since_hours: int) -> str:
    """Run `docker logs` and return combined stdout+stderr as a string.

    Returns empty string on any failure.
    """
    cmd = [
        "docker", "logs", container,
        "--since", f"{since_hours}h",
        "--timestamps",
    ]
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=_DOCKER_TIMEOUT_SECONDS,
        )
        # docker logs writes to stderr by default; merge both streams
        return (result.stdout or "") + (result.stderr or "")
    except subprocess.TimeoutExpired:
        print(f"[collector] WARNING: docker logs timed out for container '{container}'")
        return ""
    except FileNotFoundError:
        print("[collector] WARNING: docker CLI not found — cannot collect logs")
        return ""
    except Exception as exc:  # noqa: BLE001
        print(f"[collector] WARNING: unexpected error fetching logs for '{container}': {exc}")
        return ""


def collect_logs(since_hours: int = 24) -> Dict[str, str]:
    """Return a mapping of service name → raw log text for all containers.

    Failures are logged and result in an empty string for that service.
    """
    logs: Dict[str, str] = {}
    for service, container in CONTAINERS.items():
        print(f"[collector] Fetching logs for service='{service}' container='{container}' since={since_hours}h")
        logs[service] = _fetch_container_logs(container, since_hours)
        line_count = logs[service].count("\n")
        print(f"[collector] Got {line_count} lines for service='{service}'")
    return logs
