"""
Wide&Deep模型测试
"""
import pytest
import numpy as np
import pandas as pd
import tensorflow as tf
from unittest.mock import Mock, patch
import tempfile
import os

from app.models.wide_deep_model import WideDeepModel, FeatureColumnBuilder, create_wide_deep_feature_columns


class TestWideDeepModel:
    """Wide&Deep模型测试类"""
    
    @pytest.fixture
    def sample_feature_columns(self):
        """创建示例特征列"""
        return create_wide_deep_feature_columns()
    
    @pytest.fixture
    def model(self, sample_feature_columns):
        """创建测试模型"""
        wide_columns, deep_columns = sample_feature_columns
        return WideDeepModel(
            wide_feature_columns=wide_columns,
            deep_feature_columns=deep_columns,
            deep_hidden_units=[32, 16],
            dropout_rate=0.1,
            learning_rate=0.001
        )
    
    @pytest.fixture
    def sample_data(self):
        """创建示例数据"""
        np.random.seed(42)
        n_samples = 100
        
        data = {
            'user_age': np.random.normal(30, 10, n_samples).clip(18, 80),
            'user_gender': np.random.choice(['M', 'F', 'Unknown'], n_samples),
            'user_activity_score': np.random.beta(2, 5, n_samples),
            'content_hot_score': np.random.beta(2, 3, n_samples),
            'content_type': np.random.choice(['article', 'video', 'product'], n_samples),
            'content_category': np.random.choice([f'cat_{i}' for i in range(10)], n_samples),
            'user_interests': np.random.choice(['tech', 'sports', 'travel'], n_samples)
        }
        
        labels = np.random.binomial(1, 0.3, n_samples)
        
        return pd.DataFrame(data), labels
    
    def test_model_initialization(self, model):
        """测试模型初始化"""
        assert model.model is not None
        assert len(model.deep_hidden_units) == 2
        assert model.dropout_rate == 0.1
        assert model.learning_rate == 0.001
    
    def test_model_summary(self, model):
        """测试模型摘要"""
        summary = model.get_model_summary()
        assert isinstance(summary, str)
        assert len(summary) > 0
    
    def test_predict_single_sample(self, model):
        """测试单样本预测"""
        # 创建示例特征
        features = {
            'user_age': np.array([25.0]),
            'user_gender': np.array(['M']),
            'user_activity_score': np.array([0.5]),
            'content_hot_score': np.array([0.7]),
            'content_type': np.array(['article']),
            'content_category': np.array(['tech']),
            'user_interests': np.array(['tech'])
        }
        
        # 预测
        predictions = model.predict(features)
        
        assert predictions.shape == (1, 1)
        assert 0 <= predictions[0][0] <= 1
    
    def test_predict_batch(self, model, sample_data):
        """测试批量预测"""
        features_df, _ = sample_data
        
        # 转换为模型输入格式
        features = {}
        for column in features_df.columns:
            if features_df[column].dtype == 'object':
                features[column] = features_df[column].astype(str).values
            else:
                features[column] = features_df[column].astype(np.float32).values
        
        # 预测
        predictions = model.predict(features)
        
        assert predictions.shape == (len(features_df), 1)
        assert all(0 <= pred[0] <= 1 for pred in predictions)
    
    def test_save_and_load_model(self, model):
        """测试模型保存和加载"""
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = os.path.join(temp_dir, "test_model")
            
            # 保存模型
            model.save_model(model_path)
            assert os.path.exists(model_path)
            
            # 创建新模型实例并加载
            wide_columns, deep_columns = create_wide_deep_feature_columns()
            new_model = WideDeepModel(wide_columns, deep_columns)
            new_model.load_model(model_path)
            
            # 验证加载的模型
            assert new_model.model is not None


class TestFeatureColumnBuilder:
    """特征列构建器测试类"""
    
    def test_build_numeric_column(self):
        """测试数值特征列构建"""
        column = FeatureColumnBuilder.build_numeric_column('test_numeric')
        assert column.key == 'test_numeric'
        assert isinstance(column, tf.feature_column.NumericColumn)
    
    def test_build_categorical_column_with_vocabulary_list(self):
        """测试分类特征列构建（词汇表）"""
        vocab = ['A', 'B', 'C']
        column = FeatureColumnBuilder.build_categorical_column_with_vocabulary_list(
            'test_categorical', vocab
        )
        assert column.key == 'test_categorical'
        assert isinstance(column, tf.feature_column.CategoricalColumn)
    
    def test_build_categorical_column_with_hash_bucket(self):
        """测试分类特征列构建（哈希桶）"""
        column = FeatureColumnBuilder.build_categorical_column_with_hash_bucket(
            'test_hash', 100
        )
        assert column.key == 'test_hash'
        assert isinstance(column, tf.feature_column.CategoricalColumn)
    
    def test_build_embedding_column(self):
        """测试嵌入特征列构建"""
        categorical_column = FeatureColumnBuilder.build_categorical_column_with_vocabulary_list(
            'test_cat', ['A', 'B', 'C']
        )
        embedding_column = FeatureColumnBuilder.build_embedding_column(
            categorical_column, 8
        )
        assert isinstance(embedding_column, tf.feature_column.EmbeddingColumn)
    
    def test_build_crossed_column(self):
        """测试交叉特征列构建"""
        column = FeatureColumnBuilder.build_crossed_column(
            ['feature1', 'feature2'], 100
        )
        assert isinstance(column, tf.feature_column.CrossedColumn)
    
    def test_build_bucketized_column(self):
        """测试分桶特征列构建"""
        numeric_column = FeatureColumnBuilder.build_numeric_column('test_numeric')
        bucketized_column = FeatureColumnBuilder.build_bucketized_column(
            numeric_column, [0, 10, 20, 30]
        )
        assert isinstance(bucketized_column, tf.feature_column.BucketizedColumn)


class TestFeatureColumns:
    """特征列测试类"""
    
    def test_create_wide_deep_feature_columns(self):
        """测试Wide&Deep特征列创建"""
        wide_columns, deep_columns = create_wide_deep_feature_columns()
        
        assert len(wide_columns) > 0
        assert len(deep_columns) > 0
        
        # 验证Wide特征列类型
        for column in wide_columns:
            assert isinstance(column, (
                tf.feature_column.IndicatorColumn,
                tf.feature_column.BucketizedColumn
            ))
        
        # 验证Deep特征列类型
        for column in deep_columns:
            assert isinstance(column, (
                tf.feature_column.NumericColumn,
                tf.feature_column.EmbeddingColumn
            ))