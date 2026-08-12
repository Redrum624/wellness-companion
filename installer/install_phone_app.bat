@echo off
setlocal enabledelayedexpansion
:: ===========================================================================
::  install_phone_app.bat - sideload the Wellness Companion Android app.
::
::  Installed to {app} by wellness_setup.iss and reachable three ways:
::    - the "install the app on my phone now" task during setup,
::    - the "Install the phone app" Start-Menu shortcut, later,
::    - by running it directly from the install folder.
::
::  ADB resolution order:
::    1. The Android SDK's platform-tools, if the user already has the SDK.
::    2. A previously downloaded copy under the per-user data root.
::    3. Fresh download of Google's standalone platform-tools zip.
::
::  NOTE: everything writable goes under %LOCALAPPDATA%\wellness-companion,
::  never next to this script - {app} is C:\Program Files\... and is read-only
::  for the normal (non-elevated) user who runs the Start-Menu shortcut.
:: ===========================================================================

set "PACKAGE=com.wellnesscompanion.app"
set "ACTIVITY=.MainActivity"
set "APK=%~dp0android\WellnessCompanion.apk"

set "DATA_ROOT=%LOCALAPPDATA%\wellness-companion"
set "TOOLS_DIR=%DATA_ROOT%\tools\platform-tools"

echo ============================================================
echo   Wellness Companion - Phone Installer
echo ============================================================
echo.
echo   Before continuing, on your phone:
echo     1. Connect it to this PC with a USB cable
echo     2. Enable USB Debugging
echo        (Settings ^> Developer Options ^> USB Debugging)
echo.

if not exist "%APK%" (
    echo [ERROR] The Android app package was not found at:
    echo         %APK%
    echo         Reinstall Wellness Companion to restore it.
    echo.
    pause
    exit /b 1
)

:: --------------------------------------------------
:: Step 1: locate ADB
:: --------------------------------------------------
echo [1/4] Locating ADB...
set "ADB="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%TOOLS_DIR%\adb.exe" set "ADB=%TOOLS_DIR%\adb.exe"

if not defined ADB (
    echo   ADB not found - downloading Google's platform-tools ^(~5 MB^)...
    if not exist "%DATA_ROOT%\tools" mkdir "%DATA_ROOT%\tools" 2>nul
    set "ZIP_FILE=%DATA_ROOT%\tools\platform-tools.zip"
    curl -L --fail --retry 3 --no-progress-meter -o "!ZIP_FILE!" "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    if errorlevel 1 (
        echo   [ERROR] Could not download platform-tools ^(no internet connection?^).
        echo           Install the Android SDK platform-tools manually and retry.
        echo.
        pause
        exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '!ZIP_FILE!' -DestinationPath '%DATA_ROOT%\tools' -Force"
    if errorlevel 1 (
        echo   [ERROR] Could not extract platform-tools.
        echo.
        pause
        exit /b 1
    )
    del /q "!ZIP_FILE!" 2>nul
    set "ADB=%TOOLS_DIR%\adb.exe"
)

if not exist "%ADB%" (
    echo   [ERROR] adb.exe still not available at %ADB%.
    echo.
    pause
    exit /b 1
)
echo   [OK] ADB: %ADB%
echo.

:: --------------------------------------------------
:: Step 2: wait for the phone
:: --------------------------------------------------
echo [2/4] Waiting for your phone...
echo        (tap "Allow USB debugging" on the phone if a prompt appears)
"%ADB%" start-server >nul 2>&1
"%ADB%" wait-for-device

"%ADB%" devices | findstr /r "device$" >nul
if errorlevel 1 (
    echo.
    echo   [ERROR] No authorised device found. Check that:
    echo     - the USB cable is connected ^(and is a data cable, not charge-only^)
    echo     - USB Debugging is ON
    echo     - you tapped "Allow" on the phone
    echo.
    pause
    exit /b 1
)
echo   [OK] Phone connected.
echo.

:: --------------------------------------------------
:: Step 3: install
:: --------------------------------------------------
:: Force-stop first: reinstalling over a running process makes the first
:: launch after `install -r` crash on some devices.
echo [3/4] Installing Wellness Companion...
"%ADB%" shell am force-stop %PACKAGE% >nul 2>&1
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo   [ERROR] Install failed - see the ADB output above.
    echo           A signature mismatch means an older build is installed;
    echo           uninstall it on the phone first, then retry.
    echo.
    pause
    exit /b 1
)
echo   [OK] Installed.
echo.

:: --------------------------------------------------
:: Step 4: grant runtime permissions + launch
:: --------------------------------------------------
:: Granting up front means the reminder notifications work without the user
:: having to catch the permission dialog on first run.
echo [4/4] Granting notification permission and launching...
"%ADB%" shell pm grant %PACKAGE% android.permission.POST_NOTIFICATIONS >nul 2>&1
"%ADB%" shell am start -n %PACKAGE%/%ACTIVITY% >nul 2>&1

echo.
echo ============================================================
echo   Done - "Daily Wellness" is now on your phone.
echo.
echo   To sync: open the desktop app, then pull to sync on the
echo   phone while both are on the same Wi-Fi network.
echo ============================================================
echo.
pause
exit /b 0
