"""
Wide&Deep模型实现
结合线性模型的记忆能力和深度神经网络的泛化能力
"""
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import numpy as np
from typing import Dict, List, Tuple, Optional
from loguru import logger


class WideDeepModel:
    """Wide&Deep推荐排序模型"""
    
    def __init__(self, 
                 wide_feature_columns: List[tf.feature_column.FeatureColumn],
                 deep_feature_columns: List[tf.feature_column.FeatureColumn],
                 deep_hidden_units: List[int] = [128, 64, 32],
                 dropout_rate: float = 0.1,
                 learning_rate: float = 0.001):
        """
        初始化Wide&Deep模型
        
        Args:
            wide_feature_columns: Wide部分的特征列
            deep_feature_columns: Deep部分的特征列
            deep_hidden_units: Deep部分隐藏层单元数
            dropout_rate: Dropout比率
            learning_rate: 学习率
        """
        self.wide_feature_columns = wide_feature_columns
        self.deep_feature_columns = deep_feature_columns
        self.deep_hidden_units = deep_hidden_units
        self.dropout_rate = dropout_rate
        self.learning_rate = learning_rate
        self.model = None
        self._build_model()
    
    def _build_model(self):
        """构建Wide&Deep模型"""
        # Wide部分输入
        wide_inputs = {}
        for column in self.wide_feature_columns:
            if hasattr(column, 'key'):
                wide_inputs[column.key] = keras.Input(
                    shape=(), name=column.key, dtype=tf.string
                    if column.dtype == tf.string else tf.float32
                )
        
        # Deep部分输入
        deep_inputs = {}
        for column in self.deep_feature_columns:
            if hasattr(column, 'key'):
                deep_inputs[column.key] = keras.Input(
                    shape=(), name=column.key, dtype=tf.string
                    if column.dtype == tf.string else tf.float32
                )
        
        # 合并所有输入
        all_inputs = {**wide_inputs, **deep_inputs}
        
        # Wide部分
        wide_features = layers.DenseFeatures(self.wide_feature_columns)(all_inputs)
        wide_output = layers.Dense(1, activation=None, name='wide_output')(wide_features)
        
        # Deep部分
        deep_features = layers.DenseFeatures(self.deep_feature_columns)(all_inputs)
        deep_hidden = deep_features
        
        for i, units in enumerate(self.deep_hidden_units):
            deep_hidden = layers.Dense(
                units, activation='relu', name=f'deep_hidden_{i}'
            )(deep_hidden)
            deep_hidden = layers.Dropout(self.dropout_rate)(deep_hidden)
        
        deep_output = layers.Dense(1, activation=None, name='deep_output')(deep_hidden)
        
        # Wide&Deep融合
        combined_output = layers.Add(name='wide_deep_add')([wide_output, deep_output])
        final_output = layers.Dense(1, activation='sigmoid', name='prediction')(combined_output)
        
        # 创建模型
        self.model = keras.Model(inputs=all_inputs, outputs=final_output)
        
        # 编译模型
        self.model.compile(
            optimizer=keras.optimizers.Adam(learning_rate=self.learning_rate),
            loss='binary_crossentropy',
            metrics=['accuracy', 'auc']
        )
        
        logger.info("Wide&Deep模型构建完成")
    
    def train(self, 
              train_dataset: tf.data.Dataset,
              validation_dataset: Optional[tf.data.Dataset] = None,
              epochs: int = 10,
              callbacks: Optional[List] = None) -> keras.callbacks.History:
        """
        训练模型
        
        Args:
            train_dataset: 训练数据集
            validation_dataset: 验证数据集
            epochs: 训练轮数
            callbacks: 回调函数列表
            
        Returns:
            训练历史
        """
        if self.model is None:
            raise ValueError("模型未初始化")
        
        logger.info(f"开始训练Wide&Deep模型，epochs={epochs}")
        
        history = self.model.fit(
            train_dataset,
            validation_data=validation_dataset,
            epochs=epochs,
            callbacks=callbacks or []
        )
        
        logger.info("模型训练完成")
        return history
    
    def predict(self, features: Dict[str, np.ndarray]) -> np.ndarray:
        """
        预测
        
        Args:
            features: 特征字典
            
        Returns:
            预测结果
        """
        if self.model is None:
            raise ValueError("模型未初始化")
        
        return self.model.predict(features)
    
    def predict_batch(self, dataset: tf.data.Dataset) -> np.ndarray:
        """
        批量预测
        
        Args:
            dataset: 数据集
            
        Returns:
            预测结果
        """
        if self.model is None:
            raise ValueError("模型未初始化")
        
        return self.model.predict(dataset)
    
    def save_model(self, model_path: str):
        """保存模型"""
        if self.model is None:
            raise ValueError("模型未初始化")
        
        self.model.save(model_path)
        logger.info(f"模型已保存到: {model_path}")
    
    def load_model(self, model_path: str):
        """加载模型"""
        self.model = keras.models.load_model(model_path)
        logger.info(f"模型已从 {model_path} 加载")
    
    def get_model_summary(self) -> str:
        """获取模型摘要"""
        if self.model is None:
            return "模型未初始化"
        
        import io
        stream = io.StringIO()
        self.model.summary(print_fn=lambda x: stream.write(x + '\n'))
        return stream.getvalue()


