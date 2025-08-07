package com.recommendation.service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 推荐效果监控服务
 * 负责收集和计算推荐系统的业务效果指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationEffectMonitoringService {

    private final MeterRegistry meterRegistry;
    
    // 实时指标计算器
    private final Map<String, AtomicLong> impressionCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> clickCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> conversionCounters = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> revenueCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> userEngagementCounters = new ConcurrentHashMap<>();
    
    // 时间窗口统计
    private final Map<String, Map<String, AtomicLong>> hourlyStats = new ConcurrentHashMap<>();
    
    /**
     * 记录推荐内容曝光
     */
    public void recordImpression(String userId, String contentId, String contentType, 
                                String algorithm, int position) {
        // 记录曝光总数
        Counter.builder("recommendation.impression.total")
                .tag("content_type", contentType)
                .tag("algorithm", algorithm)
                .tag("position_range", getPositionRange(position))
                .register(meterRegistry)
                .increment();
        
        // 更新实时计数器
        String key = contentType + "_" + algorithm;
        impressionCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        // 记录位置分布
        meterRegistry.gauge("recommendation.impression.position", position, 
                           "content_type", contentType, "algorithm", algorithm);
        
        log.debug("记录推荐曝光 - User: {}, Content: {}, Type: {}, Algorithm: {}, Position: {}", 
                 userId, contentId, contentType, algorithm, position);
    }

    /**
     * 记录推荐内容点击
     */
    public void recordClick(String userId, String contentId, String contentType, 
                           String algorithm, int position, long dwellTime) {
        // 记录点击总数
        Counter.builder("recommendation.click.total")
                .tag("content_type", contentType)
                .tag("algorithm", algorithm)
                .tag("position_range", getPositionRange(position))
                .register(meterRegistry)
                .increment();
        
        // 记录停留时间
        Timer.builder("recommendation.dwell.time")
                .tag("content_type", contentType)
                .tag("algorithm", algorithm)
                .register(meterRegistry)
                .record(dwellTime, TimeUnit.MILLISECONDS);
        
        // 更新实时计数器
        String key = contentType + "_" + algorithm;
        clickCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        
        // 计算实时CTR
        updateCTR(contentType, algorithm);
        
        log.debug("记录推荐点击 - User: {}, Content: {}, Type: {}, Algorithm: {}, Position: {}, DwellTime: {}ms", 
                 userId, contentId, contentType, algorithm, position, dwellTime);
    }

    /**
     * 记录推荐内容转化
     */
    public void recordConversion(String userId, String contentId, String contentType, 
                               String algorithm, String conversionType, double value) {
        // 记录转化总数
        Counter.builder("recommendation.conversion.total")
                .tag("content_type", contentType)
                .tag("algorithm", algorithm)
                .tag("conversion_type", conversionType)
                .register(meterRegistry)
                .increment();
        
        // 记录转化价值
        meterRegistry.gauge("recommendation.conversion.value", value,
                           "content_type", contentType, "algorithm", algorithm, 
                           "conversion_type", conversionType);
        
        // 更新实时计数器
        String key = contentType + "_" + algorithm;
        conversionCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        revenueCounters.computeIfAbsent(key, k -> new DoubleAdder()).add(value);
        
        // 计算实时CVR
        updateCVR(contentType, algorithm);
        
        log.debug("记录推荐转化 - User: {}, Content: {}, Type: {}, Algorithm: {}, ConversionType: {}, Value: {}", 
                 userId, contentId, contentType, algorithm, conversionType, value);
    }

    /**
     * 记录用户参与度指标
     */
    public void recordUserEngagement(String userId, String contentType, String algorithm,
                                   long sessionDuration, int pageViews, int interactions) {
        // 记录会话时长
        Timer.builder("recommendation.session.duration")
                .tag("content_type", contentType)
                .tag("algorithm", algorithm)
                .register(meterRegistry)
                .record(sessionDuration, TimeUnit.MILLISECONDS);
        
        // 记录页面浏览数
        meterRegistry.gauge("recommendation.session.page_views", pageViews,
                           "content_type", contentType, "algorithm", algorithm);
        
        // 记录交互次数
        meterRegistry.gauge("recommendation.session.interactions", interactions,
                           "content_type", contentType, "algorithm", algorithm);
        
        // 更新用户参与度计数器
        String key = contentType + "_" + algorithm;
        userEngagementCounters.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(interactions);
        
        log.debug("记录用户参与度 - User: {}, Type: {}, Algorithm: {}, Duration: {}ms, PageViews: {}, Interactions: {}", 
                 userId, contentType, algorithm, sessionDuration, pageViews, interactions);
    }

    /**
     * 记录推荐多样性指标
     */
    public void recordDiversityMetrics(String userId, String algorithm, 
                                     double categoryDiversity, double contentDiversity) {
        // 记录分类多样性
        meterRegistry.gauge("recommendation.diversity.category", categoryDiversity,
                           "algorithm", algorithm);
        
        // 记录内容多样性
        meterRegistry.gauge("recommendation.diversity.content", contentDiversity,
                           "algorithm", algorithm);
        
        log.debug("记录推荐多样性 - User: {}, Algorithm: {}, CategoryDiversity: {}, ContentDiversity: {}", 
                 userId, algorithm, categoryDiversity, contentDiversity);
    }

    /**
     * 记录推荐新颖性指标
     */
    public void recordNoveltyMetrics(String userId, String algorithm, 
                                   double noveltyScore, double serendipityScore) {
        // 记录新颖性得分
        meterRegistry.gauge("recommendation.novelty.score", noveltyScore,
                           "algorithm", algorithm);
        
        // 记录意外发现得分
        meterRegistry.gauge("recommendation.serendipity.score", serendipityScore,
                           "algorithm", algorithm);
        
        log.debug("记录推荐新颖性 - User: {}, Algorithm: {}, Novelty: {}, Serendipity: {}", 
                 userId, algorithm, noveltyScore, serendipityScore);
    }

    /**
     * 记录推荐覆盖率指标
     */
    public void recordCoverageMetrics(String algorithm, int totalItems, int recommendedItems,
                                    int activeUsers, int coveredUsers) {
        // 记录物品覆盖率
        double itemCoverage = (double) recommendedItems / totalItems * 100;
        meterRegistry.gauge("recommendation.coverage.item", itemCoverage,
                           "algorithm", algorithm);
        
        // 记录用户覆盖率
        double userCoverage = (double) coveredUsers / activeUsers * 100;
        meterRegistry.gauge("recommendation.coverage.user", userCoverage,
                           "algorithm", algorithm);
        
        log.debug("记录推荐覆盖率 - Algorithm: {}, ItemCoverage: {}%, UserCoverage: {}%", 
                 algorithm, itemCoverage, userCoverage);
    }

    /**
     * 记录推荐公平性指标
     */
    public void recordFairnessMetrics(String algorithm, Map<String, Double> groupCTRs,
                                    Map<String, Double> groupExposures) {
        // 记录不同群体的CTR
        groupCTRs.forEach((group, ctr) -> {
            meterRegistry.gauge("recommendation.fairness.ctr", ctr,
                               "algorithm", algorithm, "group", group);
        });
        
        // 记录不同群体的曝光率
        groupExposures.forEach((group, exposure) -> {
            meterRegistry.gauge("recommendation.fairness.exposure", exposure,
                               "algorithm", algorithm, "group", group);
        });
        
        log.debug("记录推荐公平性 - Algorithm: {}, Groups: {}", algorithm, groupCTRs.keySet());
    }

    /**
     * 获取实时CTR
     */
    public double getRealTimeCTR(String contentType, String algorithm) {
        String key = contentType + "_" + algorithm;
        long impressions = impressionCounters.getOrDefault(key, new AtomicLong(0)).get();
        long clicks = clickCounters.getOrDefault(key, new AtomicLong(0)).get();
        
        return impressions > 0 ? (double) clicks / impressions * 100 : 0.0;
    }

    /**
     * 获取实时CVR
     */
    public double getRealTimeCVR(String contentType, String algorithm) {
        String key = contentType + "_" + algorithm;
        long clicks = clickCounters.getOrDefault(key, new AtomicLong(0)).get();
        long conversions = conversionCounters.getOrDefault(key, new AtomicLong(0)).get();
        
        return clicks > 0 ? (double) conversions / clicks * 100 : 0.0;
    }

    /**
     * 获取实时收入
     */
    public double getRealTimeRevenue(String contentType, String algorithm) {
        String key = contentType + "_" + algorithm;
        return revenueCounters.getOrDefault(key, new DoubleAdder()).sum();
    }

    /**
     * 更新CTR指标
     */
    private void updateCTR(String contentType, String algorithm) {
        double ctr = getRealTimeCTR(contentType, algorithm);
        meterRegistry.gauge("recommendation.ctr.realtime", ctr,
                           "content_type", contentType, "algorithm", algorithm);
    }

    /**
     * 更新CVR指标
     */
    private void updateCVR(String contentType, String algorithm) {
        double cvr = getRealTimeCVR(contentType, algorithm);
        meterRegistry.gauge("recommendation.cvr.realtime", cvr,
                           "content_type", contentType, "algorithm", algorithm);
    }

    /**
     * 获取位置范围标签
     */
    private String getPositionRange(int position) {
        if (position <= 3) return "top3";
        if (position <= 10) return "top10";
        if (position <= 20) return "top20";
        return "others";
    }

    /**
     * 记录小时级统计数据
     */
    public void recordHourlyStats(String metricType, String key, long value) {
        String hour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        hourlyStats.computeIfAbsent(hour, k -> new ConcurrentHashMap<>())
                  .computeIfAbsent(metricType + "_" + key, k -> new AtomicLong(0))
                  .addAndGet(value);
    }

    /**
     * 获取小时级统计数据
     */
    public Map<String, AtomicLong> getHourlyStats(String hour) {
        return hourlyStats.getOrDefault(hour, new ConcurrentHashMap<>());
    }

    /**
     * 清理过期的统计数据
     */
    public void cleanupExpiredStats() {
        String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String expiredHour = LocalDateTime.now().minusHours(24)
                                              .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        
        hourlyStats.entrySet().removeIf(entry -> entry.getKey().compareTo(expiredHour) < 0);
        
        log.info("清理过期统计数据完成，当前小时: {}", currentHour);
    }

    /**
     * 检测推荐效果异常
     */
    public void detectAnomalies(String contentType, String algorithm) {
        double currentCTR = getRealTimeCTR(contentType, algorithm);
        double currentCVR = getRealTimeCVR(contentType, algorithm);
        
        // CTR异常检测
        if (currentCTR < 1.0) { // CTR低于1%
            Counter.builder("recommendation.anomaly.low_ctr")
                    .tag("content_type", contentType)
                    .tag("algorithm", algorithm)
                    .register(meterRegistry)
                    .increment();
            
            log.warn("检测到CTR异常 - ContentType: {}, Algorithm: {}, CTR: {}%", 
                    contentType, algorithm, currentCTR);
        }
        
        // CVR异常检测
        if (currentCVR < 0.5) { // CVR低于0.5%
            Counter.builder("recommendation.anomaly.low_cvr")
                    .tag("content_type", contentType)
                    .tag("algorithm", algorithm)
                    .register(meterRegistry)
                    .increment();
            
            log.warn("检测到CVR异常 - ContentType: {}, Algorithm: {}, CVR: {}%", 
                    contentType, algorithm, currentCVR);
        }
    }
}