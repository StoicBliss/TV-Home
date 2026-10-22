#!/usr/bin/env bash
# TV Control Helper (Bash)
# Usage:
#   tools/tv.sh --find
#   tools/tv.sh --probe
#   tools/tv.sh --disable examples/xiaomi-p1-croods/debloat.txt
#   tools/tv.sh --restore examples/xiaomi-p1-croods/debloat.txt
#   tools/tv.sh --shot screen.png
#   tools/tv.sh --type "search text" --enter
set -euo pipefail
cd "$(dirname "$0")/.."
PY="tools/venv/bin/python"
if [ ! -x "$PY" ]; then
    PY="python3"
fi
exec "$PY" -I tools/tv.py "$@"
