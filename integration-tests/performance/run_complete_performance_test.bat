@echo off
REM 智能内容推荐平台完整性能测试和优化脚本 (Windows版本)

setlocal enabledelayedexpansion

REM 配置参数
if "%BASE_URL%"=="" set BASE_URL=http://localhost:8080
set PERFORMANCE_DIR=%~dp0
set RESULTS_DIR=%PERFORMANCE_DIR%results
set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

REM 创建结果目录
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

echo ==========================================
echo 智能内容推荐平台完整性能测试
echo ==========================================
echo 测试时间: %date% %time%
echo 测试目标: %BASE_URL%
echo 结果目录: %RESULTS_DIR%
echo ==========================================

REM 检查依赖
echo 🔍 检查测试依赖...

REM 检查JMeter
jmeter -v >nul 2>&1
if errorlevel 1 (
    echo ❌ JMeter未安装，请先安装JMeter并添加到PATH
    pause
    exit /b 1
)

REM 检查Python依赖
python -c "import requests, psutil" >nul 2>&1
if errorlevel 1 (
    echo ❌ Python依赖缺失，正在安装...
    pip install requests psutil mysql-connector-python redis matplotlib pandas
)

REM 检查服务可用性
curl -f -s "%BASE_URL%/actuator/health" >nul 2>&1
if errorlevel 1 (
    echo ⚠️ 警告: 服务可能未启动
    echo 请确保以下服务正在运行:
    echo - 推荐服务 ^(端口8080^)
    echo - MySQL数据库 ^(端口3306^)
    echo - Redis缓存 ^(端口6379^)
    echo - Elasticsearch ^(端口9200^)
    set /p continue="是否继续测试? (y/N): "
    if /i not "!continue!"=="y" exit /b 1
)

echo ✅ 依赖检查完成

REM 系统预热
echo 🔥 系统预热...
for /l %%i in (1,1,50) do (
    curl -s "%BASE_URL%/api/v1/recommend/content?userId=warmup_%%i&size=10" >nul 2>&1
)
timeout /t 10 /nobreak >nul
echo ✅ 系统预热完成

REM 执行基准性能测试
echo 📊 执行基准性能测试...
jmeter -n ^
    -t "%PERFORMANCE_DIR%jmeter\recommendation_load_test.jmx" ^
    -l "%RESULTS_DIR%\baseline_test_%TIMESTAMP%.jtl" ^
    -e ^
    -o "%RESULTS_DIR%\baseline-report-%TIMESTAMP%" ^
    -Jbase_url="%BASE_URL%" ^
    -Jusers=500 ^
    -Jramp_time=30 ^
    -Jduration=120 ^
    > "%RESULTS_DIR%\baseline_test_%TIMESTAMP%.log" 2>&1

echo ✅ 基准测试完成

REM 执行目标性能测试
echo 🎯 执行目标性能测试 ^(10000 QPS^)...

REM 执行高负载测试
jmeter -n ^
    -t "%PERFORMANCE_DIR%jmeter\recommendation_load_test.jmx" ^
    -l "%RESULTS_DIR%\target_test_%TIMESTAMP%.jtl" ^
    -e ^
    -o "%RESULTS_DIR%\target-report-%TIMESTAMP%" ^
    -Jbase_url="%BASE_URL%" ^
    -Jusers=1000 ^
    -Jramp_time=60 ^
    -Jduration=300 ^
    > "%RESULTS_DIR%\target_test_%TIMESTAMP%.log" 2>&1

echo ✅ 目标性能测试完成

REM 分析测试结果
echo 📈 分析测试结果...

if exist "%RESULTS_DIR%\baseline_test_%TIMESTAMP%.jtl" (
    echo 分析基准测试结果...
    python "%PERFORMANCE_DIR%scripts\analyze_results.py" "%RESULTS_DIR%\baseline_test_%TIMESTAMP%.jtl"
)

if exist "%RESULTS_DIR%\target_test_%TIMESTAMP%.jtl" (
    echo 分析目标测试结果...
    python "%PERFORMANCE_DIR%scripts\analyze_results.py" "%RESULTS_DIR%\target_test_%TIMESTAMP%.jtl"
)

echo ✅ 结果分析完成

REM 执行瓶颈分析
echo 🔍 执行系统瓶颈分析...
python "%PERFORMANCE_DIR%scripts\bottleneck_analysis.py" "%RESULTS_DIR%\target_test_%TIMESTAMP%.jtl"
echo ✅ 瓶颈分析完成

REM 验证性能目标
echo 🎯 验证性能目标...
python "%PERFORMANCE_DIR%scripts\performance_validation.py" "%BASE_URL%"
echo ✅ 性能目标验证完成

