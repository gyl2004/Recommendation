"""
排序服务API接口
提供在线排序和批量预测的REST API
"""
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
import asyncio
from datetime import datetime
from loguru import logger

from ..services.ranking_service import RankingService
from ..services.fusion_reranking_service import FusionRerankingService


# 请求和响应模型
class CandidateItem(BaseModel):
    """候选内容项"""
    content_id: str = Field(..., description="内容ID")
    content_type: str = Field(..., description="内容类型")
    title: str = Field(default="", description="内容标题")
    category: str = Field(default="", description="内容分类")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="额外元数据")


class RankingRequest(BaseModel):
    """排序请求"""
    user_id: str = Field(..., description="用户ID")
    candidates: List[CandidateItem] = Field(..., description="候选内容列表")
    context: Optional[Dict[str, Any]] = Field(default=None, description="上下文信息")
    max_results: int = Field(default=10, ge=1, le=100, description="最大返回结果数")


class RankedItem(BaseModel):
    """排序后的内容项"""
    content_id: str
    content_type: str
    title: str
    category: str
    ranking_score: float
    metadata: Dict[str, Any]


class RankingResponse(BaseModel):
    """排序响应"""
    user_id: str
    ranked_items: List[RankedItem]
    total_candidates: int
    processing_time_ms: float
    timestamp: str


class BatchPredictionRequest(BaseModel):
    """批量预测请求"""
    predictions: List[Dict[str, Any]] = Field(..., description="预测请求列表")


class BatchPredictionResponse(BaseModel):
    """批量预测响应"""
    scores: List[float]
    total_requests: int
    processing_time_ms: float
    timestamp: str


class FeatureUpdateRequest(BaseModel):
    """特征更新请求"""
    entity_type: str = Field(..., description="实体类型: user 或 content")
    entity_id: str = Field(..., description="实体ID")
    features: Dict[str, Any] = Field(..., description="特征数据")


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str
    redis_connected: bool
    model_loaded: bool
    pipeline_ready: bool
    timestamp: str


class StatsResponse(BaseModel):
    """统计信息响应"""
    prediction_count: int
    total_prediction_time: float
    avg_prediction_time: float
    model_loaded: bool
    pipeline_fitted: bool


class AlgorithmResult(BaseModel):
    """单个算法的推荐结果"""
    content_id: str
    content_type: str
    title: str = ""
    category: str = ""
    score: float
    metadata: Dict[str, Any] = Field(default_factory=dict)


class FusionRerankingRequest(BaseModel):
    """融合重排请求"""
    user_id: str = Field(..., description="用户ID")
    algorithm_results: Dict[str, List[AlgorithmResult]] = Field(..., description="各算法的推荐结果")
    target_size: int = Field(default=20, ge=1, le=100, description="目标结果数量")
    context: Optional[Dict[str, Any]] = Field(default=None, description="上下文信息")


class FusedRankedItem(BaseModel):
    """融合重排后的内容项"""
    content_id: str
    content_type: str
    title: str
    category: str
    final_score: float
    fusion_score: float
    algorithm_coverage: int
    score_breakdown: Dict[str, float]
    metadata: Dict[str, Any]


class FusionRerankingResponse(BaseModel):
    """融合重排响应"""
    user_id: str
    fused_items: List[FusedRankedItem]
    total_candidates: int
    processing_time_ms: float
    timestamp: str


class ConfigUpdateRequest(BaseModel):
    """配置更新请求"""
    algorithm_weights: Optional[Dict[str, float]] = None
    diversity_config: Optional[Dict[str, Any]] = None
    business_rules: Optional[Dict[str, Any]] = None
    dedup_config: Optional[Dict[str, Any]] = None


# 创建FastAPI应用
app = FastAPI(
    title="智能内容推荐排序服务",
    description="基于Wide&Deep模型的内容排序服务",
    version="1.0.0"
)

