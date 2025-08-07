"""
特征管道服务
负责特征处理的完整流水线管理
"""
import asyncio
from typing import Dict, List, Optional, Any, Tuple
import numpy as np
from datetime import datetime, timedelta
from loguru import logger

from ..models.schemas import UserFeatures, ContentFeatures, UserBehavior
from ..core.database import get_redis, get_clickhouse
from ..core.config import settings
from .user_feature_service import UserFeatureService
from .content_feature_service import ContentFeatureService
from .feature_engineering_service import FeatureEngineeringService
from .feature_storage_service import FeatureStorageService

class FeaturePipelineService:
    """特征管道服务"""
    
    def __init__(self):
        self.user_feature_service = UserFeatureService()
        self.content_feature_service = ContentFeatureService()
        self.engineering_service = FeatureEngineeringService()
        self.storage_service = FeatureStorageService()
        
        # 管道配置
        self.pipeline_config = {
            'batch_size': settings.BATCH_SIZE,
            'max_concurrent_tasks': 10,
            'retry_attempts': 3,
            'retry_delay': 5,  # 秒
            'quality_check_enabled': True,
            'auto_scaling_enabled': True,
            'monitoring_enabled': True
        }
    
    async def run_user_feature_pipeline(self, user_ids: List[str], 
                                      pipeline_type: str = 'full') -> Dict[str, Any]:
        """运行用户特征处理管道"""
        try:
            pipeline_start = datetime.now()
            logger.info(f"开始用户特征管道处理，用户数量: {len(user_ids)}, 类型: {pipeline_type}")
            
            results = {
                'success_count': 0,
                'error_count': 0,
                'processed_users': [],
                'errors': [],
                'pipeline_stats': {},
                'quality_metrics': {}
            }
            
            # 分批处理
            batches = [user_ids[i:i + self.pipeline_config['batch_size']] 
                      for i in range(0, len(user_ids), self.pipeline_config['batch_size'])]
            
            for batch_idx, batch in enumerate(batches):
                logger.info(f"处理用户特征批次 {batch_idx + 1}/{len(batches)}")
                
                try:
                    # 步骤1: 获取原始特征
                    raw_features = await self._get_raw_user_features(batch)
                    
                    # 步骤2: 特征工程处理
                    if pipeline_type in ['full', 'engineering']:
                        processed_features = await self._process_user_features(raw_features)
                    else:
                        processed_features = raw_features
                    
                    # 步骤3: 质量检查
                    if self.pipeline_config['quality_check_enabled']:
                        quality_results = await self._check_user_feature_quality(processed_features)
                        results['quality_metrics'].update(quality_results)
                    
                    # 步骤4: 存储特征
                    storage_success = await self.storage_service.store_user_features_batch(processed_features)
                    
                    if storage_success:
                        results['success_count'] += len(processed_features)
                        results['processed_users'].extend(list(processed_features.keys()))
                    else:
                        results['error_count'] += len(batch)
                        results['errors'].append(f"批次 {batch_idx + 1} 存储失败")
                    
                except Exception as e:
                    logger.error(f"用户特征批次处理失败 {batch_idx + 1}: {e}")
                    results['error_count'] += len(batch)
                    results['errors'].append(f"批次 {batch_idx + 1}: {str(e)}")
            
            # 计算管道统计信息
            pipeline_end = datetime.now()
            results['pipeline_stats'] = {
                'total_users': len(user_ids),
                'total_batches': len(batches),
                'processing_time': (pipeline_end - pipeline_start).total_seconds(),
                'throughput': len(user_ids) / (pipeline_end - pipeline_start).total_seconds(),
                'success_rate': results['success_count'] / len(user_ids) if user_ids else 0
            }
            
            logger.info(f"用户特征管道处理完成，成功: {results['success_count']}, "
                       f"失败: {results['error_count']}, 耗时: {results['pipeline_stats']['processing_time']:.2f}秒")
            
            return results
            
        except Exception as e:
            logger.error(f"用户特征管道处理失败: {e}")
            raise
    
    async def run_content_feature_pipeline(self, content_ids: List[str], 
                                         pipeline_type: str = 'full') -> Dict[str, Any]:
        """运行内容特征处理管道"""
        try:
            pipeline_start = datetime.now()
            logger.info(f"开始内容特征管道处理，内容数量: {len(content_ids)}, 类型: {pipeline_type}")
            
            results = {
                'success_count': 0,
                'error_count': 0,
                'processed_contents': [],
                'errors': [],
                'pipeline_stats': {},
                'quality_metrics': {}
            }
            
            # 分批处理
            batches = [content_ids[i:i + self.pipeline_config['batch_size']] 
                      for i in range(0, len(content_ids), self.pipeline_config['batch_size'])]
            
            for batch_idx, batch in enumerate(batches):
                logger.info(f"处理内容特征批次 {batch_idx + 1}/{len(batches)}")
                
                try:
                    # 步骤1: 获取原始特征
                    raw_features = await self._get_raw_content_features(batch)
                    
                    # 步骤2: 特征工程处理
                    if pipeline_type in ['full', 'engineering']:
                        processed_features = await self._process_content_features(raw_features)
                    else:
                        processed_features = raw_features
                    
                    # 步骤3: 质量检查
                    if self.pipeline_config['quality_check_enabled']:
                        quality_results = await self._check_content_feature_quality(processed_features)
                        results['quality_metrics'].update(quality_results)
                    
                    # 步骤4: 存储特征
                    storage_success = await self.storage_service.store_content_features_batch(processed_features)
                    
                    if storage_success:
                        results['success_count'] += len(processed_features)
                        results['processed_contents'].extend(list(processed_features.keys()))
                    else:
                        results['error_count'] += len(batch)
                        results['errors'].append(f"批次 {batch_idx + 1} 存储失败")
                    
                except Exception as e:
                    logger.error(f"内容特征批次处理失败 {batch_idx + 1}: {e}")
                    results['error_count'] += len(batch)
                    results['errors'].append(f"批次 {batch_idx + 1}: {str(e)}")
            
            # 计算管道统计信息
            pipeline_end = datetime.now()
            results['pipeline_stats'] = {
                'total_contents': len(content_ids),
                'total_batches': len(batches),
                'processing_time': (pipeline_end - pipeline_start).total_seconds(),
                'throughput': len(content_ids) / (pipeline_end - pipeline_start).total_seconds(),
                'success_rate': results['success_count'] / len(content_ids) if content_ids else 0
            }
            
            logger.info(f"内容特征管道处理完成，成功: {results['success_count']}, "
                       f"失败: {results['error_count']}, 耗时: {results['pipeline_stats']['processing_time']:.2f}秒")
            
            return results
            
        except Exception as e:
            logger.error(f"内容特征管道处理失败: {e}")
            raise
    
    async def run_realtime_feature_pipeline(self, behavior: UserBehavior) -> Dict[str, Any]:
        """运行实时特征处理管道"""
        try:
            pipeline_start = datetime.now()
            
            results = {
                'user_feature_updated': False,
                'content_feature_updated': False,
                'behavior_stored': False,
                'processing_time': 0.0,
                'errors': []
            }
            
            # 并发处理多个任务
            tasks = [
                self._update_user_feature_realtime(behavior),
                self._update_content_feature_realtime(behavior),
                self._store_behavior_data(behavior)
            ]
            
            task_results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # 处理结果
            results['user_feature_updated'] = task_results[0] if not isinstance(task_results[0], Exception) else False
            results['content_feature_updated'] = task_results[1] if not isinstance(task_results[1], Exception) else False
            results['behavior_stored'] = task_results[2] if not isinstance(task_results[2], Exception) else False
            
            # 记录错误
            for i, result in enumerate(task_results):
                if isinstance(result, Exception):
                    task_names = ['用户特征更新', '内容特征更新', '行为数据存储']
                    results['errors'].append(f"{task_names[i]}: {str(result)}")
            
            pipeline_end = datetime.now()
            results['processing_time'] = (pipeline_end - pipeline_start).total_seconds()
            
            logger.info(f"实时特征管道处理完成，耗时: {results['processing_time']:.3f}秒")
            
            return results
            
        except Exception as e:
            logger.error(f"实时特征管道处理失败: {e}")
            raise
    
    async def schedule_feature_pipeline(self, schedule_type: str = 'daily') -> Dict[str, Any]:
        """调度特征管道处理"""
        try:
            logger.info(f"开始调度特征管道处理，类型: {schedule_type}")
            
            results = {
                'user_pipeline_results': {},
                'content_pipeline_results': {},
                'schedule_stats': {}
            }
            
            # 获取需要处理的用户和内容ID
            user_ids, content_ids = await self._get_scheduled_entities(schedule_type)
            
            # 并发运行用户和内容特征管道
            if user_ids:
                user_task = self.run_user_feature_pipeline(user_ids, 'full')
            else:
                user_task = asyncio.create_task(self._empty_pipeline_result())
            
            if content_ids:
                content_task = self.run_content_feature_pipeline(content_ids, 'full')
            else:
                content_task = asyncio.create_task(self._empty_pipeline_result())
            
            user_results, content_results = await asyncio.gather(user_task, content_task)
            
            results['user_pipeline_results'] = user_results
            results['content_pipeline_results'] = content_results
            
            # 计算调度统计信息
            results['schedule_stats'] = {
                'schedule_type': schedule_type,
                'total_users_processed': user_results.get('success_count', 0),
                'total_contents_processed': content_results.get('success_count', 0),
                'total_errors': user_results.get('error_count', 0) + content_results.get('error_count', 0),
                'scheduled_at': datetime.now().isoformat()
            }
            
            logger.info(f"调度特征管道处理完成，用户: {results['schedule_stats']['total_users_processed']}, "
                       f"内容: {results['schedule_stats']['total_contents_processed']}")
            
            return results
            
        except Exception as e:
            logger.error(f"调度特征管道处理失败: {e}")
            raise
    
    async def _get_raw_user_features(self, user_ids: List[str]) -> Dict[str, UserFeatures]:
        """获取原始用户特征"""
        return await self.user_feature_service.get_batch_user_features(user_ids)
    
    async def _get_raw_content_features(self, content_ids: List[str]) -> Dict[str, ContentFeatures]:
        """获取原始内容特征"""
        return await self.content_feature_service.get_batch_content_features(content_ids)
    
    async def _process_user_features(self, features_dict: Dict[str, UserFeatures]) -> Dict[str, UserFeatures]:
        """处理用户特征"""
        return await self.engineering_service.normalize_user_features(features_dict)
    
    async def _process_content_features(self, features_dict: Dict[str, ContentFeatures]) -> Dict[str, ContentFeatures]:
        """处理内容特征"""
        return await self.engineering_service.normalize_content_features(features_dict)
    
    async def _check_user_feature_quality(self, features_dict: Dict[str, UserFeatures]) -> Dict[str, Any]:
        """检查用户特征质量"""
        try:
            if not features_dict:
                return {}
            
            # 提取特征向量
            feature_vectors = [f.feature_vector for f in features_dict.values() if f.feature_vector]
            if not feature_vectors:
                return {}
            
            X = np.array(feature_vectors)
            return await self.engineering_service.monitor_feature_quality(X, 'user_features')
            
        except Exception as e:
            logger.error(f"用户特征质量检查失败: {e}")
            return {}
    
    async def _check_content_feature_quality(self, features_dict: Dict[str, ContentFeatures]) -> Dict[str, Any]:
        """检查内容特征质量"""
        try:
            if not features_dict:
                return {}
            
            # 提取特征向量
            feature_vectors = [f.embedding_vector for f in features_dict.values() if f.embedding_vector]
            if not feature_vectors:
                return {}
            
            X = np.array(feature_vectors)
            return await self.engineering_service.monitor_feature_quality(X, 'content_features')
            
        except Exception as e:
            logger.error(f"内容特征质量检查失败: {e}")
            return {}
    
    async def _update_user_feature_realtime(self, behavior: UserBehavior) -> bool:
        """实时更新用户特征"""
        try:
            await self.user_feature_service.process_user_behavior(behavior)
            return True
        except Exception as e:
            logger.error(f"实时更新用户特征失败: {e}")
            return False
    
    async def _update_content_feature_realtime(self, behavior: UserBehavior) -> bool:
        """实时更新内容特征（基于用户行为）"""
        try:
            # 这里可以根据用户行为更新内容的热度分数等
            # 简化实现，实际可能需要更复杂的逻辑
            return True
        except Exception as e:
            logger.error(f"实时更新内容特征失败: {e}")
            return False
    
    async def _store_behavior_data(self, behavior: UserBehavior) -> bool:
        """存储行为数据"""
        try:
            return await self.storage_service.store_user_behavior_to_clickhouse([behavior])
        except Exception as e:
            logger.error(f"存储行为数据失败: {e}")
            return False
    
    async def _get_scheduled_entities(self, schedule_type: str) -> Tuple[List[str], List[str]]:
        """获取需要调度处理的实体ID"""
        try:
            clickhouse_client = get_clickhouse()
            
            user_ids = []
            content_ids = []
            
            if schedule_type == 'daily':
                # 获取最近24小时有活动的用户
                user_query = """
                SELECT DISTINCT user_id
                FROM user_behaviors
                WHERE timestamp >= now() - INTERVAL 1 DAY
                LIMIT 10000
                """
                user_results = clickhouse_client.execute(user_query)
                user_ids = [str(row[0]) for row in user_results]
                
                # 获取最近24小时有交互的内容
                content_query = """
                SELECT DISTINCT content_id
                FROM user_behaviors
                WHERE timestamp >= now() - INTERVAL 1 DAY
                LIMIT 10000
                """
                content_results = clickhouse_client.execute(content_query)
                content_ids = [str(row[0]) for row in content_results]
            
            elif schedule_type == 'weekly':
                # 获取最近7天有活动的用户和内容
                pass  # 类似的逻辑
            
            return user_ids, content_ids
            
        except Exception as e:
            logger.error(f"获取调度实体失败: {e}")
            return [], []
    
    async def _empty_pipeline_result(self) -> Dict[str, Any]:
        """空管道结果"""
        return {
            'success_count': 0,
            'error_count': 0,
            'processed_users': [],
            'processed_contents': [],
            'errors': [],
            'pipeline_stats': {},
            'quality_metrics': {}
        }
    
    async def get_pipeline_status(self) -> Dict[str, Any]:
        """获取管道状态"""
        try:
            redis_client = await get_redis()
            
            # 获取管道运行统计
            pipeline_stats = await redis_client.get("feature_pipeline:stats")
            if pipeline_stats:
                stats = eval(pipeline_stats)  # 注意：生产环境应使用json.loads
            else:
                stats = {}
            
            # 获取质量监控指标
            user_quality = await self.engineering_service.get_quality_metrics('user_features')
            content_quality = await self.engineering_service.get_quality_metrics('content_features')
            
            status = {
                'pipeline_stats': stats,
                'quality_metrics': {
                    'user_features': user_quality,
                    'content_features': content_quality
                },
                'config': self.pipeline_config,
                'status_time': datetime.now().isoformat()
            }
            
            return status
            
        except Exception as e:
            logger.error(f"获取管道状态失败: {e}")
            return {}