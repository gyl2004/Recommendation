"""
数据模型定义
"""
from pydantic import BaseModel, Field
from typing import Dict, List, Optional, Any, Union
from datetime import datetime
from enum import Enum

class ContentType(str, Enum):
    """内容类型枚举"""
    ARTICLE = "article"
    VIDEO = "video"
    PRODUCT = "product"

class ActionType(str, Enum):
    """行为类型枚举"""
    VIEW = "view"
    CLICK = "click"
    LIKE = "like"
    SHARE = "share"
    COMMENT = "comment"
    PURCHASE = "purchase"

class UserFeatures(BaseModel):
    """用户特征模型"""
    user_id: str
    age_group: Optional[str] = None
    gender: Optional[str] = None
    location: Optional[str] = None
    interests: List[str] = Field(default_factory=list)
    behavior_score: float = 0.0
    activity_level: str = "low"  # low, medium, high
    preferred_content_types: List[ContentType] = Field(default_factory=list)
    last_active: Optional[datetime] = None
    feature_vector: List[float] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)

class ContentFeatures(BaseModel):
    """内容特征模型"""
    content_id: str
    content_type: ContentType
    title: str
    category: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
    author_id: Optional[str] = None
    publish_time: Optional[datetime] = None
    quality_score: float = 0.0
    popularity_score: float = 0.0
    text_features: Dict[str, float] = Field(default_factory=dict)
    embedding_vector: List[float] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.now)
    updated_at: datetime = Field(default_factory=datetime.now)

class UserBehavior(BaseModel):
    """用户行为模型"""
    user_id: str
    content_id: str
    action_type: ActionType
    content_type: ContentType
    session_id: Optional[str] = None
    device_type: Optional[str] = None
    duration: Optional[int] = None  # 行为持续时间(秒)
    timestamp: datetime = Field(default_factory=datetime.now)
    extra_data: Dict[str, Any] = Field(default_factory=dict)

class FeatureUpdateRequest(BaseModel):
    """特征更新请求"""
    user_ids: Optional[List[str]] = None
    content_ids: Optional[List[str]] = None
    update_type: str = "incremental"  # incremental, full
    force_update: bool = False

class BatchFeatureRequest(BaseModel):
    """批量特征请求"""
    user_ids: List[str] = Field(default_factory=list)
    content_ids: List[str] = Field(default_factory=list)
    include_vector: bool = True

class FeatureResponse(BaseModel):
    """特征响应"""
    success: bool
    message: str
    data: Optional[Dict[str, Any]] = None

class UserFeatureResponse(BaseModel):
    """用户特征响应"""
    user_features: Dict[str, UserFeatures]
    cache_hit_rate: float
    total_count: int

class ContentFeatureResponse(BaseModel):
    """内容特征响应"""
    content_features: Dict[str, ContentFeatures]
    cache_hit_rate: float
    total_count: int