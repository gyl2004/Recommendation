@echo off
REM 智能内容推荐平台部署脚本 (Windows版本)
REM 使用方法: deploy.bat [environment] [action]
REM 环境: dev, staging, prod
REM 操作: deploy, rollback, status

setlocal enabledelayedexpansion

REM 设置默认参数
set ENVIRONMENT=%1
set ACTION=%2
if "%ENVIRONMENT%"=="" set ENVIRONMENT=dev
if "%ACTION%"=="" set ACTION=deploy

set PROJECT_NAME=recommendation-system
set DOCKER_REGISTRY=ghcr.io/your-org
set KUBECTL_TIMEOUT=300s

REM 颜色定义 (Windows CMD不支持颜色，使用echo代替)
echo [INFO] 开始部署智能内容推荐平台 - 环境: %ENVIRONMENT%, 操作: %ACTION%

REM 检查依赖
echo [INFO] 检查部署依赖...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 未安装
    exit /b 1
)

kubectl version --client >nul 2>&1
if errorlevel 1 (
    echo [ERROR] kubectl 未安装
    exit /b 1
)

helm version >nul 2>&1
if errorlevel 1 (
    echo [WARNING] Helm 未安装，将跳过 Helm 相关操作
)

echo [SUCCESS] 依赖检查完成

REM 设置环境变量
echo [INFO] 设置 %ENVIRONMENT% 环境变量...

if "%ENVIRONMENT%"=="dev" (
    set NAMESPACE=recommendation-dev
    set REPLICAS=1
    set RESOURCES_REQUESTS_CPU=100m
    set RESOURCES_REQUESTS_MEMORY=256Mi
    set RESOURCES_LIMITS_CPU=500m
    set RESOURCES_LIMITS_MEMORY=512Mi
) else if "%ENVIRONMENT%"=="staging" (
    set NAMESPACE=recommendation-staging
    set REPLICAS=2
    set RESOURCES_REQUESTS_CPU=200m
    set RESOURCES_REQUESTS_MEMORY=512Mi
    set RESOURCES_LIMITS_CPU=1000m
    set RESOURCES_LIMITS_MEMORY=1Gi
) else if "%ENVIRONMENT%"=="prod" (
    set NAMESPACE=recommendation-system
    set REPLICAS=3
    set RESOURCES_REQUESTS_CPU=500m
    set RESOURCES_REQUESTS_MEMORY=512Mi
    set RESOURCES_LIMITS_CPU=2000m
    set RESOURCES_LIMITS_MEMORY=2Gi
) else (
    echo [ERROR] 不支持的环境: %ENVIRONMENT%
    exit /b 1
)

echo [SUCCESS] 环境变量设置完成

REM 根据操作类型执行相应功能
if "%ACTION%"=="deploy" goto :deploy
if "%ACTION%"=="rollback" goto :rollback
if "%ACTION%"=="status" goto :status
if "%ACTION%"=="cleanup" goto :cleanup

echo [ERROR] 不支持的操作: %ACTION%
echo 支持的操作: deploy, rollback, status, cleanup
exit /b 1

:deploy
echo [INFO] 开始部署流程...

REM 构建镜像
echo [INFO] 构建 Docker 镜像...
for /f %%i in ('git rev-parse --short HEAD') do set GIT_COMMIT=%%i
set IMAGE_TAG=%ENVIRONMENT%-%GIT_COMMIT%

echo [INFO] 构建 Java 服务...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Maven 构建失败
    exit /b 1
)

REM 构建各个服务的镜像
for %%s in (recommendation-service user-service content-service) do (
    echo [INFO] 构建 %%s 镜像...
    docker build -f docker/Dockerfile.%%s -t %DOCKER_REGISTRY%/%%s:%IMAGE_TAG% .
    docker tag %DOCKER_REGISTRY%/%%s:%IMAGE_TAG% %DOCKER_REGISTRY%/%%s:latest
)

