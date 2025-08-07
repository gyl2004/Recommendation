"""
特征工程管道
包含特征标准化、编码、转换等功能
"""
import pandas as pd
import numpy as np
from typing import Dict, List, Optional, Tuple, Any
from sklearn.preprocessing import StandardScaler, MinMaxScaler, LabelEncoder
from sklearn.feature_extraction.text import TfidfVectorizer
import pickle
import json
from loguru import logger


class FeaturePipeline:
    """特征工程管道"""
    
    def __init__(self):
        self.scalers = {}
        self.encoders = {}
        self.vectorizers = {}
        self.feature_stats = {}
        self.is_fitted = False
    
    def fit(self, data: pd.DataFrame, feature_config: Dict[str, Any]):
        """
        拟合特征管道
        
        Args:
            data: 训练数据
            feature_config: 特征配置
        """
        logger.info("开始拟合特征管道")
        
        # 数值特征标准化
        if 'numeric_features' in feature_config:
            for feature in feature_config['numeric_features']:
                if feature in data.columns:
                    scaler_type = feature_config.get('scaler_type', 'standard')
                    if scaler_type == 'standard':
                        scaler = StandardScaler()
                    elif scaler_type == 'minmax':
                        scaler = MinMaxScaler()
                    else:
                        raise ValueError(f"不支持的标准化类型: {scaler_type}")
                    
                    scaler.fit(data[[feature]])
                    self.scalers[feature] = scaler
                    
                    # 记录特征统计信息
                    self.feature_stats[feature] = {
                        'mean': data[feature].mean(),
                        'std': data[feature].std(),
                        'min': data[feature].min(),
                        'max': data[feature].max(),
                        'missing_rate': data[feature].isnull().mean()
                    }
        
        # 分类特征编码
        if 'categorical_features' in feature_config:
            for feature in feature_config['categorical_features']:
                if feature in data.columns:
                    encoder = LabelEncoder()
                    # 处理缺失值
                    feature_data = data[feature].fillna('Unknown')
                    encoder.fit(feature_data)
                    self.encoders[feature] = encoder
                    
                    # 记录特征统计信息
                    self.feature_stats[feature] = {
                        'unique_count': data[feature].nunique(),
                        'most_frequent': data[feature].mode().iloc[0] if not data[feature].empty else None,
                        'missing_rate': data[feature].isnull().mean()
                    }
        
        # 文本特征向量化
        if 'text_features' in feature_config:
            for feature in feature_config['text_features']:
                if feature in data.columns:
                    vectorizer = TfidfVectorizer(
                        max_features=feature_config.get('max_text_features', 1000),
                        stop_words='english',
                        ngram_range=(1, 2)
                    )
                    # 处理缺失值
                    text_data = data[feature].fillna('')
                    vectorizer.fit(text_data)
                    self.vectorizers[feature] = vectorizer
        
        self.is_fitted = True
        logger.info("特征管道拟合完成")
    
    def transform(self, data: pd.DataFrame) -> pd.DataFrame:
        """
        转换特征
        
        Args:
            data: 待转换数据
            
        Returns:
            转换后的数据
        """
        if not self.is_fitted:
            raise ValueError("特征管道未拟合，请先调用fit方法")
        
        transformed_data = data.copy()
        
        # 数值特征标准化
        for feature, scaler in self.scalers.items():
            if feature in transformed_data.columns:
                # 处理缺失值
                transformed_data[feature] = transformed_data[feature].fillna(
                    self.feature_stats[feature]['mean']
                )
                transformed_data[feature] = scaler.transform(
                    transformed_data[[feature]]
                ).flatten()
        
        # 分类特征编码
        for feature, encoder in self.encoders.items():
            if feature in transformed_data.columns:
                # 处理缺失值和未见过的类别
                feature_data = transformed_data[feature].fillna('Unknown')
                # 处理训练时未见过的类别
                known_classes = set(encoder.classes_)
                feature_data = feature_data.apply(
                    lambda x: x if x in known_classes else 'Unknown'
                )
                transformed_data[feature] = encoder.transform(feature_data)
        
        # 文本特征向量化
        for feature, vectorizer in self.vectorizers.items():
            if feature in transformed_data.columns:
                text_data = transformed_data[feature].fillna('')
                text_vectors = vectorizer.transform(text_data).toarray()
                
                # 创建文本特征列
                feature_names = [f"{feature}_tfidf_{i}" for i in range(text_vectors.shape[1])]
                text_df = pd.DataFrame(text_vectors, columns=feature_names, index=transformed_data.index)
                
                # 删除原始文本列，添加向量化特征
                transformed_data = transformed_data.drop(columns=[feature])
                transformed_data = pd.concat([transformed_data, text_df], axis=1)
        
        return transformed_data
    
    def fit_transform(self, data: pd.DataFrame, feature_config: Dict[str, Any]) -> pd.DataFrame:
        """拟合并转换特征"""
        self.fit(data, feature_config)
        return self.transform(data)
    
    def save_pipeline(self, filepath: str):
        """保存特征管道"""
        pipeline_data = {
            'scalers': self.scalers,
            'encoders': self.encoders,
            'vectorizers': self.vectorizers,
            'feature_stats': self.feature_stats,
            'is_fitted': self.is_fitted
        }
        
        with open(filepath, 'wb') as f:
            pickle.dump(pipeline_data, f)
        
        logger.info(f"特征管道已保存到: {filepath}")
    
    def load_pipeline(self, filepath: str):
        """加载特征管道"""
        with open(filepath, 'rb') as f:
            pipeline_data = pickle.load(f)
        
        self.scalers = pipeline_data['scalers']
        self.encoders = pipeline_data['encoders']
        self.vectorizers = pipeline_data['vectorizers']
        self.feature_stats = pipeline_data['feature_stats']
        self.is_fitted = pipeline_data['is_fitted']
        
        logger.info(f"特征管道已从 {filepath} 加载")
    
    def get_feature_importance(self, model_feature_importance: np.ndarray, 
                             feature_names: List[str]) -> Dict[str, float]:
        """
        获取特征重要性
        
        Args:
            model_feature_importance: 模型特征重要性
            feature_names: 特征名称列表
            
        Returns:
            特征重要性字典
        """
        importance_dict = {}
        for i, name in enumerate(feature_names):
            if i < len(model_feature_importance):
                importance_dict[name] = float(model_feature_importance[i])
        
        # 按重要性排序
        return dict(sorted(importance_dict.items(), key=lambda x: x[1], reverse=True))


