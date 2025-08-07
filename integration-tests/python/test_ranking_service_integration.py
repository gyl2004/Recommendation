"""
排序服务集成测试
测试推荐算法的排序功能和模型推理
"""

import asyncio
import pytest
import httpx
import numpy as np
import json
import time
from typing import Dict, List, Any


class TestRankingServiceIntegration:
    """排序服务集成测试类"""
    
    @pytest.fixture(scope="class")
    async def setup_services(self):
        """设置测试环境"""
        self.ranking_service_url = "http://localhost:8002"
        
        # 准备测试数据
        self.test_user_features = {
            "user_id": "test_user_001",
            "age_group": "25-34",
            "interests": {"technology": 0.8, "sports": 0.3, "entertainment": 0.5},
            "behavior_patterns": {
                "avg_session_duration": 300,
                "click_rate": 0.15,
                "like_rate": 0.08
            },
            "activity_score": 0.75
        }
        
        self.test_content_candidates = [
            {
                "content_id": "content_001",
                "title": "深度学习在推荐系统中的应用",
                "content_type": "article",
                "tags": ["technology", "AI", "machine_learning"],
                "category": "技术",
                "publish_time": int(time.time()) - 3600,
                "hot_score": 0.85,
                "text_features": {
                    "tfidf_vector": np.random.rand(100).tolist(),
                    "word_count": 1500,
                    "readability_score": 0.7
                }
            },
            {
                "content_id": "content_002", 
                "title": "NBA总决赛精彩回顾",
                "content_type": "video",
                "tags": ["sports", "basketball", "NBA"],
                "category": "体育",
                "publish_time": int(time.time()) - 7200,
                "hot_score": 0.92,
                "text_features": {
                    "tfidf_vector": np.random.rand(100).tolist(),
                    "duration": 600,
                    "view_count": 50000
                }
            },
            {
                "content_id": "content_003",
                "title": "最新电影推荐",
                "content_type": "article", 
                "tags": ["entertainment", "movies", "review"],
                "category": "娱乐",
                "publish_time": int(time.time()) - 1800,
                "hot_score": 0.78,
                "text_features": {
                    "tfidf_vector": np.random.rand(100).tolist(),
                    "word_count": 800,
                    "readability_score": 0.8
                }
            }
        ]
        
        yield
        
        # 清理工作
        pass

    @pytest.mark.asyncio
    async def test_basic_ranking_functionality(self, setup_services):
        """测试基础排序功能"""
        ranking_request = {
            "user_features": self.test_user_features,
            "content_candidates": self.test_content_candidates,
            "ranking_params": {
                "algorithm": "wide_deep",
                "diversity_weight": 0.2,
                "freshness_weight": 0.1
            }
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{self.ranking_service_url}/api/v1/ranking/rank",
                json=ranking_request,
                timeout=30.0
            )
            
            assert response.status_code == 200
            result = response.json()
            
            # 验证响应结构
            assert "ranked_contents" in result
            assert "ranking_metadata" in result
            
            ranked_contents = result["ranked_contents"]
            assert len(ranked_contents) == len(self.test_content_candidates)
            
            # 验证排序结果
            for i, content in enumerate(ranked_contents):
                assert "content_id" in content
                assert "score" in content
                assert "rank" in content
                assert content["rank"] == i + 1
                
                # 分数应该是递减的
                if i > 0:
                    assert content["score"] <= ranked_contents[i-1]["score"]
            
            # 验证元数据
            metadata = result["ranking_metadata"]
            assert "algorithm_used" in metadata
            assert "processing_time_ms" in metadata
            assert metadata["algorithm_used"] == "wide_deep"
            
            print("✅ 基础排序功能测试通过")

    @pytest.mark.asyncio
    async def test_different_ranking_algorithms(self, setup_services):
        """测试不同排序算法"""
        algorithms = ["wide_deep", "deepfm", "collaborative_filtering", "content_based"]
        
        async with httpx.AsyncClient() as client:
            algorithm_results = {}
            
            for algorithm in algorithms:
                ranking_request = {
                    "user_features": self.test_user_features,
                    "content_candidates": self.test_content_candidates,
                    "ranking_params": {
                        "algorithm": algorithm,
                        "diversity_weight": 0.2
                    }
                }
                
                response = await client.post(
                    f"{self.ranking_service_url}/api/v1/ranking/rank",
                    json=ranking_request,
                    timeout=30.0
                )
                
                assert response.status_code == 200
                result = response.json()
                
                # 记录结果
                algorithm_results[algorithm] = result["ranked_contents"]
                
                # 验证算法特定的行为
                if algorithm == "content_based":
                    # 基于内容的算法应该优先推荐技术类内容（用户兴趣最高）
                    top_content = result["ranked_contents"][0]
                    top_content_data = next(c for c in self.test_content_candidates 
                                          if c["content_id"] == top_content["content_id"])
                    assert "technology" in top_content_data["tags"]
                
                elif algorithm == "collaborative_filtering":
                    # 协同过滤应该考虑用户相似性
                    assert "similarity_score" in result["ranking_metadata"]
            
            # 验证不同算法产生不同的排序结果
            unique_rankings = set()
            for algorithm, contents in algorithm_results.items():
                ranking_order = tuple(c["content_id"] for c in contents)
                unique_rankings.add(ranking_order)
            
            assert len(unique_rankings) > 1, "不同算法应该产生不同的排序结果"
            
            print("✅ 不同排序算法测试通过")

    @pytest.mark.asyncio
    async def test_ranking_with_diversity_control(self, setup_services):
        """测试多样性控制"""
        # 测试不同多样性权重
        diversity_weights = [0.0, 0.3, 0.6, 0.9]
        
        async with httpx.AsyncClient() as client:
            diversity_results = {}
            
            for weight in diversity_weights:
                ranking_request = {
                    "user_features": self.test_user_features,
                    "content_candidates": self.test_content_candidates,
                    "ranking_params": {
                        "algorithm": "wide_deep",
                        "diversity_weight": weight
                    }
                }
                
                response = await client.post(
                    f"{self.ranking_service_url}/api/v1/ranking/rank",
                    json=ranking_request
                )
                
                assert response.status_code == 200
                result = response.json()
                diversity_results[weight] = result["ranked_contents"]
            
            # 验证多样性效果
            # 高多样性权重应该产生更多样化的结果
            high_diversity_result = diversity_results[0.9]
            low_diversity_result = diversity_results[0.0]
            
            # 计算内容类型多样性
            high_div_types = set(
                next(c for c in self.test_content_candidates 
                     if c["content_id"] == item["content_id"])["content_type"]
                for item in high_diversity_result
            )
            
            low_div_types = set(
                next(c for c in self.test_content_candidates 
                     if c["content_id"] == item["content_id"])["content_type"]
                for item in low_diversity_result
            )
            
            # 高多样性设置应该包含更多内容类型
            assert len(high_div_types) >= len(low_div_types)
            
            print("✅ 多样性控制测试通过")

    @pytest.mark.asyncio
    async def test_ranking_performance(self, setup_services):
        """测试排序性能"""
        # 创建大量候选内容
        large_candidates = []
        for i in range(100):
            candidate = {
                "content_id": f"perf_content_{i:03d}",
                "title": f"性能测试内容 {i}",
                "content_type": "article" if i % 2 == 0 else "video",
                "tags": [f"tag_{i%10}", f"category_{i%5}"],
                "category": f"分类{i%8}",
                "publish_time": int(time.time()) - i * 60,
                "hot_score": np.random.rand(),
                "text_features": {
                    "tfidf_vector": np.random.rand(100).tolist(),
                    "word_count": 500 + i * 10,
                    "readability_score": np.random.rand()
                }
            }
            large_candidates.append(candidate)
        
        ranking_request = {
            "user_features": self.test_user_features,
            "content_candidates": large_candidates,
            "ranking_params": {
                "algorithm": "wide_deep",
                "diversity_weight": 0.2
            }
        }
        
        async with httpx.AsyncClient() as client:
            start_time = time.time()
            
            response = await client.post(
                f"{self.ranking_service_url}/api/v1/ranking/rank",
                json=ranking_request,
                timeout=60.0
            )
            
            end_time = time.time()
            processing_time = end_time - start_time
            
            assert response.status_code == 200
            result = response.json()
            
            # 验证性能要求
            assert processing_time < 2.0, f"排序100个候选内容应在2秒内完成，实际：{processing_time:.2f}秒"
            
            # 验证结果正确性
            ranked_contents = result["ranked_contents"]
            assert len(ranked_contents) == 100
            
            # 验证排序正确性
            scores = [content["score"] for content in ranked_contents]
            assert scores == sorted(scores, reverse=True), "结果应该按分数降序排列"
            
            print(f"✅ 排序性能测试通过，处理时间：{processing_time:.2f}秒")

    @pytest.mark.asyncio
    async def test_batch_ranking(self, setup_services):
        """测试批量排序"""
        # 创建多个用户的排序请求
        batch_request = {
            "batch_id": "batch_ranking_001",
            "requests": []
        }
        
        for i in range(5):
            user_features = {
                "user_id": f"batch_user_{i}",
                "age_group": "25-34",
                "interests": {
                    "technology": np.random.rand(),
                    "sports": np.random.rand(),
                    "entertainment": np.random.rand()
                },
                "activity_score": np.random.rand()
            }
            
            request = {
                "request_id": f"req_{i}",
                "user_features": user_features,
                "content_candidates": self.test_content_candidates[:2],  # 减少候选数量
                "ranking_params": {
                    "algorithm": "wide_deep",
                    "diversity_weight": 0.2
                }
            }
            batch_request["requests"].append(request)
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{self.ranking_service_url}/api/v1/ranking/batch",
                json=batch_request,
                timeout=60.0
            )
            
            assert response.status_code == 200
            result = response.json()
            
            # 验证批量处理结果
            assert "batch_id" in result
            assert "results" in result
            assert "summary" in result
            
            results = result["results"]
            assert len(results) == 5
            
            # 验证每个结果
            for i, res in enumerate(results):
                assert res["request_id"] == f"req_{i}"
                assert res["status"] == "success"
                assert "ranked_contents" in res
                assert len(res["ranked_contents"]) == 2
            
            # 验证汇总信息
            summary = result["summary"]
            assert summary["total_requests"] == 5
            assert summary["successful_requests"] == 5
            assert summary["failed_requests"] == 0
            
            print("✅ 批量排序测试通过")

    @pytest.mark.asyncio
    async def test_model_a_b_testing(self, setup_services):
        """测试模型A/B测试"""
        # 测试不同模型版本
        model_versions = ["v1.0", "v1.1", "v2.0"]
        
        async with httpx.AsyncClient() as client:
            version_results = {}
            
            for version in model_versions:
                ranking_request = {
                    "user_features": self.test_user_features,
                    "content_candidates": self.test_content_candidates,
                    "ranking_params": {
                        "algorithm": "wide_deep",
                        "model_version": version,
                        "experiment_id": f"model_test_{version}"
                    }
                }
                
                response = await client.post(
                    f"{self.ranking_service_url}/api/v1/ranking/rank",
                    json=ranking_request
                )
                
                if response.status_code == 200:
                    result = response.json()
                    version_results[version] = result
                    
                    # 验证实验信息
                    metadata = result["ranking_metadata"]
                    assert "experiment_id" in metadata
                    assert "model_version" in metadata
                    assert metadata["model_version"] == version
                elif response.status_code == 404:
                    # 某些模型版本可能不存在，这是正常的
                    print(f"模型版本 {version} 不存在，跳过测试")
                    continue
            
            # 至少应该有一个版本可用
            assert len(version_results) > 0, "至少应该有一个模型版本可用"
            
            print("✅ 模型A/B测试通过")

    @pytest.mark.asyncio
    async def test_ranking_with_context(self, setup_services):
        """测试上下文感知排序"""
        # 测试不同上下文场景
        contexts = [
            {
                "scenario": "morning_commute",
                "time_of_day": "morning",
                "device_type": "mobile",
                "location": "subway"
            },
            {
                "scenario": "evening_leisure", 
                "time_of_day": "evening",
                "device_type": "tablet",
                "location": "home"
            },
            {
                "scenario": "work_break",
                "time_of_day": "afternoon", 
                "device_type": "desktop",
                "location": "office"
            }
        ]
        
        async with httpx.AsyncClient() as client:
            context_results = {}
            
            for context in contexts:
                ranking_request = {
                    "user_features": self.test_user_features,
                    "content_candidates": self.test_content_candidates,
                    "context": context,
                    "ranking_params": {
                        "algorithm": "wide_deep",
                        "context_weight": 0.3
                    }
                }
                
                response = await client.post(
                    f"{self.ranking_service_url}/api/v1/ranking/rank",
                    json=ranking_request
                )
                
                assert response.status_code == 200
                result = response.json()
                context_results[context["scenario"]] = result["ranked_contents"]
            
            # 验证不同上下文产生不同的排序
            scenarios = list(context_results.keys())
            for i in range(len(scenarios)):
                for j in range(i + 1, len(scenarios)):
                    result1 = context_results[scenarios[i]]
                    result2 = context_results[scenarios[j]]
                    
                    # 至少前两名应该有所不同
                    top2_1 = [c["content_id"] for c in result1[:2]]
                    top2_2 = [c["content_id"] for c in result2[:2]]
                    
                    # 允许部分相同，但不应该完全相同
                    assert top2_1 != top2_2 or len(set(top2_1 + top2_2)) > 2
            
            print("✅ 上下文感知排序测试通过")

    @pytest.mark.asyncio
    async def test_ranking_error_handling(self, setup_services):
        """测试排序错误处理"""
        async with httpx.AsyncClient() as client:
            # 测试缺少必要参数
            invalid_requests = [
                # 缺少用户特征
                {
                    "content_candidates": self.test_content_candidates,
                    "ranking_params": {"algorithm": "wide_deep"}
                },
                # 缺少候选内容
                {
                    "user_features": self.test_user_features,
                    "ranking_params": {"algorithm": "wide_deep"}
                },
                # 无效算法
                {
                    "user_features": self.test_user_features,
                    "content_candidates": self.test_content_candidates,
                    "ranking_params": {"algorithm": "invalid_algorithm"}
                },
                # 空候选列表
                {
                    "user_features": self.test_user_features,
                    "content_candidates": [],
                    "ranking_params": {"algorithm": "wide_deep"}
                }
            ]
            
            for i, invalid_request in enumerate(invalid_requests):
                response = await client.post(
                    f"{self.ranking_service_url}/api/v1/ranking/rank",
                    json=invalid_request
                )
                
                assert response.status_code in [400, 422], f"无效请求 {i+1} 应该返回错误状态码"
                
                error_response = response.json()
                assert "error" in error_response or "detail" in error_response
                
                print(f"✅ 无效请求 {i+1} 正确处理")

    @pytest.mark.asyncio
    async def test_ranking_service_health(self, setup_services):
        """测试排序服务健康状态"""
        async with httpx.AsyncClient() as client:
            # 健康检查
            response = await client.get(f"{self.ranking_service_url}/health")
            assert response.status_code == 200
            
            health_data = response.json()
            assert health_data["status"] == "healthy"
            assert "model_status" in health_data["checks"]
            assert "redis_connection" in health_data["checks"]
            
            # 模型信息
            response = await client.get(f"{self.ranking_service_url}/api/v1/ranking/models")
            assert response.status_code == 200
            
            models_info = response.json()
            assert "available_models" in models_info
            assert len(models_info["available_models"]) > 0
            
            # 指标
            response = await client.get(f"{self.ranking_service_url}/metrics")
            assert response.status_code == 200
            
            metrics = response.text
            assert "ranking_requests_total" in metrics
            assert "ranking_duration_seconds" in metrics
            assert "model_inference_duration_seconds" in metrics
            
            print("✅ 排序服务健康状态测试通过")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])