# JVM性能优化配置

## 推荐服务JVM参数优化

### 基础配置
```bash
# 堆内存配置
-Xms4g
-Xmx4g
-XX:NewRatio=1
-XX:SurvivorRatio=8

# 垃圾回收器配置 (G1GC)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1MixedGCCountTarget=8
-XX:G1MixedGCLiveThresholdPercent=85

# 元空间配置
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# GC日志配置
-XX:+PrintGC
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCApplicationStoppedTime
-Xloggc:logs/gc.log
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=100M

# JIT编译优化
-XX:+TieredCompilation
-XX:TieredStopAtLevel=4
-XX:CompileThreshold=10000

# 其他优化参数
-XX:+UseFastAccessorMethods
-XX:+OptimizeStringConcat
-XX:+UseStringDeduplication
-server
```

### 高并发场景优化
```bash
# 针对高并发的额外参数
-XX:+UseCompressedOops
-XX:+UseCompressedClassPointers
-XX:+UseBiasedLocking
-XX:BiasedLockingStartupDelay=0

# 线程栈大小优化
-Xss256k

# 直接内存配置
-XX:MaxDirectMemorySize=1g

# 预分配内存
-XX:+AlwaysPreTouch
```

## 应用启动脚本

### recommendation-service启动脚本
```bash
#!/bin/bash

# 设置JVM参数
JAVA_OPTS="-Xms4g -Xmx4g"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -Xloggc:logs/gc.log"
JAVA_OPTS="$JAVA_OPTS -XX:+TieredCompilation"
JAVA_OPTS="$JAVA_OPTS -server"

# 设置应用参数
APP_OPTS="--spring.profiles.active=prod"
APP_OPTS="$APP_OPTS --server.port=8080"
APP_OPTS="$APP_OPTS --logging.level.root=WARN"

# 启动应用
java $JAVA_OPTS -jar recommendation-service.jar $APP_OPTS
```

## JVM监控和调优

### 监控脚本
```bash
#!/bin/bash

# 获取Java进程PID
PID=$(pgrep -f recommendation-service)

if [ -z "$PID" ]; then
    echo "未找到recommendation-service进程"
    exit 1
fi

echo "监控Java进程: $PID"

# 监控GC情况
echo "=== GC统计 ==="
jstat -gc $PID

# 监控堆内存使用
echo "=== 堆内存使用 ==="
jstat -gccapacity $PID

# 监控类加载
echo "=== 类加载统计 ==="
jstat -class $PID

# 监控编译情况
echo "=== JIT编译统计 ==="
jstat -compiler $PID

# 生成堆转储 (可选)
# jmap -dump:format=b,file=heap_dump.hprof $PID
```

### GC调优建议

#### G1GC参数调优
1. **MaxGCPauseMillis**: 根据响应时间要求调整，推荐200ms
2. **G1HeapRegionSize**: 根据堆大小调整，推荐16MB
3. **G1NewSizePercent**: 年轻代占比，推荐30%
4. **G1MixedGCCountTarget**: 混合GC目标次数，推荐8次

#### 监控指标
- **GC频率**: 每分钟GC次数应小于10次
- **GC暂停时间**: 95%的GC暂停时间应小于200ms
- **内存使用率**: 堆内存使用率应保持在70%以下
- **元空间使用**: 元空间使用率应保持在80%以下

## 性能测试验证

### 压测前JVM状态检查
```bash
# 检查JVM参数
jinfo -flags $PID

# 检查初始内存状态
jstat -gc $PID

# 检查类加载情况
jstat -class $PID
```

### 压测中监控
```bash
# 持续监控GC
while true; do
    jstat -gc $PID
    sleep 5
done

# 监控线程状态
jstack $PID > thread_dump_$(date +%Y%m%d_%H%M%S).txt
```

### 压测后分析
```bash
# 分析GC日志
# 使用GCViewer或其他工具分析gc.log

# 生成内存分析报告
jmap -histo $PID > memory_histogram.txt

# 检查是否有内存泄漏
jmap -dump:format=b,file=heap_after_test.hprof $PID
```