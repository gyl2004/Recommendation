package com.recommendation.service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * A/B测试管理服务
 * 负责A/B测试的用户分组、实验管理和效果评估
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ABTestManagementService {

    private final MeterRegistry meterRegistry;
    
    // 实验配置存储
    private final Map<String, ExperimentConfig> experiments = new ConcurrentHashMap<>();
    
    // 用户分组缓存
    private final Map<String, Map<String, String>> userGroups = new ConcurrentHashMap<>();
    
    // 实验指标统计
    private final Map<String, Map<String, ExperimentMetrics>> experimentMetrics = new ConcurrentHashMap<>();

    /**
     * 实验配置类
     */
    public static class ExperimentConfig {
        private String experimentId;
        private String experimentName;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Map<String, Integer> groupTrafficRatio; // 分组流量比例
        private List<String> targetMetrics; // 目标指标
        private String status; // DRAFT, RUNNING, PAUSED, COMPLETED
        private Map<String, Object> parameters; // 实验参数
        
        // Constructors, getters and setters
        public ExperimentConfig() {}
        
        public ExperimentConfig(String experimentId, String experimentName, String description,
                              LocalDateTime startTime, LocalDateTime endTime,
                              Map<String, Integer> groupTrafficRatio, List<String> targetMetrics) {
            this.experimentId = experimentId;
            this.experimentName = experimentName;
            this.description = description;
            this.startTime = startTime;
            this.endTime = endTime;
            this.groupTrafficRatio = groupTrafficRatio;
            this.targetMetrics = targetMetrics;
            this.status = "DRAFT";
            this.parameters = new HashMap<>();
        }
        
        // Getters and setters
        public String getExperimentId() { return experimentId; }
        public void setExperimentId(String experimentId) { this.experimentId = experimentId; }
        
        public String getExperimentName() { return experimentName; }
        public void setExperimentName(String experimentName) { this.experimentName = experimentName; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public Map<String, Integer> getGroupTrafficRatio() { return groupTrafficRatio; }
        public void setGroupTrafficRatio(Map<String, Integer> groupTrafficRatio) { this.groupTrafficRatio = groupTrafficRatio; }
        
        public List<String> getTargetMetrics() { return targetMetrics; }
        public void setTargetMetrics(List<String> targetMetrics) { this.targetMetrics = targetMetrics; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }

    /**
     * 实验指标统计类
     */
    public static class ExperimentMetrics {
        private final AtomicLong userCount = new AtomicLong(0);
        private final AtomicLong impressions = new AtomicLong(0);
        private final AtomicLong clicks = new AtomicLong(0);
        private final AtomicLong conversions = new AtomicLong(0);
        private final DoubleAdder revenue = new DoubleAdder();
        private final DoubleAdder dwellTimeSum = new DoubleAdder();
        private final AtomicLong dwellTimeCount = new AtomicLong(0);
        
        public long getUserCount() { return userCount.get(); }
        public void incrementUserCount() { userCount.incrementAndGet(); }
        
        public long getImpressions() { return impressions.get(); }
        public void incrementImpressions() { impressions.incrementAndGet(); }
        
        public long getClicks() { return clicks.get(); }
        public void incrementClicks() { clicks.incrementAndGet(); }
        
        public long getConversions() { return conversions.get(); }
        public void incrementConversions() { conversions.incrementAndGet(); }
        
        public double getRevenue() { return revenue.sum(); }
        public void addRevenue(double value) { revenue.add(value); }
        
        public double getAverageDwellTime() {
            long count = dwellTimeCount.get();
            return count > 0 ? dwellTimeSum.sum() / count : 0.0;
        }
        
        public void addDwellTime(double time) {
            dwellTimeSum.add(time);
            dwellTimeCount.incrementAndGet();
        }
        
        public double getCTR() {
            long imp = impressions.get();
            return imp > 0 ? (double) clicks.get() / imp * 100 : 0.0;
        }
        
        public double getCVR() {
            long cli = clicks.get();
            return cli > 0 ? (double) conversions.get() / cli * 100 : 0.0;
        }
        
        public double getARPU() {
            long users = userCount.get();
            return users > 0 ? revenue.sum() / users : 0.0;
        }
    }

    /**
     * 创建A/B测试实验
     */
    public String createExperiment(String experimentName, String description,
                                 LocalDateTime startTime, LocalDateTime endTime,
                                 Map<String, Integer> groupTrafficRatio,
                                 List<String> targetMetrics) {
        String experimentId = "exp_" + System.currentTimeMillis();
        
        ExperimentConfig config = new ExperimentConfig(experimentId, experimentName, description,
                                                     startTime, endTime, groupTrafficRatio, targetMetrics);
        
        experiments.put(experimentId, config);
        
        // 初始化实验指标统计
        Map<String, ExperimentMetrics> metrics = new HashMap<>();
        for (String group : groupTrafficRatio.keySet()) {
            metrics.put(group, new ExperimentMetrics());
        }
        experimentMetrics.put(experimentId, metrics);
        
        log.info("创建A/B测试实验 - ID: {}, Name: {}, Groups: {}", 
                experimentId, experimentName, groupTrafficRatio.keySet());
        
        return experimentId;
    }

    /**
     * 启动实验
     */
    public void startExperiment(String experimentId) {
        ExperimentConfig config = experiments.get(experimentId);
        if (config != null) {
            config.setStatus("RUNNING");
            
            // 记录实验启动指标
            Counter.builder("abtest.experiment.started")
                    .tag("experiment_id", experimentId)
                    .tag("experiment_name", config.getExperimentName())
                    .register(meterRegistry)
                    .increment();
            
            log.info("启动A/B测试实验 - ID: {}, Name: {}", experimentId, config.getExperimentName());
        }
    }

    /**
     * 停止实验
     */
    public void stopExperiment(String experimentId) {
        ExperimentConfig config = experiments.get(experimentId);
        if (config != null) {
            config.setStatus("COMPLETED");
            
            // 记录实验完成指标
            Counter.builder("abtest.experiment.completed")
                    .tag("experiment_id", experimentId)
                    .tag("experiment_name", config.getExperimentName())
                    .register(meterRegistry)
                    .increment();
            
            log.info("停止A/B测试实验 - ID: {}, Name: {}", experimentId, config.getExperimentName());
        }
    }

    /**
     * 获取用户实验分组
     */
    public String getUserGroup(String userId, String experimentId) {
        ExperimentConfig config = experiments.get(experimentId);
        if (config == null || !config.getStatus().equals("RUNNING")) {
            return "control"; // 默认对照组
        }
        
        // 检查用户是否已经分组
        String cachedGroup = userGroups.computeIfAbsent(experimentId, k -> new ConcurrentHashMap<>())
                                      .get(userId);
        if (cachedGroup != null) {
            return cachedGroup;
        }
        
        // 基于用户ID进行一致性哈希分组
        String group = assignUserToGroup(userId, config.getGroupTrafficRatio());
        userGroups.get(experimentId).put(userId, group);
        
        // 更新用户分组统计
        ExperimentMetrics metrics = experimentMetrics.get(experimentId).get(group);
        if (metrics != null) {
            metrics.incrementUserCount();
        }
        
        // 记录分组指标
        Counter.builder("abtest.user.assigned")
                .tag("experiment_id", experimentId)
                .tag("group", group)
                .register(meterRegistry)
                .increment();
        
        log.debug("用户分组 - User: {}, Experiment: {}, Group: {}", userId, experimentId, group);
        
        return group;
    }

    /**
     * 记录实验指标
     */
    public void recordExperimentMetric(String experimentId, String userId, String metricType, 
                                     String group, double value) {
        ExperimentMetrics metrics = experimentMetrics.computeIfAbsent(experimentId, k -> new HashMap<>())
                                                   .computeIfAbsent(group, k -> new ExperimentMetrics());
        
        switch (metricType.toLowerCase()) {
            case "impression":
                metrics.incrementImpressions();
                break;
            case "click":
                metrics.incrementClicks();
                break;
            case "conversion":
                metrics.incrementConversions();
                break;
            case "revenue":
                metrics.addRevenue(value);
                break;
            case "dwell_time":
                metrics.addDwellTime(value);
                break;
        }
        
        // 记录Prometheus指标
        Counter.builder("abtest.metric.recorded")
                .tag("experiment_id", experimentId)
                .tag("group", group)
                .tag("metric_type", metricType)
                .register(meterRegistry)
                .increment();
        
        // 更新实时指标
        updateRealTimeMetrics(experimentId, group, metrics);
        
        log.debug("记录实验指标 - Experiment: {}, User: {}, Group: {}, Metric: {}, Value: {}", 
                 experimentId, userId, group, metricType, value);
    }

    /**
     * 获取实验结果
     */
    public Map<String, Object> getExperimentResults(String experimentId) {
        ExperimentConfig config = experiments.get(experimentId);
        Map<String, ExperimentMetrics> metrics = experimentMetrics.get(experimentId);
        
        if (config == null || metrics == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> results = new HashMap<>();
        results.put("experimentId", experimentId);
        results.put("experimentName", config.getExperimentName());
        results.put("status", config.getStatus());
        results.put("startTime", config.getStartTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        results.put("endTime", config.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        Map<String, Map<String, Object>> groupResults = new HashMap<>();
        
        for (Map.Entry<String, ExperimentMetrics> entry : metrics.entrySet()) {
            String group = entry.getKey();
            ExperimentMetrics metric = entry.getValue();
            
            Map<String, Object> groupMetrics = new HashMap<>();
            groupMetrics.put("userCount", metric.getUserCount());
            groupMetrics.put("impressions", metric.getImpressions());
            groupMetrics.put("clicks", metric.getClicks());
            groupMetrics.put("conversions", metric.getConversions());
            groupMetrics.put("revenue", metric.getRevenue());
            groupMetrics.put("ctr", metric.getCTR());
            groupMetrics.put("cvr", metric.getCVR());
            groupMetrics.put("arpu", metric.getARPU());
            groupMetrics.put("avgDwellTime", metric.getAverageDwellTime());
            
            groupResults.put(group, groupMetrics);
        }
        
        results.put("groups", groupResults);
        
        // 计算统计显著性
        if (groupResults.size() >= 2) {
            results.put("statisticalSignificance", calculateStatisticalSignificance(groupResults));
        }
        
        return results;
    }

    /**
     * 进行统计显著性检验
     */
    public Map<String, Object> performStatisticalTest(String experimentId, String metric) {
        Map<String, ExperimentMetrics> metrics = experimentMetrics.get(experimentId);
        if (metrics == null || metrics.size() < 2) {
            return Collections.emptyMap();
        }
        
        List<String> groups = new ArrayList<>(metrics.keySet());
        String controlGroup = groups.get(0);
        String treatmentGroup = groups.get(1);
        
        ExperimentMetrics controlMetrics = metrics.get(controlGroup);
        ExperimentMetrics treatmentMetrics = metrics.get(treatmentGroup);
        
        Map<String, Object> testResult = new HashMap<>();
        testResult.put("experimentId", experimentId);
        testResult.put("metric", metric);
        testResult.put("controlGroup", controlGroup);
        testResult.put("treatmentGroup", treatmentGroup);
        
        switch (metric.toLowerCase()) {
            case "ctr":
                testResult.putAll(performCTRTest(controlMetrics, treatmentMetrics));
                break;
            case "cvr":
                testResult.putAll(performCVRTest(controlMetrics, treatmentMetrics));
                break;
            case "arpu":
                testResult.putAll(performARPUTest(controlMetrics, treatmentMetrics));
                break;
            default:
                testResult.put("error", "Unsupported metric: " + metric);
        }
        
        return testResult;
    }

    /**
     * 获取所有活跃实验
     */
    public List<ExperimentConfig> getActiveExperiments() {
        return experiments.values().stream()
                         .filter(config -> "RUNNING".equals(config.getStatus()))
                         .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 获取实验配置
     */
    public ExperimentConfig getExperimentConfig(String experimentId) {
        return experiments.get(experimentId);
    }

    /**
     * 更新实验配置
     */
    public void updateExperimentConfig(String experimentId, ExperimentConfig newConfig) {
        if (experiments.containsKey(experimentId)) {
            experiments.put(experimentId, newConfig);
            log.info("更新实验配置 - ID: {}, Name: {}", experimentId, newConfig.getExperimentName());
        }
    }

    /**
     * 删除实验
     */
    public void deleteExperiment(String experimentId) {
        experiments.remove(experimentId);
        experimentMetrics.remove(experimentId);
        userGroups.remove(experimentId);
        
        log.info("删除实验 - ID: {}", experimentId);
    }

    // 私有辅助方法

    /**
     * 将用户分配到实验组
     */
    private String assignUserToGroup(String userId, Map<String, Integer> groupTrafficRatio) {
        int hash = Math.abs(userId.hashCode());
        int totalRatio = groupTrafficRatio.values().stream().mapToInt(Integer::intValue).sum();
        int bucket = hash % totalRatio;
        
        int currentSum = 0;
        for (Map.Entry<String, Integer> entry : groupTrafficRatio.entrySet()) {
            currentSum += entry.getValue();
            if (bucket < currentSum) {
                return entry.getKey();
            }
        }
        
        // 默认返回第一个组
        return groupTrafficRatio.keySet().iterator().next();
    }

    /**
     * 更新实时指标
     */
    private void updateRealTimeMetrics(String experimentId, String group, ExperimentMetrics metrics) {
        // 更新CTR指标
        meterRegistry.gauge("abtest.ctr.realtime", metrics.getCTR(),
                           "experiment_id", experimentId, "group", group);
        
        // 更新CVR指标
        meterRegistry.gauge("abtest.cvr.realtime", metrics.getCVR(),
                           "experiment_id", experimentId, "group", group);
        
        // 更新ARPU指标
        meterRegistry.gauge("abtest.arpu.realtime", metrics.getARPU(),
                           "experiment_id", experimentId, "group", group);
        
        // 更新用户数指标
        meterRegistry.gauge("abtest.users.count", metrics.getUserCount(),
                           "experiment_id", experimentId, "group", group);
    }

    /**
     * 计算统计显著性
     */
    private Map<String, Object> calculateStatisticalSignificance(Map<String, Map<String, Object>> groupResults) {
        Map<String, Object> significance = new HashMap<>();
        
        // 简化的统计显著性计算
        // 实际应用中应该使用更严格的统计检验方法
        
        List<String> groups = new ArrayList<>(groupResults.keySet());
        if (groups.size() >= 2) {
            String group1 = groups.get(0);
            String group2 = groups.get(1);
            
            Map<String, Object> metrics1 = groupResults.get(group1);
            Map<String, Object> metrics2 = groupResults.get(group2);
            
            double ctr1 = (Double) metrics1.get("ctr");
            double ctr2 = (Double) metrics2.get("ctr");
            
            double improvement = ((ctr2 - ctr1) / ctr1) * 100;
            boolean isSignificant = Math.abs(improvement) > 5.0; // 简化判断：改进超过5%认为显著
            
            significance.put("metric", "ctr");
            significance.put("group1", group1);
            significance.put("group2", group2);
            significance.put("group1Value", ctr1);
            significance.put("group2Value", ctr2);
            significance.put("improvement", improvement);
            significance.put("isSignificant", isSignificant);
            significance.put("confidenceLevel", isSignificant ? 95.0 : 80.0);
        }
        
        return significance;
    }

    /**
     * 执行CTR统计检验
     */
    private Map<String, Object> performCTRTest(ExperimentMetrics control, ExperimentMetrics treatment) {
        Map<String, Object> result = new HashMap<>();
        
        double controlCTR = control.getCTR();
        double treatmentCTR = treatment.getCTR();
        double improvement = ((treatmentCTR - controlCTR) / controlCTR) * 100;
        
        // 简化的Z检验
        long controlImpressions = control.getImpressions();
        long treatmentImpressions = treatment.getImpressions();
        long controlClicks = control.getClicks();
        long treatmentClicks = treatment.getClicks();
        
        if (controlImpressions > 0 && treatmentImpressions > 0) {
            double p1 = (double) controlClicks / controlImpressions;
            double p2 = (double) treatmentClicks / treatmentImpressions;
            double pooledP = (double) (controlClicks + treatmentClicks) / (controlImpressions + treatmentImpressions);
            
            double se = Math.sqrt(pooledP * (1 - pooledP) * (1.0/controlImpressions + 1.0/treatmentImpressions));
            double zScore = (p2 - p1) / se;
            
            result.put("controlCTR", controlCTR);
            result.put("treatmentCTR", treatmentCTR);
            result.put("improvement", improvement);
            result.put("zScore", zScore);
            result.put("isSignificant", Math.abs(zScore) > 1.96); // 95%置信度
            result.put("pValue", 2 * (1 - normalCDF(Math.abs(zScore))));
        }
        
        return result;
    }

    /**
     * 执行CVR统计检验
     */
    private Map<String, Object> performCVRTest(ExperimentMetrics control, ExperimentMetrics treatment) {
        Map<String, Object> result = new HashMap<>();
        
        double controlCVR = control.getCVR();
        double treatmentCVR = treatment.getCVR();
        double improvement = controlCVR > 0 ? ((treatmentCVR - controlCVR) / controlCVR) * 100 : 0;
        
        result.put("controlCVR", controlCVR);
        result.put("treatmentCVR", treatmentCVR);
        result.put("improvement", improvement);
        result.put("isSignificant", Math.abs(improvement) > 10.0); // 简化判断
        
        return result;
    }

    /**
     * 执行ARPU统计检验
     */
    private Map<String, Object> performARPUTest(ExperimentMetrics control, ExperimentMetrics treatment) {
        Map<String, Object> result = new HashMap<>();
        
        double controlARPU = control.getARPU();
        double treatmentARPU = treatment.getARPU();
        double improvement = controlARPU > 0 ? ((treatmentARPU - controlARPU) / controlARPU) * 100 : 0;
        
        result.put("controlARPU", controlARPU);
        result.put("treatmentARPU", treatmentARPU);
        result.put("improvement", improvement);
        result.put("isSignificant", Math.abs(improvement) > 15.0); // 简化判断
        
        return result;
    }

    /**
     * 正态分布累积分布函数（简化实现）
     */
    private double normalCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }

    /**
     * 误差函数（简化实现）
     */
    private double erf(double x) {
        // 简化的误差函数实现
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;

        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return sign * y;
    }
}