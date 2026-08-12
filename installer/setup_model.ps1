<#
    setup_model.ps1 - provision the Qwen3 GGUF model for Wellness Companion.

    Invoked from wellness_setup.iss [Run], hidden and elevated, while the
    installer wizard shows a StatusMsg. Every step is appended to
    <InstallRoot>\setup.log so a failed model fetch can be diagnosed after the
    fact (the old NSIS hook only showed a MessageBox that vanished on OK).

    Resolution order (first hit wins), ported from build\installer.nsh:
      1. Already at the target        -> nothing to do.
      2. <InstallRoot>\model\         -> MOVE  (offline build embedded it).
      3. <SetupDir>\model\            -> COPY  (user placed it next to setup).
      4. Download from Hugging Face   -> only with -AllowDownload.

    The target is %LOCALAPPDATA%\wellness-companion\model\, NOT the Roaming
    userData dir: a 2.5 GB binary must never sit in a roaming profile. llm.ts
    checks this exact path (see the localAppData candidate in getModelPath).

    Exit codes: 0 = model ready, 1 = model absent (setup still completes; the
    app degrades to no-AI until the file appears).
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $InstallRoot,
    [string] $SetupDir = '',
    [switch] $AllowDownload
)

$ErrorActionPreference = 'Stop'

# --- Model identity: keep in lockstep with MODEL_FILE in windows\src\main\llm.ts ---
$ModelDirRel  = 'model\Qwen3-4B-Instruct-2507-GGUF'
$ModelFile    = 'Qwen3-4B-Instruct-2507-Q4_K_M.gguf'
$ModelRel     = Join-Path $ModelDirRel $ModelFile
$ModelUrl     = 'https://huggingface.co/lmstudio-community/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf'
$ModelBytes   = 2497280448
# SHA-256 of the upstream file, taken from Hugging Face's X-Linked-ETag (the Git
# LFS object hash) and confirmed against a local Get-FileHash. Length alone is a
# weak check: curl follows redirects, so a substituted file of identical size
# would previously have been accepted and then loaded straight into llama.cpp.
$ModelSha256  = '8cdb57cbb880d313736a9bc4e3d3d2485f145b5e19cf33783746e753e82641fc'

$DataRoot   = Join-Path $env:LOCALAPPDATA 'wellness-companion'
$TargetPath = Join-Path $DataRoot $ModelRel
$TargetDir  = Join-Path $DataRoot $ModelDirRel
$LogPath    = Join-Path $InstallRoot 'setup.log'

function Write-Log {
    param([string] $Message, [string] $Level = 'INFO')
    $line = '{0}  [{1}]  {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Write-Host $line
    try { Add-Content -LiteralPath $LogPath -Value $line -Encoding utf8 } catch { }
}

function Test-ModelComplete {
    param(
        [string] $Path,
        # Hashing 2.5 GB costs ~10s. Worth it after a download or a copy from an
        # untrusted location; skippable for the cheap "is it already here" probe.
        [switch] $VerifyHash
    )
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    $len = (Get-Item -LiteralPath $Path).Length
    if ($len -ne $ModelBytes) {
        Write-Log ("Size mismatch at {0}: {1} bytes, expected {2}." -f $Path, $len, $ModelBytes) 'WARN'
        return $false
    }
    if ($VerifyHash) {
        Write-Log 'Verifying SHA-256 (this takes a few seconds for a 2.5 GB file)...'
        $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLower()
        if ($actual -ne $ModelSha256) {
            Write-Log ("SHA-256 mismatch at {0}." -f $Path) 'ERROR'
            Write-Log ("  expected {0}" -f $ModelSha256) 'ERROR'
            Write-Log ("  actual   {0}" -f $actual) 'ERROR'
            return $false
        }
        Write-Log 'SHA-256 matches the published Hugging Face digest.'
    }
    return $true
}

# robocopy is used instead of Copy-Item for the multi-GB transfer: it retries
# on transient IO errors and can MOVE (/MOV) without a separate delete pass.
# Its exit codes 0-7 all mean success; 8+ is a real failure.
function Invoke-Robocopy {
    param([string] $From, [string] $To, [switch] $Move)
    $params = @($From, $To, $ModelFile, '/R:3', '/W:5', '/NP', '/NDL', '/NJH', '/NJS')
    if ($Move) { $params += '/MOV' }
    $out = & robocopy.exe @params 2>&1
    $code = $LASTEXITCODE
    $out | ForEach-Object { if ("$_".Trim()) { Write-Log "  robocopy: $_" } }
    if ($code -ge 8) { Write-Log "robocopy failed with exit code $code." 'ERROR'; return $false }
    return $true
}

