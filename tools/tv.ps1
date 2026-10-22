# TV Control Helper (PowerShell)
# Usage:
#   .\tools\tv.ps1 --find
#   .\tools\tv.ps1 --probe
#   .\tools\tv.ps1 --backup
#   .\tools\tv.ps1 --disable examples\xiaomi-p1-croods\debloat.txt
#   .\tools\tv.ps1 --restore examples\xiaomi-p1-croods\debloat.txt
#   .\tools\tv.ps1 --shot screen.png
#   .\tools\tv.ps1 --type "search text" --enter
#   .\tools\tv.ps1 "pm list packages"
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$CommandArgs
)

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

# Locate Python in tools/venv or system
$py = Join-Path $RepoRoot "tools\venv\Scripts\python.exe"
if (-not (Test-Path $py)) {
    $py = "python"
}
$tvPy = Join-Path $RepoRoot "tools\tv.py"

if ($CommandArgs -and $CommandArgs.Count -gt 0) {
    & $py -I $tvPy @CommandArgs
} else {
    & $py -I $tvPy --probe
}
