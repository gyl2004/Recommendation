package com.recommendation.service;

import com.recommendation.service.service.MonitoringService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 监控功能集成测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class MonitoringIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void testHealthEndpoint() {
        String url = "http://localhost:" + port + "/api/v1/monitoring/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    public void testMetricsSummaryEndpoint() {
        // 先记录一些测试指标
        monitoringService.recordRecommendationRequest();
        monitoringService.recordRecommendationDuration(100, TimeUnit.MILLISECONDS);
        monitoringService.recordCacheHit("test");
        
        String url = "http://localhost:" + port + "/api/v1/monitoring/metrics/summary";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("recommendation"));
        assertTrue(response.getBody().containsKey("cache"));
        assertTrue(response.getBody().containsKey("system"));
    }

    @Test
    public void testCustomMetricRecording() {
        String metricName = "test.custom.metric";
        double value = 42.0;
        
        monitoringService.recordCustomMetric(metricName, value);
        
        // 验证指标是否被记录
        assertNotNull(meterRegistry.find(metricName).gauge());
        assertEquals(value, meterRegistry.find(metricName).gauge().value(), 0.01);
    }

    @Test
    public void testRecommendationMetrics() {
        // 记录推荐请求指标
        monitoringService.recordRecommendationRequest();
        monitoringService.recordRecommendationDuration(200, TimeUnit.MILLISECONDS);
        monitoringService.recordRecommendationError("test_error");
        
        // 验证计数器
        assertTrue(meterRegistry.find("recommendation.request.total").counter().count() > 0);
        assertTrue(meterRegistry.find("recommendation.request.errors").counter().count() > 0);
        
        // 验证计时器
        assertNotNull(meterRegistry.find("recommendation.request.duration").timer());
        assertTrue(meterRegistry.find("recommendation.request.duration").timer().count() > 0);
    }

    @Test
    public void testCacheMetrics() {
        // 记录缓存指标
        monitoringService.recordCacheHit("recommendation");
        monitoringService.recordCacheMiss("recommendation");
        
        // 验证缓存指标
        assertTrue(meterRegistry.find("recommendation.cache.hits").counter().count() > 0);
        assertTrue(meterRegistry.find("recommendation.cache.misses").counter().count() > 0);
    }

    @Test
    public void testSystemResourceMetrics() {
        // 记录系统资源指标
        monitoringService.recordSystemResourceUsage();
        
        // 验证系统指标
        assertNotNull(meterRegistry.find("recommendation.system.cpu.usage").gauge());
        assertNotNull(meterRegistry.find("recommendation.system.memory.usage").gauge());
        assertNotNull(meterRegistry.find("recommendation.system.heap.usage").gauge());
    }

    @Test
    public void testDatabaseMetrics() {
        // 记录数据库指标
        monitoringService.recordDatabaseMetrics("select", 50, true);
        monitoringService.recordDatabaseMetrics("insert", 100, false);
        
        // 验证数据库指标
        assertNotNull(meterRegistry.find("recommendation.database.operation.duration").timer());
        assertNotNull(meterRegistry.find("recommendation.database.operation.total").counter());
    }

    @Test
    public void testRedisMetrics() {
        // 记录Redis指标
        monitoringService.recordRedisMetrics("get", 10, true);
        monitoringService.recordRedisMetrics("set", 15, true);
        
        // 验证Redis指标
        assertNotNull(meterRegistry.find("recommendation.redis.operation.duration").timer());
        assertNotNull(meterRegistry.find("recommendation.redis.operation.total").counter());
    }

    @Test
    public void testAlgorithmMetrics() {
        // 记录算法指标
        monitoringService.recordAlgorithmMetrics("collaborative_filtering", 1000, 50, 150);
        
        // 验证算法指标
        assertNotNull(meterRegistry.find("recommendation.algorithm.duration").timer());
        assertNotNull(meterRegistry.find("recommendation.algorithm.candidate.count").gauge());
        assertNotNull(meterRegistry.find("recommendation.algorithm.result.count").gauge());
    }

    @Test
    public void testPrometheusEndpoint() {
        String url = "http://localhost:" + port + "/actuator/prometheus";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("# HELP"));
        assertTrue(response.getBody().contains("# TYPE"));
    }

    @Test
    public void testActuatorHealthEndpoint() {
        String url = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }
}