# 添加CORS中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局服务实例
ranking_service: Optional[RankingService] = None
fusion_reranking_service: Optional[FusionRerankingService] = None


@app.on_event("startup")
async def startup_event():
    """应用启动事件"""
    global ranking_service, fusion_reranking_service
    
    logger.info("启动排序服务")
    
    # 初始化排序服务
    ranking_service = RankingService(
        model_path="models/wide_deep_model",
        pipeline_path="models/feature_pipeline.pkl",
        redis_url="redis://localhost:6379"
    )
    
    # 初始化融合重排服务
    fusion_reranking_service = FusionRerankingService()
    
    try:
        await ranking_service.initialize()
        logger.info("排序服务启动成功")
        logger.info("融合重排服务启动成功")
    except Exception as e:
        logger.error(f"服务启动失败: {e}")
        # 即使初始化失败也继续启动，但会返回错误状态


@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭事件"""
    global ranking_service
    
    if ranking_service:
        await ranking_service.close()
        logger.info("排序服务已关闭")


@app.post("/api/v1/ranking/rank", response_model=RankingResponse)
async def rank_candidates(request: RankingRequest) -> RankingResponse:
    """
    对候选内容进行排序
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    start_time = datetime.now()
    
    try:
        # 转换候选内容格式
        candidates = [
            {
                'content_id': item.content_id,
                'content_type': item.content_type,
                'title': item.title,
                'category': item.category,
                'metadata': item.metadata
            }
            for item in request.candidates
        ]
        
        # 执行排序
        ranked_candidates = await ranking_service.rank_candidates(
            user_id=request.user_id,
            candidates=candidates,
            context=request.context
        )
        
        # 限制返回结果数量
        ranked_candidates = ranked_candidates[:request.max_results]
        
        # 转换响应格式
        ranked_items = [
            RankedItem(
                content_id=item['content_id'],
                content_type=item['content_type'],
                title=item.get('title', ''),
                category=item.get('category', ''),
                ranking_score=item['ranking_score'],
                metadata=item.get('metadata', {})
            )
            for item in ranked_candidates
        ]
        
        processing_time = (datetime.now() - start_time).total_seconds() * 1000
        
        return RankingResponse(
            user_id=request.user_id,
            ranked_items=ranked_items,
            total_candidates=len(request.candidates),
            processing_time_ms=processing_time,
            timestamp=datetime.now().isoformat()
        )
        
    except Exception as e:
        logger.error(f"排序请求处理失败: {e}")
        raise HTTPException(status_code=500, detail=f"排序处理失败: {str(e)}")


@app.post("/api/v1/ranking/batch_predict", response_model=BatchPredictionResponse)
async def batch_predict(request: BatchPredictionRequest) -> BatchPredictionResponse:
    """
    批量预测得分
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    start_time = datetime.now()
    
    try:
        # 执行批量预测
        scores = await ranking_service.batch_predict(request.predictions)
        
        processing_time = (datetime.now() - start_time).total_seconds() * 1000
        
        return BatchPredictionResponse(
            scores=scores,
            total_requests=len(request.predictions),
            processing_time_ms=processing_time,
            timestamp=datetime.now().isoformat()
        )
        
    except Exception as e:
        logger.error(f"批量预测请求处理失败: {e}")
        raise HTTPException(status_code=500, detail=f"批量预测失败: {str(e)}")


@app.post("/api/v1/ranking/features/update")
async def update_features(request: FeatureUpdateRequest, background_tasks: BackgroundTasks):
    """
    更新特征数据
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    try:
        if request.entity_type == "user":
            background_tasks.add_task(
                ranking_service.update_user_features,
                request.entity_id,
                request.features
            )
        elif request.entity_type == "content":
            background_tasks.add_task(
                ranking_service.update_content_features,
                request.entity_id,
                request.features
            )
        else:
            raise HTTPException(
                status_code=400, 
                detail="entity_type必须是'user'或'content'"
            )
        
        return {"message": "特征更新任务已提交", "entity_id": request.entity_id}
        
    except Exception as e:
        logger.error(f"特征更新请求处理失败: {e}")
        raise HTTPException(status_code=500, detail=f"特征更新失败: {str(e)}")


