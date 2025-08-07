"""
特征服务集成测试
"""
import asyncio
import pytest
import httpx
from datetime import datetime

from app.models.schemas import UserBehavior, ActionType, ContentType

# 测试配置
BASE_URL = "http://localhost:8003"
TEST_USER_IDS = ["1001", "1002", "1003"]
TEST_CONTENT_IDS = ["2001", "2002", "2003"]

class TestFeatureServiceIntegration:
    """特征服务集成测试"""
    
    @pytest.mark.asyncio
    async def test_health_check(self):
        """测试健康检查"""
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{BASE_URL}/health")
            assert response.status_code == 200
            data = response.json()
            assert data["status"] == "healthy"
    
    @pytest.mark.asyncio
    async def test_user_behavior_processing(self):
        """测试用户行为处理"""
        behavior_data = {
            "user_id": "1001",
            "content_id": "2001",
            "action_type": "view",
            "content_type": "article",
            "session_id": "session_001",
            "device_type": "mobile",
            "duration": 120,
            "timestamp": datetime.now().isoformat()
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/behavior",
                json=behavior_data
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_batch_user_features(self):
        """测试批量获取用户特征"""
        request_data = {
            "user_ids": TEST_USER_IDS,
            "include_vector": True
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/user/batch",
                json=request_data
            )
            assert response.status_code == 200
            data = response.json()
            assert "user_features" in data
    
    @pytest.mark.asyncio
    async def test_batch_content_features(self):
        """测试批量获取内容特征"""
        request_data = {
            "content_ids": TEST_CONTENT_IDS,
            "include_vector": True
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/content/batch",
                json=request_data
            )
            assert response.status_code == 200
            data = response.json()
            assert "content_features" in data
    
    @pytest.mark.asyncio
    async def test_feature_update(self):
        """测试特征更新"""
        request_data = {
            "user_ids": TEST_USER_IDS[:2],
            "content_ids": TEST_CONTENT_IDS[:2],
            "update_type": "incremental",
            "force_update": False
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/update",
                json=request_data
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_feature_statistics(self):
        """测试特征统计信息"""
        async with httpx.AsyncClient() as client:
            response = await client.get(f"{BASE_URL}/api/v1/features/statistics")
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_feature_engineering(self):
        """测试特征工程"""
        request_data = {
            "user_ids": TEST_USER_IDS
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/engineering/normalize/users",
                json=request_data
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_pipeline_execution(self):
        """测试特征管道执行"""
        request_data = {
            "user_ids": TEST_USER_IDS
        }
        
        async with httpx.AsyncClient() as client:
            response = await client.post(
                f"{BASE_URL}/api/v1/features/pipeline/users?pipeline_type=full",
                json=request_data
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_trending_contents(self):
        """测试趋势内容获取"""
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{BASE_URL}/api/v1/features/offline/trending/all?limit=50"
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_database_statistics(self):
        """测试数据库统计信息"""
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{BASE_URL}/api/v1/features/offline/database-stats"
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
    
    @pytest.mark.asyncio
    async def test_offline_scheduler_status(self):
        """测试离线调度器状态"""
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{BASE_URL}/api/v1/features/offline/scheduler/status"
            )
            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True

async def run_integration_tests():
    """运行集成测试"""
    print("开始运行特征服务集成测试...")
    
    # 等待服务启动
    await asyncio.sleep(5)
    
    test_instance = TestFeatureServiceIntegration()
    
    try:
        # 运行各项测试
        await test_instance.test_health_check()
        print("✓ 健康检查测试通过")
        
        await test_instance.test_user_behavior_processing()
        print("✓ 用户行为处理测试通过")
        
        await test_instance.test_batch_user_features()
        print("✓ 批量用户特征测试通过")
        
        await test_instance.test_batch_content_features()
        print("✓ 批量内容特征测试通过")
        
        await test_instance.test_feature_update()
        print("✓ 特征更新测试通过")
        
        await test_instance.test_feature_statistics()
        print("✓ 特征统计测试通过")
        
        await test_instance.test_feature_engineering()
        print("✓ 特征工程测试通过")
        
        await test_instance.test_pipeline_execution()
        print("✓ 特征管道测试通过")
        
        await test_instance.test_trending_contents()
        print("✓ 趋势内容测试通过")
        
        await test_instance.test_database_statistics()
        print("✓ 数据库统计测试通过")
        
        await test_instance.test_offline_scheduler_status()
        print("✓ 离线调度器状态测试通过")
        
        print("\n🎉 所有集成测试通过！")
        
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        raise

if __name__ == "__main__":
    asyncio.run(run_integration_tests())