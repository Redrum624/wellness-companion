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

    rem Integrity check. Unlike setup_model.ps1's model download (a fixed,
    rem content-addressed file that a single baked-in SHA-256 can pin forever),
    rem this URL is Google's rolling "latest" platform-tools build: its bytes
    rem (and therefore its hash) change every time Google ships a new release,
    rem so a hardcoded pin here would go stale and start failing legitimate
    rem installs. Instead: compute the real hash, verify it if the caller
    rem opted in via WC_PLATFORM_TOOLS_SHA256, and otherwise show it with a
    rem clear warning so a security-conscious user/operator can pin it.
    rem
    rem ZIP_SHA256 is cleared first: it would otherwise still hold whatever
    rem value the CALLER's environment happened to set before this script ran.
    rem If Get-FileHash emits nothing (PowerShell blocked/missing), the for /f
    rem body never executes -- and without this clear, a pre-set ZIP_SHA256
    rem that happens to match WC_PLATFORM_TOOLS_SHA256 would pass the check
    rem below without any hash actually having been computed.
    set "ZIP_SHA256="
    for /f "usebackq delims=" %%H in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-FileHash -LiteralPath '!ZIP_FILE!' -Algorithm SHA256).Hash.ToLower()"`) do set "ZIP_SHA256=%%H"
    if defined WC_PLATFORM_TOOLS_SHA256 (
        if /i not "!ZIP_SHA256!"=="!WC_PLATFORM_TOOLS_SHA256!" (
            echo   [ERROR] platform-tools.zip SHA-256 does not match WC_PLATFORM_TOOLS_SHA256.
            echo           expected: !WC_PLATFORM_TOOLS_SHA256!
            echo           actual:   !ZIP_SHA256!
            echo           This may mean the download was tampered with, or that Google
            echo           shipped a new platform-tools release ^(update your pinned
            echo           hash if so^). Aborting without extracting.
            del /q "!ZIP_FILE!" 2>nul
            pause
            exit /b 1
        )
        echo   [OK] platform-tools.zip SHA-256 verified against WC_PLATFORM_TOOLS_SHA256.
    ) else (
        echo   [WARN] Integrity of platform-tools.zip was not verified - no expected
        echo          hash was configured. SHA-256: !ZIP_SHA256!
        echo          This "latest" URL changes on every platform-tools release, so it
        echo          cannot ship a single hardcoded pin. To verify it yourself, set the
        echo          WC_PLATFORM_TOOLS_SHA256 environment variable to the expected
        echo          hash before running this installer.
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
:: Detect an existing installation so the user knows an update keeps their data.
"%ADB%" shell pm list packages %PACKAGE% 2>nul | findstr /c:"package:%PACKAGE%" >nul
if not errorlevel 1 (
    echo   [OK] Wellness Companion is already on this phone - updating in place.
    echo        Your tracked data stays on the phone.
)

:: --- Version-match notice: this build's encrypted sync (wc-sync/4) has no
::     plaintext fallback, so a phone and desktop on different versions simply
::     cannot sync with each other. Markers are written by build_installer.bat
::     and staged here by wellness_setup.iss; degrade to "unknown" rather than
::     fail when install_phone_app.bat is run outside a full installer build.
set "APP_VERSION=unknown"
if exist "%~dp0app-version.txt" set /p APP_VERSION=<"%~dp0app-version.txt"
set "APK_SIGNING=debug"
if exist "%~dp0apk-signing.txt" set /p APK_SIGNING=<"%~dp0apk-signing.txt"
echo.
echo   Desktop app version: %APP_VERSION%
echo   Phone APK version:   %APP_VERSION% ^(%APK_SIGNING%-signed^)
echo   Both apps must be on the same version to sync.
echo.

"%ADB%" shell am force-stop %PACKAGE% >nul 2>&1
:: Captured (not streamed straight to the console) so the signature-mismatch
:: banner below can be selected precisely instead of guessed from errorlevel.
set "INSTALL_LOG=%TEMP%\wc_install_result_%RANDOM%.txt"
"%ADB%" install -r "%APK%" > "%INSTALL_LOG%" 2>&1
set "INSTALL_RC=%ERRORLEVEL%"
type "%INSTALL_LOG%"
findstr /i /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" /c:"signatures do not match" "%INSTALL_LOG%" >nul
set "SIG_MISMATCH=%ERRORLEVEL%"
del /q "%INSTALL_LOG%" 2>nul

if not "%INSTALL_RC%"=="0" (
    echo.
    if "%SIG_MISMATCH%"=="0" (
        rem A debug-signed phone meeting a release-signed build ^(or vice
        rem versa^) is a DIFFERENT app to Android - it refuses to install over
        rem the existing one. Never auto-uninstall here: uninstalling wipes the
        rem phone's local database, so the phone must sync everything to the
        rem desktop FIRST.
        echo   [ERROR] This phone has a differently-signed copy installed -
        echo           installing over it is blocked to protect your data.
        echo           Do this, in order:
        echo.
        echo             1. Open the desktop app.
        echo             2. On the phone, sync fully - everything on the phone
        echo                must reach the desktop before anything is removed.
        echo             3. Uninstall the old app from the phone.
        echo             4. Run this installer again to put the new APK on.
        echo             5. Re-pair the phone and sync once more.
        echo.
        echo           Details: docs\signing-guide.md, "Moving an existing
        echo           phone across".
    ) else (
        echo   [ERROR] Install failed - see the ADB output above.
    )
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
