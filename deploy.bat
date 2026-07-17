@echo off
title Ti-Android Deploy via ADB
setlocal

set "ROOT=%~dp0"
set "APK_PATH=%ROOT%app\build\outputs\apk\debug\app-debug.apk"

echo ════════════════════════════════════════════
echo   Ti-Android Deploy via ADB
echo ════════════════════════════════════════════
echo.

:: Check ADB
where adb >nul 2>&1
if %errorlevel% neq 0 (
    echo [✗] ADB not found in PATH!
    echo  Install Android SDK platform-tools and add to PATH.
    echo  Or run: set PATH=%%USERPROFILE%%\AppData\Local\Android\Sdk\platform-tools;%%PATH%%
    pause
    exit /b 1
)

:: Check device
echo [🔍] Checking connected devices...
adb devices | findstr "device$" >nul 2>&1
if %errorlevel% neq 0 (
    echo [✗] No Android device connected!
    echo  Connect a device via USB with USB debugging enabled.
    echo  Check: adb devices
    pause
    exit /b 1
)

:: Build
echo [🔨] Building APK...
cd /d "%ROOT%"
call gradlew.bat assembleDebug 2>&1
if %errorlevel% neq 0 (
    echo [✗] Build failed!
    pause
    exit /b 1
)

:: Install
echo [📦] Installing APK...
adb install -r "%APK_PATH%"
if %errorlevel% neq 0 (
    echo [✗] Install failed!
    pause
    exit /b 1
)

echo [✅] Ti-Android installed successfully!
echo.
echo  To launch: adb shell am start -n ti.android.runtime/.MainActivity
echo  To view logs: adb logcat -s TiAndroid
echo.
pause
endlocal
