#!/bin/bash
# kbqa-project 启动脚本 (Linux/macOS)

APP_NAME="kbqa-project"
APP_VERSION="1.0.0-SNAPSHOT"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_JAR="${APP_DIR}/target/${APP_NAME}-${APP_VERSION}.jar"
PID_FILE="${APP_DIR}/${APP_NAME}.pid"
LOG_FILE="${APP_DIR}/app.log"

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx512m}"
SPRING_PROFILES="${SPRING_PROFILES:-}"

# 检查 Java 环境
check_java() {
    if ! command -v java &>/dev/null; then
        echo "[ERROR] 未找到 Java，请确保 Java 17+ 已安装并配置 JAVA_HOME"
        exit 1
    fi
    java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ]; then
        echo "[ERROR] Java 版本过低 (当前: ${java_version})，需要 Java 17+"
        exit 1
    fi
}

# 检查是否已在运行
check_running() {
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            return 0
        else
            echo "[WARN] PID 文件存在但进程已退出，清理残留 PID 文件"
            rm -f "$PID_FILE"
        fi
    fi
    return 1
}

# 构建 jar
build_if_needed() {
    if [ ! -f "$APP_JAR" ]; then
        echo "[INFO] 未找到 jar: ${APP_JAR}"
        echo "[INFO] 开始 Maven 构建..."
        if command -v mvn &>/dev/null; then
            (cd "$APP_DIR" && mvn clean package -DskipTests -q)
        elif [ -f "${APP_DIR}/mvnw" ]; then
            (cd "$APP_DIR" && ./mvnw clean package -DskipTests -q)
        else
            echo "[ERROR] 未找到 mvn 或 mvnw，请先构建项目"
            exit 1
        fi
        if [ ! -f "$APP_JAR" ]; then
            echo "[ERROR] 构建失败，未生成 jar 文件"
            exit 1
        fi
        echo "[INFO] 构建完成"
    fi
}

# 启动应用
start() {
    check_java

    if check_running; then
        echo "[WARN] ${APP_NAME} 已在运行 (PID: $(cat "$PID_FILE"))"
        exit 0
    fi

    build_if_needed

    echo "[INFO] 启动 ${APP_NAME}..."
    echo "[INFO] JAR: ${APP_JAR}"
    echo "[INFO] LOG: ${LOG_FILE}"
    echo "[INFO] JAVA_OPTS: ${JAVA_OPTS}"

    spring_opts=""
    if [ -n "$SPRING_PROFILES" ]; then
        spring_opts="--spring.profiles.active=${SPRING_PROFILES}"
    fi

    nohup java $JAVA_OPTS -jar "$APP_JAR" $spring_opts \
        --logging.file.name="$LOG_FILE" \
        > /dev/null 2>&1 &
    echo $! > "$PID_FILE"

    # 等待启动完成
    echo -n "[INFO] 等待应用启动"
    for i in $(seq 1 30); do
        sleep 1
        if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
            echo ""
            echo "[ERROR] 应用启动失败，请检查日志: ${LOG_FILE}"
            rm -f "$PID_FILE"
            exit 1
        fi
        if curl -s http://localhost:8080/actuator/health &>/dev/null 2>&1 || \
           curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ 2>/dev/null | grep -q "200\|404"; then
            echo ""
            echo "[INFO] ${APP_NAME} 启动成功 (PID: $(cat "$PID_FILE"), 端口: 8080)"
            exit 0
        fi
        echo -n "."
    done

    echo ""
    echo "[WARN] 应用可能仍在启动中，请检查日志: ${LOG_FILE}"
}

start
