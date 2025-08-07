package com.recommendation.common.exception;

/**
 * 推荐系统业务异常
 */
public class RecommendationException extends RuntimeException {
    
    private final Integer code;
    
    public RecommendationException(String message) {
        super(message);
        this.code = 500;
    }
    
    public RecommendationException(Integer code, String message) {
        super(message);
        this.code = code;
    }
    
    public RecommendationException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
    
    public RecommendationException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    
    public Integer getCode() {
        return code;
    }
}