"""
排序服务配置
"""
import os
from typing import List, Dict, Any


class Config:
    """基础配置"""
    
    # 服务配置
    SERVICE_NAME = "ranking-service"
    SERVICE_VERSION = "1.0.0"
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", 8002))
    
    # Redis配置
    REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
    REDIS_DB = int(os.getenv("REDIS_DB", 0))
    REDIS_PASSWORD = os.getenv("REDIS_PASSWORD")
    
    # 模型配置
    MODEL_PATH = os.getenv("MODEL_PATH", "models/wide_deep_model")
    PIPELINE_PATH = os.getenv("PIPELINE_PATH", "models/feature_pipeline.pkl")
    MODEL_UPDATE_INTERVAL = int(os.getenv("MODEL_UPDATE_INTERVAL", 3600))  # 秒
    
    # 特征配置
    FEATURE_CACHE_TTL = int(os.getenv("FEATURE_CACHE_TTL", 3600))  # 秒
    MAX_BATCH_SIZE = int(os.getenv("MAX_BATCH_SIZE", 1000))
    
    # 性能配置
    MAX_CANDIDATES = int(os.getenv("MAX_CANDIDATES", 1000))
    PREDICTION_TIMEOUT = float(os.getenv("PREDICTION_TIMEOUT", 5.0))  # 秒
    
    # 日志配置
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
    LOG_FILE = os.getenv("LOG_FILE", "logs/ranking_service.log")
    
    # 监控配置
    ENABLE_METRICS = os.getenv("ENABLE_METRICS", "true").lower() == "true"
    METRICS_PORT = int(os.getenv("METRICS_PORT", 8003))


class DevelopmentConfig(Config):
    """开发环境配置"""
    DEBUG = True
    LOG_LEVEL = "DEBUG"


class ProductionConfig(Config):
    """生产环境配置"""
    DEBUG = False
    LOG_LEVEL = "INFO"
    
    # 生产环境的Redis配置
    REDIS_URL = os.getenv("REDIS_URL", "redis://redis-cluster:6379")
    
    # 生产环境的模型路径
    MODEL_PATH = os.getenv("MODEL_PATH", "/app/models/wide_deep_model")
    PIPELINE_PATH = os.getenv("PIPELINE_PATH", "/app/models/feature_pipeline.pkl")


class TestConfig(Config):
    """测试环境配置"""
    DEBUG = True
    TESTING = True
    
    # 测试用的内存Redis
    REDIS_URL = "redis://localhost:6379/1"


# 配置映射
config_map = {
    'development': DevelopmentConfig,
    'production': ProductionConfig,
    'testing': TestConfig,
    'default': DevelopmentConfig
}


def get_config(config_name: str = None) -> Config:
    """获取配置"""
    if config_name is None:
        config_name = os.getenv('FLASK_ENV', 'default')
    
    return config_map.get(config_name, DevelopmentConfig)