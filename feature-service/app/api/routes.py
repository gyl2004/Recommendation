"""
特征服务API路由
"""
from fastapi import APIRouter, HTTPException, BackgroundTasks, Query
from typing import List, Optional
import asyncio
from loguru import logger

from ..models.schemas import (
    UserFeatures, ContentFeatures, UserBehavior,
    FeatureUpdateRequest, BatchFeatureRequest,
    FeatureResponse, UserFeatureResponse, ContentFeatureResponse
)
from ..services.user_feature_service import UserFeatureService
from ..services.content_feature_service import ContentFeatureService
from ..services.feature_storage_service import FeatureStorageService
from ..services.feature_engineering_service import FeatureEngineeringService
from ..services.feature_pipeline_service import FeaturePipelineService
from ..services.clickhouse_service import ClickHouseService
from ..services.offline_feature_service import OfflineFeatureService

# 创建路由器
feature_router = APIRouter(prefix="/features", tags=["features"])

# 服务实例
user_feature_service = UserFeatureService()
content_feature_service = ContentFeatureService()
storage_service = FeatureStorageService()
engineering_service = FeatureEngineeringService()
pipeline_service = FeaturePipelineService()
clickhouse_service = ClickHouseService()
offline_service = OfflineFeatureService()

@feature_router.get("/user/{user_id}", response_model=UserFeatures)
async def get_user_features(user_id: str):
    """获取用户特征"""
    try:
        features = await user_feature_service.get_user_features(user_id)
        if not features:
            raise HTTPException(status_code=404, detail="用户特征不存在")
        return features
    except Exception as e:
        logger.error(f"获取用户特征失败: {e}")
        raise HTTPException(status_code=500, detail="获取用户特征失败")

@feature_router.get("/content/{content_id}", response_model=ContentFeatures)
async def get_content_features(content_id: str):
    """获取内容特征"""
    try:
        features = await content_feature_service.get_content_features(content_id)
        if not features:
            raise HTTPException(status_code=404, detail="内容特征不存在")
        return features
    except Exception as e:
        logger.error(f"获取内容特征失败: {e}")
        raise HTTPException(status_code=500, detail="获取内容特征失败")

@feature_router.post("/user/batch", response_model=UserFeatureResponse)
async def get_batch_user_features(request: BatchFeatureRequest):
    """批量获取用户特征"""
    try:
        if not request.user_ids:
            raise HTTPException(status_code=400, detail="用户ID列表不能为空")
        
        # 先从缓存获取
        cached_features = await storage_service.get_user_features_from_cache(request.user_ids)
        
        # 统计缓存命中情况
        cache_hits = sum(1 for f in cached_features.values() if f is not None)
        cache_misses = [uid for uid, f in cached_features.items() if f is None]
        
        # 对于缓存未命中的，从服务获取
        if cache_misses:
            missing_features = await user_feature_service.get_batch_user_features(cache_misses)
            cached_features.update(missing_features)
        
        # 过滤掉None值
        valid_features = {uid: f for uid, f in cached_features.items() if f is not None}
        
        return UserFeatureResponse(
            user_features=valid_features,
            cache_hit_rate=cache_hits / len(request.user_ids) if request.user_ids else 0,
            total_count=len(valid_features)
        )
        
    except Exception as e:
        logger.error(f"批量获取用户特征失败: {e}")
        raise HTTPException(status_code=500, detail="批量获取用户特征失败")

@feature_router.post("/content/batch", response_model=ContentFeatureResponse)
async def get_batch_content_features(request: BatchFeatureRequest):
    """批量获取内容特征"""
    try:
        if not request.content_ids:
            raise HTTPException(status_code=400, detail="内容ID列表不能为空")
        
        # 先从缓存获取
        cached_features = await storage_service.get_content_features_from_cache(request.content_ids)
        
        # 统计缓存命中情况
        cache_hits = sum(1 for f in cached_features.values() if f is not None)
        cache_misses = [cid for cid, f in cached_features.items() if f is None]
        
        # 对于缓存未命中的，从服务获取
        if cache_misses:
            missing_features = await content_feature_service.get_batch_content_features(cache_misses)
            cached_features.update(missing_features)
        
        # 过滤掉None值
        valid_features = {cid: f for cid, f in cached_features.items() if f is not None}
        
        return ContentFeatureResponse(
            content_features=valid_features,
            cache_hit_rate=cache_hits / len(request.content_ids) if request.content_ids else 0,
            total_count=len(valid_features)
        )
        
    except Exception as e:
        logger.error(f"批量获取内容特征失败: {e}")
        raise HTTPException(status_code=500, detail="批量获取内容特征失败")

