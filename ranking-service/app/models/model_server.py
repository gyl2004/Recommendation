"""
模型服务器
提供高性能的模型推理服务
"""
import asyncio
import numpy as np
import pandas as pd
import tensorflow as tf
from typing import Dict, List, Optional, Any, Union
from concurrent.futures import ThreadPoolExecutor
import threading
import time
from loguru import logger
from datetime import datetime
import json
import pickle
from pathlib import Path

from .wide_deep_model import WideDeepModel, create_wide_deep_feature_columns
from .model_evaluator import OnlineEvaluator


class ModelServer:
    """模型服务器"""
    
    def __init__(self, 
                 model_path: str,
                 max_batch_size: int = 64,
                 batch_timeout_ms: int = 10,
                 max_workers: int = 4):
        """
        初始化模型服务器
        
        Args:
            model_path: 模型路径
            max_batch_size: 最大批次大小
            batch_timeout_ms: 批次超时时间(毫秒)
            max_workers: 最大工作线程数
        """
        self.model_path = model_path
        self.max_batch_size = max_batch_size
        self.batch_timeout_ms = batch_timeout_ms
        self.max_workers = max_workers
        
        # 模型相关
        self.model = None
        self.model_version = None
        self.model_loaded_time = None
        
        # 批处理相关
        self.pending_requests = []
        self.request_lock = threading.Lock()
        self.batch_processor = None
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        
        # 性能统计
        self.total_requests = 0
        self.total_predictions = 0
        self.total_inference_time = 0.0
        self.batch_count = 0
        
        # 在线评估
        self.online_evaluator = OnlineEvaluator()
        
        # 启动批处理线程
        self._start_batch_processor()
    
    def load_model(self, model_path: Optional[str] = None) -> bool:
        """
        加载模型
        
        Args:
            model_path: 模型路径，如果为None则使用初始化时的路径
            
        Returns:
            是否加载成功
        """
        try:
            path = model_path or self.model_path
            
            # 创建模型实例
            wide_columns, deep_columns = create_wide_deep_feature_columns()
            self.model = WideDeepModel(wide_columns, deep_columns)
            
            # 加载模型权重
            self.model.load_model(path)
            
            # 更新模型信息
            self.model_version = self._get_model_version(path)
            self.model_loaded_time = datetime.now()
            
            logger.info(f"模型加载成功: {path}, 版本: {self.model_version}")
            return True
            
        except Exception as e:
            logger.error(f"模型加载失败: {e}")
            return False
    
    def _get_model_version(self, model_path: str) -> str:
        """获取模型版本"""
        try:
            # 尝试从模型路径获取版本信息
            path_obj = Path(model_path)
            if path_obj.exists():
                # 使用文件修改时间作为版本
                mtime = path_obj.stat().st_mtime
                return datetime.fromtimestamp(mtime).strftime("%Y%m%d_%H%M%S")
            else:
                return "unknown"
        except Exception:
            return "unknown"
    
    async def predict_async(self, features: Dict[str, Any]) -> float:
        """
        异步预测单个样本
        
        Args:
            features: 特征字典
            
        Returns:
            预测分数
        """
        # 创建预测请求
        request = PredictionRequest(features)
        
        # 添加到待处理队列
        with self.request_lock:
            self.pending_requests.append(request)
            self.total_requests += 1
        
        # 等待结果
        result = await request.get_result()
        return result
    
    def predict_batch_sync(self, features_list: List[Dict[str, Any]]) -> List[float]:
        """
        同步批量预测
        
        Args:
            features_list: 特征字典列表
            
        Returns:
            预测分数列表
        """
        if not self.model:
            raise ValueError("模型未加载")
        
        start_time = time.time()
        
        try:
            # 转换为DataFrame
            features_df = pd.DataFrame(features_list)
            
            # 转换为模型输入格式
            model_input = {}
            for column in features_df.columns:
                if features_df[column].dtype == 'object':
                    model_input[column] = features_df[column].astype(str).values
                else:
                    model_input[column] = features_df[column].astype(np.float32).values
            
            # 执行预测
            predictions = self.model.predict(model_input)
            scores = predictions.flatten().tolist()
            
            # 更新统计信息
            inference_time = time.time() - start_time
            self.total_predictions += len(features_list)
            self.total_inference_time += inference_time
            self.batch_count += 1
            
            logger.debug(f"批量预测完成: {len(features_list)} 样本, 耗时 {inference_time:.3f}s")
            
            return scores
            
        except Exception as e:
            logger.error(f"批量预测失败: {e}")
            return [0.0] * len(features_list)
    
    def _start_batch_processor(self):
        """启动批处理线程"""
        def batch_processor():
            while True:
                try:
                    # 收集待处理请求
                    batch_requests = []
                    
                    with self.request_lock:
                        if self.pending_requests:
                            # 取出一批请求
                            batch_size = min(len(self.pending_requests), self.max_batch_size)
                            batch_requests = self.pending_requests[:batch_size]
                            self.pending_requests = self.pending_requests[batch_size:]
                    
                    if batch_requests:
                        # 处理批次
                        self._process_batch(batch_requests)
                    else:
                        # 没有请求时短暂休眠
                        time.sleep(self.batch_timeout_ms / 1000.0)
                        
                except Exception as e:
                    logger.error(f"批处理线程错误: {e}")
                    time.sleep(0.1)
        
        self.batch_processor = threading.Thread(target=batch_processor, daemon=True)
        self.batch_processor.start()
        logger.info("批处理线程已启动")
    
    def _process_batch(self, batch_requests: List['PredictionRequest']):
        """处理一批预测请求"""
        try:
            # 提取特征
            features_list = [req.features for req in batch_requests]
            
            # 执行批量预测
            scores = self.predict_batch_sync(features_list)
            
            # 设置结果
            for req, score in zip(batch_requests, scores):
                req.set_result(score)
                
        except Exception as e:
            logger.error(f"批处理失败: {e}")
            # 设置错误结果
            for req in batch_requests:
                req.set_result(0.0)
    
    def add_feedback(self, prediction: float, actual_label: int):
        """添加反馈数据用于在线评估"""
        self.online_evaluator.add_prediction(prediction, actual_label)
    
    def get_online_metrics(self) -> Dict[str, Any]:
        """获取在线评估指标"""
        return self.online_evaluator.get_current_metrics()
    
    def detect_model_drift(self, baseline_metrics: Dict[str, float]) -> Dict[str, Any]:
        """检测模型漂移"""
        return self.online_evaluator.detect_drift(baseline_metrics)
    
    def get_server_stats(self) -> Dict[str, Any]:
        """获取服务器统计信息"""
        avg_inference_time = (
            self.total_inference_time / self.batch_count 
            if self.batch_count > 0 else 0.0
        )
        
        avg_batch_size = (
            self.total_predictions / self.batch_count 
            if self.batch_count > 0 else 0.0
        )
        
        return {
            'model_info': {
                'model_path': self.model_path,
                'model_version': self.model_version,
                'model_loaded_time': self.model_loaded_time.isoformat() if self.model_loaded_time else None,
                'model_loaded': self.model is not None
            },
            'performance_stats': {
                'total_requests': self.total_requests,
                'total_predictions': self.total_predictions,
                'total_inference_time': self.total_inference_time,
                'batch_count': self.batch_count,
                'avg_inference_time': avg_inference_time,
                'avg_batch_size': avg_batch_size,
                'pending_requests': len(self.pending_requests)
            },
            'config': {
                'max_batch_size': self.max_batch_size,
                'batch_timeout_ms': self.batch_timeout_ms,
                'max_workers': self.max_workers
            },
            'timestamp': datetime.now().isoformat()
        }
    
    def health_check(self) -> Dict[str, Any]:
        """健康检查"""
        is_healthy = (
            self.model is not None and
            self.batch_processor is not None and
            self.batch_processor.is_alive()
        )
        
        return {
            'status': 'healthy' if is_healthy else 'unhealthy',
            'model_loaded': self.model is not None,
            'batch_processor_running': (
                self.batch_processor is not None and 
                self.batch_processor.is_alive()
            ),
            'pending_requests_count': len(self.pending_requests),
            'timestamp': datetime.now().isoformat()
        }
    
    def reload_model(self, new_model_path: Optional[str] = None) -> bool:
        """重新加载模型"""
        logger.info("开始重新加载模型")
        
        # 保存当前模型作为备份
        old_model = self.model
        old_version = self.model_version
        
        # 尝试加载新模型
        success = self.load_model(new_model_path)
        
        if success:
            logger.info(f"模型重新加载成功，从版本 {old_version} 更新到 {self.model_version}")
        else:
            # 恢复旧模型
            self.model = old_model
            self.model_version = old_version
            logger.error("模型重新加载失败，已恢复到旧版本")
        
        return success
    
    def shutdown(self):
        """关闭服务器"""
        logger.info("正在关闭模型服务器")
        
        # 等待待处理请求完成
        max_wait_time = 5.0  # 最多等待5秒
        start_time = time.time()
        
        while self.pending_requests and (time.time() - start_time) < max_wait_time:
            time.sleep(0.1)
        
        # 关闭线程池
        self.executor.shutdown(wait=True)
        
        logger.info("模型服务器已关闭")


