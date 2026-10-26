@echo off
chcp 65001 >nul
title ProxFibre TV
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\kit.ps1"
if errorlevel 1 pause
