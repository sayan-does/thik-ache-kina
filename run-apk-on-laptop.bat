@echo off
title Agent Remote - Launch APK on Laptop
set "EMULATOR=C:\Users\defau\AppData\Local\Android\Sdk\emulator\emulator.exe"
set "ADB=adb"
set "APK=%~dp0android\app\build\outputs\apk\debug\app-debug.apk"

echo ====================================================================
echo   AGENT REMOTE - STANDALONE LAPTOP APK RUNNER
echo   (No Android Studio GUI Required)
echo ====================================================================
echo.

if not exist "%APK%" (
    echo [ERROR] APK not found at %APK%
    pause
    exit /b 1
)

echo [*] Checking if Android emulator is already running...
%ADB% devices | findstr /i "emulator" >nul
if %errorlevel% equ 0 (
    echo [OK] Android emulator is already running!
) else (
    echo [*] Starting virtual phone emulator (Small_Phone)...
    start "" "%EMULATOR%" -avd Small_Phone -no-boot-anim
    echo [*] Waiting for emulator to connect to ADB...
    %ADB% wait-for-device
    echo [*] Waiting for system boot to complete...
    %ADB% shell "while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done"
)

echo [*] Dismissing lockscreen...
%ADB% shell wm dismiss-keyguard
%ADB% shell input keyevent 82

echo [*] Setting up ADB reverse tunnel (tcp:8765 -> tcp:8765)...
%ADB% reverse tcp:8765 tcp:8765

echo.
echo [*] Installing latest app-debug.apk...
%ADB% install -r -d -g "%APK%"

echo [*] Launching Agent Remote...
%ADB% shell am start -n com.agentremote.debug/com.agentremote.MainActivity >nul 2>&1
if %errorlevel% neq 0 (
    %ADB% shell monkey -p com.agentremote.debug 1 >nul 2>&1
)

echo.
echo [SUCCESS] Agent Remote is now running in the virtual phone on your screen!
echo ====================================================================
pause