class FeatureColumnBuilder:
    """特征列构建器"""
    
    @staticmethod
    def build_numeric_column(key: str, 
                           normalizer_fn: Optional[callable] = None) -> tf.feature_column.NumericColumn:
        """构建数值特征列"""
        return tf.feature_column.numeric_column(key, normalizer_fn=normalizer_fn)
    
    @staticmethod
    def build_categorical_column_with_vocabulary_list(
        key: str, 
        vocabulary_list: List[str]
    ) -> tf.feature_column.CategoricalColumn:
        """构建分类特征列（词汇表）"""
        return tf.feature_column.categorical_column_with_vocabulary_list(
            key, vocabulary_list
        )
    
    @staticmethod
    def build_categorical_column_with_hash_bucket(
        key: str, 
        hash_bucket_size: int
    ) -> tf.feature_column.CategoricalColumn:
        """构建分类特征列（哈希桶）"""
        return tf.feature_column.categorical_column_with_hash_bucket(
            key, hash_bucket_size
        )
    
    @staticmethod
    def build_embedding_column(
        categorical_column: tf.feature_column.CategoricalColumn,
        dimension: int
    ) -> tf.feature_column.EmbeddingColumn:
        """构建嵌入特征列"""
        return tf.feature_column.embedding_column(categorical_column, dimension)
    
    @staticmethod
    def build_crossed_column(
        keys: List[str], 
        hash_bucket_size: int
    ) -> tf.feature_column.CrossedColumn:
        """构建交叉特征列"""
        return tf.feature_column.crossed_column(keys, hash_bucket_size)
    
    @staticmethod
    def build_bucketized_column(
        source_column: tf.feature_column.NumericColumn,
        boundaries: List[float]
    ) -> tf.feature_column.BucketizedColumn:
        """构建分桶特征列"""
        return tf.feature_column.bucketized_column(source_column, boundaries)


def create_wide_deep_feature_columns() -> Tuple[List, List]:
    """
    创建Wide&Deep模型的特征列
    
    Returns:
        (wide_columns, deep_columns)
    """
    builder = FeatureColumnBuilder()
    
    # 数值特征
    user_age = builder.build_numeric_column('user_age')
    content_hot_score = builder.build_numeric_column('content_hot_score')
    user_activity_score = builder.build_numeric_column('user_activity_score')
    
    # 分类特征
    user_gender = builder.build_categorical_column_with_vocabulary_list(
        'user_gender', ['M', 'F', 'Unknown']
    )
    content_type = builder.build_categorical_column_with_vocabulary_list(
        'content_type', ['article', 'video', 'product']
    )
    content_category = builder.build_categorical_column_with_hash_bucket(
        'content_category', 100
    )
    user_interests = builder.build_categorical_column_with_hash_bucket(
        'user_interests', 1000
    )
    
    # 分桶特征
    age_buckets = builder.build_bucketized_column(
        user_age, [18, 25, 30, 35, 45, 55, 65]
    )
    
    # 交叉特征（Wide部分）
    age_gender_cross = builder.build_crossed_column(
        ['user_age', 'user_gender'], 100
    )
    content_type_category_cross = builder.build_crossed_column(
        ['content_type', 'content_category'], 1000
    )
    
    # 嵌入特征（Deep部分）
    user_gender_emb = builder.build_embedding_column(user_gender, 8)
    content_type_emb = builder.build_embedding_column(content_type, 8)
    content_category_emb = builder.build_embedding_column(content_category, 32)
    user_interests_emb = builder.build_embedding_column(user_interests, 64)
    
    # Wide特征列
    wide_columns = [
        age_buckets,
        tf.feature_column.indicator_column(user_gender),
        tf.feature_column.indicator_column(content_type),
        tf.feature_column.indicator_column(age_gender_cross),
        tf.feature_column.indicator_column(content_type_category_cross)
    ]
    
    # Deep特征列
    deep_columns = [
        user_age,
        content_hot_score,
        user_activity_score,
        user_gender_emb,
        content_type_emb,
        content_category_emb,
        user_interests_emb
    ]
    
    return wide_columns, deep_columns