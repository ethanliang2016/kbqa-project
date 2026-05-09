#!/bin/bash
# kbqa-project 重启脚本 (Linux/macOS)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "[INFO] 重启 kbqa-project..."

# 停止
bash "${SCRIPT_DIR}/stop.sh"
sleep 2

# 启动
bash "${SCRIPT_DIR}/start.sh"
