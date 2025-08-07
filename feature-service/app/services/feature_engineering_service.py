"""
特征工程服务
负责特征标准化、归一化、缺失值处理、特征选择和降维
"""
import numpy as np
import pandas as pd
from typing import Dict, List, Optional, Tuple, Any
from sklearn.preprocessing import StandardScaler, MinMaxScaler, RobustScaler
from sklearn.impute import SimpleImputer, KNNImputer
from sklearn.feature_selection import SelectKBest, f_classif, mutual_info_classif
from sklearn.decomposition import PCA
from sklearn.ensemble import IsolationForest
from loguru import logger
import pickle
import json
from datetime import datetime

from ..core.config import settings
from ..core.database import get_redis
from ..models.schemas import UserFeatures, ContentFeatures

class FeatureEngineeringService:
    """特征工程服务"""
    
    def __init__(self):
        self.scalers = {}
        self.imputers = {}
        self.feature_selectors = {}
        self.dimensionality_reducers = {}
        self.anomaly_detectors = {}
        self.feature_stats = {}
        
        # 特征工程配置
        self.config = {
            'scaling_method': 'standard',  # standard, minmax, robust
            'imputation_method': 'mean',   # mean, median, mode, knn
            'feature_selection_k': 50,     # 选择的特征数量
            'pca_components': 32,          # PCA降维后的维度
            'anomaly_threshold': 0.1       # 异常检测阈值
        }
    
    async def normalize_user_features(self, features_dict: Dict[str, UserFeatures]) -> Dict[str, UserFeatures]:
        """标准化用户特征"""
        try:
            if not features_dict:
                return features_dict
            
            # 提取特征向量
            feature_vectors = []
            user_ids = []
            
            for user_id, features in features_dict.items():
                if features.feature_vector:
                    feature_vectors.append(features.feature_vector)
                    user_ids.append(user_id)
            
            if not feature_vectors:
                return features_dict
            
            # 转换为numpy数组
            X = np.array(feature_vectors)
            
            # 处理缺失值
            X_imputed = await self._impute_missing_values(X, 'user_features')
            
            # 标准化
            X_scaled = await self._scale_features(X_imputed, 'user_features')
            
            # 异常检测和处理
            X_cleaned = await self._detect_and_handle_anomalies(X_scaled, 'user_features')
            
            # 更新特征向量
            for i, user_id in enumerate(user_ids):
                features_dict[user_id].feature_vector = X_cleaned[i].tolist()
                features_dict[user_id].updated_at = datetime.now()
            
            logger.info(f"用户特征标准化完成，处理数量: {len(user_ids)}")
            return features_dict
            
        except Exception as e:
            logger.error(f"用户特征标准化失败: {e}")
            return features_dict
    
    async def normalize_content_features(self, features_dict: Dict[str, ContentFeatures]) -> Dict[str, ContentFeatures]:
        """标准化内容特征"""
        try:
            if not features_dict:
                return features_dict
            
            # 提取特征向量
            feature_vectors = []
            content_ids = []
            
            for content_id, features in features_dict.items():
                if features.embedding_vector:
                    feature_vectors.append(features.embedding_vector)
                    content_ids.append(content_id)
            
            if not feature_vectors:
                return features_dict
            
            # 转换为numpy数组
            X = np.array(feature_vectors)
            
            # 处理缺失值
            X_imputed = await self._impute_missing_values(X, 'content_features')
            
            # 标准化
            X_scaled = await self._scale_features(X_imputed, 'content_features')
            
            # 异常检测和处理
            X_cleaned = await self._detect_and_handle_anomalies(X_scaled, 'content_features')
            
            # 更新特征向量
            for i, content_id in enumerate(content_ids):
                features_dict[content_id].embedding_vector = X_cleaned[i].tolist()
                features_dict[content_id].updated_at = datetime.now()
            
            logger.info(f"内容特征标准化完成，处理数量: {len(content_ids)}")
            return features_dict
            
        except Exception as e:
            logger.error(f"内容特征标准化失败: {e}")
            return features_dict
    
    async def select_important_features(self, X: np.ndarray, y: np.ndarray, 
                                      feature_type: str) -> Tuple[np.ndarray, List[int]]:
        """特征选择"""
        try:
            # 获取或创建特征选择器
            selector_key = f"{feature_type}_selector"
            
            if selector_key not in self.feature_selectors:
                # 使用互信息进行特征选择
                selector = SelectKBest(
                    score_func=mutual_info_classif,
                    k=min(self.config['feature_selection_k'], X.shape[1])
                )
                self.feature_selectors[selector_key] = selector
            else:
                selector = self.feature_selectors[selector_key]
            
            # 拟合并转换特征
            X_selected = selector.fit_transform(X, y)
            
            # 获取选中的特征索引
            selected_indices = selector.get_support(indices=True).tolist()
            
            # 保存特征选择器
            await self._save_model(selector, f"feature_selector_{feature_type}")
            
            logger.info(f"特征选择完成，原始特征: {X.shape[1]}, 选择特征: {X_selected.shape[1]}")
            return X_selected, selected_indices
            
        except Exception as e:
            logger.error(f"特征选择失败: {e}")
            return X, list(range(X.shape[1]))
    
    async def reduce_dimensionality(self, X: np.ndarray, feature_type: str) -> np.ndarray:
        """降维处理"""
        try:
            # 获取或创建降维器
            reducer_key = f"{feature_type}_pca"
            
            if reducer_key not in self.dimensionality_reducers:
                n_components = min(self.config['pca_components'], X.shape[1], X.shape[0])
                pca = PCA(n_components=n_components, random_state=42)
                self.dimensionality_reducers[reducer_key] = pca
            else:
                pca = self.dimensionality_reducers[reducer_key]
            
            # 拟合并转换
            X_reduced = pca.fit_transform(X)
            
            # 保存降维器
            await self._save_model(pca, f"pca_{feature_type}")
            
            # 记录解释方差比
            explained_variance_ratio = pca.explained_variance_ratio_.sum()
            logger.info(f"PCA降维完成，原始维度: {X.shape[1]}, 降维后: {X_reduced.shape[1]}, "
                       f"解释方差比: {explained_variance_ratio:.3f}")
            
            return X_reduced
            
        except Exception as e:
            logger.error(f"降维处理失败: {e}")
            return X
    
    async def _impute_missing_values(self, X: np.ndarray, feature_type: str) -> np.ndarray:
        """处理缺失值"""
        try:
            # 检查是否有缺失值
            if not np.isnan(X).any():
                return X
            
            # 获取或创建填充器
            imputer_key = f"{feature_type}_imputer"
            
            if imputer_key not in self.imputers:
                if self.config['imputation_method'] == 'knn':
                    imputer = KNNImputer(n_neighbors=5)
                else:
                    imputer = SimpleImputer(strategy=self.config['imputation_method'])
                self.imputers[imputer_key] = imputer
            else:
                imputer = self.imputers[imputer_key]
            
            # 拟合并转换
            X_imputed = imputer.fit_transform(X)
            
            # 保存填充器
            await self._save_model(imputer, f"imputer_{feature_type}")
            
            missing_count = np.isnan(X).sum()
            logger.info(f"缺失值处理完成，缺失值数量: {missing_count}")
            
            return X_imputed
            
        except Exception as e:
            logger.error(f"缺失值处理失败: {e}")
            return X
    
    async def _scale_features(self, X: np.ndarray, feature_type: str) -> np.ndarray:
        """特征缩放"""
        try:
            # 获取或创建缩放器
            scaler_key = f"{feature_type}_scaler"
            
            if scaler_key not in self.scalers:
                if self.config['scaling_method'] == 'minmax':
                    scaler = MinMaxScaler()
                elif self.config['scaling_method'] == 'robust':
                    scaler = RobustScaler()
                else:
                    scaler = StandardScaler()
                self.scalers[scaler_key] = scaler
            else:
                scaler = self.scalers[scaler_key]
            
            # 拟合并转换
            X_scaled = scaler.fit_transform(X)
            
            # 保存缩放器
            await self._save_model(scaler, f"scaler_{feature_type}")
            
            return X_scaled
            
        except Exception as e:
            logger.error(f"特征缩放失败: {e}")
            return X
    
    async def _detect_and_handle_anomalies(self, X: np.ndarray, feature_type: str) -> np.ndarray:
        """异常检测和处理"""
        try:
            # 获取或创建异常检测器
            detector_key = f"{feature_type}_anomaly_detector"
            
            if detector_key not in self.anomaly_detectors:
                detector = IsolationForest(
                    contamination=self.config['anomaly_threshold'],
                    random_state=42
                )
                self.anomaly_detectors[detector_key] = detector
            else:
                detector = self.anomaly_detectors[detector_key]
            
            # 检测异常
            anomaly_labels = detector.fit_predict(X)
            
            # 统计异常数量
            anomaly_count = np.sum(anomaly_labels == -1)
            
            if anomaly_count > 0:
                # 对异常值进行处理（这里使用中位数替换）
                X_cleaned = X.copy()
                anomaly_indices = np.where(anomaly_labels == -1)[0]
                
                for idx in anomaly_indices:
                    # 用该特征的中位数替换异常值
                    for feature_idx in range(X.shape[1]):
                        median_value = np.median(X[:, feature_idx])
                        X_cleaned[idx, feature_idx] = median_value
                
                logger.info(f"异常检测完成，异常样本数量: {anomaly_count}")
                
                # 保存异常检测器
                await self._save_model(detector, f"anomaly_detector_{feature_type}")
                
                return X_cleaned
            
            return X
            
        except Exception as e:
            logger.error(f"异常检测失败: {e}")
            return X
    
    async def compute_feature_statistics(self, X: np.ndarray, feature_type: str) -> Dict[str, Any]:
        """计算特征统计信息"""
        try:
            stats = {
                'feature_type': feature_type,
                'sample_count': X.shape[0],
                'feature_count': X.shape[1],
                'mean': np.mean(X, axis=0).tolist(),
                'std': np.std(X, axis=0).tolist(),
                'min': np.min(X, axis=0).tolist(),
                'max': np.max(X, axis=0).tolist(),
                'median': np.median(X, axis=0).tolist(),
                'missing_rate': np.isnan(X).sum(axis=0).tolist() if np.isnan(X).any() else [0] * X.shape[1],
                'computed_at': datetime.now().isoformat()
            }
            
            # 保存统计信息
            self.feature_stats[feature_type] = stats
            await self._save_feature_stats(feature_type, stats)
            
            return stats
            
        except Exception as e:
            logger.error(f"计算特征统计信息失败: {e}")
            return {}
    
    async def monitor_feature_quality(self, X: np.ndarray, feature_type: str) -> Dict[str, Any]:
        """监控特征质量"""
        try:
            quality_metrics = {
                'feature_type': feature_type,
                'sample_count': X.shape[0],
                'feature_count': X.shape[1],
                'missing_rate': np.isnan(X).sum() / X.size if np.isnan(X).any() else 0.0,
                'zero_variance_features': np.sum(np.var(X, axis=0) == 0),
                'high_correlation_pairs': 0,  # 需要计算相关性矩阵
                'outlier_rate': 0.0,  # 需要异常检测
                'data_drift_score': 0.0,  # 需要与历史数据比较
                'monitored_at': datetime.now().isoformat()
            }
            
            # 计算高相关性特征对
            if X.shape[1] > 1:
                corr_matrix = np.corrcoef(X.T)
                high_corr_mask = (np.abs(corr_matrix) > 0.9) & (corr_matrix != 1.0)
                quality_metrics['high_correlation_pairs'] = np.sum(high_corr_mask) // 2
            
            # 计算异常率
            if X.shape[0] > 10:  # 样本数量足够时才进行异常检测
                detector = IsolationForest(contamination=0.1, random_state=42)
                anomaly_labels = detector.fit_predict(X)
                quality_metrics['outlier_rate'] = np.sum(anomaly_labels == -1) / len(anomaly_labels)
            
            # 保存质量监控结果
            await self._save_quality_metrics(feature_type, quality_metrics)
            
            # 检查质量告警
            await self._check_quality_alerts(quality_metrics)
            
            return quality_metrics
            
        except Exception as e:
            logger.error(f"特征质量监控失败: {e}")
            return {}
    
    async def _save_model(self, model: Any, model_name: str):
        """保存模型到Redis"""
        try:
            redis_client = await get_redis()
            
            # 序列化模型
            model_bytes = pickle.dumps(model)
            
            # 保存到Redis
            await redis_client.setex(
                f"feature_engineering:model:{model_name}",
                86400,  # 24小时过期
                model_bytes
            )
            
        except Exception as e:
            logger.error(f"保存模型失败 {model_name}: {e}")
    
    async def _load_model(self, model_name: str) -> Optional[Any]:
        """从Redis加载模型"""
        try:
            redis_client = await get_redis()
            
            model_bytes = await redis_client.get(f"feature_engineering:model:{model_name}")
            if model_bytes:
                return pickle.loads(model_bytes)
            
            return None
            
        except Exception as e:
            logger.error(f"加载模型失败 {model_name}: {e}")
            return None
    
    async def _save_feature_stats(self, feature_type: str, stats: Dict[str, Any]):
        """保存特征统计信息"""
        try:
            redis_client = await get_redis()
            
            await redis_client.setex(
                f"feature_engineering:stats:{feature_type}",
                3600,  # 1小时过期
                json.dumps(stats)
            )
            
        except Exception as e:
            logger.error(f"保存特征统计信息失败: {e}")
    
    async def _save_quality_metrics(self, feature_type: str, metrics: Dict[str, Any]):
        """保存质量监控指标"""
        try:
            redis_client = await get_redis()
            
            await redis_client.setex(
                f"feature_engineering:quality:{feature_type}",
                3600,  # 1小时过期
                json.dumps(metrics)
            )
            
        except Exception as e:
            logger.error(f"保存质量监控指标失败: {e}")
    
    async def _check_quality_alerts(self, metrics: Dict[str, Any]):
        """检查质量告警"""
        try:
            alerts = []
            
            # 检查缺失率告警
            if metrics['missing_rate'] > 0.2:
                alerts.append(f"特征缺失率过高: {metrics['missing_rate']:.2%}")
            
            # 检查零方差特征告警
            if metrics['zero_variance_features'] > 0:
                alerts.append(f"存在零方差特征: {metrics['zero_variance_features']}个")
            
            # 检查高相关性告警
            if metrics['high_correlation_pairs'] > 10:
                alerts.append(f"高相关性特征对过多: {metrics['high_correlation_pairs']}对")
            
            # 检查异常率告警
            if metrics['outlier_rate'] > 0.15:
                alerts.append(f"异常样本比例过高: {metrics['outlier_rate']:.2%}")
            
            if alerts:
                logger.warning(f"特征质量告警 [{metrics['feature_type']}]: {'; '.join(alerts)}")
            
        except Exception as e:
            logger.error(f"检查质量告警失败: {e}")
    
    async def get_feature_statistics(self, feature_type: str) -> Optional[Dict[str, Any]]:
        """获取特征统计信息"""
        try:
            redis_client = await get_redis()
            
            stats_data = await redis_client.get(f"feature_engineering:stats:{feature_type}")
            if stats_data:
                return json.loads(stats_data)
            
            return None
            
        except Exception as e:
            logger.error(f"获取特征统计信息失败: {e}")
            return None
    
    async def get_quality_metrics(self, feature_type: str) -> Optional[Dict[str, Any]]:
        """获取质量监控指标"""
        try:
            redis_client = await get_redis()
            
            metrics_data = await redis_client.get(f"feature_engineering:quality:{feature_type}")
            if metrics_data:
                return json.loads(metrics_data)
            
            return None
            
        except Exception as e:
            logger.error(f"获取质量监控指标失败: {e}")
            return None