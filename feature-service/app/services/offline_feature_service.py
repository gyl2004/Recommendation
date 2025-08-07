"""
离线特征计算服务
负责定时计算和更新离线特征
"""
import asyncio
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta
import schedule
import threading
from loguru import logger

from ..core.config import settings
from ..models.schemas import UserFeatures, ContentFeatures
from .clickhouse_service import ClickHouseService
from .feature_storage_service import FeatureStorageService
from .user_feature_service import UserFeatureService
from .content_feature_service import ContentFeatureService

class OfflineFeatureService:
    """离线特征计算服务"""
    
    def __init__(self):
        self.clickhouse_service = ClickHouseService()
        self.storage_service = FeatureStorageService()
        self.user_feature_service = UserFeatureService()
        self.content_feature_service = ContentFeatureService()
        
        self.is_running = False
        self.scheduler_thread = None
        
        # 调度配置
        self.schedule_config = {
            'user_features_daily': '02:00',      # 每日2点计算用户特征
            'content_features_hourly': ':00',    # 每小时计算内容特征
            'interaction_matrix_daily': '03:00', # 每日3点计算交互矩阵
            'trending_contents_hourly': ':30',   # 每小时30分计算趋势内容
            'cleanup_weekly': 'sunday'           # 每周日清理过期数据
        }
    
    def start_scheduler(self):
        """启动调度器"""
        if self.is_running:
            logger.warning("调度器已在运行中")
            return
        
        self.is_running = True
        
        # 配置调度任务
        self._setup_schedules()
        
        # 启动调度线程
        self.scheduler_thread = threading.Thread(target=self._run_scheduler, daemon=True)
        self.scheduler_thread.start()
        
        logger.info("离线特征计算调度器已启动")
    
    def stop_scheduler(self):
        """停止调度器"""
        self.is_running = False
        if self.scheduler_thread:
            self.scheduler_thread.join(timeout=5)
        
        schedule.clear()
        logger.info("离线特征计算调度器已停止")
    
    def _setup_schedules(self):
        """设置调度任务"""
        # 每日用户特征计算
        schedule.every().day.at(self.schedule_config['user_features_daily']).do(
            self._schedule_user_features_computation
        )
        
        # 每小时内容特征计算
        schedule.every().hour.at(self.schedule_config['content_features_hourly']).do(
            self._schedule_content_features_computation
        )
        
        # 每日交互矩阵计算
        schedule.every().day.at(self.schedule_config['interaction_matrix_daily']).do(
            self._schedule_interaction_matrix_computation
        )
        
        # 每小时趋势内容计算
        schedule.every().hour.at(self.schedule_config['trending_contents_hourly']).do(
            self._schedule_trending_contents_computation
        )
        
        # 每周数据清理
        schedule.every().week.do(self._schedule_data_cleanup)
        
        logger.info("调度任务配置完成")
    
    def _run_scheduler(self):
        """运行调度器"""
        while self.is_running:
            try:
                schedule.run_pending()
                threading.Event().wait(60)  # 每分钟检查一次
            except Exception as e:
                logger.error(f"调度器运行异常: {e}")
    
    def _schedule_user_features_computation(self):
        """调度用户特征计算"""
        try:
            logger.info("开始调度用户特征计算")
            asyncio.create_task(self.compute_all_user_features())
        except Exception as e:
            logger.error(f"调度用户特征计算失败: {e}")
    
    def _schedule_content_features_computation(self):
        """调度内容特征计算"""
        try:
            logger.info("开始调度内容特征计算")
            asyncio.create_task(self.compute_all_content_features())
        except Exception as e:
            logger.error(f"调度内容特征计算失败: {e}")
    
    def _schedule_interaction_matrix_computation(self):
        """调度交互矩阵计算"""
        try:
            logger.info("开始调度交互矩阵计算")
            asyncio.create_task(self.compute_interaction_matrix())
        except Exception as e:
            logger.error(f"调度交互矩阵计算失败: {e}")
    
    def _schedule_trending_contents_computation(self):
        """调度趋势内容计算"""
        try:
            logger.info("开始调度趋势内容计算")
            asyncio.create_task(self.compute_trending_contents())
        except Exception as e:
            logger.error(f"调度趋势内容计算失败: {e}")
    
    def _schedule_data_cleanup(self):
        """调度数据清理"""
        try:
            logger.info("开始调度数据清理")
            asyncio.create_task(self.cleanup_expired_data())
        except Exception as e:
            logger.error(f"调度数据清理失败: {e}")
    
    async def compute_all_user_features(self) -> Dict[str, Any]:
        """计算所有用户特征"""
        try:
            start_time = datetime.now()
            logger.info("开始计算所有用户特征")
            
            # 从ClickHouse计算离线特征
            offline_features = await self.clickhouse_service.compute_user_offline_features()
            
            if not offline_features:
                logger.warning("没有用户离线特征数据")
                return {'success': False, 'message': '没有用户离线特征数据'}
            
            # 转换为UserFeatures对象并更新
            updated_count = 0
            error_count = 0
            
            for user_id, feature_data in offline_features.items():
                try:
                    # 获取现有特征或创建新特征
                    existing_features = await self.user_feature_service.get_user_features(user_id)
                    if not existing_features:
                        existing_features = await self.user_feature_service._create_default_features(user_id)
                    
                    # 更新特征数据
                    existing_features.behavior_score = feature_data['behavior_score']
                    existing_features.last_active = feature_data['last_active_time']
                    existing_features.updated_at = datetime.now()
                    
                    # 根据行为数据更新活跃度
                    if feature_data['daily_avg_actions'] > 10:
                        existing_features.activity_level = "high"
                    elif feature_data['daily_avg_actions'] > 3:
                        existing_features.activity_level = "medium"
                    else:
                        existing_features.activity_level = "low"
                    
                    # 更新内容类型偏好
                    content_preferences = []
                    if feature_data['article_interactions'] > 0:
                        content_preferences.append('article')
                    if feature_data['video_interactions'] > 0:
                        content_preferences.append('video')
                    if feature_data['product_interactions'] > 0:
                        content_preferences.append('product')
                    existing_features.preferred_content_types = content_preferences
                    
                    # 缓存更新后的特征
                    await self.user_feature_service._cache_user_features(user_id, existing_features)
                    updated_count += 1
                    
                except Exception as e:
                    logger.error(f"更新用户特征失败 user_id={user_id}: {e}")
                    error_count += 1
            
            end_time = datetime.now()
            processing_time = (end_time - start_time).total_seconds()
            
            result = {
                'success': True,
                'updated_count': updated_count,
                'error_count': error_count,
                'processing_time': processing_time,
                'message': f'用户特征计算完成，更新: {updated_count}, 错误: {error_count}'
            }
            
            logger.info(f"用户特征计算完成: {result['message']}, 耗时: {processing_time:.2f}秒")
            return result
            
        except Exception as e:
            logger.error(f"计算所有用户特征失败: {e}")
            return {'success': False, 'message': f'计算失败: {str(e)}'}
    
    async def compute_all_content_features(self) -> Dict[str, Any]:
        """计算所有内容特征"""
        try:
            start_time = datetime.now()
            logger.info("开始计算所有内容特征")
            
            # 从ClickHouse计算离线特征
            offline_features = await self.clickhouse_service.compute_content_offline_features()
            
            if not offline_features:
                logger.warning("没有内容离线特征数据")
                return {'success': False, 'message': '没有内容离线特征数据'}
            
            # 转换为ContentFeatures对象并更新
            updated_count = 0
            error_count = 0
            
            for content_id, feature_data in offline_features.items():
                try:
                    # 获取现有特征或创建新特征
                    existing_features = await self.content_feature_service.get_content_features(content_id)
                    if not existing_features:
                        # 创建新的内容特征
                        existing_features = ContentFeatures(
                            content_id=content_id,
                            content_type=feature_data['content_type'],
                            title=f"Content {content_id}",  # 简化处理
                            quality_score=0.0,
                            popularity_score=0.0
                        )
                    
                    # 更新特征数据
                    existing_features.popularity_score = feature_data['popularity_score']
                    existing_features.quality_score = feature_data['engagement_rate'] * 10  # 简化计算
                    existing_features.updated_at = datetime.now()
                    
                    # 缓存更新后的特征
                    await self.content_feature_service._cache_content_features(content_id, existing_features)
                    updated_count += 1
                    
                except Exception as e:
                    logger.error(f"更新内容特征失败 content_id={content_id}: {e}")
                    error_count += 1
            
            end_time = datetime.now()
            processing_time = (end_time - start_time).total_seconds()
            
            result = {
                'success': True,
                'updated_count': updated_count,
                'error_count': error_count,
                'processing_time': processing_time,
                'message': f'内容特征计算完成，更新: {updated_count}, 错误: {error_count}'
            }
            
            logger.info(f"内容特征计算完成: {result['message']}, 耗时: {processing_time:.2f}秒")
            return result
            
        except Exception as e:
            logger.error(f"计算所有内容特征失败: {e}")
            return {'success': False, 'message': f'计算失败: {str(e)}'}
    
    async def compute_interaction_matrix(self) -> Dict[str, Any]:
        """计算用户-内容交互矩阵"""
        try:
            start_time = datetime.now()
            logger.info("开始计算用户-内容交互矩阵")
            
            # 计算交互矩阵
            interaction_matrix = await self.clickhouse_service.compute_user_content_interaction_matrix()
            
            if interaction_matrix.empty:
                logger.warning("交互矩阵为空")
                return {'success': False, 'message': '交互矩阵为空'}
            
            # 将交互矩阵存储到ClickHouse
            user_vectors = {}
            content_vectors = {}
            
            # 提取用户向量（每个用户对所有内容的交互分数）
            for user_id in interaction_matrix.index:
                user_vectors[user_id] = interaction_matrix.loc[user_id].values.tolist()
            
            # 提取内容向量（每个内容被所有用户的交互分数）
            for content_id in interaction_matrix.columns:
                content_vectors[content_id] = interaction_matrix[content_id].values.tolist()
            
            # 存储向量到ClickHouse
            await self.storage_service.store_feature_vectors_to_clickhouse(user_vectors, content_vectors)
            
            end_time = datetime.now()
            processing_time = (end_time - start_time).total_seconds()
            
            result = {
                'success': True,
                'user_count': len(user_vectors),
                'content_count': len(content_vectors),
                'matrix_shape': interaction_matrix.shape,
                'processing_time': processing_time,
                'message': f'交互矩阵计算完成，用户: {len(user_vectors)}, 内容: {len(content_vectors)}'
            }
            
            logger.info(f"交互矩阵计算完成: {result['message']}, 耗时: {processing_time:.2f}秒")
            return result
            
        except Exception as e:
            logger.error(f"计算交互矩阵失败: {e}")
            return {'success': False, 'message': f'计算失败: {str(e)}'}
    
    async def compute_trending_contents(self) -> Dict[str, Any]:
        """计算趋势内容"""
        try:
            start_time = datetime.now()
            logger.info("开始计算趋势内容")
            
            # 获取各类型的趋势内容
            all_trending = await self.clickhouse_service.get_trending_contents(limit=200)
            article_trending = await self.clickhouse_service.get_trending_contents('article', 100)
            video_trending = await self.clickhouse_service.get_trending_contents('video', 100)
            product_trending = await self.clickhouse_service.get_trending_contents('product', 100)
            
            # 缓存趋势内容到Redis
            from ..core.database import get_redis
            import json
            
            redis_client = await get_redis()
            
            # 缓存全部趋势内容
            await redis_client.setex(
                "trending:all",
                3600,  # 1小时过期
                json.dumps(all_trending, default=str)
            )
            
            # 缓存分类趋势内容
            for content_type, trending_list in [
                ('article', article_trending),
                ('video', video_trending),
                ('product', product_trending)
            ]:
                await redis_client.setex(
                    f"trending:{content_type}",
                    3600,
                    json.dumps(trending_list, default=str)
                )
            
            end_time = datetime.now()
            processing_time = (end_time - start_time).total_seconds()
            
            result = {
                'success': True,
                'all_trending_count': len(all_trending),
                'article_trending_count': len(article_trending),
                'video_trending_count': len(video_trending),
                'product_trending_count': len(product_trending),
                'processing_time': processing_time,
                'message': f'趋势内容计算完成，总数: {len(all_trending)}'
            }
            
            logger.info(f"趋势内容计算完成: {result['message']}, 耗时: {processing_time:.2f}秒")
            return result
            
        except Exception as e:
            logger.error(f"计算趋势内容失败: {e}")
            return {'success': False, 'message': f'计算失败: {str(e)}'}
    
    async def cleanup_expired_data(self) -> Dict[str, Any]:
        """清理过期数据"""
        try:
            start_time = datetime.now()
            logger.info("开始清理过期数据")
            
            from ..core.database import get_clickhouse
            clickhouse_client = get_clickhouse()
            
            # 清理过期的用户行为数据（保留90天）
            cleanup_date = datetime.now() - timedelta(days=90)
            
            behavior_cleanup_query = f"""
            ALTER TABLE user_behaviors DELETE 
            WHERE timestamp < '{cleanup_date.strftime('%Y-%m-%d %H:%M:%S')}'
            """
            
            # 清理过期的特征向量数据（保留30天）
            vector_cleanup_date = datetime.now() - timedelta(days=30)
            vector_cleanup_query = f"""
            ALTER TABLE feature_vectors DELETE 
            WHERE created_at < '{vector_cleanup_date.strftime('%Y-%m-%d %H:%M:%S')}'
            """
            
            # 清理过期的特征备份数据（保留7天）
            backup_cleanup_date = datetime.now() - timedelta(days=7)
            backup_cleanup_query = f"""
            ALTER TABLE feature_backups DELETE 
            WHERE backup_time < '{backup_cleanup_date.strftime('%Y-%m-%d %H:%M:%S')}'
            """
            
            # 执行清理操作
            try:
                clickhouse_client.execute(behavior_cleanup_query)
                logger.info("用户行为数据清理完成")
            except Exception as e:
                logger.error(f"用户行为数据清理失败: {e}")
            
            try:
                clickhouse_client.execute(vector_cleanup_query)
                logger.info("特征向量数据清理完成")
            except Exception as e:
                logger.error(f"特征向量数据清理失败: {e}")
            
            try:
                clickhouse_client.execute(backup_cleanup_query)
                logger.info("特征备份数据清理完成")
            except Exception as e:
                logger.error(f"特征备份数据清理失败: {e}")
            
            # 优化表性能
            await self.clickhouse_service.optimize_table_performance()
            
            # 清理Redis过期特征
            await self.storage_service.cleanup_expired_features()
            
            end_time = datetime.now()
            processing_time = (end_time - start_time).total_seconds()
            
            result = {
                'success': True,
                'processing_time': processing_time,
                'message': '过期数据清理完成'
            }
            
            logger.info(f"过期数据清理完成，耗时: {processing_time:.2f}秒")
            return result
            
        except Exception as e:
            logger.error(f"清理过期数据失败: {e}")
            return {'success': False, 'message': f'清理失败: {str(e)}'}
    
    async def get_computation_status(self) -> Dict[str, Any]:
        """获取计算状态"""
        try:
            # 获取数据库统计信息
            db_stats = await self.clickhouse_service.get_database_statistics()
            
            # 获取缓存统计信息
            cache_stats = await self.storage_service.get_cache_statistics()
            
            status = {
                'scheduler_running': self.is_running,
                'database_stats': db_stats,
                'cache_stats': cache_stats,
                'schedule_config': self.schedule_config,
                'next_runs': self._get_next_scheduled_runs(),
                'status_time': datetime.now().isoformat()
            }
            
            return status
            
        except Exception as e:
            logger.error(f"获取计算状态失败: {e}")
            return {}
    
    def _get_next_scheduled_runs(self) -> Dict[str, str]:
        """获取下次调度运行时间"""
        try:
            next_runs = {}
            for job in schedule.jobs:
                next_runs[str(job.job_func)] = str(job.next_run)
            return next_runs
        except Exception as e:
            logger.error(f"获取调度时间失败: {e}")
            return {}