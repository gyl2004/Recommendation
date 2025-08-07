"""
融合重排服务测试
"""
import pytest
import asyncio
import sys
import os
from datetime import datetime, timedelta
from typing import Dict, List, Any

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from app.services.fusion_reranking_service import FusionRerankingService


class TestFusionRerankingService:
    """融合重排服务测试类"""
    
    @pytest.fixture
    def service(self):
        """创建测试服务实例"""
        config = {
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
                'blocked_categories': ['spam'],
                'blocked_authors': ['blocked_author'],
                'min_user_rating': 3.0,
                'require_content_review': True
            },
            'dedup_config': {
                'similarity_threshold': 0.8,
                'title_similarity_weight': 0.4,
                'content_similarity_weight': 0.6
            }
        }
        return FusionRerankingService(config)
    
    @pytest.fixture
    def sample_algorithm_results(self):
        """创建示例算法结果"""
        return {
            'collaborative_filtering': [
                {
                    'content_id': 'content_1',
                    'content_type': 'article',
                    'title': 'Test Article 1',
                    'category': 'tech',
                    'score': 0.9,
                    'author_id': 'author_1',
                    'publish_time': datetime.now().isoformat(),
                    'quality_score': 0.8,
                    'review_status': 'approved',
                    'user_rating': 4.5
                },
                {
                    'content_id': 'content_2',
                    'content_type': 'video',
                    'title': 'Test Video 1',
                    'category': 'entertainment',
                    'score': 0.8,
                    'author_id': 'author_2',
                    'publish_time': datetime.now().isoformat(),
                    'quality_score': 0.7,
                    'review_status': 'approved',
                    'user_rating': 4.0
                }
            ],
            'content_based': [
                {
                    'content_id': 'content_1',  # 重复内容
                    'content_type': 'article',
                    'title': 'Test Article 1',
                    'category': 'tech',
                    'score': 0.85,
                    'author_id': 'author_1',
                    'publish_time': datetime.now().isoformat(),
                    'quality_score': 0.8,
                    'review_status': 'approved',
                    'user_rating': 4.5
                },
                {
                    'content_id': 'content_3',
                    'content_type': 'article',
                    'title': 'Test Article 2',
                    'category': 'sports',
                    'score': 0.75,
                    'author_id': 'author_3',
                    'publish_time': datetime.now().isoformat(),
                    'quality_score': 0.9,
                    'review_status': 'approved',
                    'user_rating': 4.2
                }
            ],
            'deep_learning': [
                {
                    'content_id': 'content_4',
                    'content_type': 'product',
                    'title': 'Test Product 1',
                    'category': 'shopping',
                    'score': 0.95,
                    'author_id': 'author_4',
                    'publish_time': datetime.now().isoformat(),
                    'quality_score': 0.85,
                    'review_status': 'approved',
                    'user_rating': 4.8
                }
            ]
        }
    
    @pytest.mark.asyncio
    async def test_fuse_and_rerank_basic(self, service, sample_algorithm_results):
        """测试基本融合重排功能"""
        result = await service.fuse_and_rerank(
            algorithm_results=sample_algorithm_results,
            user_id='test_user',
            target_size=10
        )
        
        # 验证结果
        assert isinstance(result, list)
        assert len(result) > 0
        assert len(result) <= 10
        
        # 验证每个结果都有必要的字段
        for item in result:
            assert 'content_id' in item
            assert 'fusion_score' in item
            assert 'final_score' in item
            assert 'algorithm_coverage' in item
    
    @pytest.mark.asyncio
    async def test_algorithm_fusion(self, service, sample_algorithm_results):
        """测试算法融合功能"""
        fused_results = await service._fuse_algorithm_results(sample_algorithm_results)
        
        # 验证融合结果
        assert isinstance(fused_results, list)
        assert len(fused_results) == 4  # 去重后应该有4个不同的内容
        
        # 验证融合得分
        for item in fused_results:
            assert 'fusion_score' in item
            assert 'algorithm_coverage' in item
            assert 'algorithm_details' in item
            assert 0 <= item['fusion_score'] <= 1.1  # 包含覆盖度奖励
    
    @pytest.mark.asyncio
    async def test_deduplication(self, service):
        """测试去重功能"""
        # 创建包含重复内容的测试数据
        results_with_duplicates = [
            {
                'content_id': 'content_1',
                'title': 'Test Article',
                'summary': 'This is a test article about technology',
                'fusion_score': 0.9
            },
            {
                'content_id': 'content_1',  # 精确重复
                'title': 'Test Article',
                'summary': 'This is a test article about technology',
                'fusion_score': 0.8
            },
            {
                'content_id': 'content_2',
                'title': 'Test Article Similar',  # 相似标题
                'summary': 'This is a test article about technology and innovation',
                'fusion_score': 0.85
            },
            {
                'content_id': 'content_3',
                'title': 'Different Article',
                'summary': 'This is completely different content',
                'fusion_score': 0.7
            }
        ]
        
        deduplicated = await service._deduplicate_results(results_with_duplicates)
        
        # 验证去重结果
        assert len(deduplicated) < len(results_with_duplicates)
        
        # 验证没有精确重复的content_id
        content_ids = [item['content_id'] for item in deduplicated]
        assert len(content_ids) == len(set(content_ids))
    
    @pytest.mark.asyncio
    async def test_business_rules_filtering(self, service):
        """测试业务规则过滤"""
        # 创建测试数据，包含各种违反业务规则的内容
        test_results = [
            {
                'content_id': 'content_1',
                'quality_score': 0.8,  # 符合质量要求
                'publish_time': datetime.now().isoformat(),  # 新内容
                'category': 'tech',  # 正常分类
                'author_id': 'author_1',  # 正常作者
                'user_rating': 4.0,  # 符合评分要求
                'review_status': 'approved'  # 已审核
            },
            {
                'content_id': 'content_2',
                'quality_score': 0.5,  # 质量不达标
                'publish_time': datetime.now().isoformat(),
                'category': 'tech',
                'author_id': 'author_1',
                'user_rating': 4.0,
                'review_status': 'approved'
            },
            {
                'content_id': 'content_3',
                'quality_score': 0.8,
                'publish_time': (datetime.now() - timedelta(days=40)).isoformat(),  # 内容过旧
                'category': 'tech',
                'author_id': 'author_1',
                'user_rating': 4.0,
                'review_status': 'approved'
            },
            {
                'content_id': 'content_4',
                'quality_score': 0.8,
                'publish_time': datetime.now().isoformat(),
                'category': 'spam',  # 被屏蔽的分类
                'author_id': 'author_1',
                'user_rating': 4.0,
                'review_status': 'approved'
            },
            {
                'content_id': 'content_5',
                'quality_score': 0.8,
                'publish_time': datetime.now().isoformat(),
                'category': 'tech',
                'author_id': 'blocked_author',  # 被屏蔽的作者
                'user_rating': 4.0,
                'review_status': 'approved'
            }
        ]
        
        filtered_results = await service._apply_business_rules(
            test_results, 'test_user'
        )
        
        # 验证过滤结果
        assert len(filtered_results) == 1  # 只有第一个内容符合所有规则
        assert filtered_results[0]['content_id'] == 'content_1'
    
    @pytest.mark.asyncio
    async def test_diversity_ensuring(self, service):
        """测试多样性保证"""
        # 创建缺乏多样性的测试数据
        test_results = []
        for i in range(10):
            test_results.append({
                'content_id': f'content_{i}',
                'content_type': 'article' if i < 8 else 'video',  # 大部分是文章
                'category': 'tech' if i < 7 else 'sports',  # 大部分是科技
                'author_id': 'author_1' if i < 6 else f'author_{i}',  # 大部分是同一作者
                'publish_time': datetime.now().isoformat(),
                'fusion_score': 0.9 - i * 0.05  # 递减得分
            })
        
        diversified_results = await service._ensure_diversity(test_results, 5)
        
        # 验证多样性
        assert len(diversified_results) == 5
        
        # 统计各维度的分布
        content_types = [item['content_type'] for item in diversified_results]
        categories = [item['category'] for item in diversified_results]
        authors = [item['author_id'] for item in diversified_results]
        
        # 验证多样性改善
        assert len(set(content_types)) > 1 or len(content_types) <= 2  # 内容类型有多样性
        assert len(set(categories)) > 1 or len(categories) <= 3  # 分类有多样性
        assert len(set(authors)) >= 2  # 作者有多样性
    
    def test_text_similarity_calculation(self, service):
        """测试文本相似度计算"""
        # 测试相同文本
        similarity = service._calculate_text_similarity("hello world", "hello world")
        assert similarity == 1.0
        
        # 测试完全不同的文本
        similarity = service._calculate_text_similarity("hello world", "foo bar")
        assert similarity == 0.0
        
        # 测试部分相似的文本
        similarity = service._calculate_text_similarity("hello world test", "hello world example")
        assert 0 < similarity < 1
        
        # 测试空文本
        similarity = service._calculate_text_similarity("", "hello")
        assert similarity == 0.0
    
    def test_freshness_boost_calculation(self, service):
        """测试新鲜度加权计算"""
        # 测试新内容
        fresh_content = {
            'publish_time': datetime.now().isoformat()
        }
        freshness = service._calculate_freshness_boost(fresh_content)
        assert 0.8 <= freshness <= 1.0
        
        # 测试旧内容
        old_content = {
            'publish_time': (datetime.now() - timedelta(days=7)).isoformat()
        }
        freshness = service._calculate_freshness_boost(old_content)
        assert 0.0 <= freshness < 0.8
        
        # 测试没有发布时间的内容
        no_time_content = {}
        freshness = service._calculate_freshness_boost(no_time_content)
        assert freshness == 0.5
    
    def test_popularity_boost_calculation(self, service):
        """测试热度加权计算"""
        # 测试高热度内容
        popular_content = {
            'view_count': 10000,
            'like_count': 1000,
            'share_count': 100,
            'comment_count': 50
        }
        popularity = service._calculate_popularity_boost(popular_content)
        assert 0.5 <= popularity <= 1.0
        
        # 测试低热度内容
        unpopular_content = {
            'view_count': 10,
            'like_count': 1,
            'share_count': 0,
            'comment_count': 0
        }
        popularity = service._calculate_popularity_boost(unpopular_content)
        assert 0.0 <= popularity < 0.5
        
        # 测试没有热度数据的内容
        no_data_content = {}
        popularity = service._calculate_popularity_boost(no_data_content)
        assert popularity >= 0.0
    
    def test_config_management(self, service):
        """测试配置管理"""
        # 获取初始配置
        initial_config = service.get_service_config()
        assert 'algorithm_weights' in initial_config
        assert 'diversity_config' in initial_config
        assert 'business_rules' in initial_config
        assert 'dedup_config' in initial_config
        
        # 更新配置
        new_config = {
            'algorithm_weights': {
                'collaborative_filtering': 0.5,
                'content_based': 0.5
            }
        }
        service.update_config(new_config)
        
        # 验证配置更新
        updated_config = service.get_service_config()
        assert updated_config['algorithm_weights']['collaborative_filtering'] == 0.5
        assert updated_config['algorithm_weights']['content_based'] == 0.5
    
    @pytest.mark.asyncio
    async def test_empty_input_handling(self, service):
        """测试空输入处理"""
        # 测试空算法结果
        result = await service.fuse_and_rerank({}, 'test_user', 10)
        assert result == []
        
        # 测试空候选列表
        result = await service._deduplicate_results([])
        assert result == []
        
        # 测试空多样性处理
        result = await service._ensure_diversity([], 10)
        assert result == []
    
    @pytest.mark.asyncio
    async def test_error_handling(self, service):
        """测试错误处理"""
        # 测试格式错误的算法结果
        malformed_results = {
            'test_algorithm': [
                {'content_id': 'test'}  # 缺少必要字段
            ]
        }
        
        # 应该不会抛出异常，而是返回降级结果
        result = await service.fuse_and_rerank(malformed_results, 'test_user', 10)
        assert isinstance(result, list)


if __name__ == "__main__":
    pytest.main([__file__])