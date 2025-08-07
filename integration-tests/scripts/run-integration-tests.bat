@echo off
setlocal enabledelayedexpansion

REM 端到端集成测试运行脚本 (Windows版本)
echo 🚀 开始运行智能内容推荐平台集成测试

REM 设置项目路径
set "PROJECT_ROOT=%~dp0..\.."
set "INTEGRATION_TEST_DIR=%PROJECT_ROOT%\integration-tests"
set "TEST_RESULTS_DIR=%INTEGRATION_TEST_DIR%\test-results"

REM 创建测试结果目录
if not exist "%TEST_RESULTS_DIR%" mkdir "%TEST_RESULTS_DIR%"

echo [INFO] 检查依赖...

REM 检查Docker
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker未安装，请先安装Docker
    exit /b 1
)

REM 检查Maven
mvn --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven未安装，请先安装Maven
    exit /b 1
)

echo [SUCCESS] 依赖检查通过

echo [INFO] 启动基础设施服务...
cd /d "%PROJECT_ROOT%"
docker-compose up -d mysql redis elasticsearch rabbitmq

echo [INFO] 等待基础设施服务启动...
timeout /t 30 /nobreak >nul

echo [INFO] 构建和启动应用服务...
mvn clean package -DskipTests -q
docker-compose up -d recommendation-service user-service content-service feature-service ranking-service

echo [INFO] 等待应用服务启动...
timeout /t 45 /nobreak >nul

echo [INFO] 运行Java集成测试...
cd /d "%INTEGRATION_TEST_DIR%"
mvn test -Dspring.profiles.active=test

echo [INFO] 运行Python集成测试...
cd /d "%INTEGRATION_TEST_DIR%\python"
pip install -r requirements.txt
python -m pytest -v

echo [SUCCESS] 集成测试完成
pause