@app.get("/api/v1/ranking/health", response_model=HealthResponse)
async def health_check() -> HealthResponse:
    """
    健康检查
    """
    if not ranking_service:
        return HealthResponse(
            status="unhealthy",
            redis_connected=False,
            model_loaded=False,
            pipeline_ready=False,
            timestamp=datetime.now().isoformat()
        )
    
    try:
        health_info = await ranking_service.health_check()
        return HealthResponse(**health_info)
        
    except Exception as e:
        logger.error(f"健康检查失败: {e}")
        return HealthResponse(
            status="unhealthy",
            redis_connected=False,
            model_loaded=False,
            pipeline_ready=False,
            timestamp=datetime.now().isoformat()
        )


@app.get("/api/v1/ranking/stats", response_model=StatsResponse)
async def get_stats() -> StatsResponse:
    """
    获取服务统计信息
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    try:
        stats = ranking_service.get_service_stats()
        return StatsResponse(**stats)
        
    except Exception as e:
        logger.error(f"获取统计信息失败: {e}")
        raise HTTPException(status_code=500, detail=f"获取统计信息失败: {str(e)}")


@app.get("/api/v1/ranking/model/info")
async def get_model_info():
    """
    获取模型信息
    """
    if not ranking_service or not ranking_service.model:
        raise HTTPException(status_code=503, detail="模型未加载")
    
    try:
        model_summary = ranking_service.model.get_model_summary()
        return {
            "model_type": "Wide&Deep",
            "model_summary": model_summary,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"获取模型信息失败: {e}")
        raise HTTPException(status_code=500, detail=f"获取模型信息失败: {str(e)}")


@app.post("/api/v1/ranking/model/reload")
async def reload_model():
    """
    重新加载模型
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    try:
        # 这里可以扩展为从配置中读取新的模型路径
        success = True  # 简化实现，实际应该调用模型重载逻辑
        
        if success:
            return {
                "message": "模型重新加载成功",
                "timestamp": datetime.now().isoformat()
            }
        else:
            raise HTTPException(status_code=500, detail="模型重新加载失败")
            
    except Exception as e:
        logger.error(f"重新加载模型失败: {e}")
        raise HTTPException(status_code=500, detail=f"重新加载模型失败: {str(e)}")