Write-Log '=========================================================='
Write-Log "Wellness Companion model setup starting."
Write-Log "InstallRoot   : $InstallRoot"
Write-Log "SetupDir      : $(if ($SetupDir) { $SetupDir } else { '(not supplied)' })"
Write-Log "Target        : $TargetPath"
Write-Log "AllowDownload : $AllowDownload"

# --- Step 1: already in place? -------------------------------------------------
if (Test-ModelComplete $TargetPath) {
    Write-Log 'Model already present and complete. Nothing to do.'
    exit 0
}

if (Test-Path -LiteralPath $TargetPath) {
    Write-Log 'A partial/corrupt model exists at the target; removing it.' 'WARN'
    Remove-Item -LiteralPath $TargetPath -Force -ErrorAction SilentlyContinue
}

New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

# --- Step 2: embedded by the offline build ------------------------------------
$embedded = Join-Path $InstallRoot $ModelRel
if (Test-Path -LiteralPath $embedded) {
    Write-Log 'Found the model embedded in the install folder; moving it to the data root...'
    if (Invoke-Robocopy -From (Join-Path $InstallRoot $ModelDirRel) -To $TargetDir -Move) {
        if (Test-ModelComplete $TargetPath -VerifyHash) { Write-Log 'Model ready (moved from the installer).'; exit 0 }
    }
    Write-Log 'Move from the install folder did not produce a complete model.' 'WARN'
}

# --- Step 3: placed next to the setup exe -------------------------------------
if ($SetupDir) {
    $adjacent = Join-Path $SetupDir $ModelRel
    if (Test-Path -LiteralPath $adjacent) {
        Write-Log "Found a model next to the installer at $adjacent; copying..."
        if (Invoke-Robocopy -From (Join-Path $SetupDir $ModelDirRel) -To $TargetDir) {
            if (Test-ModelComplete $TargetPath -VerifyHash) { Write-Log 'Model ready (copied from the installer folder).'; exit 0 }
        }
        Write-Log 'Copy from the installer folder did not produce a complete model.' 'WARN'
    }
}

# --- Step 4: download ----------------------------------------------------------
if (-not $AllowDownload) {
    Write-Log 'No local model found and downloading was declined. Skipping.' 'WARN'
    Write-Log "Place the model folder at $DataRoot\model\ later, or re-run this installer."
    exit 1
}

Write-Log 'Downloading the model (~2.5 GB) from Hugging Face. This can take several minutes...'

# curl.exe ships with Windows 10 1803+ and resumes/retries far better than
# Invoke-WebRequest, which buffers the whole body in memory for a 2.5 GB file.
$curl = Join-Path $env:SystemRoot 'System32\curl.exe'
if (-not (Test-Path -LiteralPath $curl)) {
    Write-Log 'curl.exe not found in System32; cannot download.' 'ERROR'
    exit 1
}

& $curl -L --fail --retry 3 --retry-delay 5 --no-progress-meter -o $TargetPath $ModelUrl
$curlCode = $LASTEXITCODE

if ($curlCode -ne 0) {
    Write-Log "Download failed (curl exit $curlCode) - no internet connection?" 'ERROR'
    if (Test-Path -LiteralPath $TargetPath) { Remove-Item -LiteralPath $TargetPath -Force -ErrorAction SilentlyContinue }
    Write-Log "Setup will finish; the app runs without AI until the model is placed at $DataRoot\model\."
    exit 1
}

Write-Log 'Download finished; verifying...'
if (Test-ModelComplete $TargetPath -VerifyHash) {
    Write-Log 'Model ready (downloaded and verified).'
    exit 0
}

Write-Log 'The downloaded file is incomplete or corrupt; deleting it.' 'ERROR'
Remove-Item -LiteralPath $TargetPath -Force -ErrorAction SilentlyContinue
Write-Log 'Re-run the installer with a stable connection to retry.'
exit 1
