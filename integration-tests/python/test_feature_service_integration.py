"""
特征服务集成测试
测试特征提取、存储和更新的完整流程
"""

import asyncio
import pytest
import httpx
import redis
import json
import time
from typing import Dict, List, Any
from datetime import datetime, timedelta


class TestFeatureServiceIntegration:
    """特征服务集成测试类"""
    
    @pytest.fixture(scope="class")
    async def setup_services(self):
        """设置测试环境"""
        # 连接Redis
        self.redis_client = redis.Redis(host='localhost', port=6379, db=1)
        
        # 清理测试数据
        self.redis_client.flushdb()
        
        # 特征服务URL
        self.feature_service_url = "http://localhost:8001"
        
        yield
        
        # 清理
        self.redis_client.flushdb()
        self.redis_client.close()

    @pytest.mark.asyncio
    async def test_user_feature_extraction_and_storage(self, setup_services):
        """测试用户特征提取和存储"""
        user_id = "test_user_001"
        
        # 模拟用户行为数据
        behavior_data = {
            "user_id": user_id,
            "behaviors": [
                {
                    "content_id": "content_001",
                    "action_type": "view",
                    "duration": 120,
                    "timestamp": int(time.time())
                },
                {
                    "content_id": "content_002", 
                    "action_type": "like",
                    "timestamp": int(time.time())
                },
                {
                    "content_id": "content_003",
                    "action_type": "share",
                    "timestamp": int(time.time())
                }
            ]
        }
        
        async with httpx.AsyncClient() as client:
            # 发送行为数据进行特征提取
            response = await client.post(
                f"{self.feature_service_url}/api/v1/features/user/extract",
                json=behavior_data,
                timeout=30.0
            )
            
            assert response.status_code == 200
            result = response.json()
            assert result["status"] == "success"
            assert "feature_id" in result
            
            # 等待特征处理完成
            await asyncio.sleep(2)
            
            # 获取用户特征
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/user/{user_id}"
            )
            
            assert response.status_code == 200
            features = response.json()
            
            # 验证特征结构
            assert "user_id" in features
            assert "interests" in features
            assert "behavior_patterns" in features
            assert "activity_score" in features
            assert "last_updated" in features
            
            # 验证特征值的合理性
            assert features["user_id"] == user_id
            assert isinstance(features["interests"], dict)
            assert isinstance(features["activity_score"], float)
            assert 0 <= features["activity_score"] <= 1
            
            print("✅ 用户特征提取和存储测试通过")

    @pytest.mark.asyncio
    async def test_content_feature_extraction(self, setup_services):
        """测试内容特征提取"""
        content_data = {
            "content_id": "content_test_001",
            "title": "人工智能在推荐系统中的应用",
            "content": "本文介绍了深度学习、机器学习在个性化推荐中的最新进展...",
            "content_type": "article",
            "tags": ["AI", "机器学习", "推荐系统"],
            "category": "技术"
        }
        
        async with httpx.AsyncClient() as client:
            # 提取内容特征
            response = await client.post(
                f"{self.feature_service_url}/api/v1/features/content/extract",
                json=content_data,
                timeout=30.0
            )
            
            assert response.status_code == 200
            result = response.json()
            assert result["status"] == "success"
            
            # 获取内容特征
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/content/{content_data['content_id']}"
            )
            
            assert response.status_code == 200
            features = response.json()
            
            # 验证特征结构
            assert "content_id" in features
            assert "text_features" in features
            assert "category_features" in features
            assert "tag_features" in features
            assert "embedding" in features
            
            # 验证特征值
            assert features["content_id"] == content_data["content_id"]
            assert isinstance(features["text_features"], dict)
            assert isinstance(features["embedding"], list)
            assert len(features["embedding"]) > 0  # 词向量维度
            
            print("✅ 内容特征提取测试通过")

    @pytest.mark.asyncio
    async def test_real_time_feature_update(self, setup_services):
        """测试实时特征更新"""
        user_id = "test_user_002"
        
        # 初始化用户特征
        initial_behavior = {
            "user_id": user_id,
            "behaviors": [
                {
                    "content_id": "content_001",
                    "action_type": "view",
                    "duration": 60,
                    "timestamp": int(time.time())
                }
            ]
        }
        
        async with httpx.AsyncClient() as client:
            # 创建初始特征
            await client.post(
                f"{self.feature_service_url}/api/v1/features/user/extract",
                json=initial_behavior
            )
            
            await asyncio.sleep(1)
            
            # 获取初始特征
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/user/{user_id}"
            )
            initial_features = response.json()
            initial_score = initial_features["activity_score"]
            
            # 添加新的行为数据
            new_behavior = {
                "user_id": user_id,
                "behaviors": [
                    {
                        "content_id": "content_002",
                        "action_type": "like",
                        "timestamp": int(time.time())
                    },
                    {
                        "content_id": "content_003",
                        "action_type": "share",
                        "timestamp": int(time.time())
                    }
                ]
            }
            
            # 更新特征
            response = await client.post(
                f"{self.feature_service_url}/api/v1/features/user/update",
                json=new_behavior
            )
            
            assert response.status_code == 200
            
            await asyncio.sleep(2)
            
            # 获取更新后的特征
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/user/{user_id}"
            )
            updated_features = response.json()
            updated_score = updated_features["activity_score"]
            
            # 验证特征已更新
            assert updated_score > initial_score, "活跃度分数应该增加"
            assert len(updated_features["interests"]) >= len(initial_features["interests"])
            
            print("✅ 实时特征更新测试通过")

    @pytest.mark.asyncio
    async def test_batch_feature_processing(self, setup_services):
        """测试批量特征处理"""
        batch_data = {
            "batch_id": "batch_001",
            "users": [
                {
                    "user_id": f"batch_user_{i}",
                    "behaviors": [
                        {
                            "content_id": f"content_{j}",
                            "action_type": "view" if j % 2 == 0 else "like",
                            "duration": 60 + j * 10,
                            "timestamp": int(time.time()) - j * 3600
                        }
                        for j in range(5)
                    ]
                }
                for i in range(10)
            ]
        }
        
        async with httpx.AsyncClient() as client:
            # 提交批量处理任务
            response = await client.post(
                f"{self.feature_service_url}/api/v1/features/batch/process",
                json=batch_data,
                timeout=60.0
            )
            
            assert response.status_code == 200
            result = response.json()
            assert result["status"] == "accepted"
            assert "task_id" in result
            
            task_id = result["task_id"]
            
            # 轮询任务状态
            max_attempts = 30
            for attempt in range(max_attempts):
                response = await client.get(
                    f"{self.feature_service_url}/api/v1/features/batch/status/{task_id}"
                )
                
                assert response.status_code == 200
                status = response.json()
                
                if status["status"] == "completed":
                    break
                elif status["status"] == "failed":
                    pytest.fail(f"批量处理失败: {status.get('error', 'Unknown error')}")
                
                await asyncio.sleep(2)
            else:
                pytest.fail("批量处理超时")
            
            # 验证处理结果
            assert status["processed_count"] == 10
            assert status["success_count"] == 10
            assert status["error_count"] == 0
            
            # 随机检查几个用户的特征
            for i in [0, 5, 9]:
                user_id = f"batch_user_{i}"
                response = await client.get(
                    f"{self.feature_service_url}/api/v1/features/user/{user_id}"
                )
                
                assert response.status_code == 200
                features = response.json()
                assert features["user_id"] == user_id
                assert "interests" in features
                
            print("✅ 批量特征处理测试通过")

    @pytest.mark.asyncio
    async def test_feature_quality_validation(self, setup_services):
        """测试特征质量验证"""
        # 测试异常数据的处理
        invalid_data_cases = [
            # 空行为数据
            {
                "user_id": "invalid_user_001",
                "behaviors": []
            },
            # 无效的行为类型
            {
                "user_id": "invalid_user_002", 
                "behaviors": [
                    {
                        "content_id": "content_001",
                        "action_type": "invalid_action",
                        "timestamp": int(time.time())
                    }
                ]
            },
            # 缺少必要字段
            {
                "user_id": "invalid_user_003",
                "behaviors": [
                    {
                        "content_id": "content_001",
                        # 缺少action_type
                        "timestamp": int(time.time())
                    }
                ]
            }
        ]
        
        async with httpx.AsyncClient() as client:
            for i, invalid_data in enumerate(invalid_data_cases):
                response = await client.post(
                    f"{self.feature_service_url}/api/v1/features/user/extract",
                    json=invalid_data
                )
                
                # 应该返回错误或者处理为默认值
                if response.status_code == 400:
                    error = response.json()
                    assert "error" in error
                    print(f"✅ 无效数据案例 {i+1} 正确返回错误")
                elif response.status_code == 200:
                    # 如果接受了数据，应该生成默认特征
                    result = response.json()
                    assert result["status"] in ["success", "partial_success"]
                    print(f"✅ 无效数据案例 {i+1} 生成默认特征")

    @pytest.mark.asyncio
    async def test_feature_cache_performance(self, setup_services):
        """测试特征缓存性能"""
        user_id = "cache_test_user"
        
        # 创建用户特征
        behavior_data = {
            "user_id": user_id,
            "behaviors": [
                {
                    "content_id": "content_001",
                    "action_type": "view",
                    "duration": 120,
                    "timestamp": int(time.time())
                }
            ]
        }
        
        async with httpx.AsyncClient() as client:
            # 创建特征
            await client.post(
                f"{self.feature_service_url}/api/v1/features/user/extract",
                json=behavior_data
            )
            
            await asyncio.sleep(1)
            
            # 第一次请求（可能需要从数据库加载）
            start_time = time.time()
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/user/{user_id}"
            )
            first_request_time = time.time() - start_time
            
            assert response.status_code == 200
            
            # 第二次请求（应该从缓存加载）
            start_time = time.time()
            response = await client.get(
                f"{self.feature_service_url}/api/v1/features/user/{user_id}"
            )
            second_request_time = time.time() - start_time
            
            assert response.status_code == 200
            
            # 缓存命中的请求应该更快
            assert second_request_time < first_request_time * 0.8, \
                f"缓存请求应该更快: {second_request_time:.3f}s vs {first_request_time:.3f}s"
            
            # 验证Redis中确实有缓存
            cache_key = f"user:features:{user_id}"
            cached_data = self.redis_client.get(cache_key)
            assert cached_data is not None, "Redis中应该有缓存数据"
            
            print(f"✅ 特征缓存性能测试通过")
            print(f"第一次请求: {first_request_time:.3f}s")
            print(f"第二次请求: {second_request_time:.3f}s")

    @pytest.mark.asyncio
    async def test_feature_service_health_check(self, setup_services):
        """测试特征服务健康检查"""
        async with httpx.AsyncClient() as client:
            # 健康检查
            response = await client.get(f"{self.feature_service_url}/health")
            assert response.status_code == 200
            
            health_data = response.json()
            assert health_data["status"] == "healthy"
            assert "redis_connection" in health_data["checks"]
            assert "clickhouse_connection" in health_data["checks"]
            assert health_data["checks"]["redis_connection"] == "ok"
            
            # 指标检查
            response = await client.get(f"{self.feature_service_url}/metrics")
            assert response.status_code == 200
            
            metrics = response.text
            assert "feature_extraction_total" in metrics
            assert "feature_cache_hits_total" in metrics
            assert "feature_processing_duration_seconds" in metrics
            
            print("✅ 特征服务健康检查测试通过")

    @pytest.mark.asyncio
    async def test_concurrent_feature_requests(self, setup_services):
        """测试并发特征请求"""
        user_ids = [f"concurrent_user_{i}" for i in range(20)]
        
        # 为所有用户创建特征
        async with httpx.AsyncClient() as client:
            tasks = []
            for user_id in user_ids:
                behavior_data = {
                    "user_id": user_id,
                    "behaviors": [
                        {
                            "content_id": f"content_{i}",
                            "action_type": "view",
                            "duration": 60 + i * 10,
                            "timestamp": int(time.time())
                        }
                        for i in range(3)
                    ]
                }
                
                task = client.post(
                    f"{self.feature_service_url}/api/v1/features/user/extract",
                    json=behavior_data
                )
                tasks.append(task)
            
            # 并发执行
            responses = await asyncio.gather(*tasks, return_exceptions=True)
            
            # 检查结果
            success_count = 0
            for response in responses:
                if isinstance(response, httpx.Response) and response.status_code == 200:
                    success_count += 1
            
            success_rate = success_count / len(responses)
            assert success_rate >= 0.95, f"并发请求成功率应大于95%，实际: {success_rate:.2%}"
            
            print(f"✅ 并发特征请求测试通过，成功率: {success_rate:.2%}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])