@echo off
setlocal enabledelayedexpansion
:: ===========================================================================
::  build_installer.bat - build the Wellness Companion Windows installer.
::
::  Steps:
::    0. Ensure vc_redist.x64.exe is present (download from aka.ms if missing).
::    1. Build the Electron app             (electron-vite build).
::    2. Package the NSIS installer          (electron-builder --win).
::         - offline: embed model\ + vc_redist.x64.exe via extraResources.
::         - lean:    nothing embedded; the model is downloaded at install time.
::    3. Copy dist\WellnessCompanion-Setup.exe to the project root.
::
::  Usage (normally invoked via ..\build_setup.bat):
::    build_installer.bat            -> lean  (model downloaded at install)
::    build_installer.bat lean       -> lean
::    build_installer.bat offline    -> offline (model + redist embedded, ~2.8 GB)
:: ===========================================================================

set "INSTALLER_DIR=%~dp0"
:: ROOT = project root (parent of windows\)
pushd "%INSTALLER_DIR%.."
set "ROOT=%CD%\"
popd

set "VCREDIST=%ROOT%vc_redist.x64.exe"
set "MODEL_GGUF=%ROOT%model\Qwen3-4B-Instruct-2507-GGUF\Qwen3-4B-Instruct-2507-Q4_K_M.gguf"
set "OUTPUT_EXE=%INSTALLER_DIR%dist\WellnessCompanion-Setup.exe"

:: --- Build mode (lean default) ---
set "MODE=lean"
if /I "%~1"=="offline"   set "MODE=offline"
if /I "%~1"=="--offline" set "MODE=offline"

:: Offline embeds the model + redist via a dedicated config that extends the
:: package.json "build" field with extraResources (see electron-builder.offline.js).
set "EB_EXTRA="
if "%MODE%"=="offline" set "EB_EXTRA=--config electron-builder.offline.js"

echo.
echo ============================================================
echo   Wellness Companion Installer Build
if "%MODE%"=="offline" (
    echo   Mode: OFFLINE - model + VC++ redist embedded ^(~2.8 GB^)
) else (
    echo   Mode: LEAN - model downloaded on the target PC at install
)
echo ============================================================
echo.

:: Offline requires the model present to embed it.
if "%MODE%"=="offline" if not exist "%MODEL_GGUF%" (
    echo [ERROR] Offline build requested but the model is missing:
    echo         %MODEL_GGUF%
    echo         Place the model\ folder at the project root and retry.
    exit /b 1
)

:: --------------------------------------------------
:: Step 0: Visual C++ Redistributable
:: --------------------------------------------------
if not exist "%VCREDIST%" (
    echo [0/3] Downloading Visual C++ Redistributable...
    powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'https://aka.ms/vs/17/release/vc_redist.x64.exe' -OutFile '%VCREDIST%' } catch { exit 1 }"
    if not exist "%VCREDIST%" (
        echo   [WARN] Could not download vc_redist.x64.exe.
        if "%MODE%"=="offline" (
            echo   [ERROR] Offline build needs vc_redist.x64.exe to embed it. Aborting.
            exit /b 1
        )
        echo   Lean build continues; ship vc_redist.x64.exe next to the exe,
        echo   or the app prompts the user to install it on first run.
    ) else (
        echo   [OK] vc_redist.x64.exe downloaded.
    )
) else (
    echo [0/3] vc_redist.x64.exe already present.
)

:: --------------------------------------------------
:: Step 1: Build the Electron app
:: --------------------------------------------------
echo.
echo [1/3] Building Electron app...
cd /d "%INSTALLER_DIR%"
call npx electron-vite build
if %ERRORLEVEL% neq 0 (
    echo [ERROR] electron-vite build failed.
    exit /b 1
)

:: --------------------------------------------------
:: Step 2: Package the NSIS installer
:: --------------------------------------------------
echo.
echo [2/3] Packaging installer ^(%MODE%^)...
call npx electron-builder --win %EB_EXTRA%
if %ERRORLEVEL% neq 0 (
    echo [ERROR] electron-builder packaging failed.
    exit /b 1
)
if not exist "%OUTPUT_EXE%" (
    echo [ERROR] Installer not produced at %OUTPUT_EXE%.
    exit /b 1
)

:: --------------------------------------------------
:: Step 3: Copy the installer to the project root
:: --------------------------------------------------
echo.
echo [3/3] Placing installer at project root...
copy /y "%OUTPUT_EXE%" "%ROOT%WellnessCompanion-Setup.exe" >nul

echo.
echo ============================================================
echo   BUILD COMPLETE ^(%MODE%^)
echo   Installer: %ROOT%WellnessCompanion-Setup.exe
echo.
if "%MODE%"=="offline" (
    echo   To distribute: ship just this single file -
    echo     WellnessCompanion-Setup.exe   ^(model + redist embedded^)
) else (
    echo   To distribute the LEAN installer, ship together:
    echo     WellnessCompanion-Setup.exe
    echo     vc_redist.x64.exe             ^(optional; app prompts otherwise^)
    echo   The AI model is downloaded automatically during install
    echo   ^(internet required; ~2.5 GB^).
)
echo ============================================================
echo.
exit /b 0
