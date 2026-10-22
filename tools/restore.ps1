# Re-enable packages and restore the stock launcher
# Usage: .\tools\restore.ps1 .\examples\xiaomi-p1-croods\debloat.txt
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$ListFile,

    [Parameter(Mandatory = $false)]
    [string]$StockLauncher = "com.google.android.tvlauncher/.MainActivity"
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$argsList = @("--restore", $ListFile)
if ($StockLauncher) {
    $argsList += @("--stock", $StockLauncher)
}
& (Join-Path $ScriptDir "tv.ps1") @argsList
