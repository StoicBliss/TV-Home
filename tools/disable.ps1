# Disable every package in a debloat list for user 0 (packages are disabled, never uninstalled)
# Usage: .\tools\disable.ps1 .\examples\xiaomi-p1-croods\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$ListFile
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
& (Join-Path $ScriptDir "tv.ps1") --disable $ListFile
