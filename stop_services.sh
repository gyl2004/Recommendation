#!/bin/bash

echo "=========================================="
echo "停止Java服务"
echo "=========================================="

# 停止推荐服务
if [ -f logs/recommendation-service.pid ]; then
    PID=$(cat logs/recommendation-service.pid)
    if kill -0 $PID 2>/dev/null; then
        echo "停止推荐服务 (PID: $PID)..."
        kill $PID
        rm logs/recommendation-service.pid
    else
        echo "推荐服务已停止"
        rm -f logs/recommendation-service.pid
    fi
else
    echo "未找到推荐服务PID文件"
fi

# 停止用户服务
if [ -f logs/user-service.pid ]; then
    PID=$(cat logs/user-service.pid)
    if kill -0 $PID 2>/dev/null; then
        echo "停止用户服务 (PID: $PID)..."
        kill $PID
        rm logs/user-service.pid
    else
        echo "用户服务已停止"
        rm -f logs/user-service.pid
    fi
else
    echo "未找到用户服务PID文件"
fi

# 停止内容服务
if [ -f logs/content-service.pid ]; then
    PID=$(cat logs/content-service.pid)
    if kill -0 $PID 2>/dev/null; then
        echo "停止内容服务 (PID: $PID)..."
        kill $PID
        rm logs/content-service.pid
    else
        echo "内容服务已停止"
        rm -f logs/content-service.pid
    fi
else
    echo "未找到内容服务PID文件"
fi

# 强制杀死可能残留的Java进程
echo "检查残留的Java进程..."
JAVA_PIDS=$(ps aux | grep "java.*App" | grep -v grep | awk '{print $2}')
if [ ! -z "$JAVA_PIDS" ]; then
    echo "发现残留Java进程，强制停止..."
    echo $JAVA_PIDS | xargs kill -9
fi

echo "所有服务已停止"