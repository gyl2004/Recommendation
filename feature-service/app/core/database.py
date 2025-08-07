"""
数据库连接管理
"""
import redis.asyncio as redis
from clickhouse_driver import Client
from typing import Optional
import asyncio
from loguru import logger

from .config import settings

# Redis连接池
redis_pool: Optional[redis.ConnectionPool] = None
redis_client: Optional[redis.Redis] = None

# ClickHouse客户端
clickhouse_client: Optional[Client] = None

async def init_redis():
    """初始化Redis连接"""
    global redis_pool, redis_client
    
    try:
        redis_pool = redis.ConnectionPool(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            db=settings.REDIS_DB,
            password=settings.REDIS_PASSWORD if settings.REDIS_PASSWORD else None,
            max_connections=settings.REDIS_MAX_CONNECTIONS,
            decode_responses=True
        )
        
        redis_client = redis.Redis(connection_pool=redis_pool)
        
        # 测试连接
        await redis_client.ping()
        logger.info("Redis连接初始化成功")
        
    except Exception as e:
        logger.error(f"Redis连接初始化失败: {e}")
        raise

async def init_clickhouse():
    """初始化ClickHouse连接"""
    global clickhouse_client
    
    try:
        clickhouse_client = Client(
            host=settings.CLICKHOUSE_HOST,
            port=settings.CLICKHOUSE_PORT,
            user=settings.CLICKHOUSE_USER,
            password=settings.CLICKHOUSE_PASSWORD,
            database=settings.CLICKHOUSE_DATABASE
        )
        
        # 测试连接
        clickhouse_client.execute("SELECT 1")
        logger.info("ClickHouse连接初始化成功")
        
    except Exception as e:
        logger.error(f"ClickHouse连接初始化失败: {e}")
        raise

async def get_redis() -> redis.Redis:
    """获取Redis客户端"""
    if redis_client is None:
        await init_redis()
    return redis_client

def get_clickhouse() -> Client:
    """获取ClickHouse客户端"""
    if clickhouse_client is None:
        raise RuntimeError("ClickHouse客户端未初始化")
    return clickhouse_client

async def close_connections():
    """关闭数据库连接"""
    global redis_client, clickhouse_client
    
    if redis_client:
        await redis_client.close()
        logger.info("Redis连接已关闭")
    
    if clickhouse_client:
        clickhouse_client.disconnect()
        logger.info("ClickHouse连接已关闭")