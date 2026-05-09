@echo off
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

REM kbqa-project 启动脚本 (Windows)

set APP_NAME=kbqa-project
set APP_VERSION=1.0.0-SNAPSHOT
set APP_DIR=%~dp0..
set APP_JAR=%APP_DIR%\target\%APP_NAME%-%APP_VERSION%.jar
set PID_FILE=%APP_DIR%\%APP_NAME%.pid
set LOG_FILE=%APP_DIR%\app.log

if "%JAVA_OPTS%"=="" set JAVA_OPTS=-Xms256m -Xmx512m

REM 检查 Java 环境
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] 未找到 Java，请确保 Java 17+ 已安装并配置 JAVA_HOME
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set java_ver=%%~v
)
for /f "delims=." %%m in ("!java_ver!") do set java_major=%%m
if !java_major! lss 17 (
    echo [ERROR] Java 版本过低 ^(当前: !java_major!^)，需要 Java 17+
    exit /b 1
)

REM 检查是否已在运行
if exist "%PID_FILE%" (
    set /p old_pid=<"%PID_FILE%"
    tasklist /FI "PID eq !old_pid!" /NH 2>nul | findstr /i "java" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo [WARN] %APP_NAME% 已在运行 ^(PID: !old_pid!^)
        exit /b 0
    ) else (
        echo [WARN] PID 文件存在但进程已退出，清理残留
        del /f /q "%PID_FILE%" >nul 2>&1
    )
)

REM 构建 jar
if not exist "%APP_JAR%" (
    echo [INFO] 未找到 jar: %APP_JAR%
    echo [INFO] 开始 Maven 构建...
    where mvn >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        pushd "%APP_DIR%" && mvn clean package -DskipTests -q && popd
    ) else if exist "%APP_DIR%\mvnw.cmd" (
        pushd "%APP_DIR%" && mvnw.cmd clean package -DskipTests -q && popd
    ) else (
        echo [ERROR] 未找到 mvn 或 mvnw，请先构建项目
        exit /b 1
    )
    if not exist "%APP_JAR%" (
        echo [ERROR] 构建失败，未生成 jar 文件
        exit /b 1
    )
    echo [INFO] 构建完成
)

REM 启动应用
echo [INFO] 启动 %APP_NAME%...
echo [INFO] JAR: %APP_JAR%
echo [INFO] LOG: %LOG_FILE%
echo [INFO] JAVA_OPTS: %JAVA_OPTS%

set spring_opts=
if not "%SPRING_PROFILES%"=="" set spring_opts=--spring.profiles.active=%SPRING_PROFILES%

start /b "" java %JAVA_OPTS% -jar "%APP_JAR%" %spring_opts% --logging.file.name="%LOG_FILE%" >nul 2>&1

REM 获取 java 进程 PID（最近启动的）
timeout /t 2 /nobreak >nul
for /f "tokens=2" %%p in ('tasklist /FI "IMAGENAME eq java.exe" /FO LIST ^| findstr /i "PID" ^| sort /r') do (
    set app_pid=%%p
    goto :got_pid
)
:got_pid
if defined app_pid (
    echo !app_pid!> "%PID_FILE%"
    echo [INFO] %APP_NAME% 已启动 ^(PID: !app_pid!^, 端口: 8080^)
) else (
    echo [WARN] 无法获取 PID，应用可能仍在启动中
)

REM 等待 HTTP 可用
echo [INFO] 等待应用就绪...
for /l %%i in (1,1,30) do (
    curl -s -o nul -w "%%{http_code}" http://localhost:8080/ 2>nul | findstr /r "200 404" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        echo [INFO] %APP_NAME% 启动成功
        exit /b 0
    )
    timeout /t 1 /nobreak >nul
)
echo [WARN] 应用可能仍在启动中，请检查日志: %LOG_FILE%
