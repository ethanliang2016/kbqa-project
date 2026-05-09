@echo off
chcp 65001 >nul 2>&1

REM kbqa-project 重启脚本 (Windows)

echo [INFO] 重启 kbqa-project...

call "%~dp0stop.bat"
timeout /t 2 /nobreak >nul

call "%~dp0start.bat"
