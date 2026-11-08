@echo off
title Agent Remote Host Terminal
cd /d "%~dp0"
python agent-bridge\attach.py
pause
