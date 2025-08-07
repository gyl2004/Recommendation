"""
模型服务器测试
"""
import pytest
import asyncio
import tempfile
import os
import time
from unittest.mock import Mock, patch, AsyncMock
import threading

from app.models.model_server import ModelServer, PredictionRequest, ModelManager


class TestPredictionRequest:
    """预测请求测试类"""
    
    def test_prediction_request_creation(self):
        """测试预测请求创建"""
        features = {'user_age': 25, 'content_type': 'article'}
        request = PredictionRequest(features)
        
        assert request.features == features
        assert request.result is None
        assert isinstance(request.timestamp, float)
    
    @pytest.mark.asyncio
    async def test_set_and_get_result(self):
        """测试设置和获取结果"""
        features = {'user_age': 25}
        request = PredictionRequest(features)
        
        # 在另一个任务中设置结果
        async def set_result_later():
            await asyncio.sleep(0.1)
            request.set_result(0.75)
        
        # 启动设置结果的任务
        asyncio.create_task(set_result_later())
        
        # 获取结果
        result = await request.get_result(timeout=1.0)
        assert result == 0.75
    
    @pytest.mark.asyncio
    async def test_get_result_timeout(self):
        """测试获取结果超时"""
        features = {'user_age': 25}
        request = PredictionRequest(features)
        
        # 不设置结果，直接获取（应该超时）
        result = await request.get_result(timeout=0.1)
        assert result == 0.0  # 超时返回默认值


