"""
排序服务测试
"""
import pytest
import asyncio
from unittest.mock import Mock, AsyncMock, patch
import json
import tempfile
import os

from app.services.ranking_service import RankingService


class TestRankingService:
    """排序服务测试类"""
    
    @pytest.fixture
    async def mock_redis(self):
        """模拟Redis客户端"""
        redis_mock = AsyncMock()
        redis_mock.ping.return_value = True
        redis_mock.get.return_value = None
        redis_mock.setex.return_value = True
        redis_mock.mget.return_value = []
        redis_mock.pipeline.return_value = AsyncMock()
        return redis_mock
    
    @pytest.fixture
    def temp_files(self):
        """创建临时文件"""
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = os.path.join(temp_dir, "test_model")
            pipeline_path = os.path.join(temp_dir, "test_pipeline.pkl")
            
            # 创建空文件
            os.makedirs(model_path, exist_ok=True)
            with open(pipeline_path, 'wb') as f:
                import pickle
                pickle.dump({'test': 'data'}, f)
            
            yield model_path, pipeline_path
    
    @pytest.fixture
    async def ranking_service(self, mock_redis, temp_files):
        """创建排序服务实例"""
        model_path, pipeline_path = temp_files
        
        service = RankingService(
            model_path=model_path,
            pipeline_path=pipeline_path,
            redis_url="redis://localhost:6379"
        )
        
        # 模拟Redis客户端
        service.redis_client = mock_redis
        
        # 模拟组件初始化
        with patch('app.services.ranking_service.FeatureStore') as mock_store, \
             patch('app.services.ranking_service.RealTimeFeatureProcessor') as mock_processor, \
             patch('app.services.ranking_service.FeaturePipeline') as mock_pipeline, \
             patch('app.services.ranking_service.WideDeepModel') as mock_model:
            
            # 设置模拟对象
            service.feature_store = mock_store.return_value
            service.feature_processor = mock_processor.return_value
            service.pipeline = mock_pipeline.return_value
            service.model = mock_model.return_value
            
            # 设置模拟方法
            service.pipeline.is_fitted = True
            service.model.predict.return_value = [[0.5]]
            
            yield service
    
    @pytest.mark.asyncio
    async def test_initialization(self, ranking_service):
        """测试服务初始化"""
        # 服务应该已经初始化
        assert ranking_service.redis_client is not None
        assert ranking_service.feature_store is not None
        assert ranking_service.feature_processor is not None
        assert ranking_service.pipeline is not None
        assert ranking_service.model is not None
    
    @pytest.mark.asyncio
    async def test_rank_candidates_empty_list(self, ranking_service):
        """测试空候选列表排序"""
        result = await ranking_service.rank_candidates("user_1", [])
        assert result == []
    
    @pytest.mark.asyncio
    async def test_rank_candidates_success(self, ranking_service):
        """测试成功排序候选内容"""
        # 模拟特征获取
        ranking_service.feature_store.get_user_features.return_value = {
            'user_age': 25.0,
            'user_gender': 'M'
        }
        ranking_service.feature_store.get_content_features.return_value = {
            'content_type': 'article',
            'content_hot_score': 0.7
        }
        
        # 模拟上下文特征处理
        ranking_service.feature_processor.process_context_features.return_value = {
            'hour': 14,
            'device_type': 0
        }
        
        # 模拟预测结果
        ranking_service.model.predict.side_effect = [[[0.8]], [[0.6]], [[0.9]]]
        
        candidates = [
            {'content_id': 'content_1', 'title': 'Title 1'},
            {'content_id': 'content_2', 'title': 'Title 2'},
            {'content_id': 'content_3', 'title': 'Title 3'}
        ]
        
        result = await ranking_service.rank_candidates("user_1", candidates)
        
        # 验证结果
        assert len(result) == 3
        assert result[0]['content_id'] == 'content_3'  # 最高分
        assert result[1]['content_id'] == 'content_1'  # 第二高分
        assert result[2]['content_id'] == 'content_2'  # 最低分
        
        # 验证得分
        assert result[0]['ranking_score'] == 0.9
        assert result[1]['ranking_score'] == 0.8
        assert result[2]['ranking_score'] == 0.6
    
    @pytest.mark.asyncio
    async def test_batch_predict_empty_list(self, ranking_service):
        """测试空请求列表批量预测"""
        result = await ranking_service.batch_predict([])
        assert result == []
    
    @pytest.mark.asyncio
    async def test_batch_predict_success(self, ranking_service):
        """测试成功批量预测"""
        # 模拟预测结果
        ranking_service.model.predict.return_value = [[0.7], [0.5], [0.8]]
        
        requests = [
            {'features': {'user_age': 25, 'content_type': 'article'}},
            {'features': {'user_age': 30, 'content_type': 'video'}},
            {'features': {'user_age': 35, 'content_type': 'product'}}
        ]
        
        result = await ranking_service.batch_predict(requests)
        
        assert len(result) == 3
        assert result == [0.7, 0.5, 0.8]
    
    @pytest.mark.asyncio
    async def test_update_user_features(self, ranking_service):
        """测试更新用户特征"""
        # 模拟特征处理
        ranking_service.feature_processor.process_user_features.return_value = {
            'processed_feature': 'value'
        }
        
        await ranking_service.update_user_features("user_1", {'raw_feature': 'value'})
        
        # 验证调用
        ranking_service.feature_processor.process_user_features.assert_called_once_with(
            {'raw_feature': 'value'}
        )
        ranking_service.feature_store.set_user_features.assert_called_once_with(
            "user_1", {'processed_feature': 'value'}
        )
    
    @pytest.mark.asyncio
    async def test_update_content_features(self, ranking_service):
        """测试更新内容特征"""
        # 模拟特征处理
        ranking_service.feature_processor.process_content_features.return_value = {
            'processed_feature': 'value'
        }
        
        await ranking_service.update_content_features("content_1", {'raw_feature': 'value'})
        
        # 验证调用
        ranking_service.feature_processor.process_content_features.assert_called_once_with(
            {'raw_feature': 'value'}
        )
        ranking_service.feature_store.set_content_features.assert_called_once_with(
            "content_1", {'processed_feature': 'value'}
        )
    
    def test_get_service_stats(self, ranking_service):
        """测试获取服务统计"""
        # 设置一些统计数据
        ranking_service.prediction_count = 10
        ranking_service.total_prediction_time = 5.0
        
        stats = ranking_service.get_service_stats()
        
        assert stats['prediction_count'] == 10
        assert stats['total_prediction_time'] == 5.0
        assert stats['avg_prediction_time'] == 0.5
        assert stats['model_loaded'] is True
        assert stats['pipeline_fitted'] is True
    
    @pytest.mark.asyncio
    async def test_health_check_healthy(self, ranking_service):
        """测试健康检查 - 健康状态"""
        # 模拟健康状态
        ranking_service.redis_client.ping.return_value = True
        
        health = await ranking_service.health_check()
        
        assert health['status'] == 'healthy'
        assert health['redis_connected'] is True
        assert health['model_loaded'] is True
        assert health['pipeline_ready'] is True
        assert 'timestamp' in health
    
    @pytest.mark.asyncio
    async def test_health_check_unhealthy(self, ranking_service):
        """测试健康检查 - 不健康状态"""
        # 模拟Redis连接失败
        ranking_service.redis_client.ping.side_effect = Exception("Redis connection failed")
        
        health = await ranking_service.health_check()
        
        assert health['status'] == 'unhealthy'
        assert 'error' in health
        assert 'timestamp' in health
    
    @pytest.mark.asyncio
    async def test_get_user_features_cached(self, ranking_service):
        """测试获取缓存的用户特征"""
        # 模拟缓存命中
        cached_features = {'user_age': 30, 'user_gender': 'F'}
        ranking_service.feature_store.get_user_features.return_value = cached_features
        
        result = await ranking_service._get_user_features("user_1")
        
        assert result == cached_features
        ranking_service.feature_store.get_user_features.assert_called_once_with("user_1")
    
    @pytest.mark.asyncio
    async def test_get_user_features_default(self, ranking_service):
        """测试获取默认用户特征"""
        # 模拟缓存未命中
        ranking_service.feature_store.get_user_features.return_value = None
        
        result = await ranking_service._get_user_features("user_1")
        
        # 验证返回默认特征
        assert 'user_age' in result
        assert 'user_gender' in result
        assert 'user_activity_score' in result
        assert 'user_interests' in result
        
        # 验证缓存默认特征
        ranking_service.feature_store.set_user_features.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_get_content_features_cached(self, ranking_service):
        """测试获取缓存的内容特征"""
        # 模拟缓存命中
        cached_features = {'content_type': 'video', 'content_hot_score': 0.8}
        ranking_service.feature_store.get_content_features.return_value = cached_features
        
        result = await ranking_service._get_content_features("content_1")
        
        assert result == cached_features
        ranking_service.feature_store.get_content_features.assert_called_once_with("content_1")
    
    @pytest.mark.asyncio
    async def test_get_content_features_default(self, ranking_service):
        """测试获取默认内容特征"""
        # 模拟缓存未命中
        ranking_service.feature_store.get_content_features.return_value = None
        
        result = await ranking_service._get_content_features("content_1")
        
        # 验证返回默认特征
        assert 'content_type' in result
        assert 'content_category' in result
        assert 'content_hot_score' in result
        assert 'content_duration' in result
        
        # 验证缓存默认特征
        ranking_service.feature_store.set_content_features.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_predict_score_success(self, ranking_service):
        """测试成功预测得分"""
        # 模拟预测结果
        ranking_service.model.predict.return_value = [[0.75]]
        
        features = {'user_age': 25, 'content_type': 'article'}
        result = await ranking_service._predict_score(features)
        
        assert result == 0.75
    
    @pytest.mark.asyncio
    async def test_predict_score_error(self, ranking_service):
        """测试预测得分错误处理"""
        # 模拟预测失败
        ranking_service.model.predict.side_effect = Exception("Prediction failed")
        
        features = {'user_age': 25, 'content_type': 'article'}
        result = await ranking_service._predict_score(features)
        
        # 应该返回默认得分
        assert result == 0.0