package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;

import java.util.List;
import java.util.Map;

/**
 * 个性化处理服务接口
 */
public interface PersonalizationService {
    
    /**
     * 基于用户上下文调整推荐结果
     *
     * @param userId 用户ID
     * @param items 推荐项列表
     * @param context 用户上下文
     * @return 调整后的推荐结果
     */
    List<RecommendResponse.RecommendItem> adjustByUserContext(String userId, 
                                                             List<RecommendResponse.RecommendItem> items,
                                                             UserContext context);
    
    /**
     * 生成推荐解释
     *
     * @param userId 用户ID
     * @param item 推荐项
     * @param context 上下文信息
     * @return 推荐解释
     */
    String generateExplanation(String userId, RecommendResponse.RecommendItem item, Map<String, Object> context);
    
    /**
     * 计算推荐置信度
     *
     * @param userId 用户ID
     * @param item 推荐项
     * @param algorithmScores 各算法分数
     * @return 置信度
     */
    Double calculateConfidence(String userId, RecommendResponse.RecommendItem item, Map<String, Double> algorithmScores);
    
    /**
     * 基于A/B测试调整推荐策略
     *
     * @param userId 用户ID
     * @param items 推荐项列表
     * @param experimentName 实验名称
     * @return 调整后的推荐结果
     */
    List<RecommendResponse.RecommendItem> adjustByABTest(String userId, 
                                                        List<RecommendResponse.RecommendItem> items,
                                                        String experimentName);
    
    /**
     * 用户上下文信息
     */
    class UserContext {
        private String deviceType;      // 设备类型
        private String location;        // 地理位置
        private String timeOfDay;       // 时间段
        private String dayOfWeek;       // 星期几
        private String weather;         // 天气情况
        private String networkType;     // 网络类型
        private Map<String, Object> extraContext; // 扩展上下文
        
        // getters and setters
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getTimeOfDay() { return timeOfDay; }
        public void setTimeOfDay(String timeOfDay) { this.timeOfDay = timeOfDay; }
        
        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        
        public String getWeather() { return weather; }
        public void setWeather(String weather) { this.weather = weather; }
        
        public String getNetworkType() { return networkType; }
        public void setNetworkType(String networkType) { this.networkType = networkType; }
        
        public Map<String, Object> getExtraContext() { return extraContext; }
        public void setExtraContext(Map<String, Object> extraContext) { this.extraContext = extraContext; }
    }
}