@echo off
setlocal
echo ===============================================================
echo   Compiling Agent Remote Windows Standalone Desktop Application
echo ===============================================================

cd /d "%~dp0agent-bridge"

echo [1/3] Verifying PyInstaller environment...
python -m pip install --upgrade pyinstaller

echo [2/3] Building AgentRemote.exe with PyInstaller...
python -m PyInstaller --clean --noconfirm AgentRemote.spec

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] PyInstaller compilation failed!
    exit /b %ERRORLEVEL%
)

echo [3/3] Verifying compiled executable...
if exist "dist\AgentRemote\AgentRemote.exe" (
    echo [SUCCESS] Compiled standalone workstation binary at:
    echo %cd%\dist\AgentRemote\AgentRemote.exe
) else (
    echo [ERROR] Expected binary dist\AgentRemote\AgentRemote.exe was not created!
    exit /b 1
)

echo ===============================================================
echo   Build Complete!
echo ===============================================================
