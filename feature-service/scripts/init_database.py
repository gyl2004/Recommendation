#!/usr/bin/env python3
"""
数据库初始化脚本
初始化ClickHouse表结构和Redis配置
"""
import asyncio
import sys
import os
from pathlib import Path

# 添加项目根目录到Python路径
sys.path.append(str(Path(__file__).parent.parent))

from app.core.database import init_clickhouse, init_redis, get_clickhouse
from app.core.config import settings
from loguru import logger

async def init_clickhouse_tables():
    """初始化ClickHouse表结构"""
    try:
        await init_clickhouse()
        clickhouse_client = get_clickhouse()
        
        # 读取SQL脚本
        sql_file = Path(__file__).parent / "init_clickhouse.sql"
        with open(sql_file, 'r', encoding='utf-8') as f:
            sql_content = f.read()
        
        # 分割SQL语句并执行
        sql_statements = [stmt.strip() for stmt in sql_content.split(';') if stmt.strip()]
        
        for stmt in sql_statements:
            if stmt and not stmt.startswith('--'):
                try:
                    clickhouse_client.execute(stmt)
                    logger.info(f"执行SQL成功: {stmt[:50]}...")
                except Exception as e:
                    logger.error(f"执行SQL失败: {stmt[:50]}... 错误: {e}")
        
        logger.info("ClickHouse表结构初始化完成")
        
    except Exception as e:
        logger.error(f"初始化ClickHouse失败: {e}")
        raise

async def init_redis_config():
    """初始化Redis配置"""
    try:
        await init_redis()
        logger.info("Redis连接初始化完成")
        
    except Exception as e:
        logger.error(f"初始化Redis失败: {e}")
        raise

async def create_sample_data():
    """创建示例数据"""
    try:
        clickhouse_client = get_clickhouse()
        
        # 插入示例用户行为数据
        sample_behaviors = [
            (1001, 2001, 'view', 'article', 'session_001', 'mobile', '2024-01-01 10:00:00', 120, '{}'),
            (1001, 2002, 'click', 'article', 'session_001', 'mobile', '2024-01-01 10:02:00', 0, '{}'),
            (1001, 2002, 'like', 'article', 'session_001', 'mobile', '2024-01-01 10:03:00', 0, '{}'),
            (1002, 2001, 'view', 'article', 'session_002', 'desktop', '2024-01-01 11:00:00', 180, '{}'),
            (1002, 2003, 'view', 'video', 'session_002', 'desktop', '2024-01-01 11:05:00', 300, '{}'),
            (1003, 2004, 'view', 'product', 'session_003', 'mobile', '2024-01-01 12:00:00', 60, '{}'),
            (1003, 2004, 'purchase', 'product', 'session_003', 'mobile', '2024-01-01 12:10:00', 0, '{"amount": 99.99}'),
        ]
        
        clickhouse_client.execute(
            """
            INSERT INTO user_behaviors 
            (user_id, content_id, action_type, content_type, session_id, device_type, timestamp, duration, extra_data)
            VALUES
            """,
            sample_behaviors
        )
        
        logger.info("示例数据创建完成")
        
    except Exception as e:
        logger.error(f"创建示例数据失败: {e}")

async def main():
    """主函数"""
    logger.info("开始初始化数据库...")
    
    try:
        # 初始化ClickHouse
        await init_clickhouse_tables()
        
        # 初始化Redis
        await init_redis_config()
        
        # 创建示例数据
        await create_sample_data()
        
        logger.info("数据库初始化完成！")
        
    except Exception as e:
        logger.error(f"数据库初始化失败: {e}")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())