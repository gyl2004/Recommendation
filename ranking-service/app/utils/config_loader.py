"""
配置加载工具
"""
import yaml
import os
from typing import Dict, Any, Optional
import logging
logger = logging.getLogger(__name__)


class ConfigLoader:
    """配置加载器"""
    
    @staticmethod
    def load_yaml_config(config_path: str) -> Dict[str, Any]:
        """
        加载YAML配置文件
        
        Args:
            config_path: 配置文件路径
            
        Returns:
            配置字典
        """
        try:
            if not os.path.exists(config_path):
                logger.warning(f"配置文件不存在: {config_path}")
                return {}
            
            with open(config_path, 'r', encoding='utf-8') as file:
                config = yaml.safe_load(file)
                logger.info(f"成功加载配置文件: {config_path}")
                return config or {}
                
        except Exception as e:
            logger.error(f"加载配置文件失败: {config_path}, 错误: {e}")
            return {}
    
    @staticmethod
    def load_fusion_reranking_config(config_path: Optional[str] = None) -> Dict[str, Any]:
        """
        加载融合重排配置
        
        Args:
            config_path: 配置文件路径，如果为None则使用默认路径
            
        Returns:
            融合重排配置字典
        """
        if config_path is None:
            # 使用默认配置路径
            current_dir = os.path.dirname(os.path.abspath(__file__))
            config_path = os.path.join(
                current_dir, '..', '..', 'config', 'fusion_reranking_config.yaml'
            )
        
        config = ConfigLoader.load_yaml_config(config_path)
        
        # 如果配置加载失败，返回默认配置
        if not config:
            logger.warning("使用默认融合重排配置")
            config = ConfigLoader._get_default_fusion_reranking_config()
        
        return config
    
    @staticmethod
    def _get_default_fusion_reranking_config() -> Dict[str, Any]:
        """
        获取默认融合重排配置
        
        Returns:
            默认配置字典
        """
        return {
            'algorithm_weights': {
                'collaborative_filtering': 0.3,
                'content_based': 0.3,
                'deep_learning': 0.4
            },
            'diversity_config': {
                'category_diversity_weight': 0.3,
                'content_type_diversity_weight': 0.2,
                'author_diversity_weight': 0.2,
                'time_diversity_weight': 0.3,
                'max_same_category_ratio': 0.4,
                'max_same_author_ratio': 0.3
            },
            'business_rules': {
                'min_content_quality_score': 0.6,
                'max_content_age_days': 30,
                'blocked_categories': [],
                'blocked_authors': [],
                'min_user_rating': 3.0,
                'require_content_review': True
            },
            'dedup_config': {
                'similarity_threshold': 0.8,
                'title_similarity_weight': 0.4,
                'content_similarity_weight': 0.6
            },
            'final_ranking_config': {
                'base_score_weight': 0.6,
                'freshness_boost_weight': 0.15,
                'popularity_boost_weight': 0.15,
                'personalization_boost_weight': 0.1,
                'freshness_half_life_hours': 24,
                'max_popularity_score': 20
            },
            'performance_config': {
                'max_candidates_per_algorithm': 100,
                'max_fusion_candidates': 500,
                'similarity_check_batch_size': 50,
                'enable_parallel_processing': True
            },
            'monitoring_config': {
                'log_processing_time': True,
                'log_filter_stats': True,
                'log_diversity_stats': True,
                'enable_metrics_collection': True
            }
        }
    
    @staticmethod
    def validate_fusion_reranking_config(config: Dict[str, Any]) -> bool:
        """
        验证融合重排配置的有效性
        
        Args:
            config: 配置字典
            
        Returns:
            配置是否有效
        """
        try:
            # 检查必需的配置节
            required_sections = [
                'algorithm_weights',
                'diversity_config',
                'business_rules',
                'dedup_config'
            ]
            
            for section in required_sections:
                if section not in config:
                    logger.error(f"缺少必需的配置节: {section}")
                    return False
            
            # 验证算法权重
            algorithm_weights = config['algorithm_weights']
            if not isinstance(algorithm_weights, dict) or not algorithm_weights:
                logger.error("算法权重配置无效")
                return False
            
            # 验证权重值
            for algorithm, weight in algorithm_weights.items():
                if not isinstance(weight, (int, float)) or weight < 0:
                    logger.error(f"算法权重值无效: {algorithm} = {weight}")
                    return False
            
            # 验证多样性配置
            diversity_config = config['diversity_config']
            required_diversity_keys = [
                'category_diversity_weight',
                'content_type_diversity_weight',
                'author_diversity_weight',
                'time_diversity_weight'
            ]
            
            for key in required_diversity_keys:
                if key not in diversity_config:
                    logger.error(f"缺少多样性配置项: {key}")
                    return False
                
                value = diversity_config[key]
                if not isinstance(value, (int, float)) or not (0 <= value <= 1):
                    logger.error(f"多样性配置值无效: {key} = {value}")
                    return False
            
            # 验证业务规则配置
            business_rules = config['business_rules']
            if 'min_content_quality_score' in business_rules:
                score = business_rules['min_content_quality_score']
                if not isinstance(score, (int, float)) or not (0 <= score <= 1):
                    logger.error(f"最低内容质量分数无效: {score}")
                    return False
            
            if 'max_content_age_days' in business_rules:
                days = business_rules['max_content_age_days']
                if not isinstance(days, int) or days < 0:
                    logger.error(f"最大内容年龄无效: {days}")
                    return False
            
            # 验证去重配置
            dedup_config = config['dedup_config']
            if 'similarity_threshold' in dedup_config:
                threshold = dedup_config['similarity_threshold']
                if not isinstance(threshold, (int, float)) or not (0 <= threshold <= 1):
                    logger.error(f"相似度阈值无效: {threshold}")
                    return False
            
            logger.info("融合重排配置验证通过")
            return True
            
        except Exception as e:
            logger.error(f"配置验证失败: {e}")
            return False
    
    @staticmethod
    def merge_configs(base_config: Dict[str, Any], 
                     override_config: Dict[str, Any]) -> Dict[str, Any]:
        """
        合并配置字典
        
        Args:
            base_config: 基础配置
            override_config: 覆盖配置
            
        Returns:
            合并后的配置
        """
        merged_config = base_config.copy()
        
        for key, value in override_config.items():
            if key in merged_config and isinstance(merged_config[key], dict) and isinstance(value, dict):
                # 递归合并嵌套字典
                merged_config[key] = ConfigLoader.merge_configs(merged_config[key], value)
            else:
                # 直接覆盖
                merged_config[key] = value
        
        return merged_config