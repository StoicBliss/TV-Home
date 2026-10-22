#!/usr/bin/env bash
# Disable every package in a debloat list for user 0 (packages are disabled, never uninstalled)
# Usage: tools/disable.sh examples/tcl-google-tv-g07/debloat.txt
set -euo pipefail
HERE="$(dirname "$0")"
exec "$HERE/tv.sh" --disable "$@"
