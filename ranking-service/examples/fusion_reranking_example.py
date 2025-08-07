"""
融合重排服务使用示例
"""
import asyncio
import json
from datetime import datetime, timedelta
from typing import Dict, List, Any

# 假设我们在ranking-service目录下运行
import sys
import os
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

from app.services.fusion_reranking_service import FusionRerankingService


async def create_sample_algorithm_results() -> Dict[str, List[Dict[str, Any]]]:
    """创建示例算法结果"""
    
    # 协同过滤算法结果
    collaborative_filtering_results = [
        {
            'content_id': 'article_001',
            'content_type': 'article',
            'title': '人工智能的未来发展趋势',
            'category': 'technology',
            'author_id': 'author_001',
            'publish_time': (datetime.now() - timedelta(hours=2)).isoformat(),
            'score': 0.92,
            'quality_score': 0.85,
            'review_status': 'approved',
            'user_rating': 4.5,
            'view_count': 15000,
            'like_count': 1200,
            'share_count': 300,
            'comment_count': 150
        },
        {
            'content_id': 'video_001',
            'content_type': 'video',
            'title': '机器学习入门教程',
            'category': 'education',
            'author_id': 'author_002',
            'publish_time': (datetime.now() - timedelta(hours=6)).isoformat(),
            'score': 0.88,
            'quality_score': 0.90,
            'review_status': 'approved',
            'user_rating': 4.7,
            'view_count': 25000,
            'like_count': 2100,
            'share_count': 450,
            'comment_count': 280
        },
        {
            'content_id': 'product_001',
            'content_type': 'product',
            'title': '智能手表推荐',
            'category': 'shopping',
            'author_id': 'author_003',
            'publish_time': (datetime.now() - timedelta(hours=12)).isoformat(),
            'score': 0.85,
            'quality_score': 0.80,
            'review_status': 'approved',
            'user_rating': 4.2,
            'view_count': 8000,
            'like_count': 650,
            'share_count': 120,
            'comment_count': 85
        }
    ]
    
    # 基于内容的推荐算法结果
    content_based_results = [
        {
            'content_id': 'article_001',  # 与协同过滤重复
            'content_type': 'article',
            'title': '人工智能的未来发展趋势',
            'category': 'technology',
            'author_id': 'author_001',
            'publish_time': (datetime.now() - timedelta(hours=2)).isoformat(),
            'score': 0.89,
            'quality_score': 0.85,
            'review_status': 'approved',
            'user_rating': 4.5,
            'view_count': 15000,
            'like_count': 1200,
            'share_count': 300,
            'comment_count': 150
        },
        {
            'content_id': 'article_002',
            'content_type': 'article',
            'title': '深度学习在图像识别中的应用',
            'category': 'technology',
            'author_id': 'author_004',
            'publish_time': (datetime.now() - timedelta(hours=4)).isoformat(),
            'score': 0.86,
            'quality_score': 0.88,
            'review_status': 'approved',
            'user_rating': 4.3,
            'view_count': 12000,
            'like_count': 980,
            'share_count': 220,
            'comment_count': 120
        },
        {
            'content_id': 'article_003',
            'content_type': 'article',
            'title': '区块链技术解析',
            'category': 'technology',
            'author_id': 'author_001',  # 与第一篇文章同作者
            'publish_time': (datetime.now() - timedelta(hours=8)).isoformat(),
            'score': 0.82,
            'quality_score': 0.75,
            'review_status': 'approved',
            'user_rating': 4.0,
            'view_count': 9500,
            'like_count': 720,
            'share_count': 180,
            'comment_count': 95
        }
    ]
    
    # 深度学习算法结果
    deep_learning_results = [
        {
            'content_id': 'video_002',
            'content_type': 'video',
            'title': 'Python编程实战',
            'category': 'education',
            'author_id': 'author_005',
            'publish_time': (datetime.now() - timedelta(hours=1)).isoformat(),
            'score': 0.94,
            'quality_score': 0.92,
            'review_status': 'approved',
            'user_rating': 4.8,
            'view_count': 18000,
            'like_count': 1500,
            'share_count': 380,
            'comment_count': 200
        },
        {
            'content_id': 'article_004',
            'content_type': 'article',
            'title': '云计算服务对比',
            'category': 'technology',
            'author_id': 'author_006',
            'publish_time': (datetime.now() - timedelta(hours=3)).isoformat(),
            'score': 0.90,
            'quality_score': 0.87,
            'review_status': 'approved',
            'user_rating': 4.4,
            'view_count': 11000,
            'like_count': 890,
            'share_count': 210,
            'comment_count': 110
        },
        {
            'content_id': 'product_002',
            'content_type': 'product',
            'title': '笔记本电脑选购指南',
            'category': 'shopping',
            'author_id': 'author_007',
            'publish_time': (datetime.now() - timedelta(hours=5)).isoformat(),
            'score': 0.87,
            'quality_score': 0.83,
            'review_status': 'approved',
            'user_rating': 4.1,
            'view_count': 13500,
            'like_count': 1100,
            'share_count': 250,
            'comment_count': 140
        }
    ]
    
    return {
        'collaborative_filtering': collaborative_filtering_results,
        'content_based': content_based_results,
        'deep_learning': deep_learning_results
    }