class RealTimeFeatureProcessor:
    """实时特征处理器"""
    
    def __init__(self, pipeline: FeaturePipeline):
        self.pipeline = pipeline
    
    def process_user_features(self, user_data: Dict[str, Any]) -> Dict[str, float]:
        """
        处理用户特征
        
        Args:
            user_data: 用户数据
            
        Returns:
            处理后的用户特征
        """
        # 转换为DataFrame
        df = pd.DataFrame([user_data])
        
        # 应用特征管道
        processed_df = self.pipeline.transform(df)
        
        # 转换为字典
        return processed_df.iloc[0].to_dict()
    
    def process_content_features(self, content_data: Dict[str, Any]) -> Dict[str, float]:
        """
        处理内容特征
        
        Args:
            content_data: 内容数据
            
        Returns:
            处理后的内容特征
        """
        # 转换为DataFrame
        df = pd.DataFrame([content_data])
        
        # 应用特征管道
        processed_df = self.pipeline.transform(df)
        
        # 转换为字典
        return processed_df.iloc[0].to_dict()
    
    def process_context_features(self, context_data: Dict[str, Any]) -> Dict[str, float]:
        """
        处理上下文特征
        
        Args:
            context_data: 上下文数据
            
        Returns:
            处理后的上下文特征
        """
        processed_features = {}
        
        # 时间特征
        if 'timestamp' in context_data:
            import datetime
            dt = datetime.datetime.fromtimestamp(context_data['timestamp'])
            processed_features.update({
                'hour': dt.hour,
                'day_of_week': dt.weekday(),
                'is_weekend': 1 if dt.weekday() >= 5 else 0
            })
        
        # 设备特征
        if 'device_type' in context_data:
            device_mapping = {'mobile': 0, 'tablet': 1, 'desktop': 2}
            processed_features['device_type'] = device_mapping.get(
                context_data['device_type'], 0
            )
        
        # 地理位置特征
        if 'location' in context_data:
            # 简化的地理位置编码
            location_hash = hash(context_data['location']) % 1000
            processed_features['location_hash'] = location_hash
        
        return processed_features


class FeatureStore:
    """特征存储"""
    
    def __init__(self, redis_client):
        self.redis_client = redis_client
        self.feature_ttl = 3600  # 特征缓存1小时
    
    async def get_user_features(self, user_id: str) -> Optional[Dict[str, Any]]:
        """获取用户特征"""
        key = f"user_features:{user_id}"
        features_json = await self.redis_client.get(key)
        
        if features_json:
            return json.loads(features_json)
        return None
    
    async def set_user_features(self, user_id: str, features: Dict[str, Any]):
        """设置用户特征"""
        key = f"user_features:{user_id}"
        features_json = json.dumps(features, ensure_ascii=False)
        await self.redis_client.setex(key, self.feature_ttl, features_json)
    
    async def get_content_features(self, content_id: str) -> Optional[Dict[str, Any]]:
        """获取内容特征"""
        key = f"content_features:{content_id}"
        features_json = await self.redis_client.get(key)
        
        if features_json:
            return json.loads(features_json)
        return None
    
    async def set_content_features(self, content_id: str, features: Dict[str, Any]):
        """设置内容特征"""
        key = f"content_features:{content_id}"
        features_json = json.dumps(features, ensure_ascii=False)
        await self.redis_client.setex(key, self.feature_ttl, features_json)
    
    async def batch_get_features(self, keys: List[str]) -> Dict[str, Optional[Dict[str, Any]]]:
        """批量获取特征"""
        if not keys:
            return {}
        
        values = await self.redis_client.mget(keys)
        result = {}
        
        for key, value in zip(keys, values):
            if value:
                result[key] = json.loads(value)
            else:
                result[key] = None
        
        return result
    
    async def batch_set_features(self, features_dict: Dict[str, Dict[str, Any]]):
        """批量设置特征"""
        if not features_dict:
            return
        
        pipe = self.redis_client.pipeline()
        for key, features in features_dict.items():
            features_json = json.dumps(features, ensure_ascii=False)
            pipe.setex(key, self.feature_ttl, features_json)
        
        await pipe.execute()


def create_sample_feature_config() -> Dict[str, Any]:
    """创建示例特征配置"""
    return {
        'numeric_features': [
            'user_age',
            'user_activity_score',
            'content_hot_score',
            'content_duration',
            'user_content_similarity'
        ],
        'categorical_features': [
            'user_gender',
            'content_type',
            'content_category',
            'user_interests',
            'device_type'
        ],
        'text_features': [
            'content_title',
            'content_tags'
        ],
        'scaler_type': 'standard',
        'max_text_features': 1000
    }