for %%s in (feature-service ranking-service) do (
    echo [INFO] 构建 %%s 镜像...
    docker build -f docker/Dockerfile.%%s -t %DOCKER_REGISTRY%/%%s:%IMAGE_TAG% .
    docker tag %DOCKER_REGISTRY%/%%s:%IMAGE_TAG% %DOCKER_REGISTRY%/%%s:latest
)

echo [SUCCESS] 镜像构建完成

REM 推送镜像
echo [INFO] 推送镜像到仓库...
for %%s in (recommendation-service user-service content-service feature-service ranking-service) do (
    echo [INFO] 推送 %%s 镜像...
    docker push %DOCKER_REGISTRY%/%%s:%IMAGE_TAG%
    docker push %DOCKER_REGISTRY%/%%s:latest
)

echo [SUCCESS] 镜像推送完成

REM 创建命名空间
echo [INFO] 创建命名空间 %NAMESPACE%...
kubectl get namespace %NAMESPACE% >nul 2>&1
if errorlevel 1 (
    kubectl create namespace %NAMESPACE%
    echo [SUCCESS] 命名空间 %NAMESPACE% 创建成功
) else (
    echo [WARNING] 命名空间 %NAMESPACE% 已存在
)

REM 部署基础设施
echo [INFO] 部署基础设施组件...
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

if not "%ENVIRONMENT%"=="prod" (
    echo [INFO] 部署开发/测试环境的数据库和中间件...
    docker-compose -f docker-compose.%ENVIRONMENT%.yml up -d mysql redis elasticsearch clickhouse rabbitmq
) else (
    echo [INFO] 生产环境使用外部数据库和中间件
)

echo [SUCCESS] 基础设施部署完成

REM 部署应用服务
echo [INFO] 部署应用服务...
kubectl apply -f k8s/recommendation-service.yaml

REM 等待部署完成
echo [INFO] 等待服务启动...
for %%s in (recommendation-service user-service content-service) do (
    kubectl rollout status deployment/%%s -n %NAMESPACE% --timeout=%KUBECTL_TIMEOUT%
)

echo [SUCCESS] 应用服务部署完成

REM 执行健康检查
call :health_check

echo [SUCCESS] 部署完成! 访问地址: http://api-%ENVIRONMENT%.recommendation.com
goto :end

:rollback
echo [INFO] 回滚部署...
for %%s in (recommendation-service user-service content-service) do (
    echo [INFO] 回滚 %%s...
    kubectl rollout undo deployment/%%s -n %NAMESPACE%
    kubectl rollout status deployment/%%s -n %NAMESPACE% --timeout=%KUBECTL_TIMEOUT%
)
echo [SUCCESS] 回滚完成
call :health_check
goto :end

:status
echo [INFO] 显示部署状态...
echo === 命名空间 ===
kubectl get namespace %NAMESPACE%
echo === Pods ===
kubectl get pods -n %NAMESPACE% -o wide
echo === Services ===
kubectl get services -n %NAMESPACE%
echo === Ingress ===
kubectl get ingress -n %NAMESPACE%
goto :end

:cleanup
echo [INFO] 清理部署资源...
set /p CONFIRM="确定要删除 %NAMESPACE% 命名空间中的所有资源吗? (y/N): "
if /i "%CONFIRM%"=="y" (
    kubectl delete namespace %NAMESPACE%
    echo [SUCCESS] 资源清理完成
) else (
    echo [INFO] 取消清理操作
)
goto :end

:health_check
echo [INFO] 执行健康检查...
kubectl get pods -n %NAMESPACE%

for %%s in (recommendation-service user-service content-service) do (
    echo [INFO] 检查 %%s 健康状态...
    kubectl wait --for=condition=ready pod -l app=%%s -n %NAMESPACE% --timeout=300s
    if errorlevel 1 (
        echo [ERROR] %%s 健康检查失败
        exit /b 1
    ) else (
        echo [SUCCESS] %%s 健康检查通过
    )
)

echo [SUCCESS] 所有服务健康检查通过
goto :eof

:end
echo [INFO] 脚本执行完成
endlocal