async def demonstrate_fusion_reranking():
    """演示融合重排功能"""
    
    print("=== 融合重排服务演示 ===\n")
    
    # 1. 初始化服务
    print("1. 初始化融合重排服务...")
    service = FusionRerankingService()
    print("   服务初始化完成\n")
    
    # 2. 创建示例数据
    print("2. 创建示例算法结果...")
    algorithm_results = await create_sample_algorithm_results()
    
    # 打印各算法的结果数量
    for algorithm_name, results in algorithm_results.items():
        print(f"   {algorithm_name}: {len(results)} 个候选内容")
    print()
    
    # 3. 执行融合重排
    print("3. 执行融合重排...")
    user_id = "demo_user_001"
    target_size = 5
    context = {
        'device_type': 'mobile',
        'user_active_hours': [9, 10, 11, 14, 15, 16, 19, 20, 21],
        'location': 'Beijing'
    }
    
    start_time = datetime.now()
    fused_results = await service.fuse_and_rerank(
        algorithm_results=algorithm_results,
        user_id=user_id,
        target_size=target_size,
        context=context
    )
    processing_time = (datetime.now() - start_time).total_seconds()
    
    print(f"   处理完成，耗时: {processing_time:.3f}s")
    print(f"   最终推荐结果数量: {len(fused_results)}\n")
    
    # 4. 展示结果
    print("4. 融合重排结果:")
    print("-" * 80)
    for i, item in enumerate(fused_results, 1):
        print(f"排名 {i}:")
        print(f"  内容ID: {item['content_id']}")
        print(f"  标题: {item.get('title', 'N/A')}")
        print(f"  类型: {item.get('content_type', 'N/A')}")
        print(f"  分类: {item.get('category', 'N/A')}")
        print(f"  最终得分: {item.get('final_score', 0):.4f}")
        print(f"  融合得分: {item.get('fusion_score', 0):.4f}")
        print(f"  算法覆盖度: {item.get('algorithm_coverage', 0)}")
        
        # 显示得分分解
        score_breakdown = item.get('score_breakdown', {})
        if score_breakdown:
            print("  得分分解:")
            for score_type, score_value in score_breakdown.items():
                print(f"    {score_type}: {score_value:.4f}")
        
        print()
    
    # 5. 展示配置信息
    print("5. 服务配置信息:")
    print("-" * 80)
    config = service.get_service_config()
    
    print("算法权重:")
    for algorithm, weight in config['algorithm_weights'].items():
        print(f"  {algorithm}: {weight}")
    
    print("\n多样性配置:")
    diversity_config = config['diversity_config']
    print(f"  分类多样性权重: {diversity_config['category_diversity_weight']}")
    print(f"  内容类型多样性权重: {diversity_config['content_type_diversity_weight']}")
    print(f"  作者多样性权重: {diversity_config['author_diversity_weight']}")
    print(f"  时间多样性权重: {diversity_config['time_diversity_weight']}")
    
    print("\n业务规则:")
    business_rules = config['business_rules']
    print(f"  最低内容质量分数: {business_rules['min_content_quality_score']}")
    print(f"  最大内容年龄: {business_rules['max_content_age_days']} 天")
    print(f"  最低用户评分: {business_rules['min_user_rating']}")
    
    print("\n去重配置:")
    dedup_config = config['dedup_config']
    print(f"  相似度阈值: {dedup_config['similarity_threshold']}")
    print(f"  标题相似度权重: {dedup_config['title_similarity_weight']}")
    print(f"  内容相似度权重: {dedup_config['content_similarity_weight']}")


async def demonstrate_config_update():
    """演示配置更新功能"""
    
    print("\n=== 配置更新演示 ===\n")
    
    # 初始化服务
    service = FusionRerankingService()
    
    # 显示原始配置
    print("1. 原始算法权重:")
    original_config = service.get_service_config()
    for algorithm, weight in original_config['algorithm_weights'].items():
        print(f"   {algorithm}: {weight}")
    
    # 更新配置
    print("\n2. 更新算法权重...")
    new_config = {
        'algorithm_weights': {
            'collaborative_filtering': 0.4,
            'content_based': 0.4,
            'deep_learning': 0.2
        }
    }
    service.update_config(new_config)
    
    # 显示更新后的配置
    print("3. 更新后的算法权重:")
    updated_config = service.get_service_config()
    for algorithm, weight in updated_config['algorithm_weights'].items():
        print(f"   {algorithm}: {weight}")


async def main():
    """主函数"""
    try:
        # 演示基本功能
        await demonstrate_fusion_reranking()
        
        # 演示配置更新
        await demonstrate_config_update()
        
        print("\n=== 演示完成 ===")
        
    except Exception as e:
        print(f"演示过程中出现错误: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    # 运行演示
    asyncio.run(main())