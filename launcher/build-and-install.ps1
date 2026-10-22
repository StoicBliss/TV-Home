# Build and install the launcher APK on Windows
# Usage:
#   .\launcher\build-and-install.ps1            # build + install
#   .\launcher\build-and-install.ps1 -BuildOnly  # build only
param(
    [switch]$BuildOnly,
    [string]$StockLauncher = "com.google.android.tvlauncher"
)

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$RepoRoot = Split-Path -Parent $ScriptDir

# Discover Java
if (-not $env:JAVA_HOME) {
    if (Test-Path "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot") {
        $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
    } elseif (Test-Path "$env:ProgramFiles\Java\jdk-17*") {
        $found = Get-Item "$env:ProgramFiles\Java\jdk-17*" | Select-Object -First 1
        $env:JAVA_HOME = $found.FullName
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\javac.exe")) {
    Write-Error "JDK 17 not found. Please set `$env:JAVA_HOME to your JDK 17 path."
}
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
Write-Host "Using Java: $env:JAVA_HOME"

# Discover Android SDK
$Sdk = $env:ANDROID_HOME
if (-not $Sdk -or -not (Test-Path $Sdk)) {
    if (Test-Path "$env:LOCALAPPDATA\Android\Sdk") {
        $Sdk = "$env:LOCALAPPDATA\Android\Sdk"
    }
}
if (-not $Sdk -or -not (Test-Path $Sdk)) {
    Write-Error "Android SDK not found. Please install Android SDK or set `$env:ANDROID_HOME."
}

# Find latest build-tools and platform jar
$btDir = Get-ChildItem "$Sdk\build-tools" | Sort-Object Name -Descending | Select-Object -First 1
if (-not $btDir) {
    Write-Error "No build-tools found in $Sdk\build-tools"
}
$BT = $btDir.FullName

$platDir = Get-ChildItem "$Sdk\platforms" | Sort-Object Name -Descending | Select-Object -First 1
if (-not $platDir -or -not (Test-Path "$($platDir.FullName)\android.jar")) {
    Write-Error "No android.jar found in $Sdk\platforms"
}
$JAR = "$($platDir.FullName)\android.jar"

Write-Host "Using Build-Tools: $BT"
Write-Host "Using Platform: $($platDir.Name)"

$aapt2 = Join-Path $BT "aapt2.exe"
$d8 = Join-Path $BT "d8.bat"
$zipalign = Join-Path $BT "zipalign.exe"
$apksigner = Join-Path $BT "apksigner.bat"
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"

$B = Join-Path $ScriptDir "build"
if (Test-Path $B) {
    Remove-Item -Recurse -Force $B
}
New-Item -ItemType Directory -Path "$B\gen" -Force | Out-Null
New-Item -ItemType Directory -Path "$B\classes" -Force | Out-Null
New-Item -ItemType Directory -Path "$B\dex" -Force | Out-Null

Push-Location $ScriptDir
try {
    Write-Host "[1/6] Compiling resources with aapt2..."
    & $aapt2 compile --dir res -o "$B\res.zip"
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

    Write-Host "[2/6] Linking APK package..."
    & $aapt2 link -o "$B\base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B\gen" `
        --min-sdk-version 28 --target-sdk-version 29 "$B\res.zip"
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

    Write-Host "[3/6] Compiling Java classes..."
    $javaFiles = @(Get-ChildItem -Path "src", "$B\gen" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName })
    & $javac -nowarn -Xlint:-options -encoding UTF-8 --release 11 -classpath "$JAR" -d "$B\classes" @javaFiles
    if ($LASTEXITCODE -ne 0) { throw "javac failed" }

    Write-Host "[4/6] Converting classes to Dalvik executable (dex)..."
    $classFiles = @(Get-ChildItem -Path "$B\classes" -Filter "*.class" -Recurse | ForEach-Object { $_.FullName })
    & cmd.exe /c "$d8" --min-api 28 --lib "$JAR" --output "$B\dex" @classFiles
    if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

    Copy-Item "$B\base.apk" "$B\unsigned.apk"
    Write-Host "[5/6] Adding classes.dex and assets to APK and zip aligning..."
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $mode = [System.IO.Compression.ZipArchiveMode]::Update
    $zipArchive = [System.IO.Compression.ZipFile]::Open("$B\unsigned.apk", $mode)
    
    # Add classes.dex
    $dexFile = "$B\dex\classes.dex"
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $dexFile, "classes.dex") | Out-Null
    
    # Add assets with normalized forward slashes
    if (Test-Path "assets") {
        Get-ChildItem -Path "assets" -Recurse -File | ForEach-Object {
            $relPath = $_.FullName.Substring((Get-Item "assets").FullName.Length + 1).Replace('\', '/')
            $entryName = "assets/$relPath"
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $_.FullName, $entryName) | Out-Null
        }
    }
    $zipArchive.Dispose()

    & $zipalign -f 4 "$B\unsigned.apk" "$B\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

    $keystore = Join-Path $ScriptDir "debug.keystore"
    if (-not (Test-Path $keystore)) {
        Write-Host "Generating debug keystore..."
        & $keytool -genkeypair -keystore $keystore -storepass android -keypass android `
            -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tvhome"
    }

    Write-Host "[6/6] Signing APK..."
    & cmd.exe /c "$apksigner" sign --ks $keystore --ks-pass pass:android --out "$B\tvhome.apk" "$B\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

    $apkSize = (Get-Item "$B\tvhome.apk").Length
    Write-Host "Successfully built $B\tvhome.apk ($apkSize bytes)" -ForegroundColor Green

    if ($BuildOnly) {
        return
    }

    Write-Host "Installing to TV over ADB..."
    $py = Join-Path $RepoRoot "tools\venv\Scripts\python.exe"
    $tvPy = Join-Path $RepoRoot "tools\tv.py"

    & $py -I $tvPy --push-b64 "$B\tvhome.apk" /data/local/tmp/tvhome.apk

    $installCmds = @(
        "pm install -r /data/local/tmp/tvhome.apk",
        "rm -f /data/local/tmp/tvhome.apk",
        "pm grant com.shafee.tvhome android.permission.ACCESS_FINE_LOCATION",
        "pm grant com.shafee.tvhome android.permission.ACCESS_COARSE_LOCATION",
        "cmd package set-home-activity --user 0 com.shafee.tvhome/.MainActivity",
        "pm disable-user --user 0 $StockLauncher",
        "pm disable-user --user 0 com.google.android.tungsten.setupwraith",
        "pm disable-user --user 0 com.xiaomi.mitv.manualhelp",
        "cmd package set-home-activity --user 0 com.shafee.tvhome/.MainActivity",
        "am force-stop com.android.tv.settings",
        "input keyevent KEYCODE_HOME"
    )
    $batchInput = ($installCmds -join "`n") + "`n"
    $batchInput | & $py -I $tvPy --batch

    Write-Host "Installed! Press Home on the TV remote." -ForegroundColor Green
} finally {
    Pop-Location
}
