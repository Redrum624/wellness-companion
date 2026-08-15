@echo off
setlocal enabledelayedexpansion
:: ===========================================================================
::  build_installer.bat - one command to build the Wellness Companion installer.
::
::  Steps:
::    0. Clean installer\output (no stale, version-stamped installers pile up).
::    1. Ensure vc_redist.x64.exe is present (downloaded from aka.ms if missing).
::    2. Build the Android APK if it is missing: assembleRelease when
::       android\keystore.properties exists, else assembleDebug. Writes
::       apk-signing.txt (release/debug) and app-version.txt (this build's
::       version) so the installer and install_phone_app.bat can read them
::       without re-deriving anything.
::    3. Build the Electron app  (electron-vite build + electron-builder --dir),
::       producing windows\dist\win-unpacked\ — NOT an installer.
::    4. Generate installer\icon\wellness.ico from the app PNG if missing.
::    5. Compile wellness_setup.iss with Inno Setup (ISCC) and code-sign both
::       the app exe and the setup exe.
::    6. Write a version-stamped README.txt next to the installer.
::
::  The version is read from windows\package.json and passed to ISCC as
::  /DAppVersion, so package.json is the single source of truth.
::
::  Code signing: both EXEs are Authenticode-signed with a SELF-SIGNED cert
::  created on first run by make_signing_cert.ps1 (subject "CN=Wellness
::  Companion Self-Signed", kept in the current user's cert store). Self-signed
::  is a valid signature but not CA-trusted, so SmartScreen still warns
::  "unknown publisher" elsewhere; run make_signing_cert.ps1 -Trust (as admin)
::  to trust it on THIS machine. Signing is SKIPPED with a warning (the build
::  still succeeds) when signtool.exe cannot be found.
::
::  Usage:
::    build_installer.bat            -> lean    (model downloaded at install)
::    build_installer.bat offline    -> offline (model embedded, ~2.8 GB)
::
::  Run from anywhere; paths resolve relative to this script.
::  Prereqs: Node.js + windows\ deps installed, Inno Setup 6, Python w/ Pillow
::           (icon only), Android SDK + JDK (only if the APK must be built),
::           Windows SDK signtool.exe (optional).
:: ===========================================================================

set "INSTALLER_DIR=%~dp0"
pushd "%INSTALLER_DIR%.."
set "REPO_ROOT=%CD%"
popd

set "WIN_DIR=%REPO_ROOT%\windows"
set "PKG_JSON=%WIN_DIR%\package.json"
set "ISS=%INSTALLER_DIR%wellness_setup.iss"
set "VCREDIST=%REPO_ROOT%\vc_redist.x64.exe"
set "KEYSTORE_PROPS=%REPO_ROOT%\android\keystore.properties"
set "UNPACKED=%WIN_DIR%\dist\win-unpacked"
set "APP_EXE=%UNPACKED%\Wellness Companion.exe"
set "ICON_PNG=%WIN_DIR%\resources\icon.png"
set "ICON_ICO=%INSTALLER_DIR%icon\wellness.ico"

:: --- Version: single source of truth is windows\package.json -----------------
set "APPVER="
for /f "usebackq tokens=2 delims=:," %%V in (`findstr /r /c:"\"version\"[ ]*:" "%PKG_JSON%"`) do (
    set "RAW=%%~V"
    set "RAW=!RAW: =!"
    set "RAW=!RAW:"=!"
    if not defined APPVER set "APPVER=!RAW!"
)
if not defined APPVER (
    echo [ERROR] Could not read "version" from %PKG_JSON%.
    exit /b 1
)

set "SETUP_EXE=%INSTALLER_DIR%output\Wellness Companion Setup %APPVER%.exe"
set "README_TXT=%INSTALLER_DIR%output\Wellness Companion %APPVER% README.txt"

:: --- Build mode --------------------------------------------------------------
set "ISCC_DEFINES="
set "BUILD_MODE=lean - the AI model is downloaded at install time"
if /I "%~1"=="offline"   set "ISCC_DEFINES=/DINCLUDE_MODEL"
if /I "%~1"=="--offline" set "ISCC_DEFINES=/DINCLUDE_MODEL"
if defined ISCC_DEFINES  set "BUILD_MODE=offline - the AI model is embedded, ~2.8 GB"

:: --- Locate ISCC -------------------------------------------------------------
set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"

:: --- Locate signtool + ensure the self-signed cert exists (optional) ---------
set "SIGN_NAME=Wellness Companion Self-Signed"
set "SIGNTOOL="
for /f "delims=" %%I in ('where signtool 2^>nul') do set "SIGNTOOL=%%I"
if not defined SIGNTOOL for /f "delims=" %%I in ('dir /b /s "%ProgramFiles(x86)%\Windows Kits\10\bin\signtool.exe" 2^>nul ^| findstr /i "x64"') do set "SIGNTOOL=%%I"
if not defined SIGNTOOL for /f "delims=" %%I in ('dir /b /s "%ProgramFiles(x86)%\Windows Kits\10\bin\signtool.exe" 2^>nul') do set "SIGNTOOL=%%I"

echo.
echo ============================================================
echo   Wellness Companion Installer Build
echo   Version: %APPVER%
echo   Mode:    %BUILD_MODE%
echo ============================================================
echo.

if defined SIGNTOOL (
    echo   [OK] signtool: !SIGNTOOL!
    powershell -NoProfile -ExecutionPolicy Bypass -File "%INSTALLER_DIR%make_signing_cert.ps1" >nul
) else (
    echo   [WARN] signtool.exe not found - the EXEs will NOT be code-signed
    echo          ^(install the Windows SDK to enable signing^).
)
echo.

:: Offline requires the model to be present to embed it.
if defined ISCC_DEFINES if not exist "%REPO_ROOT%\model\Qwen3-4B-Instruct-2507-GGUF\Qwen3-4B-Instruct-2507-Q4_K_M.gguf" (
    echo [ERROR] Offline build requested but the model is missing:
    echo         %REPO_ROOT%\model\Qwen3-4B-Instruct-2507-GGUF\
    echo         Place the model folder at the project root and retry.
    exit /b 1
)

:: --------------------------------------------------
:: Step 0: clean the output directory
:: --------------------------------------------------
echo [0/6] Cleaning the output directory...
if exist "%INSTALLER_DIR%output" (
    del /q "%INSTALLER_DIR%output\*" 2>nul
    for /d %%D in ("%INSTALLER_DIR%output\*") do rd /s /q "%%D" 2>nul
    echo   [OK] Cleaned: %INSTALLER_DIR%output
) else (
    mkdir "%INSTALLER_DIR%output"
    echo   [OK] Created: %INSTALLER_DIR%output
)
echo.

:: --------------------------------------------------
:: Step 1: Visual C++ Redistributable (always embedded -> single-file setup)
:: --------------------------------------------------
echo [1/6] Visual C++ Redistributable...
if exist "%VCREDIST%" (
    echo   [OK] Already present.
) else (
    echo   Downloading vc_redist.x64.exe...
    powershell -NoProfile -Command "try { Invoke-WebRequest -Uri 'https://aka.ms/vs/17/release/vc_redist.x64.exe' -OutFile '%VCREDIST%' } catch { exit 1 }"
    if not exist "%VCREDIST%" (
        echo   [ERROR] Could not download vc_redist.x64.exe. It is embedded in the
        echo           installer, so the build cannot continue without it.
        exit /b 1
    )
    echo   [OK] Downloaded.
)
echo.

:: --------------------------------------------------
:: Step 2: Android APK - release when a signing keystore is present, else debug
:: --------------------------------------------------
:: A release build has a DIFFERENT signature than the debug build (Android's
:: universally-known debug key vs. the user's own keystore.properties), so the
:: two are tracked at their own, separate output paths - no ambiguity about
:: which one a stale file left behind belongs to.
if exist "%KEYSTORE_PROPS%" (
    set "GRADLE_TASK=assembleRelease"
    set "APK=%REPO_ROOT%\android\app\build\outputs\apk\release\app-release.apk"
    set "APK_SIGNING=release"
) else (
    set "GRADLE_TASK=assembleDebug"
    set "APK=%REPO_ROOT%\android\app\build\outputs\apk\debug\app-debug.apk"
    set "APK_SIGNING=debug"
)
echo [2/6] Android APK ^(!GRADLE_TASK!^)...
if exist "%APK%" (
    echo   [OK] APK already present.
) else (
    echo   APK missing - building with gradlew !GRADLE_TASK!...
    if not exist "%REPO_ROOT%\android\gradlew.bat" (
        echo   [ERROR] android\gradlew.bat not found - cannot build the APK.
        exit /b 1
    )
    :: Absolute path, not a bare "gradlew.bat": cmd does not reliably resolve a
    :: batch file from the pushd'd directory (NoDefaultCurrentDirectoryInExePath
    :: makes it fail outright), and this branch only runs on a clean clone where
    :: the APK is absent — so the failure never showed up in an incremental build.
    pushd "%REPO_ROOT%\android"
    call "%REPO_ROOT%\android\gradlew.bat" !GRADLE_TASK! --console=plain
    set "GRADLE_RC=!ERRORLEVEL!"
    popd
    if not "!GRADLE_RC!"=="0" (
        echo   [ERROR] Gradle build failed ^(exit !GRADLE_RC!^).
        exit /b 1
    )
    if not exist "%APK%" (
        echo   [ERROR] Gradle finished but the APK is not at %APK%.
        exit /b 1
    )
    echo   [OK] APK built ^(!APK_SIGNING!-signed^).
)
if "!APK_SIGNING!"=="debug" (
    echo   [WARN] No android\keystore.properties - shipping the DEBUG-signed APK.
    echo          See docs\signing-guide.md to create a release keystore.
)

:: --- Version lockstep: app-version.txt (below) is printed by
::     install_phone_app.bat as BOTH the desktop and the phone version, but
::     the APK's real version comes from android\app\build.gradle.kts
::     versionName, not from windows\package.json. If a release bumps one
::     and forgets the other, the installer would print a false claim about
::     the exact property the paired-upgrade banner exists to surface -- so
::     verify the two agree before writing that file.
set "GRADLE_KTS=%REPO_ROOT%\android\app\build.gradle.kts"
set "APKVER="
for /f "usebackq tokens=2 delims==" %%V in (`findstr /r /c:"versionName[ ]*=" "%GRADLE_KTS%"`) do (
    set "RAW=%%~V"
    set "RAW=!RAW: =!"
    set "RAW=!RAW:"=!"
    if not defined APKVER set "APKVER=!RAW!"
)
if not defined APKVER (
    echo   [ERROR] Could not read "versionName" from %GRADLE_KTS%.
    exit /b 1
)
if /I not "!APKVER!"=="!APPVER!" (
    echo.
    echo   ============================================================
    echo     [ERROR] VERSION LOCKSTEP BROKEN
    echo     windows\package.json  version : %APPVER%
    echo     build.gradle.kts   versionName : !APKVER!
    echo     install_phone_app.bat prints ONE version for both apps -
    echo     bump both to match before building the installer.
    echo   ============================================================
    echo.
    exit /b 1
)
echo   [OK] Version lockstep: desktop and phone both %APPVER%.
:: Marker files staged into the installer by wellness_setup.iss, then read by
:: install_phone_app.bat at %~dp0 (i.e. {app}) - never regenerated on the fly
:: there, since the installed folder has no gradle/adb to derive them from.
> "%INSTALLER_DIR%apk-signing.txt" echo !APK_SIGNING!
> "%INSTALLER_DIR%app-version.txt" echo %APPVER%
echo.

:: --------------------------------------------------
:: Step 3: Electron app -> dist\win-unpacked (dir target, no NSIS)
:: --------------------------------------------------
echo [3/6] Building the Electron app...
pushd "%WIN_DIR%"
call npx electron-vite build
if errorlevel 1 (
    echo   [ERROR] electron-vite build failed.
    popd & exit /b 1
)
call npx electron-builder --win --dir
if errorlevel 1 (
    echo   [ERROR] electron-builder --dir failed.
    popd & exit /b 1
)
popd
if not exist "%APP_EXE%" (
    echo   [ERROR] The unpacked app was not produced at:
    echo           %APP_EXE%
    exit /b 1
)
echo   [OK] Unpacked app: %UNPACKED%
call :sign_file "%APP_EXE%"
echo.

:: --------------------------------------------------
:: Step 4: installer icon
:: --------------------------------------------------
echo [4/6] Installer icon...
if exist "%ICON_ICO%" (
    echo   [OK] Icon already present.
) else (
    python "%INSTALLER_DIR%make_icon.py" "%ICON_PNG%" "%ICON_ICO%"
    if errorlevel 1 (
        echo   [ERROR] Icon generation failed ^(is Pillow installed?^).
        echo           pip install Pillow
        exit /b 1
    )
)
echo.

:: --------------------------------------------------
:: Step 5: compile the Inno Setup script
:: --------------------------------------------------
echo [5/6] Compiling the installer ^(Wellness Companion Setup %APPVER%.exe^)...
if not exist "%ISCC%" (
    echo   [ERROR] ISCC.exe ^(Inno Setup 6^) not found.
    echo           Install Inno Setup 6 from https://jrsoftware.org/isdl.php
    exit /b 1
)
"%ISCC%" /DAppVersion=%APPVER% %ISCC_DEFINES% "%ISS%"
if errorlevel 1 (
    echo   [ERROR] Inno Setup compile failed.
    exit /b 1
)
if not exist "%SETUP_EXE%" (
    echo   [ERROR] The installer was not produced at %SETUP_EXE%.
    exit /b 1
)
call :sign_file "%SETUP_EXE%"
echo.

:: --------------------------------------------------
:: Step 6: version-stamped README.txt beside the installer
:: --------------------------------------------------
echo [6/6] Generating the README.txt...
python "%INSTALLER_DIR%make_readme_txt.py" "%REPO_ROOT%\README.md" "%APPVER%" "%INSTALLER_DIR%output" "Wellness Companion"
if errorlevel 1 echo   [WARN] README.txt generation failed ^(non-fatal^).
echo.

echo ============================================================
echo   BUILD COMPLETE ^(%BUILD_MODE%^)
echo   Installer: %SETUP_EXE%
echo   Readme:    %README_TXT%
echo.
echo   Ship the Setup exe on its own - the VC++ runtime and the
echo   Android APK are both embedded inside it.
if not defined ISCC_DEFINES echo   The AI model ^(~2.5 GB^) is downloaded during install.
echo ============================================================
echo.
endlocal
exit /b 0

:: --------------------------------------------------
:: :sign_file "<path>" - Authenticode-sign one file with the self-signed cert.
::   Tries a timestamped signature first (so it stays valid after the cert
::   expires); falls back to no-timestamp when offline. Never fails the build.
:: --------------------------------------------------
:sign_file
if not defined SIGNTOOL goto :eof
echo   Signing %~nx1...
"%SIGNTOOL%" sign /n "%SIGN_NAME%" /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 "%~1" >nul 2>&1
if not errorlevel 1 ( echo   [OK] Signed %~nx1 & goto :eof )
echo   [WARN] Timestamped signing failed ^(offline?^) - retrying without a timestamp...
"%SIGNTOOL%" sign /n "%SIGN_NAME%" /fd SHA256 "%~1" >nul 2>&1
if not errorlevel 1 ( echo   [OK] Signed %~nx1 ^(no timestamp^) & goto :eof )
echo   [WARN] Signing failed for %~nx1 - continuing unsigned.
goto :eof
