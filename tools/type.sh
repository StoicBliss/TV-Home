#!/usr/bin/env bash
# Type text into focused field on the TV (cross-platform via tools/tv.py)
# Usage:
#   tools/type.sh "hello world"
#   tools/type.sh --enter "hello world"
#   tools/type.sh                      # prompt hidden (for passwords)
set -euo pipefail
HERE="$(dirname "$0")"
exec "$HERE/tv.sh" --type "$@"
