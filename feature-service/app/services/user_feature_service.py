"""
用户特征服务
负责用户特征的提取、计算和存储
"""
import json
import asyncio
from typing import Dict, List, Optional, Tuple
from datetime import datetime, timedelta
import numpy as np
from loguru import logger

from ..core.database import get_redis, get_clickhouse
from ..core.config import settings
from ..models.schemas import UserFeatures, UserBehavior, ActionType, ContentType

class UserFeatureService:
    """用户特征服务"""
    
    def __init__(self):
        self.redis_key_prefix = "user:features:"
        self.behavior_weights = {
            ActionType.VIEW: 1.0,
            ActionType.CLICK: 2.0,
            ActionType.LIKE: 3.0,
            ActionType.SHARE: 4.0,
            ActionType.COMMENT: 3.5,
            ActionType.PURCHASE: 5.0
        }
    
    async def get_user_features(self, user_id: str) -> Optional[UserFeatures]:
        """获取用户特征"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{user_id}"
            
            # 从Redis获取缓存的特征
            cached_data = await redis_client.get(cache_key)
            if cached_data:
                feature_data = json.loads(cached_data)
                return UserFeatures(**feature_data)
            
            # 缓存未命中，从ClickHouse计算特征
            features = await self._compute_user_features(user_id)
            if features:
                # 缓存特征数据
                await self._cache_user_features(user_id, features)
                return features
            
            return None
            
        except Exception as e:
            logger.error(f"获取用户特征失败 user_id={user_id}: {e}")
            return None
    
    async def get_batch_user_features(self, user_ids: List[str]) -> Dict[str, UserFeatures]:
        """批量获取用户特征"""
        results = {}
        
        # 并发获取特征
        tasks = [self.get_user_features(user_id) for user_id in user_ids]
        features_list = await asyncio.gather(*tasks, return_exceptions=True)
        
        for user_id, features in zip(user_ids, features_list):
            if isinstance(features, UserFeatures):
                results[user_id] = features
            elif isinstance(features, Exception):
                logger.error(f"获取用户特征异常 user_id={user_id}: {features}")
        
        return results
    
    async def update_user_features(self, user_id: str, force_update: bool = False) -> bool:
        """更新用户特征"""
        try:
            # 检查是否需要更新
            if not force_update and not await self._should_update_features(user_id):
                return True
            
            # 重新计算特征
            features = await self._compute_user_features(user_id)
            if features:
                await self._cache_user_features(user_id, features)
                logger.info(f"用户特征更新成功 user_id={user_id}")
                return True
            
            return False
            
        except Exception as e:
            logger.error(f"更新用户特征失败 user_id={user_id}: {e}")
            return False
    
    async def process_user_behavior(self, behavior: UserBehavior):
        """处理用户行为，实时更新特征"""
        try:
            # 获取当前特征
            current_features = await self.get_user_features(behavior.user_id)
            if not current_features:
                # 如果没有特征，创建新的
                current_features = await self._create_default_features(behavior.user_id)
            
            # 更新行为分数
            weight = self.behavior_weights.get(behavior.action_type, 1.0)
            current_features.behavior_score += weight * 0.1  # 增量更新
            
            # 更新兴趣标签
            await self._update_interest_tags(current_features, behavior)
            
            # 更新活跃度
            await self._update_activity_level(current_features, behavior)
            
            # 更新最后活跃时间
            current_features.last_active = behavior.timestamp
            current_features.updated_at = datetime.now()
            
            # 缓存更新后的特征
            await self._cache_user_features(behavior.user_id, current_features)
            
        except Exception as e:
            logger.error(f"处理用户行为失败: {e}")
    
    async def _compute_user_features(self, user_id: str) -> Optional[UserFeatures]:
        """从ClickHouse计算用户特征"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 查询用户行为数据
            query = """
            SELECT 
                action_type,
                content_type,
                COUNT(*) as action_count,
                AVG(duration) as avg_duration,
                MAX(timestamp) as last_action
            FROM user_behaviors 
            WHERE user_id = %(user_id)s 
                AND timestamp >= %(start_time)s
            GROUP BY action_type, content_type
            ORDER BY action_count DESC
            """
            
            start_time = datetime.now() - timedelta(days=30)  # 最近30天
            result = clickhouse_client.execute(
                query, 
                {'user_id': int(user_id), 'start_time': start_time}
            )
            
            if not result:
                return await self._create_default_features(user_id)
            
            # 计算特征
            features = UserFeatures(user_id=user_id)
            
            # 计算行为分数
            total_score = 0
            content_type_counts = {}
            
            for row in result:
                action_type, content_type, count, avg_duration, last_action = row
                weight = self.behavior_weights.get(ActionType(action_type), 1.0)
                total_score += weight * count
                
                content_type_counts[content_type] = content_type_counts.get(content_type, 0) + count
            
            features.behavior_score = min(total_score / 100.0, 10.0)  # 归一化到0-10
            
            # 确定偏好的内容类型
            if content_type_counts:
                sorted_types = sorted(content_type_counts.items(), key=lambda x: x[1], reverse=True)
                features.preferred_content_types = [ContentType(ct) for ct, _ in sorted_types[:3]]
            
            # 计算活跃度
            total_actions = sum(count for _, _, count, _, _ in result)
            if total_actions > 100:
                features.activity_level = "high"
            elif total_actions > 20:
                features.activity_level = "medium"
            else:
                features.activity_level = "low"
            
            # 生成特征向量
            features.feature_vector = await self._generate_feature_vector(features)
            
            return features
            
        except Exception as e:
            logger.error(f"计算用户特征失败 user_id={user_id}: {e}")
            return None
    
    async def _create_default_features(self, user_id: str) -> UserFeatures:
        """创建默认用户特征"""
        features = UserFeatures(
            user_id=user_id,
            behavior_score=0.0,
            activity_level="low",
            feature_vector=[0.0] * 64  # 64维特征向量
        )
        return features
    
    async def _cache_user_features(self, user_id: str, features: UserFeatures):
        """缓存用户特征到Redis"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{user_id}"
            
            # 序列化特征数据
            feature_data = features.model_dump(mode='json')
            
            # 设置缓存
            await redis_client.setex(
                cache_key,
                settings.USER_FEATURE_EXPIRE,
                json.dumps(feature_data, default=str)
            )
            
        except Exception as e:
            logger.error(f"缓存用户特征失败 user_id={user_id}: {e}")
    
    async def _should_update_features(self, user_id: str) -> bool:
        """判断是否需要更新特征"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{user_id}"
            
            # 检查缓存是否存在
            exists = await redis_client.exists(cache_key)
            if not exists:
                return True
            
            # 检查更新时间
            cached_data = await redis_client.get(cache_key)
            if cached_data:
                feature_data = json.loads(cached_data)
                updated_at = datetime.fromisoformat(feature_data.get('updated_at', ''))
                if datetime.now() - updated_at > timedelta(hours=1):
                    return True
            
            return False
            
        except Exception as e:
            logger.error(f"检查特征更新状态失败 user_id={user_id}: {e}")
            return True
    
    async def _update_interest_tags(self, features: UserFeatures, behavior: UserBehavior):
        """更新兴趣标签"""
        # 这里可以根据内容标签更新用户兴趣
        # 简化实现，实际应该从内容服务获取内容标签
        if behavior.content_type == ContentType.ARTICLE:
            if "tech" not in features.interests:
                features.interests.append("tech")
        elif behavior.content_type == ContentType.VIDEO:
            if "entertainment" not in features.interests:
                features.interests.append("entertainment")
    
    async def _update_activity_level(self, features: UserFeatures, behavior: UserBehavior):
        """更新活跃度"""
        # 基于最近行为频率更新活跃度
        if features.last_active:
            time_diff = behavior.timestamp - features.last_active
            if time_diff.total_seconds() < 3600:  # 1小时内
                if features.activity_level == "low":
                    features.activity_level = "medium"
                elif features.activity_level == "medium":
                    features.activity_level = "high"
    
    async def _generate_feature_vector(self, features: UserFeatures) -> List[float]:
        """生成特征向量"""
        # 简化的特征向量生成
        vector = [0.0] * 64
        
        # 行为分数特征
        vector[0] = features.behavior_score / 10.0
        
        # 活跃度特征
        activity_mapping = {"low": 0.2, "medium": 0.6, "high": 1.0}
        vector[1] = activity_mapping.get(features.activity_level, 0.2)
        
        # 内容类型偏好特征
        for i, content_type in enumerate(features.preferred_content_types[:3]):
            if i < 3:
                vector[2 + i] = 1.0
        
        # 兴趣标签特征（简化）
        for i, interest in enumerate(features.interests[:10]):
            if i < 10:
                vector[5 + i] = 1.0
        
        return vector