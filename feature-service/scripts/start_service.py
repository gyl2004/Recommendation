#!/usr/bin/env python3
"""
特征服务启动脚本
初始化数据库并启动服务
"""
import asyncio
import sys
import os
from pathlib import Path

# 添加项目根目录到Python路径
sys.path.append(str(Path(__file__).parent.parent))

from app.core.database import init_redis, init_clickhouse
from app.core.logging import setup_logging
from app.services.offline_feature_service import OfflineFeatureService
from loguru import logger

async def initialize_service():
    """初始化服务"""
    try:
        # 设置日志
        setup_logging()
        logger.info("开始初始化特征服务...")
        
        # 初始化数据库连接
        await init_redis()
        await init_clickhouse()
        
        # 初始化ClickHouse表结构
        from scripts.init_database import init_clickhouse_tables
        await init_clickhouse_tables()
        
        # 启动离线特征计算调度器
        offline_service = OfflineFeatureService()
        offline_service.start_scheduler()
        
        logger.info("特征服务初始化完成")
        return True
        
    except Exception as e:
        logger.error(f"特征服务初始化失败: {e}")
        return False

def main():
    """主函数"""
    # 初始化服务
    success = asyncio.run(initialize_service())
    
    if not success:
        logger.error("服务初始化失败，退出")
        sys.exit(1)
    
    # 启动FastAPI服务
    import uvicorn
    from app.core.config import settings
    
    logger.info(f"启动特征服务，地址: {settings.HOST}:{settings.PORT}")
    
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level="info"
    )

if __name__ == "__main__":
    main()