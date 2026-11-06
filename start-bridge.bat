@echo off
title Agent Remote Bridge
cd /d "%~dp0agent-bridge"
echo Starting Agent Remote Bridge Daemon...
python bridge.py
pause