class TestModelServer:
    """模型服务器测试类"""
    
    @pytest.fixture
    def temp_model_path(self):
        """创建临时模型路径"""
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = os.path.join(temp_dir, "test_model")
            os.makedirs(model_path, exist_ok=True)
            yield model_path
    
    @pytest.fixture
    def mock_model_server(self, temp_model_path):
        """创建模拟的模型服务器"""
        server = ModelServer(
            model_path=temp_model_path,
            max_batch_size=4,
            batch_timeout_ms=50,
            max_workers=2
        )
        
        # 模拟模型
        mock_model = Mock()
        mock_model.predict.return_value = [[0.5], [0.6], [0.7]]
        server.model = mock_model
        server.model_version = "test_v1"
        server.model_loaded_time = time.time()
        
        yield server
        
        # 清理
        server.shutdown()
    
    def test_model_server_initialization(self, temp_model_path):
        """测试模型服务器初始化"""
        server = ModelServer(
            model_path=temp_model_path,
            max_batch_size=32,
            batch_timeout_ms=20,
            max_workers=4
        )
        
        assert server.model_path == temp_model_path
        assert server.max_batch_size == 32
        assert server.batch_timeout_ms == 20
        assert server.max_workers == 4
        assert server.model is None
        assert server.total_requests == 0
        assert server.batch_processor is not None
        assert server.batch_processor.is_alive()
        
        server.shutdown()
    
    @patch('app.models.model_server.WideDeepModel')
    @patch('app.models.model_server.create_wide_deep_feature_columns')
    def test_load_model_success(self, mock_create_columns, mock_model_class, temp_model_path):
        """测试成功加载模型"""
        # 设置模拟
        mock_create_columns.return_value = ([], [])
        mock_model_instance = Mock()
        mock_model_class.return_value = mock_model_instance
        
        server = ModelServer(temp_model_path)
        
        # 加载模型
        success = server.load_model()
        
        assert success is True
        assert server.model == mock_model_instance
        assert server.model_version is not None
        assert server.model_loaded_time is not None
        
        server.shutdown()
    
    @patch('app.models.model_server.WideDeepModel')
    def test_load_model_failure(self, mock_model_class, temp_model_path):
        """测试加载模型失败"""
        # 设置模拟抛出异常
        mock_model_class.side_effect = Exception("Model load failed")
        
        server = ModelServer(temp_model_path)
        
        # 加载模型
        success = server.load_model()
        
        assert success is False
        assert server.model is None
        
        server.shutdown()
    
    def test_predict_batch_sync_success(self, mock_model_server):
        """测试同步批量预测成功"""
        features_list = [
            {'user_age': 25, 'content_type': 'article'},
            {'user_age': 30, 'content_type': 'video'},
            {'user_age': 35, 'content_type': 'product'}
        ]
        
        # 设置模拟返回值
        mock_model_server.model.predict.return_value = [[0.5], [0.6], [0.7]]
        
        scores = mock_model_server.predict_batch_sync(features_list)
        
        assert len(scores) == 3
        assert scores == [0.5, 0.6, 0.7]
        assert mock_model_server.total_predictions == 3
        assert mock_model_server.batch_count == 1
    
    def test_predict_batch_sync_no_model(self, temp_model_path):
        """测试没有模型时的批量预测"""
        server = ModelServer(temp_model_path)
        
        features_list = [{'user_age': 25}]
        
        with pytest.raises(ValueError, match="模型未加载"):
            server.predict_batch_sync(features_list)
        
        server.shutdown()
    
    def test_predict_batch_sync_error(self, mock_model_server):
        """测试批量预测错误处理"""
        features_list = [{'user_age': 25}]
        
        # 设置模拟抛出异常
        mock_model_server.model.predict.side_effect = Exception("Prediction failed")
        
        scores = mock_model_server.predict_batch_sync(features_list)
        
        # 应该返回默认分数
        assert scores == [0.0]
    
    @pytest.mark.asyncio
    async def test_predict_async(self, mock_model_server):
        """测试异步预测"""
        features = {'user_age': 25, 'content_type': 'article'}
        
        # 设置模拟返回值
        mock_model_server.model.predict.return_value = [[0.8]]
        
        # 等待一小段时间让批处理线程处理请求
        score = await mock_model_server.predict_async(features)
        
        assert isinstance(score, float)
        assert mock_model_server.total_requests >= 1
    
    def test_add_feedback(self, mock_model_server):
        """测试添加反馈"""
        mock_model_server.add_feedback(0.8, 1)
        
        # 检查在线评估器是否收到数据
        assert len(mock_model_server.online_evaluator.predictions) == 1
        assert len(mock_model_server.online_evaluator.labels) == 1
    
    def test_get_online_metrics(self, mock_model_server):
        """测试获取在线指标"""
        # 添加一些反馈数据
        for i in range(20):
            mock_model_server.add_feedback(0.5 + i * 0.02, i % 2)
        
        metrics = mock_model_server.get_online_metrics()
        
        # 应该包含在线指标
        assert isinstance(metrics, dict)
    
    def test_get_server_stats(self, mock_model_server):
        """测试获取服务器统计"""
        # 执行一些预测来生成统计数据
        features_list = [{'user_age': 25}]
        mock_model_server.predict_batch_sync(features_list)
        
        stats = mock_model_server.get_server_stats()
        
        # 检查统计信息结构
        assert 'model_info' in stats
        assert 'performance_stats' in stats
        assert 'config' in stats
        assert 'timestamp' in stats
        
        # 检查模型信息
        model_info = stats['model_info']
        assert model_info['model_loaded'] is True
        assert model_info['model_version'] == "test_v1"
        
        # 检查性能统计
        perf_stats = stats['performance_stats']
        assert perf_stats['total_predictions'] >= 1
        assert perf_stats['batch_count'] >= 1
    
    def test_health_check_healthy(self, mock_model_server):
        """测试健康检查 - 健康状态"""
        health = mock_model_server.health_check()
        
        assert health['status'] == 'healthy'
        assert health['model_loaded'] is True
        assert health['batch_processor_running'] is True
        assert 'timestamp' in health
    
    def test_health_check_unhealthy(self, temp_model_path):
        """测试健康检查 - 不健康状态"""
        server = ModelServer(temp_model_path)
        # 不加载模型
        
        health = server.health_check()
        
        assert health['status'] == 'unhealthy'
        assert health['model_loaded'] is False
        
        server.shutdown()
    
    @patch('app.models.model_server.WideDeepModel')
    @patch('app.models.model_server.create_wide_deep_feature_columns')
    def test_reload_model_success(self, mock_create_columns, mock_model_class, mock_model_server):
        """测试成功重新加载模型"""
        # 设置模拟
        mock_create_columns.return_value = ([], [])
        new_mock_model = Mock()
        mock_model_class.return_value = new_mock_model
        
        old_model = mock_model_server.model
        
        success = mock_model_server.reload_model()
        
        assert success is True
        assert mock_model_server.model == new_mock_model
        assert mock_model_server.model != old_model
    
    @patch('app.models.model_server.WideDeepModel')
    def test_reload_model_failure(self, mock_model_class, mock_model_server):
        """测试重新加载模型失败"""
        # 设置模拟抛出异常
        mock_model_class.side_effect = Exception("Reload failed")
        
        old_model = mock_model_server.model
        old_version = mock_model_server.model_version
        
        success = mock_model_server.reload_model()
        
        assert success is False
        assert mock_model_server.model == old_model  # 应该恢复到旧模型
        assert mock_model_server.model_version == old_version


