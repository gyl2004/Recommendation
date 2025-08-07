package com.recommendation.service.service;

import com.recommendation.service.dto.FeedbackRequest;

/**
 * 用户反馈服务接口
 */
public interface FeedbackService {
    
    /**
     * 记录用户反馈
     *
     * @param request 反馈请求
     */
    void recordFeedback(FeedbackRequest request);
    
    /**
     * 异步处理用户反馈
     *
     * @param request 反馈请求
     */
    void processFeedbackAsync(FeedbackRequest request);
    
    /**
     * 更新用户画像
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param feedbackType 反馈类型
     */
    void updateUserProfile(String userId, String contentId, String feedbackType);
    
    /**
     * 更新内容热度
     *
     * @param contentId 内容ID
     * @param feedbackType 反馈类型
     */
    void updateContentHotness(String contentId, String feedbackType);
}