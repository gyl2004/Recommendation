package com.recommendation.service.service.impl;

import com.recommendation.service.dto.FeedbackRequest;
import com.recommendation.service.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 用户反馈服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {
    
    private final RabbitTemplate rabbitTemplate;
    
    private static final String FEEDBACK_EXCHANGE = "recommendation.feedback.exchange";
    private static final String FEEDBACK_ROUTING_KEY = "feedback.process";
    
    @Override
    public void recordFeedback(FeedbackRequest request) {
        try {
            // 设置时间戳
            if (request.getTimestamp() == null) {
                request.setTimestamp(System.currentTimeMillis());
            }
            
            // 发送到消息队列进行异步处理
            rabbitTemplate.convertAndSend(FEEDBACK_EXCHANGE, FEEDBACK_ROUTING_KEY, request);
            
            log.info("用户反馈已发送到消息队列 - userId: {}, contentId: {}, feedbackType: {}", 
                    request.getUserId(), request.getContentId(), request.getFeedbackType());
            
        } catch (Exception e) {
            log.error("发送用户反馈到消息队列失败 - userId: {}, contentId: {}, error: {}", 
                    request.getUserId(), request.getContentId(), e.getMessage(), e);
            
            // 消息队列失败时，直接异步处理
            processFeedbackAsync(request);
        }
    }
    
    @Override
    @Async
    public void processFeedbackAsync(FeedbackRequest request) {
        try {
            log.info("开始异步处理用户反馈 - userId: {}, contentId: {}, feedbackType: {}", 
                    request.getUserId(), request.getContentId(), request.getFeedbackType());
            
            // 更新用户画像
            updateUserProfile(request.getUserId(), request.getContentId(), request.getFeedbackType());
            
            // 更新内容热度
            updateContentHotness(request.getContentId(), request.getFeedbackType());
            
            log.info("用户反馈处理完成 - userId: {}, contentId: {}", 
                    request.getUserId(), request.getContentId());
            
        } catch (Exception e) {
            log.error("异步处理用户反馈失败 - userId: {}, contentId: {}, error: {}", 
                    request.getUserId(), request.getContentId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void updateUserProfile(String userId, String contentId, String feedbackType) {
        try {
            // 根据反馈类型更新用户画像
            switch (feedbackType) {
                case "click":
                    // 增加点击权重
                    log.debug("更新用户点击行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                case "like":
                    // 增加喜欢权重
                    log.debug("更新用户喜欢行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                case "share":
                    // 增加分享权重
                    log.debug("更新用户分享行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                case "comment":
                    // 增加评论权重
                    log.debug("更新用户评论行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                case "dislike":
                    // 降低推荐权重
                    log.debug("更新用户不喜欢行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                case "report":
                    // 加入黑名单
                    log.debug("更新用户举报行为 - userId: {}, contentId: {}", userId, contentId);
                    break;
                default:
                    log.warn("未知的反馈类型 - feedbackType: {}", feedbackType);
            }
            
            // 这里应该调用用户画像服务更新用户特征
            // userProfileService.updateProfile(userId, contentId, feedbackType);
            
        } catch (Exception e) {
            log.error("更新用户画像失败 - userId: {}, contentId: {}, error: {}", 
                    userId, contentId, e.getMessage(), e);
        }
    }
    
    @Override
    public void updateContentHotness(String contentId, String feedbackType) {
        try {
            // 根据反馈类型更新内容热度
            double hotScore = 0.0;
            
            switch (feedbackType) {
                case "click":
                    hotScore = 1.0;
                    break;
                case "like":
                    hotScore = 3.0;
                    break;
                case "share":
                    hotScore = 5.0;
                    break;
                case "comment":
                    hotScore = 2.0;
                    break;
                case "dislike":
                    hotScore = -1.0;
                    break;
                case "report":
                    hotScore = -5.0;
                    break;
                default:
                    hotScore = 0.0;
            }
            
            log.debug("更新内容热度 - contentId: {}, feedbackType: {}, hotScore: {}", 
                    contentId, feedbackType, hotScore);
            
            // 这里应该调用内容服务更新内容热度
            // contentService.updateHotness(contentId, hotScore);
            
        } catch (Exception e) {
            log.error("更新内容热度失败 - contentId: {}, error: {}", contentId, e.getMessage(), e);
        }
    }
}