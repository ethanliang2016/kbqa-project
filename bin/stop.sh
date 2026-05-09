#!/bin/bash
# kbqa-project 停止脚本 (Linux/macOS)

APP_NAME="kbqa-project"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"
GRACEFUL_TIMEOUT=15

# 查找进程 PID
find_pid() {
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        else
            rm -f "$PID_FILE"
        fi
    fi

    # 通过 jar 名称查找
    pid=$(ps -ef | grep "kbqa-project" | grep -v grep | grep java | awk '{print $2}')
    if [ -n "$pid" ]; then
        echo "$pid"
        return 0
    fi

    return 1
}

stop() {
    pid=$(find_pid)
    if [ -z "$pid" ]; then
        echo "[INFO] ${APP_NAME} 未在运行"
        exit 0
    fi

    echo "[INFO] 停止 ${APP_NAME} (PID: ${pid})..."

    # 发送 SIGTERM 优雅停止
    kill "$pid" 2>/dev/null

    # 等待进程退出
    echo -n "[INFO] 等待应用关闭"
    for i in $(seq 1 "$GRACEFUL_TIMEOUT"); do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo ""
            rm -f "$PID_FILE"
            echo "[INFO] ${APP_NAME} 已停止"
            exit 0
        fi
        sleep 1
        echo -n "."
    done

    # 超时后强制终止
    echo ""
    echo "[WARN] 优雅关闭超时，强制终止 (PID: ${pid})"
    kill -9 "$pid" 2>/dev/null
    rm -f "$PID_FILE"
    echo "[INFO] ${APP_NAME} 已强制停止"
}

stop