@feature_router.post("/update", response_model=FeatureResponse)
async def update_features(request: FeatureUpdateRequest, background_tasks: BackgroundTasks):
    """更新特征"""
    try:
        success_count = 0
        total_count = 0
        
        # 更新用户特征
        if request.user_ids:
            total_count += len(request.user_ids)
            if request.update_type == "full":
                # 全量更新
                for user_id in request.user_ids:
                    if await user_feature_service.update_user_features(user_id, force_update=True):
                        success_count += 1
            else:
                # 增量更新，放到后台任务
                background_tasks.add_task(
                    _update_user_features_background,
                    request.user_ids,
                    request.force_update
                )
                success_count += len(request.user_ids)
        
        # 更新内容特征
        if request.content_ids:
            total_count += len(request.content_ids)
            if request.update_type == "full":
                # 全量更新
                results = await content_feature_service.batch_update_content_features(request.content_ids)
                success_count += sum(1 for r in results.values() if r)
            else:
                # 增量更新，放到后台任务
                background_tasks.add_task(
                    _update_content_features_background,
                    request.content_ids,
                    request.force_update
                )
                success_count += len(request.content_ids)
        
        return FeatureResponse(
            success=True,
            message=f"特征更新任务已提交，成功: {success_count}/{total_count}",
            data={"success_count": success_count, "total_count": total_count}
        )
        
    except Exception as e:
        logger.error(f"更新特征失败: {e}")
        raise HTTPException(status_code=500, detail="更新特征失败")

@feature_router.post("/behavior", response_model=FeatureResponse)
async def process_user_behavior(behavior: UserBehavior, background_tasks: BackgroundTasks):
    """处理用户行为，实时更新特征"""
    try:
        # 异步处理用户行为
        background_tasks.add_task(
            user_feature_service.process_user_behavior,
            behavior
        )
        
        # 存储行为数据到ClickHouse
        background_tasks.add_task(
            storage_service.store_user_behavior_to_clickhouse,
            [behavior]
        )
        
        return FeatureResponse(
            success=True,
            message="用户行为处理任务已提交"
        )
        
    except Exception as e:
        logger.error(f"处理用户行为失败: {e}")
        raise HTTPException(status_code=500, detail="处理用户行为失败")

@feature_router.get("/statistics")
async def get_feature_statistics():
    """获取特征统计信息"""
    try:
        stats = await storage_service.get_cache_statistics()
        return FeatureResponse(
            success=True,
            message="获取统计信息成功",
            data=stats
        )
    except Exception as e:
        logger.error(f"获取统计信息失败: {e}")
        raise HTTPException(status_code=500, detail="获取统计信息失败")

@feature_router.post("/cleanup")
async def cleanup_expired_features(background_tasks: BackgroundTasks):
    """清理过期特征"""
    try:
        background_tasks.add_task(storage_service.cleanup_expired_features)
        return FeatureResponse(
            success=True,
            message="清理任务已提交"
        )
    except Exception as e:
        logger.error(f"清理过期特征失败: {e}")
        raise HTTPException(status_code=500, detail="清理过期特征失败")

@feature_router.post("/backup")
async def backup_features(
    background_tasks: BackgroundTasks,
    backup_type: str = Query(default="daily", description="备份类型")
):
    """备份特征数据"""
    try:
        background_tasks.add_task(
            storage_service.backup_features_to_clickhouse,
            backup_type
        )
        return FeatureResponse(
            success=True,
            message="备份任务已提交"
        )
    except Exception as e:
        logger.error(f"备份特征数据失败: {e}")
        raise HTTPException(status_code=500, detail="备份特征数据失败")

