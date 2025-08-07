"""
内容特征服务
负责内容特征的提取、计算和存储
"""
import json
import asyncio
from typing import Dict, List, Optional
from datetime import datetime, timedelta
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from loguru import logger

from ..core.database import get_redis, get_clickhouse
from ..core.config import settings
from ..models.schemas import ContentFeatures, ContentType

class ContentFeatureService:
    """内容特征服务"""
    
    def __init__(self):
        self.redis_key_prefix = "content:features:"
        self.tfidf_vectorizer = TfidfVectorizer(
            max_features=1000,
            stop_words='english',
            ngram_range=(1, 2)
        )
    
    async def get_content_features(self, content_id: str) -> Optional[ContentFeatures]:
        """获取内容特征"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{content_id}"
            
            # 从Redis获取缓存的特征
            cached_data = await redis_client.get(cache_key)
            if cached_data:
                feature_data = json.loads(cached_data)
                return ContentFeatures(**feature_data)
            
            # 缓存未命中，从数据库计算特征
            features = await self._compute_content_features(content_id)
            if features:
                # 缓存特征数据
                await self._cache_content_features(content_id, features)
                return features
            
            return None
            
        except Exception as e:
            logger.error(f"获取内容特征失败 content_id={content_id}: {e}")
            return None
    
    async def get_batch_content_features(self, content_ids: List[str]) -> Dict[str, ContentFeatures]:
        """批量获取内容特征"""
        results = {}
        
        # 并发获取特征
        tasks = [self.get_content_features(content_id) for content_id in content_ids]
        features_list = await asyncio.gather(*tasks, return_exceptions=True)
        
        for content_id, features in zip(content_ids, features_list):
            if isinstance(features, ContentFeatures):
                results[content_id] = features
            elif isinstance(features, Exception):
                logger.error(f"获取内容特征异常 content_id={content_id}: {features}")
        
        return results
    
    async def update_content_features(self, content_id: str, force_update: bool = False) -> bool:
        """更新内容特征"""
        try:
            # 检查是否需要更新
            if not force_update and not await self._should_update_features(content_id):
                return True
            
            # 重新计算特征
            features = await self._compute_content_features(content_id)
            if features:
                await self._cache_content_features(content_id, features)
                logger.info(f"内容特征更新成功 content_id={content_id}")
                return True
            
            return False
            
        except Exception as e:
            logger.error(f"更新内容特征失败 content_id={content_id}: {e}")
            return False
    
    async def batch_update_content_features(self, content_ids: List[str]) -> Dict[str, bool]:
        """批量更新内容特征"""
        results = {}
        
        # 分批处理
        batch_size = settings.BATCH_SIZE
        for i in range(0, len(content_ids), batch_size):
            batch = content_ids[i:i + batch_size]
            
            # 并发更新
            tasks = [self.update_content_features(content_id) for content_id in batch]
            batch_results = await asyncio.gather(*tasks, return_exceptions=True)
            
            for content_id, result in zip(batch, batch_results):
                if isinstance(result, bool):
                    results[content_id] = result
                else:
                    results[content_id] = False
                    logger.error(f"批量更新内容特征异常 content_id={content_id}: {result}")
        
        return results
    
    async def _compute_content_features(self, content_id: str) -> Optional[ContentFeatures]:
        """计算内容特征"""
        try:
            # 这里应该从内容服务获取内容数据
            # 简化实现，假设从ClickHouse获取内容基础信息
            content_data = await self._get_content_data(content_id)
            if not content_data:
                return None
            
            features = ContentFeatures(
                content_id=content_id,
                content_type=ContentType(content_data.get('content_type', 'article')),
                title=content_data.get('title', ''),
                category=content_data.get('category'),
                tags=content_data.get('tags', []),
                author_id=content_data.get('author_id'),
                publish_time=content_data.get('publish_time')
            )
            
            # 计算文本特征
            if features.content_type == ContentType.ARTICLE:
                features.text_features = await self._extract_text_features(content_data)
            
            # 计算质量分数
            features.quality_score = await self._calculate_quality_score(content_data)
            
            # 计算热度分数
            features.popularity_score = await self._calculate_popularity_score(content_id)
            
            # 生成嵌入向量
            features.embedding_vector = await self._generate_embedding_vector(features)
            
            return features
            
        except Exception as e:
            logger.error(f"计算内容特征失败 content_id={content_id}: {e}")
            return None
    
    async def _get_content_data(self, content_id: str) -> Optional[Dict]:
        """获取内容数据"""
        try:
            # 这里应该调用内容服务API获取内容数据
            # 简化实现，返回模拟数据
            return {
                'content_id': content_id,
                'content_type': 'article',
                'title': f'Sample Article {content_id}',
                'content': f'This is sample content for article {content_id}',
                'category': 'technology',
                'tags': ['tech', 'ai', 'machine-learning'],
                'author_id': '1001',
                'publish_time': datetime.now(),
                'view_count': 1000,
                'like_count': 50,
                'share_count': 10
            }
            
        except Exception as e:
            logger.error(f"获取内容数据失败 content_id={content_id}: {e}")
            return None
    
    async def _extract_text_features(self, content_data: Dict) -> Dict[str, float]:
        """提取文本特征"""
        try:
            text_features = {}
            
            title = content_data.get('title', '')
            content = content_data.get('content', '')
            
            # 文本长度特征
            text_features['title_length'] = len(title)
            text_features['content_length'] = len(content)
            text_features['word_count'] = len(content.split())
            
            # 标题质量特征
            text_features['title_word_count'] = len(title.split())
            text_features['has_question_mark'] = 1.0 if '?' in title else 0.0
            text_features['has_exclamation'] = 1.0 if '!' in title else 0.0
            
            # 内容质量特征
            sentences = content.split('.')
            text_features['sentence_count'] = len(sentences)
            text_features['avg_sentence_length'] = np.mean([len(s.split()) for s in sentences if s.strip()])
            
            return text_features
            
        except Exception as e:
            logger.error(f"提取文本特征失败: {e}")
            return {}
    
    async def _calculate_quality_score(self, content_data: Dict) -> float:
        """计算内容质量分数"""
        try:
            score = 0.0
            
            # 基于内容长度
            content_length = len(content_data.get('content', ''))
            if content_length > 1000:
                score += 2.0
            elif content_length > 500:
                score += 1.0
            
            # 基于标签数量
            tags_count = len(content_data.get('tags', []))
            score += min(tags_count * 0.5, 2.0)
            
            # 基于标题质量
            title = content_data.get('title', '')
            if len(title.split()) >= 5:
                score += 1.0
            
            return min(score, 10.0)  # 归一化到0-10
            
        except Exception as e:
            logger.error(f"计算质量分数失败: {e}")
            return 0.0
    
    async def _calculate_popularity_score(self, content_id: str) -> float:
        """计算热度分数"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 查询最近7天的交互数据
            query = """
            SELECT 
                COUNT(*) as total_interactions,
                SUM(CASE WHEN action_type = 'view' THEN 1 ELSE 0 END) as views,
                SUM(CASE WHEN action_type = 'like' THEN 1 ELSE 0 END) as likes,
                SUM(CASE WHEN action_type = 'share' THEN 1 ELSE 0 END) as shares
            FROM user_behaviors 
            WHERE content_id = %(content_id)s 
                AND timestamp >= %(start_time)s
            """
            
            start_time = datetime.now() - timedelta(days=7)
            result = clickhouse_client.execute(
                query, 
                {'content_id': int(content_id), 'start_time': start_time}
            )
            
            if result and result[0]:
                total_interactions, views, likes, shares = result[0]
                
                # 加权计算热度分数
                score = (views * 1.0 + likes * 3.0 + shares * 5.0) / 100.0
                return min(score, 10.0)
            
            return 0.0
            
        except Exception as e:
            logger.error(f"计算热度分数失败 content_id={content_id}: {e}")
            return 0.0
    
    async def _generate_embedding_vector(self, features: ContentFeatures) -> List[float]:
        """生成内容嵌入向量"""
        try:
            # 简化的嵌入向量生成
            vector = [0.0] * 128
            
            # 基于内容类型
            type_mapping = {
                ContentType.ARTICLE: [1.0, 0.0, 0.0],
                ContentType.VIDEO: [0.0, 1.0, 0.0],
                ContentType.PRODUCT: [0.0, 0.0, 1.0]
            }
            type_vec = type_mapping.get(features.content_type, [0.0, 0.0, 0.0])
            vector[:3] = type_vec
            
            # 基于质量和热度分数
            vector[3] = features.quality_score / 10.0
            vector[4] = features.popularity_score / 10.0
            
            # 基于标签（简化）
            for i, tag in enumerate(features.tags[:10]):
                if i < 10:
                    vector[5 + i] = 1.0
            
            # 基于文本特征
            if features.text_features:
                vector[15] = min(features.text_features.get('word_count', 0) / 1000.0, 1.0)
                vector[16] = features.text_features.get('has_question_mark', 0.0)
                vector[17] = features.text_features.get('has_exclamation', 0.0)
            
            return vector
            
        except Exception as e:
            logger.error(f"生成嵌入向量失败: {e}")
            return [0.0] * 128
    
    async def _cache_content_features(self, content_id: str, features: ContentFeatures):
        """缓存内容特征到Redis"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{content_id}"
            
            # 序列化特征数据
            feature_data = features.model_dump(mode='json')
            
            # 设置缓存
            await redis_client.setex(
                cache_key,
                settings.CONTENT_FEATURE_EXPIRE,
                json.dumps(feature_data, default=str)
            )
            
        except Exception as e:
            logger.error(f"缓存内容特征失败 content_id={content_id}: {e}")
    
    async def _should_update_features(self, content_id: str) -> bool:
        """判断是否需要更新特征"""
        try:
            redis_client = await get_redis()
            cache_key = f"{self.redis_key_prefix}{content_id}"
            
            # 检查缓存是否存在
            exists = await redis_client.exists(cache_key)
            if not exists:
                return True
            
            # 检查更新时间
            cached_data = await redis_client.get(cache_key)
            if cached_data:
                feature_data = json.loads(cached_data)
                updated_at = datetime.fromisoformat(feature_data.get('updated_at', ''))
                if datetime.now() - updated_at > timedelta(hours=2):
                    return True
            
            return False
            
        except Exception as e:
            logger.error(f"检查特征更新状态失败 content_id={content_id}: {e}")
            return True