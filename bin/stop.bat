@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

REM kbqa-project 停止脚本 (Windows)

set APP_NAME=kbqa-project
set APP_DIR=%~dp0..
set PID_FILE=%APP_DIR%\%APP_NAME%.pid
set GRACEFUL_TIMEOUT=15

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

REM 通过进程名查找
if not defined pid (
    for /f "tokens=2" %%p in ('wmic process where "commandline like '%%kbqa-project%%' and name='java.exe'" get ProcessId /value 2^>nul ^| findstr /i "ProcessId"') do (
        set pid=%%p
    )
)

if not defined pid (
    echo [INFO] %APP_NAME% 未在运行
    exit /b 0
)

echo [INFO] 停止 %APP_NAME% ^(PID: !pid!^)...

REM 发送 Ctrl+C 信号优雅停止
taskkill /PID !pid! >nul 2>&1

REM 等待进程退出
echo [INFO] 等待应用关闭...
for /l %%i in (1,1,%GRACEFUL_TIMEOUT%) do (
    tasklist /FI "PID eq !pid!" /NH 2>nul | findstr /i "java" >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        del /f /q "%PID_FILE%" >nul 2>&1
        echo [INFO] %APP_NAME% 已停止
        exit /b 0
    )
    timeout /t 1 /nobreak >nul
)

REM 超时强制终止
echo [WARN] 优雅关闭超时，强制终止 ^(PID: !pid!^)
taskkill /F /PID !pid! >nul 2>&1
del /f /q "%PID_FILE%" >nul 2>&1
echo [INFO] %APP_NAME% 已强制停止
