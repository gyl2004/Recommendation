package com.recommendation.user.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户行为消息监听器
 * 处理用户行为事件的异步处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserBehaviorListener {

    /**
     * 处理用户行为消息
     */
    @RabbitListener(queues = "user.behavior.queue")
    public void handleUserBehavior(Map<String, Object> message) {
        try {
            log.info("收到用户行为消息: {}", message);

            Long behaviorId = Long.valueOf(message.get("behaviorId").toString());
            Long userId = Long.valueOf(message.get("userId").toString());
            Long contentId = Long.valueOf(message.get("contentId").toString());
            String actionType = message.get("actionType").toString();
            String contentType = message.get("contentType").toString();
            String timestamp = message.get("timestamp").toString();
            Integer duration = Integer.valueOf(message.get("duration").toString());

            // 这里可以进行各种异步处理：
            // 1. 更新用户画像
            // 2. 计算内容热度
            // 3. 触发推荐算法更新
            // 4. 发送到数据分析系统

            processUserProfileUpdate(userId, contentId, actionType, contentType);
            processContentHotScore(contentId, actionType);
            processRecommendationUpdate(userId, contentId, actionType);

            log.info("用户行为消息处理完成: behaviorId={}", behaviorId);

        } catch (Exception e) {
            log.error("处理用户行为消息失败: {}", message, e);
            // 这里可以实现重试机制或者发送到死信队列
        }
    }

    /**
     * 处理用户画像更新
     */
    private void processUserProfileUpdate(Long userId, Long contentId, String actionType, String contentType) {
        log.debug("处理用户画像更新: userId={}, contentId={}, actionType={}, contentType={}", 
                userId, contentId, actionType, contentType);

        // 根据用户行为更新用户画像
        // 例如：
        // - 浏览文章 -> 增加对应分类的兴趣权重
        // - 点赞视频 -> 增加视频偏好度
        // - 分享商品 -> 增加购物倾向

        // 这里可以调用用户画像服务或发送消息到用户画像队列
    }

    /**
     * 处理内容热度计算
     */
    private void processContentHotScore(Long contentId, String actionType) {
        log.debug("处理内容热度计算: contentId={}, actionType={}", contentId, actionType);

        // 根据行为类型计算内容热度分数
        // 例如：
        // - 浏览 +1分
        // - 点击 +2分
        // - 点赞 +5分
        // - 分享 +10分
        // - 评论 +8分

        // 这里可以更新Redis中的内容热度缓存
    }

    /**
     * 处理推荐算法更新
     */
    private void processRecommendationUpdate(Long userId, Long contentId, String actionType) {
        log.debug("处理推荐算法更新: userId={}, contentId={}, actionType={}", 
                userId, contentId, actionType);

        // 触发推荐算法的实时更新
        // 例如：
        // - 更新用户-物品交互矩阵
        // - 触发协同过滤算法重新计算
        // - 更新深度学习模型的特征

        // 这里可以调用推荐服务的API或发送消息到推荐队列
    }
}