class PredictionRequest:
    """预测请求"""
    
    def __init__(self, features: Dict[str, Any]):
        self.features = features
        self.result = None
        self.event = asyncio.Event()
        self.timestamp = time.time()
    
    async def get_result(self, timeout: float = 1.0) -> float:
        """获取预测结果"""
        try:
            await asyncio.wait_for(self.event.wait(), timeout=timeout)
            return self.result
        except asyncio.TimeoutError:
            logger.warning("预测请求超时")
            return 0.0
    
    def set_result(self, result: float):
        """设置预测结果"""
        self.result = result
        self.event.set()


class ModelManager:
    """模型管理器"""
    
    def __init__(self, models_dir: str):
        self.models_dir = Path(models_dir)
        self.active_servers = {}
        self.model_configs = {}
    
    def register_model(self, 
                      model_name: str,
                      model_path: str,
                      config: Optional[Dict[str, Any]] = None):
        """注册模型"""
        self.model_configs[model_name] = {
            'model_path': model_path,
            'config': config or {},
            'registered_time': datetime.now().isoformat()
        }
        
        logger.info(f"模型已注册: {model_name}")
    
    def start_model_server(self, model_name: str) -> bool:
        """启动模型服务器"""
        if model_name not in self.model_configs:
            logger.error(f"模型未注册: {model_name}")
            return False
        
        if model_name in self.active_servers:
            logger.warning(f"模型服务器已在运行: {model_name}")
            return True
        
        try:
            config = self.model_configs[model_name]
            server = ModelServer(
                model_path=config['model_path'],
                **config['config']
            )
            
            # 加载模型
            if server.load_model():
                self.active_servers[model_name] = server
                logger.info(f"模型服务器启动成功: {model_name}")
                return True
            else:
                logger.error(f"模型服务器启动失败: {model_name}")
                return False
                
        except Exception as e:
            logger.error(f"启动模型服务器时出错: {e}")
            return False
    
    def stop_model_server(self, model_name: str) -> bool:
        """停止模型服务器"""
        if model_name not in self.active_servers:
            logger.warning(f"模型服务器未运行: {model_name}")
            return True
        
        try:
            server = self.active_servers[model_name]
            server.shutdown()
            del self.active_servers[model_name]
            
            logger.info(f"模型服务器已停止: {model_name}")
            return True
            
        except Exception as e:
            logger.error(f"停止模型服务器时出错: {e}")
            return False
    
    def get_model_server(self, model_name: str) -> Optional[ModelServer]:
        """获取模型服务器"""
        return self.active_servers.get(model_name)
    
    def list_models(self) -> Dict[str, Any]:
        """列出所有模型"""
        models_info = {}
        
        for model_name, config in self.model_configs.items():
            is_active = model_name in self.active_servers
            server_stats = None
            
            if is_active:
                server = self.active_servers[model_name]
                server_stats = server.get_server_stats()
            
            models_info[model_name] = {
                'config': config,
                'is_active': is_active,
                'server_stats': server_stats
            }
        
        return models_info
    
    def health_check_all(self) -> Dict[str, Any]:
        """检查所有模型服务器的健康状态"""
        health_status = {}
        
        for model_name, server in self.active_servers.items():
            health_status[model_name] = server.health_check()
        
        return {
            'overall_status': 'healthy' if all(
                status['status'] == 'healthy' 
                for status in health_status.values()
            ) else 'unhealthy',
            'model_health': health_status,
            'timestamp': datetime.now().isoformat()
        }