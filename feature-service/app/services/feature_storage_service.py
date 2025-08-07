"""
特征存储服务
负责特征数据在Redis和ClickHouse中的存储管理
"""
import json
import asyncio
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta
import numpy as np
from loguru import logger

from ..core.database import get_redis, get_clickhouse
from ..core.config import settings
from ..models.schemas import UserFeatures, ContentFeatures, UserBehavior

class FeatureStorageService:
    """特征存储服务"""
    
    def __init__(self):
        self.redis_user_prefix = "user:features:"
        self.redis_content_prefix = "content:features:"
        self.redis_batch_prefix = "batch:features:"
    
    async def store_user_features_batch(self, features_dict: Dict[str, UserFeatures]) -> bool:
        """批量存储用户特征到Redis"""
        try:
            redis_client = await get_redis()
            
            # 使用pipeline提高性能
            pipe = redis_client.pipeline()
            
            for user_id, features in features_dict.items():
                cache_key = f"{self.redis_user_prefix}{user_id}"
                feature_data = features.model_dump(mode='json')
                
                pipe.setex(
                    cache_key,
                    settings.USER_FEATURE_EXPIRE,
                    json.dumps(feature_data, default=str)
                )
            
            await pipe.execute()
            logger.info(f"批量存储用户特征成功，数量: {len(features_dict)}")
            return True
            
        except Exception as e:
            logger.error(f"批量存储用户特征失败: {e}")
            return False
    
    async def store_content_features_batch(self, features_dict: Dict[str, ContentFeatures]) -> bool:
        """批量存储内容特征到Redis"""
        try:
            redis_client = await get_redis()
            
            # 使用pipeline提高性能
            pipe = redis_client.pipeline()
            
            for content_id, features in features_dict.items():
                cache_key = f"{self.redis_content_prefix}{content_id}"
                feature_data = features.model_dump(mode='json')
                
                pipe.setex(
                    cache_key,
                    settings.CONTENT_FEATURE_EXPIRE,
                    json.dumps(feature_data, default=str)
                )
            
            await pipe.execute()
            logger.info(f"批量存储内容特征成功，数量: {len(features_dict)}")
            return True
            
        except Exception as e:
            logger.error(f"批量存储内容特征失败: {e}")
            return False
    
    async def store_user_behavior_to_clickhouse(self, behaviors: List[UserBehavior]) -> bool:
        """批量存储用户行为数据到ClickHouse"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 准备数据
            data = []
            for behavior in behaviors:
                data.append([
                    int(behavior.user_id),
                    int(behavior.content_id),
                    behavior.action_type.value,
                    behavior.content_type.value,
                    behavior.session_id or '',
                    behavior.device_type or '',
                    behavior.timestamp,
                    behavior.duration or 0,
                    json.dumps(behavior.extra_data)
                ])
            
            # 批量插入
            clickhouse_client.execute(
                """
                INSERT INTO user_behaviors 
                (user_id, content_id, action_type, content_type, session_id, device_type, timestamp, duration, extra_data)
                VALUES
                """,
                data
            )
            
            logger.info(f"批量存储用户行为数据成功，数量: {len(behaviors)}")
            return True
            
        except Exception as e:
            logger.error(f"批量存储用户行为数据失败: {e}")
            return False
    
    async def get_user_features_from_cache(self, user_ids: List[str]) -> Dict[str, Optional[UserFeatures]]:
        """从缓存批量获取用户特征"""
        try:
            redis_client = await get_redis()
            results = {}
            
            # 使用pipeline批量获取
            pipe = redis_client.pipeline()
            cache_keys = [f"{self.redis_user_prefix}{user_id}" for user_id in user_ids]
            
            for cache_key in cache_keys:
                pipe.get(cache_key)
            
            cached_data_list = await pipe.execute()
            
            for user_id, cached_data in zip(user_ids, cached_data_list):
                if cached_data:
                    try:
                        feature_data = json.loads(cached_data)
                        results[user_id] = UserFeatures(**feature_data)
                    except Exception as e:
                        logger.error(f"解析用户特征缓存失败 user_id={user_id}: {e}")
                        results[user_id] = None
                else:
                    results[user_id] = None
            
            return results
            
        except Exception as e:
            logger.error(f"批量获取用户特征缓存失败: {e}")
            return {user_id: None for user_id in user_ids}
    
    async def get_content_features_from_cache(self, content_ids: List[str]) -> Dict[str, Optional[ContentFeatures]]:
        """从缓存批量获取内容特征"""
        try:
            redis_client = await get_redis()
            results = {}
            
            # 使用pipeline批量获取
            pipe = redis_client.pipeline()
            cache_keys = [f"{self.redis_content_prefix}{content_id}" for content_id in content_ids]
            
            for cache_key in cache_keys:
                pipe.get(cache_key)
            
            cached_data_list = await pipe.execute()
            
            for content_id, cached_data in zip(content_ids, cached_data_list):
                if cached_data:
                    try:
                        feature_data = json.loads(cached_data)
                        results[content_id] = ContentFeatures(**feature_data)
                    except Exception as e:
                        logger.error(f"解析内容特征缓存失败 content_id={content_id}: {e}")
                        results[content_id] = None
                else:
                    results[content_id] = None
            
            return results
            
        except Exception as e:
            logger.error(f"批量获取内容特征缓存失败: {e}")
            return {content_id: None for content_id in content_ids}
    
    async def store_feature_vectors_to_clickhouse(self, user_vectors: Dict[str, List[float]], 
                                                content_vectors: Dict[str, List[float]]) -> bool:
        """存储特征向量到ClickHouse用于离线计算"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 存储用户特征向量
            if user_vectors:
                user_data = []
                for user_id, vector in user_vectors.items():
                    user_data.append([
                        int(user_id),
                        'user',
                        vector,
                        datetime.now()
                    ])
                
                clickhouse_client.execute(
                    """
                    INSERT INTO feature_vectors 
                    (entity_id, entity_type, feature_vector, created_at)
                    VALUES
                    """,
                    user_data
                )
            
            # 存储内容特征向量
            if content_vectors:
                content_data = []
                for content_id, vector in content_vectors.items():
                    content_data.append([
                        int(content_id),
                        'content',
                        vector,
                        datetime.now()
                    ])
                
                clickhouse_client.execute(
                    """
                    INSERT INTO feature_vectors 
                    (entity_id, entity_type, feature_vector, created_at)
                    VALUES
                    """,
                    content_data
                )
            
            logger.info(f"存储特征向量成功，用户: {len(user_vectors)}, 内容: {len(content_vectors)}")
            return True
            
        except Exception as e:
            logger.error(f"存储特征向量失败: {e}")
            return False
    
    async def cleanup_expired_features(self):
        """清理过期的特征数据"""
        try:
            redis_client = await get_redis()
            
            # 获取所有特征键
            user_keys = await redis_client.keys(f"{self.redis_user_prefix}*")
            content_keys = await redis_client.keys(f"{self.redis_content_prefix}*")
            
            expired_count = 0
            
            # 检查并删除过期的用户特征
            for key in user_keys:
                ttl = await redis_client.ttl(key)
                if ttl == -1:  # 没有设置过期时间
                    await redis_client.expire(key, settings.USER_FEATURE_EXPIRE)
                elif ttl == -2:  # 已过期
                    expired_count += 1
            
            # 检查并删除过期的内容特征
            for key in content_keys:
                ttl = await redis_client.ttl(key)
                if ttl == -1:  # 没有设置过期时间
                    await redis_client.expire(key, settings.CONTENT_FEATURE_EXPIRE)
                elif ttl == -2:  # 已过期
                    expired_count += 1
            
            logger.info(f"特征缓存清理完成，过期数量: {expired_count}")
            
        except Exception as e:
            logger.error(f"清理过期特征失败: {e}")
    
    async def get_cache_statistics(self) -> Dict[str, Any]:
        """获取缓存统计信息"""
        try:
            redis_client = await get_redis()
            
            # 获取键数量
            user_keys = await redis_client.keys(f"{self.redis_user_prefix}*")
            content_keys = await redis_client.keys(f"{self.redis_content_prefix}*")
            
            # 获取内存使用情况
            info = await redis_client.info('memory')
            
            statistics = {
                'user_feature_count': len(user_keys),
                'content_feature_count': len(content_keys),
                'total_feature_count': len(user_keys) + len(content_keys),
                'memory_used': info.get('used_memory_human', 'N/A'),
                'memory_peak': info.get('used_memory_peak_human', 'N/A'),
                'timestamp': datetime.now().isoformat()
            }
            
            return statistics
            
        except Exception as e:
            logger.error(f"获取缓存统计信息失败: {e}")
            return {}
    
    async def backup_features_to_clickhouse(self, backup_type: str = 'daily') -> bool:
        """备份特征数据到ClickHouse"""
        try:
            redis_client = await get_redis()
            clickhouse_client = get_clickhouse()
            
            backup_data = []
            
            if backup_type == 'daily':
                # 备份用户特征
                user_keys = await redis_client.keys(f"{self.redis_user_prefix}*")
                for key in user_keys[:1000]:  # 限制数量避免内存问题
                    cached_data = await redis_client.get(key)
                    if cached_data:
                        user_id = key.replace(self.redis_user_prefix, '')
                        backup_data.append([
                            int(user_id),
                            'user_features',
                            cached_data,
                            datetime.now()
                        ])
                
                # 备份内容特征
                content_keys = await redis_client.keys(f"{self.redis_content_prefix}*")
                for key in content_keys[:1000]:  # 限制数量避免内存问题
                    cached_data = await redis_client.get(key)
                    if cached_data:
                        content_id = key.replace(self.redis_content_prefix, '')
                        backup_data.append([
                            int(content_id),
                            'content_features',
                            cached_data,
                            datetime.now()
                        ])
            
            if backup_data:
                clickhouse_client.execute(
                    """
                    INSERT INTO feature_backups 
                    (entity_id, feature_type, feature_data, backup_time)
                    VALUES
                    """,
                    backup_data
                )
                
                logger.info(f"特征数据备份成功，数量: {len(backup_data)}")
            
            return True
            
        except Exception as e:
            logger.error(f"备份特征数据失败: {e}")
            return False