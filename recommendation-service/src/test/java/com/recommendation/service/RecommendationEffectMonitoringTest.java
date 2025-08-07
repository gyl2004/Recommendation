package com.recommendation.service;

import com.recommendation.service.service.RecommendationEffectMonitoringService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推荐效果监控测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class RecommendationEffectMonitoringTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RecommendationEffectMonitoringService effectMonitoringService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void testRecordImpression() {
        // 记录曝光
        effectMonitoringService.recordImpression("user1", "content1", "article", "collaborative_filtering", 1);
        
        // 验证指标
        assertNotNull(meterRegistry.find("recommendation.impression.total").counter());
        assertTrue(meterRegistry.find("recommendation.impression.total").counter().count() > 0);
    }

    @Test
    public void testRecordClick() {
        // 先记录曝光
        effectMonitoringService.recordImpression("user1", "content1", "article", "collaborative_filtering", 1);
        
        // 记录点击
        effectMonitoringService.recordClick("user1", "content1", "article", "collaborative_filtering", 1, 5000);
        
        // 验证指标
        assertNotNull(meterRegistry.find("recommendation.click.total").counter());
        assertTrue(meterRegistry.find("recommendation.click.total").counter().count() > 0);
        
        assertNotNull(meterRegistry.find("recommendation.dwell.time").timer());
        assertTrue(meterRegistry.find("recommendation.dwell.time").timer().count() > 0);
    }

    @Test
    public void testRecordConversion() {
        // 记录转化
        effectMonitoringService.recordConversion("user1", "content1", "article", "collaborative_filtering", "purchase", 99.99);
        
        // 验证指标
        assertNotNull(meterRegistry.find("recommendation.conversion.total").counter());
        assertTrue(meterRegistry.find("recommendation.conversion.total").counter().count() > 0);
        
        assertNotNull(meterRegistry.find("recommendation.conversion.value").gauge());
    }

    @Test
    public void testCTRCalculation() {
        String contentType = "article";
        String algorithm = "collaborative_filtering";
        
        // 记录曝光和点击
        for (int i = 0; i < 10; i++) {
            effectMonitoringService.recordImpression("user1", "content" + i, contentType, algorithm, i + 1);
        }
        
        for (int i = 0; i < 3; i++) {
            effectMonitoringService.recordClick("user1", "content" + i, contentType, algorithm, i + 1, 1000);
        }
        
        // 验证CTR计算
        double ctr = effectMonitoringService.getRealTimeCTR(contentType, algorithm);
        assertEquals(30.0, ctr, 0.1); // 3/10 = 30%
    }

    @Test
    public void testCVRCalculation() {
        String contentType = "article";
        String algorithm = "collaborative_filtering";
        
        // 记录点击和转化
        for (int i = 0; i < 10; i++) {
            effectMonitoringService.recordClick("user1", "content" + i, contentType, algorithm, i + 1, 1000);
        }
        
        for (int i = 0; i < 2; i++) {
            effectMonitoringService.recordConversion("user1", "content" + i, contentType, algorithm, "purchase", 50.0);
        }
        
        // 验证CVR计算
        double cvr = effectMonitoringService.getRealTimeCVR(contentType, algorithm);
        assertEquals(20.0, cvr, 0.1); // 2/10 = 20%
    }

    @Test
    public void testRevenueCalculation() {
        String contentType = "article";
        String algorithm = "collaborative_filtering";
        
        // 记录转化收入
        effectMonitoringService.recordConversion("user1", "content1", contentType, algorithm, "purchase", 99.99);
        effectMonitoringService.recordConversion("user1", "content2", contentType, algorithm, "purchase", 149.99);
        
        // 验证收入计算
        double revenue = effectMonitoringService.getRealTimeRevenue(contentType, algorithm);
        assertEquals(249.98, revenue, 0.01);
    }

    @Test
    public void testUserEngagementRecording() {
        effectMonitoringService.recordUserEngagement("user1", "article", "collaborative_filtering", 
                                                   120000, 5, 3);
        
        // 验证用户参与度指标
        assertNotNull(meterRegistry.find("recommendation.session.duration").timer());
        assertNotNull(meterRegistry.find("recommendation.session.page_views").gauge());
        assertNotNull(meterRegistry.find("recommendation.session.interactions").gauge());
    }

    @Test
    public void testDiversityMetrics() {
        effectMonitoringService.recordDiversityMetrics("user1", "collaborative_filtering", 0.8, 0.7);
        
        // 验证多样性指标
        assertNotNull(meterRegistry.find("recommendation.diversity.category").gauge());
        assertNotNull(meterRegistry.find("recommendation.diversity.content").gauge());
    }

    @Test
    public void testNoveltyMetrics() {
        effectMonitoringService.recordNoveltyMetrics("user1", "collaborative_filtering", 0.6, 0.4);
        
        // 验证新颖性指标
        assertNotNull(meterRegistry.find("recommendation.novelty.score").gauge());
        assertNotNull(meterRegistry.find("recommendation.serendipity.score").gauge());
    }

    @Test
    public void testCoverageMetrics() {
        effectMonitoringService.recordCoverageMetrics("collaborative_filtering", 10000, 5000, 50000, 30000);
        
        // 验证覆盖率指标
        assertNotNull(meterRegistry.find("recommendation.coverage.item").gauge());
        assertNotNull(meterRegistry.find("recommendation.coverage.user").gauge());
        
        assertEquals(50.0, meterRegistry.find("recommendation.coverage.item").gauge().value(), 0.1);
        assertEquals(60.0, meterRegistry.find("recommendation.coverage.user").gauge().value(), 0.1);
    }

    @Test
    public void testFairnessMetrics() {
        Map<String, Double> groupCTRs = new HashMap<>();
        groupCTRs.put("young", 5.0);
        groupCTRs.put("senior", 3.0);
        
        Map<String, Double> groupExposures = new HashMap<>();
        groupExposures.put("young", 40.0);
        groupExposures.put("senior", 20.0);
        
        effectMonitoringService.recordFairnessMetrics("collaborative_filtering", groupCTRs, groupExposures);
        
        // 验证公平性指标
        assertNotNull(meterRegistry.find("recommendation.fairness.ctr").gauge());
        assertNotNull(meterRegistry.find("recommendation.fairness.exposure").gauge());
    }

    @Test
    public void testAnomalyDetection() {
        String contentType = "article";
        String algorithm = "collaborative_filtering";
        
        // 创建低CTR场景
        for (int i = 0; i < 100; i++) {
            effectMonitoringService.recordImpression("user1", "content" + i, contentType, algorithm, i + 1);
        }
        // 只有很少点击，造成低CTR
        effectMonitoringService.recordClick("user1", "content1", contentType, algorithm, 1, 1000);
        
        // 执行异常检测
        effectMonitoringService.detectAnomalies(contentType, algorithm);
        
        // 验证异常检测指标
        assertNotNull(meterRegistry.find("recommendation.anomaly.low_ctr").counter());
    }

    @Test
    public void testImpressionAPI() {
        String url = "http://localhost:" + port + "/api/v1/recommendation-effect/impression";
        
        Map<String, Object> request = new HashMap<>();
        request.put("userId", "user1");
        request.put("contentId", "content1");
        request.put("contentType", "article");
        request.put("algorithm", "collaborative_filtering");
        request.put("position", 1);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("曝光记录成功", response.getBody());
    }

    @Test
    public void testClickAPI() {
        String url = "http://localhost:" + port + "/api/v1/recommendation-effect/click";
        
        Map<String, Object> request = new HashMap<>();
        request.put("userId", "user1");
        request.put("contentId", "content1");
        request.put("contentType", "article");
        request.put("algorithm", "collaborative_filtering");
        request.put("position", 1);
        request.put("dwellTime", 5000);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("点击记录成功", response.getBody());
    }

    @Test
    public void testConversionAPI() {
        String url = "http://localhost:" + port + "/api/v1/recommendation-effect/conversion";
        
        Map<String, Object> request = new HashMap<>();
        request.put("userId", "user1");
        request.put("contentId", "content1");
        request.put("contentType", "article");
        request.put("algorithm", "collaborative_filtering");
        request.put("conversionType", "purchase");
        request.put("value", 99.99);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("转化记录成功", response.getBody());
    }

    @Test
    public void testRealTimeMetricsAPI() {
        // 先记录一些数据
        effectMonitoringService.recordImpression("user1", "content1", "article", "collaborative_filtering", 1);
        effectMonitoringService.recordClick("user1", "content1", "article", "collaborative_filtering", 1, 1000);
        effectMonitoringService.recordConversion("user1", "content1", "article", "collaborative_filtering", "purchase", 50.0);
        
        String url = "http://localhost:" + port + "/api/v1/recommendation-effect/metrics/realtime?contentType=article&algorithm=collaborative_filtering";
        
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("ctr"));
        assertTrue(response.getBody().containsKey("cvr"));
        assertTrue(response.getBody().containsKey("revenue"));
    }

    @Test
    public void testGenerateTestDataAPI() {
        String url = "http://localhost:" + port + "/api/v1/recommendation-effect/test/generate?count=50";
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("测试数据生成完成"));
    }
}