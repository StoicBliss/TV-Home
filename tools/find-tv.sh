#!/usr/bin/env bash
# Find Android TVs on your Wi-Fi (cross-platform discovery via tools/tv.py)
# Usage: tools/find-tv.sh
set -euo pipefail
HERE="$(dirname "$0")"
exec "$HERE/tv.sh" --find "$@"
