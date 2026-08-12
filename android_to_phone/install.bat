@echo off
setlocal

echo ========================================
echo  Wellness Companion - Phone Installer
echo ========================================
echo.
echo  Prerequisites:
echo    1. USB cable connected to your phone
echo    2. USB Debugging enabled on your phone
echo       (Settings ^> Developer Options ^> USB Debugging)
echo.

set ADB=%~dp0adb\adb.exe
set APK=%~dp0WellnessCompanion.apk

:: Check ADB exists
if not exist "%ADB%" (
    echo ERROR: adb.exe not found at %ADB%
    echo Run build_android.bat first to bundle ADB.
    pause
    exit /b 1
)

:: Check APK exists
if not exist "%APK%" (
    echo ERROR: WellnessCompanion.apk not found.
    echo Run build_android.bat first to build the app.
    pause
    exit /b 1
)

:: Start ADB server
echo [1/3] Starting ADB server...
"%ADB%" start-server >nul 2>&1

:: Wait for device
echo [2/3] Waiting for phone...
echo        (connect your phone via USB and tap "Allow" if prompted)
"%ADB%" wait-for-device

:: Check device is connected
"%ADB%" devices | findstr /r "device$" >nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: No device found. Make sure:
    echo   - USB cable is connected
    echo   - USB Debugging is ON
    echo   - You tapped "Allow" on the phone prompt
    pause
    exit /b 1
)

echo        Phone connected!
echo.

:: Install
echo [3/3] Installing Wellness Companion...
"%ADB%" install -r "%APK%"
if %ERRORLEVEL% neq 0 (
    echo.
    echo INSTALL FAILED. Check the error above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Installed successfully!
echo  Open "Wellness Companion" on your phone.
echo ========================================
echo.

:: Launch the app
"%ADB%" shell am start -n com.wellnesscompanion.app/.MainActivity >nul 2>&1

pause