class TestModelManager:
    """模型管理器测试类"""
    
    @pytest.fixture
    def temp_models_dir(self):
        """创建临时模型目录"""
        with tempfile.TemporaryDirectory() as temp_dir:
            yield temp_dir
    
    @pytest.fixture
    def model_manager(self, temp_models_dir):
        """创建模型管理器"""
        return ModelManager(temp_models_dir)
    
    def test_model_manager_initialization(self, temp_models_dir):
        """测试模型管理器初始化"""
        manager = ModelManager(temp_models_dir)
        
        assert str(manager.models_dir) == temp_models_dir
        assert manager.active_servers == {}
        assert manager.model_configs == {}
    
    def test_register_model(self, model_manager, temp_models_dir):
        """测试注册模型"""
        model_path = os.path.join(temp_models_dir, "test_model")
        config = {'max_batch_size': 32}
        
        model_manager.register_model("test_model", model_path, config)
        
        assert "test_model" in model_manager.model_configs
        model_config = model_manager.model_configs["test_model"]
        assert model_config['model_path'] == model_path
        assert model_config['config'] == config
        assert 'registered_time' in model_config
    
    @patch('app.models.model_server.ModelServer')
    def test_start_model_server_success(self, mock_server_class, model_manager, temp_models_dir):
        """测试成功启动模型服务器"""
        # 注册模型
        model_path = os.path.join(temp_models_dir, "test_model")
        model_manager.register_model("test_model", model_path)
        
        # 设置模拟
        mock_server = Mock()
        mock_server.load_model.return_value = True
        mock_server_class.return_value = mock_server
        
        success = model_manager.start_model_server("test_model")
        
        assert success is True
        assert "test_model" in model_manager.active_servers
        assert model_manager.active_servers["test_model"] == mock_server
    
    @patch('app.models.model_server.ModelServer')
    def test_start_model_server_load_failure(self, mock_server_class, model_manager, temp_models_dir):
        """测试启动模型服务器时加载失败"""
        # 注册模型
        model_path = os.path.join(temp_models_dir, "test_model")
        model_manager.register_model("test_model", model_path)
        
        # 设置模拟
        mock_server = Mock()
        mock_server.load_model.return_value = False
        mock_server_class.return_value = mock_server
        
        success = model_manager.start_model_server("test_model")
        
        assert success is False
        assert "test_model" not in model_manager.active_servers
    
    def test_start_model_server_not_registered(self, model_manager):
        """测试启动未注册的模型服务器"""
        success = model_manager.start_model_server("nonexistent_model")
        
        assert success is False
    
    def test_stop_model_server_success(self, model_manager):
        """测试成功停止模型服务器"""
        # 添加一个活跃的服务器
        mock_server = Mock()
        model_manager.active_servers["test_model"] = mock_server
        
        success = model_manager.stop_model_server("test_model")
        
        assert success is True
        assert "test_model" not in model_manager.active_servers
        mock_server.shutdown.assert_called_once()
    
    def test_stop_model_server_not_running(self, model_manager):
        """测试停止未运行的模型服务器"""
        success = model_manager.stop_model_server("nonexistent_model")
        
        assert success is True  # 应该返回True（已经停止）
    
    def test_get_model_server(self, model_manager):
        """测试获取模型服务器"""
        # 添加一个活跃的服务器
        mock_server = Mock()
        model_manager.active_servers["test_model"] = mock_server
        
        server = model_manager.get_model_server("test_model")
        assert server == mock_server
        
        # 获取不存在的服务器
        server = model_manager.get_model_server("nonexistent_model")
        assert server is None
    
    def test_list_models(self, model_manager, temp_models_dir):
        """测试列出模型"""
        # 注册一个模型
        model_path = os.path.join(temp_models_dir, "test_model")
        model_manager.register_model("test_model", model_path)
        
        # 添加一个活跃的服务器
        mock_server = Mock()
        mock_server.get_server_stats.return_value = {'test': 'stats'}
        model_manager.active_servers["test_model"] = mock_server
        
        models_info = model_manager.list_models()
        
        assert "test_model" in models_info
        model_info = models_info["test_model"]
        assert model_info['is_active'] is True
        assert model_info['server_stats'] == {'test': 'stats'}
        assert 'config' in model_info
    
    def test_health_check_all(self, model_manager):
        """测试检查所有模型的健康状态"""
        # 添加两个活跃的服务器
        mock_server1 = Mock()
        mock_server1.health_check.return_value = {'status': 'healthy'}
        model_manager.active_servers["model1"] = mock_server1
        
        mock_server2 = Mock()
        mock_server2.health_check.return_value = {'status': 'unhealthy'}
        model_manager.active_servers["model2"] = mock_server2
        
        health_status = model_manager.health_check_all()
        
        assert 'overall_status' in health_status
        assert 'model_health' in health_status
        assert 'timestamp' in health_status
        
        # 整体状态应该是不健康的（因为有一个不健康的模型）
        assert health_status['overall_status'] == 'unhealthy'
        
        # 检查各个模型的健康状态
        assert health_status['model_health']['model1']['status'] == 'healthy'
        assert health_status['model_health']['model2']['status'] == 'unhealthy'