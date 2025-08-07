"""
排序服务
提供在线推理和批量预测接口
"""
import asyncio
import numpy as np
import pandas as pd
from typing import Dict, List, Optional, Any, Tuple
from loguru import logger
import aioredis
import json
from datetime import datetime

from ..models.wide_deep_model import WideDeepModel, create_wide_deep_feature_columns
from ..features.feature_pipeline import FeaturePipeline, RealTimeFeatureProcessor, FeatureStore


class RankingService:
    """排序服务"""
    
    def __init__(self, 
                 model_path: str,
                 pipeline_path: str,
                 redis_url: str = "redis://localhost:6379"):
        """
        初始化排序服务
        
        Args:
            model_path: 模型文件路径
            pipeline_path: 特征管道路径
            redis_url: Redis连接URL
        """
        self.model_path = model_path
        self.pipeline_path = pipeline_path
        self.redis_url = redis_url
        
        # 初始化组件
        self.model = None
        self.pipeline = None
        self.feature_processor = None
        self.feature_store = None
        self.redis_client = None
        
        # 性能统计
        self.prediction_count = 0
        self.total_prediction_time = 0.0
    
    async def initialize(self):
        """初始化服务"""
        logger.info("初始化排序服务")
        
        # 初始化Redis连接
        self.redis_client = aioredis.from_url(self.redis_url)
        
        # 初始化特征存储
        self.feature_store = FeatureStore(self.redis_client)
        
        # 加载特征管道
        self.pipeline = FeaturePipeline()
        try:
            self.pipeline.load_pipeline(self.pipeline_path)
            logger.info("特征管道加载成功")
        except Exception as e:
            logger.warning(f"特征管道加载失败: {e}，将使用默认配置")
            # 如果加载失败，创建默认管道
            self.pipeline = FeaturePipeline()
        
        # 初始化特征处理器
        self.feature_processor = RealTimeFeatureProcessor(self.pipeline)
        
        # 加载模型
        try:
            wide_columns, deep_columns = create_wide_deep_feature_columns()
            self.model = WideDeepModel(wide_columns, deep_columns)
            self.model.load_model(self.model_path)
            logger.info("Wide&Deep模型加载成功")
        except Exception as e:
            logger.error(f"模型加载失败: {e}")
            # 创建默认模型
            wide_columns, deep_columns = create_wide_deep_feature_columns()
            self.model = WideDeepModel(wide_columns, deep_columns)
            logger.warning("使用默认模型配置")
        
        logger.info("排序服务初始化完成")
    
    async def rank_candidates(self, 
                            user_id: str,
                            candidates: List[Dict[str, Any]],
                            context: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """
        对候选内容进行排序
        
        Args:
            user_id: 用户ID
            candidates: 候选内容列表
            context: 上下文信息
            
        Returns:
            排序后的内容列表
        """
        start_time = datetime.now()
        
        if not candidates:
            return []
        
        try:
            # 获取用户特征
            user_features = await self._get_user_features(user_id)
            
            # 处理上下文特征
            context_features = {}
            if context:
                context_features = self.feature_processor.process_context_features(context)
            
            # 为每个候选内容计算得分
            scored_candidates = []
            for candidate in candidates:
                try:
                    # 获取内容特征
                    content_features = await self._get_content_features(candidate['content_id'])
                    
                    # 合并所有特征
                    combined_features = {
                        **user_features,
                        **content_features,
                        **context_features
                    }
                    
                    # 预测得分
                    score = await self._predict_score(combined_features)
                    
                    # 添加得分到候选内容
                    candidate_with_score = candidate.copy()
                    candidate_with_score['ranking_score'] = float(score)
                    scored_candidates.append(candidate_with_score)
                    
                except Exception as e:
                    logger.error(f"处理候选内容 {candidate.get('content_id')} 时出错: {e}")
                    # 给失败的候选内容一个默认得分
                    candidate_with_score = candidate.copy()
                    candidate_with_score['ranking_score'] = 0.0
                    scored_candidates.append(candidate_with_score)
            
            # 按得分排序
            ranked_candidates = sorted(
                scored_candidates, 
                key=lambda x: x['ranking_score'], 
                reverse=True
            )
            
            # 更新性能统计
            prediction_time = (datetime.now() - start_time).total_seconds()
            self.prediction_count += 1
            self.total_prediction_time += prediction_time
            
            logger.info(f"排序完成，处理 {len(candidates)} 个候选内容，耗时 {prediction_time:.3f}s")
            
            return ranked_candidates
            
        except Exception as e:
            logger.error(f"排序过程出错: {e}")
            # 返回原始候选内容，添加默认得分
            return [
                {**candidate, 'ranking_score': 0.0} 
                for candidate in candidates
            ]
    
    async def batch_predict(self, 
                          prediction_requests: List[Dict[str, Any]]) -> List[float]:
        """
        批量预测
        
        Args:
            prediction_requests: 预测请求列表，每个请求包含特征数据
            
        Returns:
            预测得分列表
        """
        if not prediction_requests:
            return []
        
        try:
            # 准备批量特征数据
            batch_features = []
            for request in prediction_requests:
                features = request.get('features', {})
                batch_features.append(features)
            
            # 转换为DataFrame
            features_df = pd.DataFrame(batch_features)
            
            # 应用特征管道
            if self.pipeline and self.pipeline.is_fitted:
                processed_features_df = self.pipeline.transform(features_df)
            else:
                processed_features_df = features_df
            
            # 转换为模型输入格式
            model_input = {}
            for column in processed_features_df.columns:
                model_input[column] = processed_features_df[column].values
            
            # 批量预测
            predictions = self.model.predict(model_input)
            
            # 转换为列表
            scores = predictions.flatten().tolist()
            
            logger.info(f"批量预测完成，处理 {len(prediction_requests)} 个请求")
            
            return scores
            
        except Exception as e:
            logger.error(f"批量预测出错: {e}")
            # 返回默认得分
            return [0.0] * len(prediction_requests)
    
    async def _get_user_features(self, user_id: str) -> Dict[str, Any]:
        """获取用户特征"""
        try:
            # 从特征存储获取
            cached_features = await self.feature_store.get_user_features(user_id)
            if cached_features:
                return cached_features
            
            # 如果缓存中没有，返回默认特征
            default_features = {
                'user_age': 25.0,
                'user_gender': 'Unknown',
                'user_activity_score': 0.5,
                'user_interests': 'general'
            }
            
            # 缓存默认特征
            await self.feature_store.set_user_features(user_id, default_features)
            
            return default_features
            
        except Exception as e:
            logger.error(f"获取用户特征失败: {e}")
            return {
                'user_age': 25.0,
                'user_gender': 'Unknown',
                'user_activity_score': 0.5,
                'user_interests': 'general'
            }
    
    async def _get_content_features(self, content_id: str) -> Dict[str, Any]:
        """获取内容特征"""
        try:
            # 从特征存储获取
            cached_features = await self.feature_store.get_content_features(content_id)
            if cached_features:
                return cached_features
            
            # 如果缓存中没有，返回默认特征
            default_features = {
                'content_type': 'article',
                'content_category': 'general',
                'content_hot_score': 0.5,
                'content_duration': 300.0
            }
            
            # 缓存默认特征
            await self.feature_store.set_content_features(content_id, default_features)
            
            return default_features
            
        except Exception as e:
            logger.error(f"获取内容特征失败: {e}")
            return {
                'content_type': 'article',
                'content_category': 'general',
                'content_hot_score': 0.5,
                'content_duration': 300.0
            }
    
    async def _predict_score(self, features: Dict[str, Any]) -> float:
        """预测单个样本得分"""
        try:
            # 转换为DataFrame
            features_df = pd.DataFrame([features])
            
            # 应用特征管道
            if self.pipeline and self.pipeline.is_fitted:
                processed_features_df = self.pipeline.transform(features_df)
            else:
                processed_features_df = features_df
            
            # 转换为模型输入格式
            model_input = {}
            for column in processed_features_df.columns:
                model_input[column] = processed_features_df[column].values
            
            # 预测
            prediction = self.model.predict(model_input)
            
            return float(prediction[0][0])
            
        except Exception as e:
            logger.error(f"预测得分失败: {e}")
            return 0.0
    
    async def update_user_features(self, user_id: str, features: Dict[str, Any]):
        """更新用户特征"""
        try:
            # 处理特征
            processed_features = self.feature_processor.process_user_features(features)
            
            # 存储到特征存储
            await self.feature_store.set_user_features(user_id, processed_features)
            
            logger.info(f"用户 {user_id} 特征更新成功")
            
        except Exception as e:
            logger.error(f"更新用户特征失败: {e}")
    
    async def update_content_features(self, content_id: str, features: Dict[str, Any]):
        """更新内容特征"""
        try:
            # 处理特征
            processed_features = self.feature_processor.process_content_features(features)
            
            # 存储到特征存储
            await self.feature_store.set_content_features(content_id, processed_features)
            
            logger.info(f"内容 {content_id} 特征更新成功")
            
        except Exception as e:
            logger.error(f"更新内容特征失败: {e}")
    
    def get_service_stats(self) -> Dict[str, Any]:
        """获取服务统计信息"""
        avg_prediction_time = (
            self.total_prediction_time / self.prediction_count 
            if self.prediction_count > 0 else 0.0
        )
        
        return {
            'prediction_count': self.prediction_count,
            'total_prediction_time': self.total_prediction_time,
            'avg_prediction_time': avg_prediction_time,
            'model_loaded': self.model is not None,
            'pipeline_fitted': self.pipeline.is_fitted if self.pipeline else False
        }
    
    async def health_check(self) -> Dict[str, Any]:
        """健康检查"""
        try:
            # 检查Redis连接
            redis_ok = await self.redis_client.ping()
            
            # 检查模型状态
            model_ok = self.model is not None
            
            # 检查特征管道状态
            pipeline_ok = self.pipeline is not None and self.pipeline.is_fitted
            
            return {
                'status': 'healthy' if all([redis_ok, model_ok]) else 'unhealthy',
                'redis_connected': redis_ok,
                'model_loaded': model_ok,
                'pipeline_ready': pipeline_ok,
                'timestamp': datetime.now().isoformat()
            }
            
        except Exception as e:
            logger.error(f"健康检查失败: {e}")
            return {
                'status': 'unhealthy',
                'error': str(e),
                'timestamp': datetime.now().isoformat()
            }
    
    async def close(self):
        """关闭服务"""
        if self.redis_client:
            await self.redis_client.close()
        logger.info("排序服务已关闭")