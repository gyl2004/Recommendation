package com.recommendation.user.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 用户行为数据收集请求DTO
 */
@Data
public class UserBehaviorRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 内容ID
     */
    @NotNull(message = "内容ID不能为空")
    private Long contentId;

    /**
     * 行为类型：view(浏览)、click(点击)、like(点赞)、share(分享)、comment(评论)
     */
    @NotBlank(message = "行为类型不能为空")
    private String actionType;

    /**
     * 内容类型：article(文章)、video(视频)、product(商品)
     */
    private String contentType;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 设备类型：mobile、desktop、tablet
     */
    private String deviceType;

    /**
     * 行为持续时间（秒）
     */
    private Integer duration;

    /**
     * 额外数据
     */
    private Map<String, Object> extraData;
}