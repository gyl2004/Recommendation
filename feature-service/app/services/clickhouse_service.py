"""
ClickHouse服务
负责ClickHouse数据库的操作和离线特征计算
"""
import asyncio
from typing import Dict, List, Optional, Any, Tuple
from datetime import datetime, timedelta
import pandas as pd
import numpy as np
from loguru import logger

from ..core.database import get_clickhouse
from ..core.config import settings
from ..models.schemas import UserBehavior, ActionType, ContentType

class ClickHouseService:
    """ClickHouse服务"""
    
    def __init__(self):
        self.batch_size = settings.BATCH_SIZE
        
        # 离线特征计算配置
        self.feature_config = {
            'user_feature_window_days': 30,    # 用户特征计算窗口期
            'content_feature_window_days': 7,  # 内容特征计算窗口期
            'min_interactions': 5,             # 最小交互次数
            'feature_update_interval': 3600    # 特征更新间隔(秒)
        }
    
    async def batch_insert_user_behaviors(self, behaviors: List[UserBehavior]) -> bool:
        """批量插入用户行为数据"""
        try:
            if not behaviors:
                return True
            
            clickhouse_client = get_clickhouse()
            
            # 准备数据
            data = []
            for behavior in behaviors:
                data.append([
                    int(behavior.user_id),
                    int(behavior.content_id),
                    behavior.action_type.value,
                    behavior.content_type.value,
                    behavior.session_id or '',
                    behavior.device_type or '',
                    behavior.timestamp,
                    behavior.duration or 0,
                    str(behavior.extra_data) if behavior.extra_data else '{}'
                ])
            
            # 批量插入
            clickhouse_client.execute(
                """
                INSERT INTO user_behaviors 
                (user_id, content_id, action_type, content_type, session_id, device_type, timestamp, duration, extra_data)
                VALUES
                """,
                data
            )
            
            logger.info(f"批量插入用户行为数据成功，数量: {len(behaviors)}")
            return True
            
        except Exception as e:
            logger.error(f"批量插入用户行为数据失败: {e}")
            return False
    
    async def compute_user_offline_features(self, user_ids: List[str] = None) -> Dict[str, Dict[str, Any]]:
        """计算用户离线特征"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 构建查询条件
            where_clause = ""
            if user_ids:
                user_ids_str = ','.join(user_ids)
                where_clause = f"AND user_id IN ({user_ids_str})"
            
            # 计算用户特征的SQL查询
            query = f"""
            SELECT 
                user_id,
                COUNT(*) as total_actions,
                COUNT(DISTINCT content_id) as unique_contents,
                COUNT(DISTINCT session_id) as session_count,
                COUNT(DISTINCT toDate(timestamp)) as active_days,
                
                -- 行为类型统计
                countIf(action_type = 'view') as view_count,
                countIf(action_type = 'click') as click_count,
                countIf(action_type = 'like') as like_count,
                countIf(action_type = 'share') as share_count,
                countIf(action_type = 'comment') as comment_count,
                countIf(action_type = 'purchase') as purchase_count,
                
                -- 内容类型偏好
                countIf(content_type = 'article') as article_interactions,
                countIf(content_type = 'video') as video_interactions,
                countIf(content_type = 'product') as product_interactions,
                
                -- 时间特征
                avg(duration) as avg_duration,
                max(timestamp) as last_active_time,
                min(timestamp) as first_active_time,
                
                -- 设备偏好
                uniqExact(device_type) as device_types_count,
                any(device_type) as primary_device,
                
                -- 活跃度指标
                COUNT(*) / COUNT(DISTINCT toDate(timestamp)) as daily_avg_actions,
                
                -- 行为分数计算
                sum(multiIf(
                    action_type = 'view', 1,
                    action_type = 'click', 2,
                    action_type = 'like', 3,
                    action_type = 'share', 4,
                    action_type = 'comment', 3.5,
                    action_type = 'purchase', 5,
                    0
                )) as behavior_score
                
            FROM user_behaviors 
            WHERE timestamp >= now() - INTERVAL {self.feature_config['user_feature_window_days']} DAY
                {where_clause}
            GROUP BY user_id
            HAVING total_actions >= {self.feature_config['min_interactions']}
            ORDER BY behavior_score DESC
            """
            
            results = clickhouse_client.execute(query)
            
            # 处理结果
            user_features = {}
            for row in results:
                user_id = str(row[0])
                user_features[user_id] = {
                    'total_actions': row[1],
                    'unique_contents': row[2],
                    'session_count': row[3],
                    'active_days': row[4],
                    'view_count': row[5],
                    'click_count': row[6],
                    'like_count': row[7],
                    'share_count': row[8],
                    'comment_count': row[9],
                    'purchase_count': row[10],
                    'article_interactions': row[11],
                    'video_interactions': row[12],
                    'product_interactions': row[13],
                    'avg_duration': float(row[14]) if row[14] else 0.0,
                    'last_active_time': row[15],
                    'first_active_time': row[16],
                    'device_types_count': row[17],
                    'primary_device': row[18],
                    'daily_avg_actions': float(row[19]) if row[19] else 0.0,
                    'behavior_score': float(row[20]) if row[20] else 0.0,
                    'computed_at': datetime.now()
                }
            
            logger.info(f"计算用户离线特征完成，用户数量: {len(user_features)}")
            return user_features
            
        except Exception as e:
            logger.error(f"计算用户离线特征失败: {e}")
            return {}
    
    async def compute_content_offline_features(self, content_ids: List[str] = None) -> Dict[str, Dict[str, Any]]:
        """计算内容离线特征"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 构建查询条件
            where_clause = ""
            if content_ids:
                content_ids_str = ','.join(content_ids)
                where_clause = f"AND content_id IN ({content_ids_str})"
            
            # 计算内容特征的SQL查询
            query = f"""
            SELECT 
                content_id,
                content_type,
                COUNT(*) as total_interactions,
                COUNT(DISTINCT user_id) as unique_users,
                COUNT(DISTINCT session_id) as session_count,
                
                -- 行为类型统计
                countIf(action_type = 'view') as view_count,
                countIf(action_type = 'click') as click_count,
                countIf(action_type = 'like') as like_count,
                countIf(action_type = 'share') as share_count,
                countIf(action_type = 'comment') as comment_count,
                countIf(action_type = 'purchase') as purchase_count,
                
                -- 转化率指标
                click_count / view_count as ctr,
                like_count / view_count as like_rate,
                share_count / view_count as share_rate,
                
                -- 用户参与度
                avg(duration) as avg_view_duration,
                quantile(0.5)(duration) as median_duration,
                quantile(0.9)(duration) as p90_duration,
                
                -- 时间特征
                max(timestamp) as last_interaction_time,
                min(timestamp) as first_interaction_time,
                
                -- 热度分数
                sum(multiIf(
                    action_type = 'view', 1,
                    action_type = 'click', 2,
                    action_type = 'like', 3,
                    action_type = 'share', 5,
                    action_type = 'comment', 4,
                    action_type = 'purchase', 10,
                    0
                )) as popularity_score,
                
                -- 质量指标
                countIf(duration >= 30) / countIf(action_type = 'view') as engagement_rate,
                COUNT(DISTINCT user_id) / COUNT(*) as user_diversity
                
            FROM user_behaviors 
            WHERE timestamp >= now() - INTERVAL {self.feature_config['content_feature_window_days']} DAY
                {where_clause}
            GROUP BY content_id, content_type
            HAVING total_interactions >= {self.feature_config['min_interactions']}
            ORDER BY popularity_score DESC
            """
            
            results = clickhouse_client.execute(query)
            
            # 处理结果
            content_features = {}
            for row in results:
                content_id = str(row[0])
                content_features[content_id] = {
                    'content_type': row[1],
                    'total_interactions': row[2],
                    'unique_users': row[3],
                    'session_count': row[4],
                    'view_count': row[5],
                    'click_count': row[6],
                    'like_count': row[7],
                    'share_count': row[8],
                    'comment_count': row[9],
                    'purchase_count': row[10],
                    'ctr': float(row[11]) if row[11] else 0.0,
                    'like_rate': float(row[12]) if row[12] else 0.0,
                    'share_rate': float(row[13]) if row[13] else 0.0,
                    'avg_view_duration': float(row[14]) if row[14] else 0.0,
                    'median_duration': float(row[15]) if row[15] else 0.0,
                    'p90_duration': float(row[16]) if row[16] else 0.0,
                    'last_interaction_time': row[17],
                    'first_interaction_time': row[18],
                    'popularity_score': float(row[19]) if row[19] else 0.0,
                    'engagement_rate': float(row[20]) if row[20] else 0.0,
                    'user_diversity': float(row[21]) if row[21] else 0.0,
                    'computed_at': datetime.now()
                }
            
            logger.info(f"计算内容离线特征完成，内容数量: {len(content_features)}")
            return content_features
            
        except Exception as e:
            logger.error(f"计算内容离线特征失败: {e}")
            return {}
    
    async def compute_user_content_interaction_matrix(self, user_ids: List[str] = None, 
                                                    content_ids: List[str] = None) -> pd.DataFrame:
        """计算用户-内容交互矩阵"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 构建查询条件
            where_conditions = []
            if user_ids:
                user_ids_str = ','.join(user_ids)
                where_conditions.append(f"user_id IN ({user_ids_str})")
            if content_ids:
                content_ids_str = ','.join(content_ids)
                where_conditions.append(f"content_id IN ({content_ids_str})")
            
            where_clause = ""
            if where_conditions:
                where_clause = "AND " + " AND ".join(where_conditions)
            
            # 查询用户-内容交互数据
            query = f"""
            SELECT 
                user_id,
                content_id,
                sum(multiIf(
                    action_type = 'view', 1,
                    action_type = 'click', 2,
                    action_type = 'like', 3,
                    action_type = 'share', 4,
                    action_type = 'comment', 3.5,
                    action_type = 'purchase', 5,
                    0
                )) as interaction_score
            FROM user_behaviors 
            WHERE timestamp >= now() - INTERVAL {self.feature_config['user_feature_window_days']} DAY
                {where_clause}
            GROUP BY user_id, content_id
            HAVING interaction_score > 0
            ORDER BY user_id, content_id
            """
            
            results = clickhouse_client.execute(query)
            
            # 转换为DataFrame
            if results:
                df = pd.DataFrame(results, columns=['user_id', 'content_id', 'interaction_score'])
                df['user_id'] = df['user_id'].astype(str)
                df['content_id'] = df['content_id'].astype(str)
                
                # 创建交互矩阵
                interaction_matrix = df.pivot(index='user_id', columns='content_id', values='interaction_score')
                interaction_matrix = interaction_matrix.fillna(0)
                
                logger.info(f"计算用户-内容交互矩阵完成，用户: {len(interaction_matrix.index)}, "
                           f"内容: {len(interaction_matrix.columns)}")
                
                return interaction_matrix
            else:
                return pd.DataFrame()
            
        except Exception as e:
            logger.error(f"计算用户-内容交互矩阵失败: {e}")
            return pd.DataFrame()
    
    async def get_trending_contents(self, content_type: str = None, limit: int = 100) -> List[Dict[str, Any]]:
        """获取趋势内容"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 构建查询条件
            where_clause = ""
            if content_type:
                where_clause = f"AND content_type = '{content_type}'"
            
            # 查询趋势内容
            query = f"""
            SELECT 
                content_id,
                content_type,
                COUNT(*) as recent_interactions,
                COUNT(DISTINCT user_id) as recent_users,
                sum(multiIf(
                    action_type = 'view', 1,
                    action_type = 'click', 2,
                    action_type = 'like', 3,
                    action_type = 'share', 5,
                    action_type = 'comment', 4,
                    0
                )) as trend_score,
                max(timestamp) as last_interaction
            FROM user_behaviors 
            WHERE timestamp >= now() - INTERVAL 24 HOUR
                {where_clause}
            GROUP BY content_id, content_type
            HAVING recent_interactions >= 10
            ORDER BY trend_score DESC
            LIMIT {limit}
            """
            
            results = clickhouse_client.execute(query)
            
            # 处理结果
            trending_contents = []
            for row in results:
                trending_contents.append({
                    'content_id': str(row[0]),
                    'content_type': row[1],
                    'recent_interactions': row[2],
                    'recent_users': row[3],
                    'trend_score': float(row[4]),
                    'last_interaction': row[5]
                })
            
            logger.info(f"获取趋势内容完成，数量: {len(trending_contents)}")
            return trending_contents
            
        except Exception as e:
            logger.error(f"获取趋势内容失败: {e}")
            return []
    
    async def get_user_behavior_patterns(self, user_id: str) -> Dict[str, Any]:
        """获取用户行为模式"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 查询用户行为模式
            query = f"""
            SELECT 
                -- 时间模式
                toHour(timestamp) as hour,
                toDayOfWeek(timestamp) as day_of_week,
                COUNT(*) as action_count,
                
                -- 行为类型分布
                action_type,
                content_type,
                
                -- 会话模式
                session_id,
                device_type,
                avg(duration) as avg_duration
                
            FROM user_behaviors 
            WHERE user_id = {user_id}
                AND timestamp >= now() - INTERVAL 30 DAY
            GROUP BY hour, day_of_week, action_type, content_type, session_id, device_type
            ORDER BY action_count DESC
            """
            
            results = clickhouse_client.execute(query)
            
            # 分析行为模式
            patterns = {
                'hourly_distribution': {},
                'daily_distribution': {},
                'action_type_distribution': {},
                'content_type_distribution': {},
                'device_preference': {},
                'session_patterns': []
            }
            
            for row in results:
                hour, day_of_week, action_count, action_type, content_type, session_id, device_type, avg_duration = row
                
                # 小时分布
                patterns['hourly_distribution'][hour] = patterns['hourly_distribution'].get(hour, 0) + action_count
                
                # 星期分布
                patterns['daily_distribution'][day_of_week] = patterns['daily_distribution'].get(day_of_week, 0) + action_count
                
                # 行为类型分布
                patterns['action_type_distribution'][action_type] = patterns['action_type_distribution'].get(action_type, 0) + action_count
                
                # 内容类型分布
                patterns['content_type_distribution'][content_type] = patterns['content_type_distribution'].get(content_type, 0) + action_count
                
                # 设备偏好
                patterns['device_preference'][device_type] = patterns['device_preference'].get(device_type, 0) + action_count
            
            logger.info(f"获取用户行为模式完成，用户: {user_id}")
            return patterns
            
        except Exception as e:
            logger.error(f"获取用户行为模式失败: {e}")
            return {}
    
    async def create_feature_computation_job(self, job_type: str, params: Dict[str, Any]) -> str:
        """创建特征计算任务"""
        try:
            job_id = f"{job_type}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
            
            # 根据任务类型执行不同的计算
            if job_type == 'user_features':
                user_ids = params.get('user_ids')
                await self.compute_user_offline_features(user_ids)
                
            elif job_type == 'content_features':
                content_ids = params.get('content_ids')
                await self.compute_content_offline_features(content_ids)
                
            elif job_type == 'interaction_matrix':
                user_ids = params.get('user_ids')
                content_ids = params.get('content_ids')
                await self.compute_user_content_interaction_matrix(user_ids, content_ids)
            
            logger.info(f"特征计算任务创建成功，任务ID: {job_id}")
            return job_id
            
        except Exception as e:
            logger.error(f"创建特征计算任务失败: {e}")
            raise
    
    async def optimize_table_performance(self):
        """优化表性能"""
        try:
            clickhouse_client = get_clickhouse()
            
            # 优化用户行为表
            clickhouse_client.execute("OPTIMIZE TABLE user_behaviors FINAL")
            
            # 优化特征向量表
            clickhouse_client.execute("OPTIMIZE TABLE feature_vectors FINAL")
            
            # 优化统计表
            clickhouse_client.execute("OPTIMIZE TABLE user_profile_stats FINAL")
            clickhouse_client.execute("OPTIMIZE TABLE content_stats FINAL")
            
            logger.info("表性能优化完成")
            
        except Exception as e:
            logger.error(f"表性能优化失败: {e}")
    
    async def get_database_statistics(self) -> Dict[str, Any]:
        """获取数据库统计信息"""
        try:
            clickhouse_client = get_clickhouse()
            
            stats = {}
            
            # 获取表大小统计
            tables = ['user_behaviors', 'feature_vectors', 'user_profile_stats', 'content_stats']
            
            for table in tables:
                query = f"""
                SELECT 
                    COUNT(*) as row_count,
                    formatReadableSize(sum(data_compressed_bytes)) as compressed_size,
                    formatReadableSize(sum(data_uncompressed_bytes)) as uncompressed_size
                FROM system.parts 
                WHERE table = '{table}' AND active = 1
                """
                
                result = clickhouse_client.execute(query)
                if result:
                    stats[table] = {
                        'row_count': result[0][0],
                        'compressed_size': result[0][1],
                        'uncompressed_size': result[0][2]
                    }
            
            # 获取最近数据统计
            recent_behaviors_query = """
            SELECT 
                COUNT(*) as recent_behaviors,
                COUNT(DISTINCT user_id) as active_users,
                COUNT(DISTINCT content_id) as active_contents
            FROM user_behaviors 
            WHERE timestamp >= now() - INTERVAL 24 HOUR
            """
            
            recent_result = clickhouse_client.execute(recent_behaviors_query)
            if recent_result:
                stats['recent_activity'] = {
                    'recent_behaviors': recent_result[0][0],
                    'active_users': recent_result[0][1],
                    'active_contents': recent_result[0][2]
                }
            
            stats['updated_at'] = datetime.now().isoformat()
            
            return stats
            
        except Exception as e:
            logger.error(f"获取数据库统计信息失败: {e}")
            return {}