"""
用户特征服务测试
"""
import pytest
import asyncio
from datetime import datetime
from unittest.mock import AsyncMock, MagicMock

from app.services.user_feature_service import UserFeatureService
from app.models.schemas import UserFeatures, UserBehavior, ActionType, ContentType

@pytest.fixture
def user_feature_service():
    """创建用户特征服务实例"""
    return UserFeatureService()

@pytest.fixture
def sample_user_behavior():
    """创建示例用户行为"""
    return UserBehavior(
        user_id="1001",
        content_id="2001",
        action_type=ActionType.VIEW,
        content_type=ContentType.ARTICLE,
        session_id="session_001",
        device_type="mobile",
        duration=120,
        timestamp=datetime.now()
    )

@pytest.fixture
def sample_user_features():
    """创建示例用户特征"""
    return UserFeatures(
        user_id="1001",
        age_group="25-34",
        interests=["tech", "sports"],
        behavior_score=8.5,
        activity_level="high",
        preferred_content_types=[ContentType.ARTICLE],
        feature_vector=[0.1, 0.2, 0.3],
        last_active=datetime.now()
    )

class TestUserFeatureService:
    """用户特征服务测试类"""
    
    @pytest.mark.asyncio
    async def test_create_default_features(self, user_feature_service):
        """测试创建默认用户特征"""
        user_id = "1001"
        features = await user_feature_service._create_default_features(user_id)
        
        assert features.user_id == user_id
        assert features.behavior_score == 0.0
        assert features.activity_level == "low"
        assert len(features.feature_vector) == 64
    
    @pytest.mark.asyncio
    async def test_update_interest_tags(self, user_feature_service, sample_user_features, sample_user_behavior):
        """测试更新兴趣标签"""
        # 测试文章内容
        sample_user_behavior.content_type = ContentType.ARTICLE
        await user_feature_service._update_interest_tags(sample_user_features, sample_user_behavior)
        assert "tech" in sample_user_features.interests
        
        # 测试视频内容
        sample_user_behavior.content_type = ContentType.VIDEO
        await user_feature_service._update_interest_tags(sample_user_features, sample_user_behavior)
        assert "entertainment" in sample_user_features.interests
    
    @pytest.mark.asyncio
    async def test_update_activity_level(self, user_feature_service, sample_user_features, sample_user_behavior):
        """测试更新活跃度"""
        # 设置最后活跃时间为1小时前
        sample_user_features.last_active = datetime.now()
        sample_user_features.activity_level = "low"
        
        await user_feature_service._update_activity_level(sample_user_features, sample_user_behavior)
        assert sample_user_features.activity_level == "medium"
    
    @pytest.mark.asyncio
    async def test_generate_feature_vector(self, user_feature_service, sample_user_features):
        """测试生成特征向量"""
        vector = await user_feature_service._generate_feature_vector(sample_user_features)
        
        assert len(vector) == 64
        assert vector[0] == sample_user_features.behavior_score / 10.0  # 行为分数特征
        assert vector[1] == 1.0  # 高活跃度特征
        assert vector[2] == 1.0  # 文章内容类型偏好
    
    def test_behavior_weights(self, user_feature_service):
        """测试行为权重配置"""
        weights = user_feature_service.behavior_weights
        
        assert weights[ActionType.VIEW] == 1.0
        assert weights[ActionType.CLICK] == 2.0
        assert weights[ActionType.LIKE] == 3.0
        assert weights[ActionType.SHARE] == 4.0
        assert weights[ActionType.COMMENT] == 3.5
        assert weights[ActionType.PURCHASE] == 5.0

if __name__ == "__main__":
    pytest.main([__file__])