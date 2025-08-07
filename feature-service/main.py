"""
特征服务主入口
提供用户特征和内容特征的提取、存储、更新功能
"""
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from contextlib import asynccontextmanager

from app.api.routes import feature_router
from app.core.config import settings
from app.core.database import init_redis, init_clickhouse, close_connections
from app.core.logging import setup_logging
from app.services.offline_feature_service import OfflineFeatureService

# 全局离线服务实例
offline_service = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    global offline_service
    
    # 启动时初始化
    setup_logging()
    await init_redis()
    await init_clickhouse()
    
    # 启动离线特征计算调度器
    offline_service = OfflineFeatureService()
    offline_service.start_scheduler()
    
    yield
    
    # 关闭时清理资源
    if offline_service:
        offline_service.stop_scheduler()
    await close_connections()

app = FastAPI(
    title="智能推荐特征服务",
    description="提供用户特征和内容特征的提取、存储、更新功能",
    version="1.0.0",
    lifespan=lifespan
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(feature_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    """健康检查接口"""
    return {"status": "healthy", "service": "feature-service"}

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level="info"
    )