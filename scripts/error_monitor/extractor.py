"""Extract structured error blocks from raw Docker log output.

All regexes are compiled at module level.
"""
from __future__ import annotations

import re

try:
    from .models import ErrorBlock
except ImportError:
    from models import ErrorBlock  # type: ignore[no-redef]

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

MAX_BLOCKS = 10
MAX_PAYLOAD_CHARS = 50_000
CONTEXT_BEFORE = 20
CONTEXT_AFTER = 30

# ---------------------------------------------------------------------------
# Compiled patterns (module-level)
# ---------------------------------------------------------------------------

_RE_ERROR = re.compile(r"\bERROR\b")
_RE_WARN = re.compile(r"\bWARN\b")
_RE_EXCEPTION = re.compile(r"\bException\b|Caused by:|^\s+at com\.", re.MULTILINE)
_RE_NETWORK = re.compile(
    r"Connection refused|Connection timeout|Read timeout"
    r"|WebSocket.*(?:close|fail|error)"
    r"|SocketException|TimeoutException",
    re.IGNORECASE,
)


def _is_error_line(line: str) -> bool:
    """Return True if the line contains any error-relevant pattern."""
    return bool(
        _RE_ERROR.search(line)
        or _RE_EXCEPTION.search(line)
        or _RE_NETWORK.search(line)
    )


def _is_warn_line(line: str) -> bool:
    return bool(_RE_WARN.search(line))


def _gather_error_indices(lines: list[str]) -> set[int]:
    """Return the set of line indices that are directly error-relevant."""
    indices: set[int] = set()
    for i, line in enumerate(lines):
        if _is_error_line(line):
            indices.add(i)
    return indices


def _expand_with_context(
    error_indices: set[int], total: int
) -> list[list[int]]:
    """Expand each error index with surrounding context and group into consecutive runs."""
    if not error_indices:
        return []

    expanded: set[int] = set()
    for idx in error_indices:
        start = max(0, idx - CONTEXT_BEFORE)
        end = min(total, idx + CONTEXT_AFTER + 1)
        expanded.update(range(start, end))

    sorted_indices = sorted(expanded)

    # Group consecutive indices into contiguous blocks
    groups: list[list[int]] = []
    current: list[int] = [sorted_indices[0]]
    for i in sorted_indices[1:]:
        if i == current[-1] + 1:
            current.append(i)
        else:
            groups.append(current)
            current = [i]
    groups.append(current)
    return groups


def _block_key(lines: list[str], indices: list[int]) -> str:
    """Produce a deduplication key from the error lines in a block."""
    error_lines = [lines[i] for i in indices if _is_error_line(lines[i])]
    # Normalize timestamps (leading datetime prefix) so repeated blocks match
    normalized = []
    for line in error_lines:
        # Strip leading ISO timestamp if present: 2024-01-02T03:04:05.123Z or 2024-01-02 03:04:05
        cleaned = re.sub(
            r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}[.,\d]*Z?\s*", "", line
        )
        normalized.append(cleaned.strip())
    return "\n".join(normalized)


def extract_error_blocks(service: str, raw_logs: str) -> list[ErrorBlock]:
    """Parse *raw_logs* and return up to MAX_BLOCKS deduplicated ErrorBlock instances."""
    if not raw_logs:
        return []

    lines = raw_logs.splitlines()
    error_indices = _gather_error_indices(lines)
    if not error_indices:
        return []

    groups = _expand_with_context(error_indices, len(lines))

    # Deduplicate: same error content increments occurrence_count
    seen: dict[str, int] = {}           # key → index in result list
    results: list[ErrorBlock] = []
    total_chars = 0

    for group_indices in groups:
        if len(results) >= MAX_BLOCKS:
            break
        if total_chars >= MAX_PAYLOAD_CHARS:
            break

        block_lines = tuple(lines[i] for i in group_indices)
        key = _block_key(lines, group_indices)

        if key in seen:
            existing_idx = seen[key]
            old = results[existing_idx]
            results[existing_idx] = ErrorBlock(
                service=old.service,
                lines=old.lines,
                occurrence_count=old.occurrence_count + 1,
            )
        else:
            block_text = "\n".join(block_lines)
            if total_chars + len(block_text) > MAX_PAYLOAD_CHARS:
                break
            seen[key] = len(results)
            results.append(ErrorBlock(service=service, lines=block_lines))
            total_chars += len(block_text)

    return results
