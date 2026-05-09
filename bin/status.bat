@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

REM kbqa-project 状态检查脚本 (Windows)

set APP_NAME=kbqa-project
set APP_DIR=%~dp0..
set PID_FILE=%APP_DIR%\%APP_NAME%.pid

REM 获取 PID
set pid=
if exist "%PID_FILE%" (
    set /p pid=<"%PID_FILE%"
    if defined pid (
        tasklist /FI "PID eq !pid!" /NH 2>nul | findstr /i "java" >nul 2>&1
        if !ERRORLEVEL! neq 0 (
            set pid=
            del /f /q "%PID_FILE%" >nul 2>&1
        )
    )
)

if not defined pid (
    for /f "tokens=2" %%p in ('wmic process where "commandline like '%%kbqa-project%%' and name='java.exe'" get ProcessId /value 2^>nul ^| findstr /i "ProcessId"') do (
        set pid=%%p
    )
)

if not defined pid (
    echo [INFO] %APP_NAME% 未在运行
    exit /b 1
)

echo [INFO] %APP_NAME% 正在运行 ^(PID: !pid!^)

REM 检查 HTTP 端口
curl -s -o nul -w "%%{http_code}" http://localhost:8080/ 2>nul | findstr /r "200 404" >nul 2>&1
if !ERRORLEVEL! equ 0 (
    echo [INFO] HTTP 端口 8080 可访问
) else (
    echo [WARN] HTTP 端口 8080 不可访问，应用可能仍在启动中
)

REM 显示内存占用
for /f "tokens=2 delims=:" %%m in ('wmic process where "ProcessId=!pid!" get WorkingSetSize /value 2^>nul ^| findstr /i "WorkingSetSize"') do (
    set /a mem_mb=%%m/1048576
    echo [INFO] 内存占用: !mem_mb!MB
)

REM 显示运行时长
for /f "tokens=2 delims=:" %%e in ('wmic process where "ProcessId=!pid!" get CreationDate /value 2^>nul ^| findstr /i "CreationDate"') do (
    echo [INFO] 启动时间: %%e
)
