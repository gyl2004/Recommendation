"""
模型评估器测试
"""
import pytest
import numpy as np
import pandas as pd
import tempfile
import os
from unittest.mock import Mock, patch
import json

from app.models.model_evaluator import ModelEvaluator, OnlineEvaluator


class TestModelEvaluator:
    """模型评估器测试类"""
    
    @pytest.fixture
    def evaluator(self):
        """创建评估器实例"""
        return ModelEvaluator()
    
    @pytest.fixture
    def sample_binary_data(self):
        """创建二分类测试数据"""
        np.random.seed(42)
        n_samples = 1000
        
        y_true = np.random.binomial(1, 0.3, n_samples)
        y_pred_proba = np.random.beta(2, 5, n_samples)
        y_pred = (y_pred_proba > 0.5).astype(int)
        
        return y_true, y_pred, y_pred_proba
    
    def test_evaluate_binary_classification_basic(self, evaluator, sample_binary_data):
        """测试基础二分类评估"""
        y_true, y_pred, y_pred_proba = sample_binary_data
        
        metrics = evaluator.evaluate_binary_classification(y_true, y_pred, y_pred_proba)
        
        # 检查必要的指标
        required_metrics = ['accuracy', 'precision', 'recall', 'f1_score', 'auc', 'log_loss']
        for metric in required_metrics:
            assert metric in metrics
            assert isinstance(metrics[metric], float)
            assert 0 <= metrics[metric] <= 1 or metric == 'log_loss'  # log_loss可能大于1
    
    def test_evaluate_binary_classification_without_proba(self, evaluator, sample_binary_data):
        """测试不带概率的二分类评估"""
        y_true, y_pred, _ = sample_binary_data
        
        metrics = evaluator.evaluate_binary_classification(y_true, y_pred)
        
        # 检查基础指标
        basic_metrics = ['accuracy', 'precision', 'recall', 'f1_score']
        for metric in basic_metrics:
            assert metric in metrics
            assert isinstance(metrics[metric], float)
        
        # 检查概率相关指标不存在
        proba_metrics = ['auc', 'log_loss', 'pr_auc']
        for metric in proba_metrics:
            assert metric not in metrics
    
    def test_evaluate_ranking_metrics(self, evaluator):
        """测试排序指标评估"""
        # 创建排序测试数据
        y_true = np.array([3, 2, 1, 0, 1, 2])  # 相关性分数
        y_pred = np.array([0.9, 0.8, 0.6, 0.3, 0.5, 0.7])  # 预测分数
        
        metrics = evaluator.evaluate_ranking_metrics(y_true, y_pred, k_values=[1, 3, 5])
        
        # 检查NDCG指标
        assert 'ndcg@1' in metrics
        assert 'ndcg@3' in metrics
        assert 'ndcg@5' in metrics
        
        # 检查其他排序指标
        assert 'map' in metrics
        assert 'mrr' in metrics
        
        # 检查指标值范围
        for metric_name, value in metrics.items():
            assert isinstance(value, float)
            assert 0 <= value <= 1
    
    def test_calculate_ndcg(self, evaluator):
        """测试NDCG计算"""
        y_true = np.array([3, 2, 1, 0])
        y_pred = np.array([0.9, 0.8, 0.6, 0.3])  # 完美排序
        
        ndcg = evaluator._calculate_ndcg(y_true, y_pred, k=4)
        
        # 完美排序的NDCG应该为1
        assert abs(ndcg - 1.0) < 1e-6
    
    def test_calculate_map(self, evaluator):
        """测试MAP计算"""
        y_true = np.array([1, 0, 1, 0, 1])
        y_pred = np.array([0.9, 0.8, 0.7, 0.6, 0.5])  # 前3个预测最高，但只有1,3是相关的
        
        map_score = evaluator._calculate_map(y_true, y_pred)
        
        # MAP应该在0-1之间
        assert 0 <= map_score <= 1
        assert isinstance(map_score, float)
    
    def test_calculate_mrr(self, evaluator):
        """测试MRR计算"""
        y_true = np.array([0, 1, 0, 0])  # 第二个位置是相关的
        y_pred = np.array([0.9, 0.8, 0.7, 0.6])  # 第二个预测分数最高
        
        mrr = evaluator._calculate_mrr(y_true, y_pred)
        
        # 第一个相关项在第2位，所以MRR = 1/2 = 0.5
        assert abs(mrr - 0.5) < 1e-6
    
    def test_compare_models(self, evaluator):
        """测试模型比较"""
        # 创建模拟评估报告
        reports = [
            {
                'evaluation_name': 'model_v1',
                'timestamp': '2023-01-01T00:00:00',
                'metrics': {'auc': 0.8, 'f1_score': 0.7, 'accuracy': 0.75}
            },
            {
                'evaluation_name': 'model_v2',
                'timestamp': '2023-01-02T00:00:00',
                'metrics': {'auc': 0.85, 'f1_score': 0.72, 'accuracy': 0.78}
            }
        ]
        
        comparison = evaluator.compare_models(reports, primary_metric='auc')
        
        # 检查比较结果结构
        assert 'best_model' in comparison
        assert 'metric_statistics' in comparison
        assert 'model_details' in comparison
        
        # 检查最佳模型
        assert comparison['best_model']['name'] == 'model_v2'
        assert comparison['best_model']['score'] == 0.85
    
    def test_save_and_load_evaluation_report(self, evaluator):
        """测试评估报告保存和加载"""
        # 创建测试报告
        test_report = {
            'evaluation_name': 'test_model',
            'timestamp': '2023-01-01T00:00:00',
            'metrics': {'auc': 0.8, 'f1_score': 0.7}
        }
        
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
            temp_path = f.name
        
        try:
            # 保存报告
            evaluator.save_evaluation_report(test_report, temp_path)
            assert os.path.exists(temp_path)
            
            # 加载报告
            loaded_report = evaluator.load_evaluation_report(temp_path)
            
            # 验证内容
            assert loaded_report == test_report
            
        finally:
            # 清理临时文件
            if os.path.exists(temp_path):
                os.unlink(temp_path)
    
    def test_get_evaluation_summary_empty(self, evaluator):
        """测试空评估历史摘要"""
        summary = evaluator.get_evaluation_summary()
        assert 'message' in summary
        assert summary['message'] == '暂无评估历史'
    
    def test_get_evaluation_summary_with_history(self, evaluator):
        """测试有历史的评估摘要"""
        # 添加评估历史
        evaluator.evaluation_history = [
            {
                'evaluation_name': 'test1',
                'timestamp': '2023-01-01T00:00:00',
                'metrics': {'auc': 0.8}
            },
            {
                'evaluation_name': 'test2',
                'timestamp': '2023-01-02T00:00:00',
                'metrics': {'auc': 0.85}
            }
        ]
        
        summary = evaluator.get_evaluation_summary()
        
        assert summary['total_evaluations'] == 2
        assert summary['latest_evaluation'] == 'test2'
        assert summary['best_auc'] == 0.85
        assert len(summary['evaluation_names']) == 2


