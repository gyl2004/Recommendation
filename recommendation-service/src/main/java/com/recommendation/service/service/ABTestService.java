package com.recommendation.service.service;

import java.util.Map;

/**
 * A/B测试服务接口
 */
public interface ABTestService {
    
    /**
     * 获取用户的实验分组
     *
     * @param userId 用户ID
     * @param experimentName 实验名称
     * @return 实验分组（control/treatment）
     */
    String getExperimentGroup(String userId, String experimentName);
    
    /**
     * 记录实验指标
     *
     * @param userId 用户ID
     * @param experimentName 实验名称
     * @param group 实验分组
     * @param metric 指标名称
     * @param value 指标值
     */
    void recordExperimentMetric(String userId, String experimentName, String group, String metric, Double value);
    
    /**
     * 获取实验配置
     *
     * @param experimentName 实验名称
     * @return 实验配置
     */
    ExperimentConfig getExperimentConfig(String experimentName);
    
    /**
     * 检查实验是否启用
     *
     * @param experimentName 实验名称
     * @return 是否启用
     */
    boolean isExperimentEnabled(String experimentName);
    
    /**
     * 获取实验统计数据
     *
     * @param experimentName 实验名称
     * @return 统计数据
     */
    ExperimentStats getExperimentStats(String experimentName);
    
    /**
     * 实验配置
     */
    class ExperimentConfig {
        private String name;
        private boolean enabled;
        private double trafficRatio; // 流量分配比例
        private Map<String, Object> parameters;
        private long startTime;
        private long endTime;
        
        // getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public double getTrafficRatio() { return trafficRatio; }
        public void setTrafficRatio(double trafficRatio) { this.trafficRatio = trafficRatio; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
    }
    
    /**
     * 实验统计数据
     */
    class ExperimentStats {
        private String experimentName;
        private long controlGroupSize;
        private long treatmentGroupSize;
        private Map<String, Double> controlMetrics;
        private Map<String, Double> treatmentMetrics;
        private double significance; // 显著性水平
        
        // getters and setters
        public String getExperimentName() { return experimentName; }
        public void setExperimentName(String experimentName) { this.experimentName = experimentName; }
        
        public long getControlGroupSize() { return controlGroupSize; }
        public void setControlGroupSize(long controlGroupSize) { this.controlGroupSize = controlGroupSize; }
        
        public long getTreatmentGroupSize() { return treatmentGroupSize; }
        public void setTreatmentGroupSize(long treatmentGroupSize) { this.treatmentGroupSize = treatmentGroupSize; }
        
        public Map<String, Double> getControlMetrics() { return controlMetrics; }
        public void setControlMetrics(Map<String, Double> controlMetrics) { this.controlMetrics = controlMetrics; }
        
        public Map<String, Double> getTreatmentMetrics() { return treatmentMetrics; }
        public void setTreatmentMetrics(Map<String, Double> treatmentMetrics) { this.treatmentMetrics = treatmentMetrics; }
        
        public double getSignificance() { return significance; }
        public void setSignificance(double significance) { this.significance = significance; }
    }
}