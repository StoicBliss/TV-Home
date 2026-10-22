#!/usr/bin/env bash
# Undo everything: re-enable packages and restore the stock launcher
# Usage: tools/restore.sh <debloat list>
#   STOCK_LAUNCHER=com.google.android.tvlauncher/.MainActivity tools/restore.sh list.txt
set -euo pipefail
HERE="$(dirname "$0")"
STOCK_ARGS=()
if [ -n "${STOCK_LAUNCHER:-}" ]; then
  STOCK_ARGS=(--stock "$STOCK_LAUNCHER")
fi
exec "$HERE/tv.sh" --restore "$@" "${STOCK_ARGS[@]}"