class TestOnlineEvaluator:
    """在线评估器测试类"""
    
    @pytest.fixture
    def online_evaluator(self):
        """创建在线评估器实例"""
        return OnlineEvaluator(window_size=100)
    
    def test_add_prediction(self, online_evaluator):
        """测试添加预测结果"""
        online_evaluator.add_prediction(0.8, 1)
        
        assert len(online_evaluator.predictions) == 1
        assert len(online_evaluator.labels) == 1
        assert len(online_evaluator.timestamps) == 1
        
        assert online_evaluator.predictions[0] == 0.8
        assert online_evaluator.labels[0] == 1
    
    def test_window_size_limit(self, online_evaluator):
        """测试窗口大小限制"""
        # 添加超过窗口大小的数据
        for i in range(150):
            online_evaluator.add_prediction(0.5, i % 2)
        
        # 检查窗口大小限制
        assert len(online_evaluator.predictions) == 100
        assert len(online_evaluator.labels) == 100
        assert len(online_evaluator.timestamps) == 100
    
    def test_get_current_metrics_insufficient_data(self, online_evaluator):
        """测试数据不足时的指标获取"""
        # 添加少量数据
        for i in range(5):
            online_evaluator.add_prediction(0.5, i % 2)
        
        metrics = online_evaluator.get_current_metrics()
        assert 'message' in metrics
        assert metrics['message'] == '样本数量不足'
    
    def test_get_current_metrics_sufficient_data(self, online_evaluator):
        """测试数据充足时的指标获取"""
        # 添加足够的数据
        np.random.seed(42)
        for i in range(50):
            prediction = np.random.random()
            label = np.random.randint(0, 2)
            online_evaluator.add_prediction(prediction, label)
        
        metrics = online_evaluator.get_current_metrics()
        
        # 检查基础指标
        basic_metrics = ['accuracy', 'precision', 'recall', 'f1_score', 'auc']
        for metric in basic_metrics:
            assert metric in metrics
            assert isinstance(metrics[metric], float)
        
        # 检查在线特有指标
        assert 'sample_count' in metrics
        assert 'positive_rate' in metrics
        assert 'avg_prediction' in metrics
        
        assert metrics['sample_count'] == 50
    
    def test_detect_drift_insufficient_data(self, online_evaluator):
        """测试数据不足时的漂移检测"""
        baseline_metrics = {'auc': 0.8, 'accuracy': 0.75}
        
        # 添加少量数据
        for i in range(5):
            online_evaluator.add_prediction(0.5, i % 2)
        
        drift_result = online_evaluator.detect_drift(baseline_metrics)
        assert 'message' in drift_result
    
    def test_detect_drift_no_drift(self, online_evaluator):
        """测试无漂移情况"""
        baseline_metrics = {'auc': 0.8, 'accuracy': 0.75}
        
        # 添加与基线相似的数据
        np.random.seed(42)
        for i in range(50):
            # 生成与基线相似的数据
            prediction = np.random.beta(2, 3)  # 偏向较低的预测值
            label = np.random.binomial(1, 0.3)  # 30%正样本率
            online_evaluator.add_prediction(prediction, label)
        
        drift_result = online_evaluator.detect_drift(baseline_metrics, threshold=0.1)
        
        assert 'drift_detected' in drift_result
        assert 'drift_details' in drift_result
        assert isinstance(drift_result['drift_detected'], bool)
    
    def test_detect_drift_with_drift(self, online_evaluator):
        """测试有漂移情况"""
        baseline_metrics = {'auc': 0.9, 'accuracy': 0.85}  # 高基线
        
        # 添加质量较差的数据
        np.random.seed(42)
        for i in range(50):
            # 生成随机预测（质量差）
            prediction = np.random.random()
            label = np.random.binomial(1, 0.5)
            online_evaluator.add_prediction(prediction, label)
        
        drift_result = online_evaluator.detect_drift(baseline_metrics, threshold=0.05)
        
        assert 'drift_detected' in drift_result
        assert 'drift_details' in drift_result
        
        # 检查漂移详情
        for metric_name, details in drift_result['drift_details'].items():
            assert 'baseline' in details
            assert 'current' in details
            assert 'drift' in details
            assert 'drift_detected' in details