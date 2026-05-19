import os as _os
import sys as _sys

_PKG_DIR = _os.path.dirname(_os.path.abspath(__file__))
_EM_DIR = _os.path.join(_PKG_DIR, "..", "error_monitor")

# pr_review must precede error_monitor so bare "from models import" resolves
# to pr_review/models.py, not error_monitor/models.py
for _p in (_EM_DIR, _PKG_DIR):
    if _p not in _sys.path:
        _sys.path.insert(0, _p)
