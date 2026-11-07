@echo off
title Agent Remote Host Supervisor v2.0
cd /d "%~dp0"

echo [Agent Remote] Starting Headless Bridge Daemon in Interactive Session 1...
start "Agent Remote Daemon" /min python agent_daemon.py

timeout /t 1 /nobreak >nul

echo [Agent Remote] Starting Desktop System Tray Controller...
python desktop_tray.py