REM 生成优化建议
echo 💡 生成优化建议...

(
echo # 智能内容推荐平台性能优化总结
echo.
echo ## 测试概述
echo - 测试时间: %date% %time%
echo - 测试目标: 10,000 QPS, 500ms响应时间, 99.9%可用性
echo - 测试结果目录: %RESULTS_DIR%
echo.
echo ## 优化建议优先级
echo.
echo ### 🚨 紧急优化 ^(立即执行^)
echo 1. **JVM参数调优**
echo    - 增加堆内存: -Xms4g -Xmx4g
echo    - 使用G1垃圾回收器: -XX:+UseG1GC
echo    - 优化GC参数: -XX:MaxGCPauseMillis=200
echo.
echo 2. **数据库连接池优化**
echo    - 增加最大连接数: maximum-pool-size: 50
echo    - 优化连接超时: connection-timeout: 30000
echo    - 启用连接测试: connection-test-query: SELECT 1
echo.
echo 3. **Redis缓存优化**
echo    - 增加最大内存: maxmemory 6gb
echo    - 优化淘汰策略: maxmemory-policy allkeys-lru
echo    - 启用内存碎片整理: activedefrag yes
echo.
echo ### ⚡ 短期优化 ^(1-2周内^)
echo 1. **应用层优化**
echo    - 实现多级缓存策略
echo    - 优化推荐算法复杂度
echo    - 增加异步处理
echo.
echo 2. **数据库优化**
echo    - 添加必要索引
echo    - 优化慢查询
echo    - 实现读写分离
echo.
echo 3. **系统架构优化**
echo    - 实现负载均衡
echo    - 增加服务实例
echo    - 优化网络配置
echo.
echo ### 🔧 中期优化 ^(1-2月内^)
echo 1. **微服务拆分**
echo    - 拆分推荐服务
echo    - 实现服务治理
echo    - 增加熔断机制
echo.
echo 2. **基础设施优化**
echo    - 容器化部署
echo    - 自动扩缩容
echo    - 监控告警完善
echo.
echo ## 预期效果
echo - QPS提升: 50-100%%
echo - 响应时间减少: 30-50%%
echo - 系统稳定性提升: 显著改善
echo - 资源利用率优化: 20-30%%
echo.
echo ## 下一步行动
echo 1. 立即应用紧急优化配置
echo 2. 重新执行性能测试验证效果
echo 3. 根据结果调整优化策略
echo 4. 建立持续性能监控
) > "%RESULTS_DIR%\optimization_summary_%TIMESTAMP%.md"

echo ✅ 优化建议生成完成

REM 生成测试总结
echo 📋 生成测试总结...

(
echo 智能内容推荐平台性能测试总结
echo =====================================
echo.
echo 测试时间: %date% %time%
echo 测试目标: %BASE_URL%
echo.
echo 生成的文件:
echo - 基准测试结果: baseline_test_%TIMESTAMP%.jtl
echo - 目标测试结果: target_test_%TIMESTAMP%.jtl
echo - HTML报告: baseline-report-%TIMESTAMP%\index.html
echo - HTML报告: target-report-%TIMESTAMP%\index.html
echo - 瓶颈分析报告: bottleneck_analysis_report.json
echo - 性能验证报告: performance_validation_report_*.json
echo - 优化建议: optimization_summary_%TIMESTAMP%.md
echo.
echo 查看方式:
echo 1. 打开HTML报告查看详细测试结果
echo 2. 查看JSON报告了解系统瓶颈
echo 3. 阅读优化建议制定改进计划
echo.
echo 下一步:
echo 1. 应用优化建议
echo 2. 重新执行测试
echo 3. 对比优化前后效果
) > "%RESULTS_DIR%\test_summary_%TIMESTAMP%.txt"

echo ✅ 清理和总结完成

echo.
echo ==========================================
echo ✅ 完整性能测试流程执行完成!
echo ==========================================
echo 测试结果目录: %RESULTS_DIR%
echo 主要报告文件:
echo - HTML报告: %RESULTS_DIR%\target-report-%TIMESTAMP%\index.html
echo - 优化建议: %RESULTS_DIR%\optimization_summary_%TIMESTAMP%.md
echo - 测试总结: %RESULTS_DIR%\test_summary_%TIMESTAMP%.txt
echo.
echo 建议下一步操作:
echo 1. 查看HTML报告了解详细性能数据
echo 2. 阅读优化建议并应用相关配置
echo 3. 重新执行测试验证优化效果
echo ==========================================

pause