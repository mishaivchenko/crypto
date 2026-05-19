"""Re-export sanitize() from the error_monitor module — single source of truth."""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "error_monitor"))

from sanitizer import sanitize  # noqa: F401 — re-export

__all__ = ["sanitize"]
