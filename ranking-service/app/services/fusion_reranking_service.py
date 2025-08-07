"""
推荐结果融合和重排服务
实现多算法结果融合、去重、多样性保证和业务规则过滤
"""
import asyncio
import numpy as np
from typing import Dict, List, Optional, Any, Set, Tuple
import logging
logger = logging.getLogger(__name__)
from datetime import datetime, timedelta
from collections import defaultdict
import math
import random

from ..utils.config_loader import ConfigLoader


class FusionRerankingService:
    """推荐结果融合和重排服务"""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None, config_path: Optional[str] = None):
        """
        初始化融合重排服务
        
        Args:
            config: 配置参数，如果提供则优先使用
            config_path: 配置文件路径
        """
        # 加载配置
        if config is not None:
            self.config = config
        else:
            try:
                self.config = ConfigLoader.load_fusion_reranking_config(config_path)
            except:
                # 如果配置加载失败，使用默认配置
                self.config = self._get_default_config()
        
        # 验证配置
        try:
            if not ConfigLoader.validate_fusion_reranking_config(self.config):
                logger.warning("配置验证失败，使用默认配置")
                self.config = self._get_default_config()
        except:
            logger.warning("配置验证出错，使用默认配置")
            self.config = self._get_default_config()
        
        # 算法权重配置
        self.algorithm_weights = self.config.get('algorithm_weights', {})
        
        # 多样性参数
        self.diversity_config = self.config.get('diversity_config', {})
        
        # 业务规则配置
        self.business_rules = self.config.get('business_rules', {})
        
        # 去重配置
        self.dedup_config = self.config.get('dedup_config', {})
        
        # 最终排序配置
        self.final_ranking_config = self.config.get('final_ranking_config', {})
        
        # 性能配置
        self.performance_config = self.config.get('performance_config', {})
        
        # 监控配置
        self.monitoring_config = self.config.get('monitoring_config', {})
        
        logger.info("融合重排服务初始化完成")
    
    def _get_default_config(self) -> Dict[str, Any]:
        """获取默认配置"""
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
            }
        }
    
    async def fuse_and_rerank(self,
                            algorithm_results: Dict[str, List[Dict[str, Any]]],
                            user_id: str,
                            target_size: int = 20,
                            context: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """
        融合多算法结果并重排
        
        Args:
            algorithm_results: 各算法的推荐结果 {algorithm_name: [content_list]}
            user_id: 用户ID
            target_size: 目标结果数量
            context: 上下文信息
            
        Returns:
            融合重排后的推荐结果
        """
        start_time = datetime.now()
        
        try:
            # 1. 多算法结果融合
            fused_results = await self._fuse_algorithm_results(algorithm_results)
            logger.info(f"算法融合完成，得到 {len(fused_results)} 个候选内容")
            
            # 2. 去重处理
            deduplicated_results = await self._deduplicate_results(fused_results)
            logger.info(f"去重完成，剩余 {len(deduplicated_results)} 个候选内容")
            
            # 3. 业务规则过滤
            filtered_results = await self._apply_business_rules(
                deduplicated_results, user_id, context
            )
            logger.info(f"业务规则过滤完成，剩余 {len(filtered_results)} 个候选内容")
            
            # 4. 多样性重排
            diversified_results = await self._ensure_diversity(
                filtered_results, target_size
            )
            logger.info(f"多样性重排完成，最终 {len(diversified_results)} 个推荐内容")
            
            # 5. 最终排序优化
            final_results = await self._final_ranking_optimization(
                diversified_results, user_id, context
            )
            
            processing_time = (datetime.now() - start_time).total_seconds()
            logger.info(f"融合重排完成，耗时 {processing_time:.3f}s")
            
            return final_results[:target_size]
            
        except Exception as e:
            logger.error(f"融合重排过程出错: {e}")
            # 返回第一个算法的结果作为降级方案
            fallback_results = []
            for algorithm_name, results in algorithm_results.items():
                if results:
                    fallback_results = results[:target_size]
                    break
            return fallback_results
    
    async def _fuse_algorithm_results(self,
                                    algorithm_results: Dict[str, List[Dict[str, Any]]]) -> List[Dict[str, Any]]:
        """
        融合多算法结果
        
        Args:
            algorithm_results: 各算法的推荐结果
            
        Returns:
            融合后的结果列表
        """
        # 收集所有内容及其来源算法得分
        content_scores = defaultdict(dict)
        all_contents = {}
        
        for algorithm_name, results in algorithm_results.items():
            algorithm_weight = self.algorithm_weights.get(algorithm_name, 0.1)
            
            for idx, content in enumerate(results):
                content_id = content['content_id']
                
                # 存储内容信息
                if content_id not in all_contents:
                    all_contents[content_id] = content.copy()
                
                # 计算位置得分 (排名越靠前得分越高)
                position_score = 1.0 / (idx + 1)
                
                # 获取算法原始得分
                original_score = content.get('score', content.get('ranking_score', 0.5))
                
                # 综合得分 = 原始得分 * 位置得分
                combined_score = original_score * position_score
                
                # 存储算法得分
                content_scores[content_id][algorithm_name] = {
                    'score': combined_score,
                    'weight': algorithm_weight,
                    'position': idx
                }
        
        # 计算融合得分
        fused_results = []
        for content_id, content in all_contents.items():
            algorithm_scores = content_scores[content_id]
            
            # 加权融合得分
            weighted_score = 0.0
            total_weight = 0.0
            
            for algorithm_name, score_info in algorithm_scores.items():
                weighted_score += score_info['score'] * score_info['weight']
                total_weight += score_info['weight']
            
            # 归一化得分
            if total_weight > 0:
                final_score = weighted_score / total_weight
            else:
                final_score = 0.0
            
            # 计算算法覆盖度奖励 (被更多算法推荐的内容得分更高)
            coverage_bonus = len(algorithm_scores) / len(self.algorithm_weights) * 0.1
            final_score += coverage_bonus
            
            # 更新内容得分
            content_with_score = content.copy()
            content_with_score['fusion_score'] = final_score
            content_with_score['algorithm_coverage'] = len(algorithm_scores)
            content_with_score['algorithm_details'] = algorithm_scores
            
            fused_results.append(content_with_score)
        
        # 按融合得分排序
        fused_results.sort(key=lambda x: x['fusion_score'], reverse=True)
        
        return fused_results
    
    async def _deduplicate_results(self,
                                 results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        去重处理
        
        Args:
            results: 待去重的结果列表
            
        Returns:
            去重后的结果列表
        """
        if not results:
            return results
        
        deduplicated = []
        seen_content_ids = set()
        similar_contents = set()
        
        for content in results:
            content_id = content['content_id']
            
            # 基于content_id的精确去重
            if content_id in seen_content_ids:
                continue
            
            # 基于相似度的去重
            if await self._is_similar_to_existing(content, deduplicated):
                similar_contents.add(content_id)
                continue
            
            seen_content_ids.add(content_id)
            deduplicated.append(content)
        
        logger.info(f"去重统计: 精确重复 {len(results) - len(deduplicated) - len(similar_contents)} 个, "
                   f"相似重复 {len(similar_contents)} 个")
        
        return deduplicated
    
    async def _is_similar_to_existing(self,
                                    content: Dict[str, Any],
                                    existing_contents: List[Dict[str, Any]]) -> bool:
        """
        检查内容是否与已有内容相似
        
        Args:
            content: 待检查的内容
            existing_contents: 已有内容列表
            
        Returns:
            是否相似
        """
        content_title = content.get('title', '').lower()
        content_summary = content.get('summary', content.get('description', '')).lower()
        
        for existing in existing_contents:
            existing_title = existing.get('title', '').lower()
            existing_summary = existing.get('summary', existing.get('description', '')).lower()
            
            # 计算标题相似度
            title_similarity = self._calculate_text_similarity(content_title, existing_title)
            
            # 计算内容相似度
            content_similarity = self._calculate_text_similarity(content_summary, existing_summary)
            
            # 综合相似度
            overall_similarity = (
                title_similarity * self.dedup_config.get('title_similarity_weight', 0.4) +
                content_similarity * self.dedup_config.get('content_similarity_weight', 0.6)
            )
            
            if overall_similarity > self.dedup_config.get('similarity_threshold', 0.8):
                return True
        
        return False
    
    def _calculate_text_similarity(self, text1: str, text2: str) -> float:
        """
        计算文本相似度 (简单的Jaccard相似度)
        
        Args:
            text1: 文本1
            text2: 文本2
            
        Returns:
            相似度分数 (0-1)
        """
        if not text1 or not text2:
            return 0.0
        
        # 分词 (简单按空格分割)
        words1 = set(text1.split())
        words2 = set(text2.split())
        
        if not words1 and not words2:
            return 1.0
        
        # Jaccard相似度
        intersection = len(words1.intersection(words2))
        union = len(words1.union(words2))
        
        return intersection / union if union > 0 else 0.0
    
    async def _apply_business_rules(self,
                                  results: List[Dict[str, Any]],
                                  user_id: str,
                                  context: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """
        应用业务规则过滤
        
        Args:
            results: 待过滤的结果列表
            user_id: 用户ID
            context: 上下文信息
            
        Returns:
            过滤后的结果列表
        """
        filtered_results = []
        filter_stats = defaultdict(int)
        
        current_time = datetime.now()
        
        for content in results:
            # 内容质量检查
            quality_score = content.get('quality_score', 0.8)
            min_quality = self.business_rules.get('min_content_quality_score', 0.6)
            if quality_score < min_quality:
                filter_stats['low_quality'] += 1
                continue
            
            # 内容时效性检查
            publish_time = content.get('publish_time')
            if publish_time:
                try:
                    if isinstance(publish_time, str):
                        publish_datetime = datetime.fromisoformat(publish_time.replace('Z', '+00:00'))
                    else:
                        publish_datetime = publish_time
                    
                    content_age = (current_time - publish_datetime).days
                    max_age = self.business_rules.get('max_content_age_days', 30)
                    if content_age > max_age:
                        filter_stats['too_old'] += 1
                        continue
                except Exception as e:
                    logger.warning(f"解析发布时间失败: {e}")
            
            # 分类黑名单检查
            category = content.get('category', '')
            blocked_categories = self.business_rules.get('blocked_categories', [])
            if category in blocked_categories:
                filter_stats['blocked_category'] += 1
                continue
            
            # 作者黑名单检查
            author_id = content.get('author_id', '')
            blocked_authors = self.business_rules.get('blocked_authors', [])
            if author_id in blocked_authors:
                filter_stats['blocked_author'] += 1
                continue
            
            # 用户评分检查
            user_rating = content.get('user_rating', 5.0)
            min_rating = self.business_rules.get('min_user_rating', 3.0)
            if user_rating < min_rating:
                filter_stats['low_rating'] += 1
                continue
            
            # 内容审核状态检查
            if self.business_rules.get('require_content_review', True):
                review_status = content.get('review_status', 'pending')
                if review_status != 'approved':
                    filter_stats['not_reviewed'] += 1
                    continue
            
            # 用户个性化过滤
            if not await self._check_user_preferences(content, user_id, context):
                filter_stats['user_preference'] += 1
                continue
            
            filtered_results.append(content)
        
        logger.info(f"业务规则过滤统计: {dict(filter_stats)}")
        
        return filtered_results
    
    async def _check_user_preferences(self,
                                    content: Dict[str, Any],
                                    user_id: str,
                                    context: Optional[Dict[str, Any]] = None) -> bool:
        """
        检查用户个性化偏好
        
        Args:
            content: 内容信息
            user_id: 用户ID
            context: 上下文信息
            
        Returns:
            是否符合用户偏好
        """
        # 这里可以添加更复杂的用户偏好检查逻辑
        # 例如：用户不感兴趣的标签、已读内容、时间偏好等
        
        # 检查用户是否已经阅读过类似内容
        content_type = content.get('content_type', '')
        category = content.get('category', '')
        
        # 简单的时间偏好检查
        if context and 'user_active_hours' in context:
            current_hour = datetime.now().hour
            active_hours = context['user_active_hours']
            if current_hour not in active_hours:
                # 如果不在用户活跃时间，降低推荐优先级但不完全过滤
                pass
        
        return True
    
    async def _ensure_diversity(self,
                              results: List[Dict[str, Any]],
                              target_size: int) -> List[Dict[str, Any]]:
        """
        确保推荐结果多样性
        
        Args:
            results: 待处理的结果列表
            target_size: 目标结果数量
            
        Returns:
            多样性处理后的结果列表
        """
        if len(results) <= target_size:
            return results
        
        # 多样性重排算法 - MMR (Maximal Marginal Relevance)
        diversified = []
        remaining = results.copy()
        
        # 各维度的计数器
        category_count = defaultdict(int)
        content_type_count = defaultdict(int)
        author_count = defaultdict(int)
        time_bucket_count = defaultdict(int)
        
        # 第一个选择得分最高的
        if remaining:
            best_item = remaining.pop(0)
            diversified.append(best_item)
            
            # 更新计数器
            self._update_diversity_counters(
                best_item, category_count, content_type_count, 
                author_count, time_bucket_count
            )
        
        # 迭代选择剩余项目
        while len(diversified) < target_size and remaining:
            best_candidate = None
            best_score = -1
            best_idx = -1
            
            for idx, candidate in enumerate(remaining):
                # 计算多样性得分
                diversity_score = self._calculate_diversity_score(
                    candidate, diversified, category_count, 
                    content_type_count, author_count, time_bucket_count
                )
                
                # 原始相关性得分
                relevance_score = candidate.get('fusion_score', 0.0)
                
                # 综合得分 = λ * 相关性 + (1-λ) * 多样性
                lambda_param = 0.7  # 相关性权重
                combined_score = (
                    lambda_param * relevance_score + 
                    (1 - lambda_param) * diversity_score
                )
                
                if combined_score > best_score:
                    best_score = combined_score
                    best_candidate = candidate
                    best_idx = idx
            
            if best_candidate:
                diversified.append(best_candidate)
                remaining.pop(best_idx)
                
                # 更新计数器
                self._update_diversity_counters(
                    best_candidate, category_count, content_type_count,
                    author_count, time_bucket_count
                )
        
        logger.info(f"多样性统计 - 分类: {dict(category_count)}, "
                   f"内容类型: {dict(content_type_count)}, "
                   f"作者: {len(author_count)} 个")
        
        return diversified
    
    def _update_diversity_counters(self,
                                 content: Dict[str, Any],
                                 category_count: Dict[str, int],
                                 content_type_count: Dict[str, int],
                                 author_count: Dict[str, int],
                                 time_bucket_count: Dict[str, int]):
        """更新多样性计数器"""
        category_count[content.get('category', 'unknown')] += 1
        content_type_count[content.get('content_type', 'unknown')] += 1
        author_count[content.get('author_id', 'unknown')] += 1
        
        # 时间桶 (按小时分组)
        publish_time = content.get('publish_time')
        if publish_time:
            try:
                if isinstance(publish_time, str):
                    dt = datetime.fromisoformat(publish_time.replace('Z', '+00:00'))
                else:
                    dt = publish_time
                time_bucket = f"{dt.date()}_{dt.hour//6}"  # 6小时为一个时间桶
                time_bucket_count[time_bucket] += 1
            except:
                time_bucket_count['unknown'] += 1
    
    def _calculate_diversity_score(self,
                                 candidate: Dict[str, Any],
                                 selected: List[Dict[str, Any]],
                                 category_count: Dict[str, int],
                                 content_type_count: Dict[str, int],
                                 author_count: Dict[str, int],
                                 time_bucket_count: Dict[str, int]) -> float:
        """
        计算候选内容的多样性得分
        
        Args:
            candidate: 候选内容
            selected: 已选择的内容列表
            category_count: 分类计数
            content_type_count: 内容类型计数
            author_count: 作者计数
            time_bucket_count: 时间桶计数
            
        Returns:
            多样性得分 (0-1)
        """
        if not selected:
            return 1.0
        
        total_selected = len(selected)
        diversity_score = 0.0
        
        # 分类多样性
        candidate_category = candidate.get('category', 'unknown')
        category_ratio = category_count.get(candidate_category, 0) / total_selected
        max_category_ratio = self.diversity_config.get('max_same_category_ratio', 0.4)
        category_penalty = max(0, category_ratio - max_category_ratio)
        category_diversity = 1.0 - category_penalty
        category_weight = self.diversity_config.get('category_diversity_weight', 0.3)
        diversity_score += category_diversity * category_weight
        
        # 内容类型多样性
        candidate_type = candidate.get('content_type', 'unknown')
        type_ratio = content_type_count.get(candidate_type, 0) / total_selected
        type_diversity = 1.0 - type_ratio
        type_weight = self.diversity_config.get('content_type_diversity_weight', 0.2)
        diversity_score += type_diversity * type_weight
        
        # 作者多样性
        candidate_author = candidate.get('author_id', 'unknown')
        author_ratio = author_count.get(candidate_author, 0) / total_selected
        max_author_ratio = self.diversity_config.get('max_same_author_ratio', 0.3)
        author_penalty = max(0, author_ratio - max_author_ratio)
        author_diversity = 1.0 - author_penalty
        author_weight = self.diversity_config.get('author_diversity_weight', 0.2)
        diversity_score += author_diversity * author_weight
        
        # 时间多样性
        publish_time = candidate.get('publish_time')
        if publish_time:
            try:
                if isinstance(publish_time, str):
                    dt = datetime.fromisoformat(publish_time.replace('Z', '+00:00'))
                else:
                    dt = publish_time
                time_bucket = f"{dt.date()}_{dt.hour//6}"
                time_ratio = time_bucket_count.get(time_bucket, 0) / total_selected
                time_diversity = 1.0 - time_ratio
                time_weight = self.diversity_config.get('time_diversity_weight', 0.3)
                diversity_score += time_diversity * time_weight
            except:
                time_weight = self.diversity_config.get('time_diversity_weight', 0.3)
                diversity_score += 0.5 * time_weight
        
        return min(1.0, max(0.0, diversity_score))
    
    async def _final_ranking_optimization(self,
                                        results: List[Dict[str, Any]],
                                        user_id: str,
                                        context: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """
        最终排序优化
        
        Args:
            results: 待优化的结果列表
            user_id: 用户ID
            context: 上下文信息
            
        Returns:
            优化后的结果列表
        """
        if not results:
            return results
        
        # 应用最终调整策略
        optimized_results = []
        
        for content in results:
            optimized_content = content.copy()
            
            # 计算最终得分
            base_score = content.get('fusion_score', 0.0)
            
            # 新鲜度加权
            freshness_boost = self._calculate_freshness_boost(content)
            
            # 热度加权
            popularity_boost = self._calculate_popularity_boost(content)
            
            # 个性化加权
            personalization_boost = await self._calculate_personalization_boost(
                content, user_id, context
            )
            
            # 最终得分 (使用配置中的权重)
            base_weight = self.final_ranking_config.get('base_score_weight', 0.6)
            freshness_weight = self.final_ranking_config.get('freshness_boost_weight', 0.15)
            popularity_weight = self.final_ranking_config.get('popularity_boost_weight', 0.15)
            personalization_weight = self.final_ranking_config.get('personalization_boost_weight', 0.1)
            
            final_score = (
                base_score * base_weight +
                freshness_boost * freshness_weight +
                popularity_boost * popularity_weight +
                personalization_boost * personalization_weight
            )
            
            optimized_content['final_score'] = final_score
            optimized_content['score_breakdown'] = {
                'base_score': base_score,
                'freshness_boost': freshness_boost,
                'popularity_boost': popularity_boost,
                'personalization_boost': personalization_boost
            }
            
            optimized_results.append(optimized_content)
        
        # 按最终得分排序
        optimized_results.sort(key=lambda x: x['final_score'], reverse=True)
        
        return optimized_results
    
    def _calculate_freshness_boost(self, content: Dict[str, Any]) -> float:
        """计算新鲜度加权"""
        publish_time = content.get('publish_time')
        if not publish_time:
            return 0.5
        
        try:
            if isinstance(publish_time, str):
                dt = datetime.fromisoformat(publish_time.replace('Z', '+00:00'))
            else:
                dt = publish_time
            
            # 计算内容年龄 (小时)
            age_hours = (datetime.now() - dt).total_seconds() / 3600
            
            # 新鲜度衰减函数 (指数衰减)
            half_life_hours = self.final_ranking_config.get('freshness_half_life_hours', 24)
            freshness = math.exp(-age_hours / half_life_hours)
            
            return min(1.0, max(0.0, freshness))
            
        except Exception as e:
            logger.warning(f"计算新鲜度失败: {e}")
            return 0.5
    
    def _calculate_popularity_boost(self, content: Dict[str, Any]) -> float:
        """计算热度加权"""
        # 综合多个热度指标
        view_count = content.get('view_count', 0)
        like_count = content.get('like_count', 0)
        share_count = content.get('share_count', 0)
        comment_count = content.get('comment_count', 0)
        
        # 归一化处理 (简单的对数归一化)
        popularity_score = (
            math.log(view_count + 1) * 0.4 +
            math.log(like_count + 1) * 0.3 +
            math.log(share_count + 1) * 0.2 +
            math.log(comment_count + 1) * 0.1
        )
        
        # 归一化到 0-1 范围
        max_expected_score = self.final_ranking_config.get('max_popularity_score', 20)
        normalized_score = min(1.0, popularity_score / max_expected_score)
        
        return normalized_score
    
    async def _calculate_personalization_boost(self,
                                             content: Dict[str, Any],
                                             user_id: str,
                                             context: Optional[Dict[str, Any]] = None) -> float:
        """计算个性化加权"""
        # 这里可以集成更复杂的个性化逻辑
        # 例如：用户历史行为、兴趣标签匹配等
        
        personalization_score = 0.5  # 默认得分
        
        # 基于上下文的简单个性化
        if context:
            # 时间偏好
            current_hour = datetime.now().hour
            user_active_hours = context.get('user_active_hours', [])
            if user_active_hours and current_hour in user_active_hours:
                personalization_score += 0.2
            
            # 设备类型偏好
            device_type = context.get('device_type', '')
            content_device_preference = content.get('device_preference', [])
            if device_type in content_device_preference:
                personalization_score += 0.1
        
        return min(1.0, max(0.0, personalization_score))
    
    def get_service_config(self) -> Dict[str, Any]:
        """获取服务配置"""
        return {
            'algorithm_weights': self.algorithm_weights,
            'diversity_config': self.diversity_config,
            'business_rules': self.business_rules,
            'dedup_config': self.dedup_config
        }
    
    def update_config(self, new_config: Dict[str, Any]):
        """更新服务配置"""
        if 'algorithm_weights' in new_config:
            self.algorithm_weights.update(new_config['algorithm_weights'])
        
        if 'diversity_config' in new_config:
            self.diversity_config.update(new_config['diversity_config'])
        
        if 'business_rules' in new_config:
            self.business_rules.update(new_config['business_rules'])
        
        if 'dedup_config' in new_config:
            self.dedup_config.update(new_config['dedup_config'])
        
        logger.info("融合重排服务配置已更新")