# 后台任务函数
async def _update_user_features_background(user_ids: List[str], force_update: bool):
    """后台更新用户特征"""
    try:
        tasks = [
            user_feature_service.update_user_features(user_id, force_update)
            for user_id in user_ids
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        success_count = sum(1 for r in results if r is True)
        logger.info(f"后台更新用户特征完成，成功: {success_count}/{len(user_ids)}")
        
    except Exception as e:
        logger.error(f"后台更新用户特征失败: {e}")

async def _update_content_features_background(content_ids: List[str], force_update: bool):
    """后台更新内容特征"""
    try:
        results = await content_feature_service.batch_update_content_features(content_ids)
        success_count = sum(1 for r in results.values() if r)
        logger.info(f"后台更新内容特征完成，成功: {success_count}/{len(content_ids)}")
        
    except Exception as e:
        logger.error(f"后台更新内容特征失败: {e}")
# 特征工程相关接口
@feature_router.post("/engineering/normalize/users", response_model=FeatureResponse)
async def normalize_user_features(request: BatchFeatureRequest):
    """标准化用户特征"""
    try:
        if not request.user_ids:
            raise HTTPException(status_code=400, detail="用户ID列表不能为空")
        
        # 获取用户特征
        features_dict = await user_feature_service.get_batch_user_features(request.user_ids)
        
        # 标准化处理
        normalized_features = await engineering_service.normalize_user_features(features_dict)
        
        # 存储标准化后的特征
        await storage_service.store_user_features_batch(normalized_features)
        
        return FeatureResponse(
            success=True,
            message=f"用户特征标准化完成，处理数量: {len(normalized_features)}",
            data={"processed_count": len(normalized_features)}
        )
        
    except Exception as e:
        logger.error(f"用户特征标准化失败: {e}")
        raise HTTPException(status_code=500, detail="用户特征标准化失败")

@feature_router.post("/engineering/normalize/contents", response_model=FeatureResponse)
async def normalize_content_features(request: BatchFeatureRequest):
    """标准化内容特征"""
    try:
        if not request.content_ids:
            raise HTTPException(status_code=400, detail="内容ID列表不能为空")
        
        # 获取内容特征
        features_dict = await content_feature_service.get_batch_content_features(request.content_ids)
        
        # 标准化处理
        normalized_features = await engineering_service.normalize_content_features(features_dict)
        
        # 存储标准化后的特征
        await storage_service.store_content_features_batch(normalized_features)
        
        return FeatureResponse(
            success=True,
            message=f"内容特征标准化完成，处理数量: {len(normalized_features)}",
            data={"processed_count": len(normalized_features)}
        )
        
    except Exception as e:
        logger.error(f"内容特征标准化失败: {e}")
        raise HTTPException(status_code=500, detail="内容特征标准化失败")

@feature_router.get("/engineering/statistics/{feature_type}")
async def get_feature_statistics(feature_type: str):
    """获取特征统计信息"""
    try:
        if feature_type not in ['user_features', 'content_features']:
            raise HTTPException(status_code=400, detail="特征类型必须是 user_features 或 content_features")
        
        stats = await engineering_service.get_feature_statistics(feature_type)
        
        return FeatureResponse(
            success=True,
            message="获取特征统计信息成功",
            data=stats
        )
        
    except Exception as e:
        logger.error(f"获取特征统计信息失败: {e}")
        raise HTTPException(status_code=500, detail="获取特征统计信息失败")

@feature_router.get("/engineering/quality/{feature_type}")
async def get_feature_quality_metrics(feature_type: str):
    """获取特征质量监控指标"""
    try:
        if feature_type not in ['user_features', 'content_features']:
            raise HTTPException(status_code=400, detail="特征类型必须是 user_features 或 content_features")
        
        metrics = await engineering_service.get_quality_metrics(feature_type)
        
        return FeatureResponse(
            success=True,
            message="获取特征质量指标成功",
            data=metrics
        )
        
    except Exception as e:
        logger.error(f"获取特征质量指标失败: {e}")
        raise HTTPException(status_code=500, detail="获取特征质量指标失败")

# 特征管道相关接口
@feature_router.post("/pipeline/users", response_model=FeatureResponse)
async def run_user_feature_pipeline(
    request: BatchFeatureRequest,
    background_tasks: BackgroundTasks,
    pipeline_type: str = Query(default="full", description="管道类型: full, engineering, basic")
):
    """运行用户特征处理管道"""
    try:
        if not request.user_ids:
            raise HTTPException(status_code=400, detail="用户ID列表不能为空")
        
        # 异步运行管道
        background_tasks.add_task(
            _run_user_pipeline_background,
            request.user_ids,
            pipeline_type
        )
        
        return FeatureResponse(
            success=True,
            message=f"用户特征管道已启动，用户数量: {len(request.user_ids)}",
            data={"user_count": len(request.user_ids), "pipeline_type": pipeline_type}
        )
        
    except Exception as e:
        logger.error(f"启动用户特征管道失败: {e}")
        raise HTTPException(status_code=500, detail="启动用户特征管道失败")

@feature_router.post("/pipeline/contents", response_model=FeatureResponse)
async def run_content_feature_pipeline(
    request: BatchFeatureRequest,
    background_tasks: BackgroundTasks,
    pipeline_type: str = Query(default="full", description="管道类型: full, engineering, basic")
):
    """运行内容特征处理管道"""
    try:
        if not request.content_ids:
            raise HTTPException(status_code=400, detail="内容ID列表不能为空")
        
        # 异步运行管道
        background_tasks.add_task(
            _run_content_pipeline_background,
            request.content_ids,
            pipeline_type
        )
        
        return FeatureResponse(
            success=True,
            message=f"内容特征管道已启动，内容数量: {len(request.content_ids)}",
            data={"content_count": len(request.content_ids), "pipeline_type": pipeline_type}
        )
        
    except Exception as e:
        logger.error(f"启动内容特征管道失败: {e}")
        raise HTTPException(status_code=500, detail="启动内容特征管道失败")

@feature_router.post("/pipeline/realtime", response_model=FeatureResponse)
async def run_realtime_feature_pipeline(behavior: UserBehavior, background_tasks: BackgroundTasks):
    """运行实时特征处理管道"""
    try:
        # 异步处理实时管道
        background_tasks.add_task(
            _run_realtime_pipeline_background,
            behavior
        )
        
        return FeatureResponse(
            success=True,
            message="实时特征管道已启动"
        )
        
    except Exception as e:
        logger.error(f"启动实时特征管道失败: {e}")
        raise HTTPException(status_code=500, detail="启动实时特征管道失败")

@feature_router.post("/pipeline/schedule", response_model=FeatureResponse)
async def schedule_feature_pipeline(
    background_tasks: BackgroundTasks,
    schedule_type: str = Query(default="daily", description="调度类型: daily, weekly, monthly")
):
    """调度特征管道处理"""
    try:
        # 异步运行调度管道
        background_tasks.add_task(
            _run_scheduled_pipeline_background,
            schedule_type
        )
        
        return FeatureResponse(
            success=True,
            message=f"特征管道调度已启动，类型: {schedule_type}"
        )
        
    except Exception as e:
        logger.error(f"启动特征管道调度失败: {e}")
        raise HTTPException(status_code=500, detail="启动特征管道调度失败")

@feature_router.get("/pipeline/status")
async def get_pipeline_status():
    """获取管道状态"""
    try:
        status = await pipeline_service.get_pipeline_status()
        
        return FeatureResponse(
            success=True,
            message="获取管道状态成功",
            data=status
        )
        
    except Exception as e:
        logger.error(f"获取管道状态失败: {e}")
        raise HTTPException(status_code=500, detail="获取管道状态失败")

# 管道后台任务函数
async def _run_user_pipeline_background(user_ids: List[str], pipeline_type: str):
    """后台运行用户特征管道"""
    try:
        results = await pipeline_service.run_user_feature_pipeline(user_ids, pipeline_type)
        logger.info(f"用户特征管道完成: {results}")
    except Exception as e:
        logger.error(f"用户特征管道后台任务失败: {e}")

async def _run_content_pipeline_background(content_ids: List[str], pipeline_type: str):
    """后台运行内容特征管道"""
    try:
        results = await pipeline_service.run_content_feature_pipeline(content_ids, pipeline_type)
        logger.info(f"内容特征管道完成: {results}")
    except Exception as e:
        logger.error(f"内容特征管道后台任务失败: {e}")

async def _run_realtime_pipeline_background(behavior: UserBehavior):
    """后台运行实时特征管道"""
    try:
        results = await pipeline_service.run_realtime_feature_pipeline(behavior)
        logger.info(f"实时特征管道完成: {results}")
    except Exception as e:
        logger.error(f"实时特征管道后台任务失败: {e}")

async def _run_scheduled_pipeline_background(schedule_type: str):
    """后台运行调度特征管道"""
    try:
        results = await pipeline_service.schedule_feature_pipeline(schedule_type)
        logger.info(f"调度特征管道完成: {results}")
    except Exception as e:
        logger.error(f"调度特征管道后台任务失败: {e}")

# ClickHouse和离线特征相关接口
@feature_router.post("/offline/compute/users", response_model=FeatureResponse)
async def compute_user_offline_features(
    background_tasks: BackgroundTasks,
    user_ids: Optional[List[str]] = None
):
    """计算用户离线特征"""
    try:
        # 异步计算离线特征
        background_tasks.add_task(
            _compute_user_offline_features_background,
            user_ids
        )
        
        return FeatureResponse(
            success=True,
            message="用户离线特征计算任务已启动",
            data={"user_count": len(user_ids) if user_ids else "all"}
        )
        
    except Exception as e:
        logger.error(f"启动用户离线特征计算失败: {e}")
        raise HTTPException(status_code=500, detail="启动用户离线特征计算失败")

@feature_router.post("/offline/compute/contents", response_model=FeatureResponse)
async def compute_content_offline_features(
    background_tasks: BackgroundTasks,
    content_ids: Optional[List[str]] = None
):
    """计算内容离线特征"""
    try:
        # 异步计算离线特征
        background_tasks.add_task(
            _compute_content_offline_features_background,
            content_ids
        )
        
        return FeatureResponse(
            success=True,
            message="内容离线特征计算任务已启动",
            data={"content_count": len(content_ids) if content_ids else "all"}
        )
        
    except Exception as e:
        logger.error(f"启动内容离线特征计算失败: {e}")
        raise HTTPException(status_code=500, detail="启动内容离线特征计算失败")

@feature_router.post("/offline/compute/interaction-matrix", response_model=FeatureResponse)
async def compute_interaction_matrix(background_tasks: BackgroundTasks):
    """计算用户-内容交互矩阵"""
    try:
        # 异步计算交互矩阵
        background_tasks.add_task(offline_service.compute_interaction_matrix)
        
        return FeatureResponse(
            success=True,
            message="交互矩阵计算任务已启动"
        )
        
    except Exception as e:
        logger.error(f"启动交互矩阵计算失败: {e}")
        raise HTTPException(status_code=500, detail="启动交互矩阵计算失败")

@feature_router.get("/offline/trending/{content_type}")
async def get_trending_contents(
    content_type: str,
    limit: int = Query(default=100, ge=1, le=500)
):
    """获取趋势内容"""
    try:
        if content_type not in ['all', 'article', 'video', 'product']:
            raise HTTPException(status_code=400, detail="内容类型必须是 all, article, video, product 之一")
        
        # 从Redis获取缓存的趋势内容
        from ..core.database import get_redis
        import json
        
        redis_client = await get_redis()
        cache_key = f"trending:{content_type}"
        
        cached_data = await redis_client.get(cache_key)
        if cached_data:
            trending_contents = json.loads(cached_data)
        else:
            # 缓存未命中，实时计算
            if content_type == 'all':
                trending_contents = await clickhouse_service.get_trending_contents(limit=limit)
            else:
                trending_contents = await clickhouse_service.get_trending_contents(content_type, limit)
        
        return FeatureResponse(
            success=True,
            message=f"获取{content_type}趋势内容成功",
            data={
                "trending_contents": trending_contents,
                "count": len(trending_contents)
            }
        )
        
    except Exception as e:
        logger.error(f"获取趋势内容失败: {e}")
        raise HTTPException(status_code=500, detail="获取趋势内容失败")

@feature_router.get("/offline/user-patterns/{user_id}")
async def get_user_behavior_patterns(user_id: str):
    """获取用户行为模式"""
    try:
        patterns = await clickhouse_service.get_user_behavior_patterns(user_id)
        
        return FeatureResponse(
            success=True,
            message="获取用户行为模式成功",
            data=patterns
        )
        
    except Exception as e:
        logger.error(f"获取用户行为模式失败: {e}")
        raise HTTPException(status_code=500, detail="获取用户行为模式失败")

@feature_router.get("/offline/database-stats")
async def get_database_statistics():
    """获取数据库统计信息"""
    try:
        stats = await clickhouse_service.get_database_statistics()
        
        return FeatureResponse(
            success=True,
            message="获取数据库统计信息成功",
            data=stats
        )
        
    except Exception as e:
        logger.error(f"获取数据库统计信息失败: {e}")
        raise HTTPException(status_code=500, detail="获取数据库统计信息失败")

@feature_router.post("/offline/scheduler/start", response_model=FeatureResponse)
async def start_offline_scheduler():
    """启动离线特征计算调度器"""
    try:
        offline_service.start_scheduler()
        
        return FeatureResponse(
            success=True,
            message="离线特征计算调度器已启动"
        )
        
    except Exception as e:
        logger.error(f"启动调度器失败: {e}")
        raise HTTPException(status_code=500, detail="启动调度器失败")

@feature_router.post("/offline/scheduler/stop", response_model=FeatureResponse)
async def stop_offline_scheduler():
    """停止离线特征计算调度器"""
    try:
        offline_service.stop_scheduler()
        
        return FeatureResponse(
            success=True,
            message="离线特征计算调度器已停止"
        )
        
    except Exception as e:
        logger.error(f"停止调度器失败: {e}")
        raise HTTPException(status_code=500, detail="停止调度器失败")

@feature_router.get("/offline/scheduler/status")
async def get_offline_scheduler_status():
    """获取离线特征计算调度器状态"""
    try:
        status = await offline_service.get_computation_status()
        
        return FeatureResponse(
            success=True,
            message="获取调度器状态成功",
            data=status
        )
        
    except Exception as e:
        logger.error(f"获取调度器状态失败: {e}")
        raise HTTPException(status_code=500, detail="获取调度器状态失败")

@feature_router.post("/offline/cleanup", response_model=FeatureResponse)
async def cleanup_expired_data(background_tasks: BackgroundTasks):
    """清理过期数据"""
    try:
        # 异步清理过期数据
        background_tasks.add_task(offline_service.cleanup_expired_data)
        
        return FeatureResponse(
            success=True,
            message="数据清理任务已启动"
        )
        
    except Exception as e:
        logger.error(f"启动数据清理失败: {e}")
        raise HTTPException(status_code=500, detail="启动数据清理失败")

@feature_router.post("/offline/optimize", response_model=FeatureResponse)
async def optimize_database_performance(background_tasks: BackgroundTasks):
    """优化数据库性能"""
    try:
        # 异步优化数据库性能
        background_tasks.add_task(clickhouse_service.optimize_table_performance)
        
        return FeatureResponse(
            success=True,
            message="数据库性能优化任务已启动"
        )
        
    except Exception as e:
        logger.error(f"启动性能优化失败: {e}")
        raise HTTPException(status_code=500, detail="启动性能优化失败")

# 离线特征计算后台任务函数
async def _compute_user_offline_features_background(user_ids: Optional[List[str]]):
    """后台计算用户离线特征"""
    try:
        results = await offline_service.compute_all_user_features()
        logger.info(f"用户离线特征计算完成: {results}")
    except Exception as e:
        logger.error(f"用户离线特征计算后台任务失败: {e}")

async def _compute_content_offline_features_background(content_ids: Optional[List[str]]):
    """后台计算内容离线特征"""
    try:
        results = await offline_service.compute_all_content_features()
        logger.info(f"内容离线特征计算完成: {results}")
    except Exception as e:
        logger.error(f"内容离线特征计算后台任务失败: {e}")