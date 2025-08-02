@echo off
echo Building Ad Blocker VPN App...
echo.

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo Error: gradlew.bat not found!
    echo Please run this script from the project root directory.
    pause
    exit /b 1
)

REM Clean previous builds
echo Cleaning previous builds...
call gradlew.bat clean

REM Build debug APK
echo Building debug APK...
call gradlew.bat assembleDebug

REM Check if build was successful
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo To install on device:
    echo 1. Enable USB Debugging on your Android device
    echo 2. Connect device via USB
    echo 3. Run: adb install app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo Build failed! Check the error messages above.
    echo.
)

pause 