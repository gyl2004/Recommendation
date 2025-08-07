package com.recommendation.service;

import com.recommendation.service.service.ABTestManagementService;
import com.recommendation.service.service.OfflineEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A/B测试和效果评估测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ABTestAndEvaluationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ABTestManagementService abTestService;

    @Autowired
    private OfflineEvaluationService offlineEvaluationService;

    @Test
    public void testCreateExperiment() {
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        
        List<String> targetMetrics = Arrays.asList("ctr", "cvr");
        
        String experimentId = abTestService.createExperiment(
            "测试实验",
            "测试A/B实验功能",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7),
            groupRatio,
            targetMetrics
        );
        
        assertNotNull(experimentId);
        assertTrue(experimentId.startsWith("exp_"));
        
        ABTestManagementService.ExperimentConfig config = abTestService.getExperimentConfig(experimentId);
        assertNotNull(config);
        assertEquals("测试实验", config.getExperimentName());
        assertEquals("DRAFT", config.getStatus());
    }

    @Test
    public void testStartAndStopExperiment() {
        // 创建实验
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        
        String experimentId = abTestService.createExperiment(
            "启停测试实验",
            "测试实验启停功能",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7),
            groupRatio,
            Arrays.asList("ctr")
        );
        
        // 启动实验
        abTestService.startExperiment(experimentId);
        ABTestManagementService.ExperimentConfig config = abTestService.getExperimentConfig(experimentId);
        assertEquals("RUNNING", config.getStatus());
        
        // 停止实验
        abTestService.stopExperiment(experimentId);
        config = abTestService.getExperimentConfig(experimentId);
        assertEquals("COMPLETED", config.getStatus());
    }

    @Test
    public void testUserGroupAssignment() {
        // 创建并启动实验
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        
        String experimentId = abTestService.createExperiment(
            "分组测试实验",
            "测试用户分组功能",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7),
            groupRatio,
            Arrays.asList("ctr")
        );
        
        abTestService.startExperiment(experimentId);
        
        // 测试用户分组
        String userId1 = "user1";
        String userId2 = "user2";
        
        String group1 = abTestService.getUserGroup(userId1, experimentId);
        String group2 = abTestService.getUserGroup(userId2, experimentId);
        
        assertNotNull(group1);
        assertNotNull(group2);
        assertTrue(Arrays.asList("control", "treatment").contains(group1));
        assertTrue(Arrays.asList("control", "treatment").contains(group2));
        
        // 同一用户多次获取应该返回相同分组
        String group1Again = abTestService.getUserGroup(userId1, experimentId);
        assertEquals(group1, group1Again);
    }

    @Test
    public void testRecordExperimentMetrics() {
        // 创建并启动实验
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        
        String experimentId = abTestService.createExperiment(
            "指标测试实验",
            "测试指标记录功能",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7),
            groupRatio,
            Arrays.asList("ctr", "cvr")
        );
        
        abTestService.startExperiment(experimentId);
        
        String userId = "user1";
        String group = abTestService.getUserGroup(userId, experimentId);
        
        // 记录各种指标
        abTestService.recordExperimentMetric(experimentId, userId, "impression", group, 1.0);
        abTestService.recordExperimentMetric(experimentId, userId, "click", group, 1.0);
        abTestService.recordExperimentMetric(experimentId, userId, "conversion", group, 1.0);
        abTestService.recordExperimentMetric(experimentId, userId, "revenue", group, 99.99);
        
        // 获取实验结果
        Map<String, Object> results = abTestService.getExperimentResults(experimentId);
        assertNotNull(results);
        assertTrue(results.containsKey("groups"));
        
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> groupResults = (Map<String, Map<String, Object>>) results.get("groups");
        assertTrue(groupResults.containsKey(group));
        
        Map<String, Object> groupMetrics = groupResults.get(group);
        assertEquals(1L, ((Number) groupMetrics.get("impressions")).longValue());
        assertEquals(1L, ((Number) groupMetrics.get("clicks")).longValue());
        assertEquals(1L, ((Number) groupMetrics.get("conversions")).longValue());
        assertEquals(99.99, ((Number) groupMetrics.get("revenue")).doubleValue(), 0.01);
    }

    @Test
    public void testStatisticalSignificanceTest() {
        // 创建并启动实验
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        
        String experimentId = abTestService.createExperiment(
            "统计检验实验",
            "测试统计显著性检验",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(7),
            groupRatio,
            Arrays.asList("ctr")
        );
        
        abTestService.startExperiment(experimentId);
        
        // 为对照组记录数据
        for (int i = 0; i < 100; i++) {
            String userId = "control_user_" + i;
            abTestService.recordExperimentMetric(experimentId, userId, "impression", "control", 1.0);
            if (i < 20) { // 20%点击率
                abTestService.recordExperimentMetric(experimentId, userId, "click", "control", 1.0);
            }
        }
        
        // 为实验组记录数据
        for (int i = 0; i < 100; i++) {
            String userId = "treatment_user_" + i;
            abTestService.recordExperimentMetric(experimentId, userId, "impression", "treatment", 1.0);
            if (i < 25) { // 25%点击率
                abTestService.recordExperimentMetric(experimentId, userId, "click", "treatment", 1.0);
            }
        }
        
        // 执行统计检验
        Map<String, Object> testResult = abTestService.performStatisticalTest(experimentId, "ctr");
        assertNotNull(testResult);
        assertTrue(testResult.containsKey("controlCTR"));
        assertTrue(testResult.containsKey("treatmentCTR"));
        assertTrue(testResult.containsKey("improvement"));
    }

    @Test
    public void testOfflineEvaluationPrecision() {
        List<String> recommendedItems = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        Set<String> relevantItems = new HashSet<>(Arrays.asList("item1", "item3", "item5"));
        
        double precision = offlineEvaluationService.calculatePrecision(recommendedItems, relevantItems, 5);
        assertEquals(0.6, precision, 0.01); // 3/5 = 0.6
        
        double precisionAt3 = offlineEvaluationService.calculatePrecision(recommendedItems, relevantItems, 3);
        assertEquals(0.667, precisionAt3, 0.01); // 2/3 ≈ 0.667
    }

    @Test
    public void testOfflineEvaluationRecall() {
        List<String> recommendedItems = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        Set<String> relevantItems = new HashSet<>(Arrays.asList("item1", "item3", "item5", "item6", "item7"));
        
        double recall = offlineEvaluationService.calculateRecall(recommendedItems, relevantItems, 5);
        assertEquals(0.6, recall, 0.01); // 3/5 = 0.6
    }

    @Test
    public void testOfflineEvaluationNDCG() {
        List<String> recommendedItems = Arrays.asList("item1", "item2", "item3", "item4", "item5");
        Map<String, Double> relevanceScores = new HashMap<>();
        relevanceScores.put("item1", 3.0);
        relevanceScores.put("item2", 1.0);
        relevanceScores.put("item3", 2.0);
        relevanceScores.put("item4", 0.0);
        relevanceScores.put("item5", 1.0);
        
        double ndcg = offlineEvaluationService.calculateNDCG(recommendedItems, relevanceScores, 5);
        assertTrue(ndcg > 0);
        assertTrue(ndcg <= 1.0);
    }

    @Test
    public void testOfflineEvaluationCoverage() {
        List<OfflineEvaluationService.RecommendationResult> recommendations = Arrays.asList(
            new OfflineEvaluationService.RecommendationResult("user1", 
                Arrays.asList("item1", "item2", "item3"), 
                Arrays.asList(0.9, 0.8, 0.7), "algorithm1"),
            new OfflineEvaluationService.RecommendationResult("user2", 
                Arrays.asList("item2", "item3", "item4"), 
                Arrays.asList(0.9, 0.8, 0.7), "algorithm1")
        );
        
        Set<String> allItems = new HashSet<>(Arrays.asList("item1", "item2", "item3", "item4", "item5"));
        
        double coverage = offlineEvaluationService.calculateCoverage(recommendations, allItems);
        assertEquals(0.8, coverage, 0.01); // 4/5 = 0.8
    }

    @Test
    public void testOfflineEvaluationDiversity() {
        List<String> recommendedItems = Arrays.asList("item1", "item2", "item3", "item4");
        Map<String, String> itemCategories = new HashMap<>();
        itemCategories.put("item1", "电子产品");
        itemCategories.put("item2", "服装");
        itemCategories.put("item3", "电子产品");
        itemCategories.put("item4", "图书");
        
        double diversity = offlineEvaluationService.calculateDiversity(recommendedItems, itemCategories);
        assertEquals(0.75, diversity, 0.01); // 3个不同类别 / 4个物品 = 0.75
    }

    @Test
    public void testOfflineEvaluationNovelty() {
        List<String> recommendedItems = Arrays.asList("item1", "item2", "item3");
        Map<String, Double> itemPopularity = new HashMap<>();
        itemPopularity.put("item1", 0.1); // 低流行度，高新颖性
        itemPopularity.put("item2", 0.5); // 中等流行度
        itemPopularity.put("item3", 0.9); // 高流行度，低新颖性
        
        double novelty = offlineEvaluationService.calculateNovelty(recommendedItems, itemPopularity);
        assertTrue(novelty > 0);
    }

    @Test
    public void testCreateExperimentAPI() {
        String url = "http://localhost:" + port + "/api/v1/abtest/experiments";
        
        Map<String, Object> request = new HashMap<>();
        request.put("experimentName", "API测试实验");
        request.put("description", "通过API创建的测试实验");
        request.put("startTime", LocalDateTime.now().toString());
        request.put("endTime", LocalDateTime.now().plusDays(7).toString());
        
        Map<String, Integer> groupRatio = new HashMap<>();
        groupRatio.put("control", 50);
        groupRatio.put("treatment", 50);
        request.put("groupTrafficRatio", groupRatio);
        
        request.put("targetMetrics", Arrays.asList("ctr", "cvr"));
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("experimentId"));
        assertTrue(response.getBody().containsKey("message"));
    }

    @Test
    public void testOfflineEvaluationAPI() {
        String url = "http://localhost:" + port + "/api/v1/offline-evaluation/full-evaluation?k=10";
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("evaluationResults"));
        assertTrue(response.getBody().containsKey("report"));
        assertTrue(response.getBody().containsKey("testDataSize"));
    }

    @Test
    public void testCreateSampleExperimentAPI() {
        String url = "http://localhost:" + port + "/api/v1/abtest/experiments/create-sample";
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("experimentId"));
        assertTrue(response.getBody().containsKey("message"));
        assertTrue(response.getBody().containsKey("experimentName"));
    }

    @Test
    public void testGenerateTestDataAPI() {
        String url = "http://localhost:" + port + "/api/v1/offline-evaluation/test/generate?userCount=20&itemCount=100&recommendationSize=10";
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("testData"));
        assertTrue(response.getBody().containsKey("algorithmRecommendations"));
        assertTrue(response.getBody().containsKey("allItems"));
        assertTrue(response.getBody().containsKey("message"));
    }
}