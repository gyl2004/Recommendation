package com.recommendation.service.service.impl;

import com.recommendation.service.service.ABTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A/B测试服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestServiceImpl implements ABTestService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis键前缀
    private static final String EXPERIMENT_CONFIG_PREFIX = "abtest:config:";
    private static final String EXPERIMENT_METRIC_PREFIX = "abtest:metric:";
    private static final String USER_GROUP_PREFIX = "abtest:user:";
    
    // 默认实验配置
    private final Map<String, ExperimentConfig> defaultExperiments = initDefaultExperiments();
    
    @Override
    public String getExperimentGroup(String userId, String experimentName) {
        try {
            // 检查实验是否启用
            if (!isExperimentEnabled(experimentName)) {
                return "control";
            }
            
            // 检查用户是否已经分组
            String cacheKey = USER_GROUP_PREFIX + experimentName + ":" + userId;
            String cachedGroup = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedGroup != null) {
                return cachedGroup;
            }
            
            // 基于用户ID和实验名称进行哈希分组
            String group = assignUserToGroup(userId, experimentName);
            
            // 缓存分组结果（实验期间保持不变）
            redisTemplate.opsForValue().set(cacheKey, group, 30, TimeUnit.DAYS);
            
            log.debug("用户A/B测试分组 - userId: {}, experiment: {}, group: {}", 
                    userId, experimentName, group);
            
            return group;
            
        } catch (Exception e) {
            log.error("获取A/B测试分组失败 - userId: {}, experiment: {}, error: {}", 
                    userId, experimentName, e.getMessage(), e);
            return "control"; // 默认返回对照组
        }
    }
    
    @Override
    public void recordExperimentMetric(String userId, String experimentName, String group, String metric, Double value) {
        try {
            String metricKey = EXPERIMENT_METRIC_PREFIX + experimentName + ":" + group + ":" + metric;
            
            // 记录指标值到Redis（使用列表存储）
            redisTemplate.opsForList().rightPush(metricKey, value);
            
            // 设置过期时间（30天）
            redisTemplate.expire(metricKey, 30, TimeUnit.DAYS);
            
            log.debug("记录A/B测试指标 - userId: {}, experiment: {}, group: {}, metric: {}, value: {}", 
                    userId, experimentName, group, metric, value);
            
        } catch (Exception e) {
            log.error("记录A/B测试指标失败 - userId: {}, experiment: {}, group: {}, metric: {}, error: {}", 
                    userId, experimentName, group, metric, e.getMessage(), e);
        }
    }
    
    @Override
    public ExperimentConfig getExperimentConfig(String experimentName) {
        try {
            String configKey = EXPERIMENT_CONFIG_PREFIX + experimentName;
            ExperimentConfig config = (ExperimentConfig) redisTemplate.opsForValue().get(configKey);
            
            if (config != null) {
                return config;
            }
            
            // 返回默认配置
            return defaultExperiments.get(experimentName);
            
        } catch (Exception e) {
            log.error("获取实验配置失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            return defaultExperiments.get(experimentName);
        }
    }
    
    @Override
    public boolean isExperimentEnabled(String experimentName) {
        try {
            ExperimentConfig config = getExperimentConfig(experimentName);
            
            if (config == null) {
                return false;
            }
            
            // 检查实验是否启用
            if (!config.isEnabled()) {
                return false;
            }
            
            // 检查实验时间范围
            long currentTime = System.currentTimeMillis();
            if (config.getStartTime() > 0 && currentTime < config.getStartTime()) {
                return false;
            }
            
            if (config.getEndTime() > 0 && currentTime > config.getEndTime()) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("检查实验状态失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public ExperimentStats getExperimentStats(String experimentName) {
        try {
            ExperimentStats stats = new ExperimentStats();
            stats.setExperimentName(experimentName);
            
            // 获取对照组和实验组的指标数据
            Map<String, Double> controlMetrics = getGroupMetrics(experimentName, "control");
            Map<String, Double> treatmentMetrics = getGroupMetrics(experimentName, "treatment");
            
            stats.setControlMetrics(controlMetrics);
            stats.setTreatmentMetrics(treatmentMetrics);
            
            // 计算组大小（简化实现）
            stats.setControlGroupSize(getGroupSize(experimentName, "control"));
            stats.setTreatmentGroupSize(getGroupSize(experimentName, "treatment"));
            
            // 计算显著性（简化实现）
            stats.setSignificance(calculateSignificance(controlMetrics, treatmentMetrics));
            
            return stats;
            
        } catch (Exception e) {
            log.error("获取实验统计数据失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            return new ExperimentStats();
        }
    }
    
    /**
     * 将用户分配到实验组
     */
    private String assignUserToGroup(String userId, String experimentName) {
        // 使用用户ID和实验名称的哈希值进行分组
        int hash = (userId + experimentName).hashCode();
        
        // 获取实验配置
        ExperimentConfig config = getExperimentConfig(experimentName);
        double trafficRatio = config != null ? config.getTrafficRatio() : 0.5;
        
        // 基于哈希值和流量比例分组
        double ratio = Math.abs(hash % 100) / 100.0;
        
        return ratio < trafficRatio ? "treatment" : "control";
    }
    
    /**
     * 获取分组指标数据
     */
    private Map<String, Double> getGroupMetrics(String experimentName, String group) {
        Map<String, Double> metrics = new HashMap<>();
        
        try {
            // 获取常见指标
            String[] metricNames = {"click_rate", "conversion_rate", "engagement_time", "recommendation_count"};
            
            for (String metricName : metricNames) {
                String metricKey = EXPERIMENT_METRIC_PREFIX + experimentName + ":" + group + ":" + metricName;
                
                // 获取指标值列表
                var values = redisTemplate.opsForList().range(metricKey, 0, -1);
                
                if (values != null && !values.isEmpty()) {
                    // 计算平均值
                    double average = values.stream()
                            .mapToDouble(v -> ((Number) v).doubleValue())
                            .average()
                            .orElse(0.0);
                    
                    metrics.put(metricName, average);
                }
            }
            
        } catch (Exception e) {
            log.error("获取分组指标数据失败 - experiment: {}, group: {}, error: {}", 
                    experimentName, group, e.getMessage(), e);
        }
        
        return metrics;
    }
    
    /**
     * 获取分组大小
     */
    private long getGroupSize(String experimentName, String group) {
        try {
            String metricKey = EXPERIMENT_METRIC_PREFIX + experimentName + ":" + group + ":recommendation_count";
            Long size = redisTemplate.opsForList().size(metricKey);
            return size != null ? size : 0L;
            
        } catch (Exception e) {
            log.error("获取分组大小失败 - experiment: {}, group: {}, error: {}", 
                    experimentName, group, e.getMessage(), e);
            return 0L;
        }
    }
    
    /**
     * 计算显著性（简化实现）
     */
    private double calculateSignificance(Map<String, Double> controlMetrics, Map<String, Double> treatmentMetrics) {
        // 这里是简化的显著性计算，实际应该使用统计学方法
        if (controlMetrics.isEmpty() || treatmentMetrics.isEmpty()) {
            return 0.0;
        }
        
        // 计算点击率差异的显著性
        Double controlCtr = controlMetrics.get("click_rate");
        Double treatmentCtr = treatmentMetrics.get("click_rate");
        
        if (controlCtr != null && treatmentCtr != null) {
            double difference = Math.abs(treatmentCtr - controlCtr);
            double baseline = Math.max(controlCtr, 0.01); // 避免除零
            
            return difference / baseline; // 简化的显著性指标
        }
        
        return 0.0;
    }
    
    /**
     * 初始化默认实验配置
     */
    private Map<String, ExperimentConfig> initDefaultExperiments() {
        Map<String, ExperimentConfig> experiments = new HashMap<>();
        
        // 多样性实验
        ExperimentConfig diversityExp = new ExperimentConfig();
        diversityExp.setName("diversity_experiment");
        diversityExp.setEnabled(true);
        diversityExp.setTrafficRatio(0.5);
        diversityExp.setParameters(Map.of("diversity_threshold", 0.3));
        experiments.put("diversity_experiment", diversityExp);
        
        // 新鲜度实验
        ExperimentConfig freshnessExp = new ExperimentConfig();
        freshnessExp.setName("freshness_experiment");
        freshnessExp.setEnabled(true);
        freshnessExp.setTrafficRatio(0.5);
        freshnessExp.setParameters(Map.of("freshness_weight", 1.2));
        experiments.put("freshness_experiment", freshnessExp);
        
        // 个性化实验
        ExperimentConfig personalizationExp = new ExperimentConfig();
        personalizationExp.setName("personalization_experiment");
        personalizationExp.setEnabled(true);
        personalizationExp.setTrafficRatio(0.5);
        personalizationExp.setParameters(Map.of("personalization_weight", 1.15));
        experiments.put("personalization_experiment", personalizationExp);
        
        return experiments;
    }
}