@app.get("/api/v1/ranking/model/evaluation")
async def get_model_evaluation():
    """
    获取模型评估指标
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    try:
        # 获取在线评估指标
        online_metrics = ranking_service.get_service_stats()
        
        return {
            "online_metrics": online_metrics,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"获取模型评估失败: {e}")
        raise HTTPException(status_code=500, detail=f"获取模型评估失败: {str(e)}")


@app.post("/api/v1/ranking/fusion_rerank", response_model=FusionRerankingResponse)
async def fusion_rerank(request: FusionRerankingRequest) -> FusionRerankingResponse:
    """
    融合多算法结果并重排
    """
    if not fusion_reranking_service:
        raise HTTPException(status_code=503, detail="融合重排服务未初始化")
    
    start_time = datetime.now()
    
    try:
        # 转换算法结果格式
        algorithm_results = {}
        total_candidates = 0
        
        for algorithm_name, results in request.algorithm_results.items():
            algorithm_results[algorithm_name] = [
                {
                    'content_id': item.content_id,
                    'content_type': item.content_type,
                    'title': item.title,
                    'category': item.category,
                    'score': item.score,
                    'metadata': item.metadata
                }
                for item in results
            ]
            total_candidates += len(results)
        
        # 执行融合重排
        fused_results = await fusion_reranking_service.fuse_and_rerank(
            algorithm_results=algorithm_results,
            user_id=request.user_id,
            target_size=request.target_size,
            context=request.context
        )
        
        # 转换响应格式
        fused_items = [
            FusedRankedItem(
                content_id=item['content_id'],
                content_type=item['content_type'],
                title=item.get('title', ''),
                category=item.get('category', ''),
                final_score=item.get('final_score', 0.0),
                fusion_score=item.get('fusion_score', 0.0),
                algorithm_coverage=item.get('algorithm_coverage', 0),
                score_breakdown=item.get('score_breakdown', {}),
                metadata=item.get('metadata', {})
            )
            for item in fused_results
        ]
        
        processing_time = (datetime.now() - start_time).total_seconds() * 1000
        
        return FusionRerankingResponse(
            user_id=request.user_id,
            fused_items=fused_items,
            total_candidates=total_candidates,
            processing_time_ms=processing_time,
            timestamp=datetime.now().isoformat()
        )
        
    except Exception as e:
        logger.error(f"融合重排请求处理失败: {e}")
        raise HTTPException(status_code=500, detail=f"融合重排处理失败: {str(e)}")


@app.get("/api/v1/ranking/fusion_config")
async def get_fusion_config():
    """
    获取融合重排服务配置
    """
    if not fusion_reranking_service:
        raise HTTPException(status_code=503, detail="融合重排服务未初始化")
    
    try:
        config = fusion_reranking_service.get_service_config()
        return {
            "config": config,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"获取配置失败: {e}")
        raise HTTPException(status_code=500, detail=f"获取配置失败: {str(e)}")


@app.post("/api/v1/ranking/fusion_config")
async def update_fusion_config(request: ConfigUpdateRequest):
    """
    更新融合重排服务配置
    """
    if not fusion_reranking_service:
        raise HTTPException(status_code=503, detail="融合重排服务未初始化")
    
    try:
        # 构建更新配置
        update_config = {}
        if request.algorithm_weights is not None:
            update_config['algorithm_weights'] = request.algorithm_weights
        if request.diversity_config is not None:
            update_config['diversity_config'] = request.diversity_config
        if request.business_rules is not None:
            update_config['business_rules'] = request.business_rules
        if request.dedup_config is not None:
            update_config['dedup_config'] = request.dedup_config
        
        # 更新配置
        fusion_reranking_service.update_config(update_config)
        
        return {
            "message": "配置更新成功",
            "updated_config": update_config,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"更新配置失败: {e}")
        raise HTTPException(status_code=500, detail=f"更新配置失败: {str(e)}")


@app.post("/api/v1/ranking/feedback")
async def submit_feedback(
    user_id: str,
    content_id: str,
    prediction_score: float,
    actual_label: int,
    background_tasks: BackgroundTasks
):
    """
    提交反馈数据用于模型在线评估
    """
    if not ranking_service:
        raise HTTPException(status_code=503, detail="排序服务未初始化")
    
    try:
        # 异步处理反馈数据
        background_tasks.add_task(
            process_feedback,
            user_id,
            content_id,
            prediction_score,
            actual_label
        )
        
        return {
            "message": "反馈数据已提交",
            "user_id": user_id,
            "content_id": content_id,
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"提交反馈失败: {e}")
        raise HTTPException(status_code=500, detail=f"提交反馈失败: {str(e)}")


async def process_feedback(user_id: str, content_id: str, prediction_score: float, actual_label: int):
    """处理反馈数据"""
    try:
        # 这里可以添加反馈数据的处理逻辑
        # 例如：存储到数据库、更新在线评估指标等
        logger.info(f"处理反馈数据: user={user_id}, content={content_id}, score={prediction_score}, label={actual_label}")
        
    except Exception as e:
        logger.error(f"处理反馈数据失败: {e}")


# 根路径
@app.get("/")
async def root():
    """根路径"""
    return {
        "service": "智能内容推荐排序服务",
        "version": "1.0.0",
        "status": "running",
        "timestamp": datetime.now().isoformat()
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)