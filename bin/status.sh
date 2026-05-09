#!/bin/bash
# kbqa-project 状态检查脚本 (Linux/macOS)

APP_NAME="kbqa-project"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"

# 检查进程状态
pid=""
if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE")
    if ! kill -0 "$pid" 2>/dev/null; then
        pid=""
        rm -f "$PID_FILE"
    fi
fi

if [ -z "$pid" ]; then
    pid=$(ps -ef | grep "kbqa-project" | grep -v grep | grep java | awk '{print $2}')
fi

if [ -z "$pid" ]; then
    echo "[INFO] ${APP_NAME} 未在运行"
    exit 1
fi

echo "[INFO] ${APP_NAME} 正在运行 (PID: ${pid})"

# 检查 HTTP 端口
http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ 2>/dev/null)
if [ -n "$http_code" ] && [ "$http_code" != "000" ]; then
    echo "[INFO] HTTP 端口 8080 可访问 (状态码: ${http_code})"
else
    echo "[WARN] HTTP 端口 8080 不可访问，应用可能仍在启动中"
fi

# 显示内存占用
mem=$(ps -o rss= -p "$pid" 2>/dev/null | awk '{printf "%.1fMB", $1/1024}')
if [ -n "$mem" ]; then
    echo "[INFO] 内存占用: ${mem}"
fi

# 显示运行时长
elapsed=$(ps -o etime= -p "$pid" 2>/dev/null | xargs)
if [ -n "$elapsed" ]; then
    echo "[INFO] 运行时长: ${elapsed}"
fi
