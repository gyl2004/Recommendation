package com.recommendation.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 推荐系统端到端集成测试
 * 测试完整的推荐流程：用户注册 -> 内容创建 -> 行为收集 -> 推荐生成
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecommendationE2ETest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 测试数据
    private static String testUserId;
    private static List<String> testContentIds = new ArrayList<>();
    private static final String TEST_USER_EMAIL = "test@example.com";
    private static final String TEST_USER_USERNAME = "testuser";

    @BeforeEach
    void setUp() throws InterruptedException {
        waitForServicesReady();
        cleanupTestData();
    }

    @Test
    @Order(1)
    @DisplayName("完整推荐流程测试 - 新用户冷启动推荐")
    void testCompleteRecommendationFlowForNewUser() throws JsonProcessingException {
        // 1. 创建测试用户
        Map<String, Object> userRequest = Map.of(
                "username", TEST_USER_USERNAME,
                "email", TEST_USER_EMAIL,
                "profileData", Map.of(
                        "age", 25,
                        "gender", "male",
                        "interests", Arrays.asList("technology", "sports")
                )
        );

        String userResponse = given()
                .contentType(ContentType.JSON)
                .body(userRequest)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(201)
                .body("username", equalTo(TEST_USER_USERNAME))
                .body("email", equalTo(TEST_USER_EMAIL))
                .extract()
                .path("id");

        testUserId = userResponse;
        assertNotNull(testUserId, "用户ID不能为空");

        // 2. 创建测试内容
        createTestContents();

        // 3. 为新用户请求推荐（冷启动场景）
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .queryParam("contentType", "mixed")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("data[0].contentId", notNullValue())
                .body("data[0].title", notNullValue())
                .body("data[0].contentType", notNullValue())
                .body("data[0].score", greaterThan(0.0f))
                .body("metadata.userId", equalTo(testUserId))
                .body("metadata.recommendationType", equalTo("cold_start"));

        System.out.println("✅ 新用户冷启动推荐测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("用户行为收集和个性化推荐测试")
    void testUserBehaviorCollectionAndPersonalizedRecommendation() throws InterruptedException {
        // 确保用户已创建
        if (testUserId == null) {
            testCompleteRecommendationFlowForNewUser();
        }

        // 1. 模拟用户行为数据
        simulateUserBehaviors();

        // 2. 等待行为数据处理完成
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    // 检查用户画像是否已更新
                    return redisTemplate.hasKey("user:features:" + testUserId);
                });

        // 3. 请求个性化推荐
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .queryParam("contentType", "mixed")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("metadata.userId", equalTo(testUserId))
                .body("metadata.recommendationType", equalTo("personalized"));

        // 4. 验证推荐结果的多样性
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 检查内容类型多样性
        Set<String> contentTypes = new HashSet<>();
        for (Map<String, Object> rec : recommendations) {
            contentTypes.add((String) rec.get("contentType"));
        }
        assertTrue(contentTypes.size() >= 2, "推荐结果应包含多种内容类型");

        System.out.println("✅ 用户行为收集和个性化推荐测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("推荐系统性能测试")
    void testRecommendationPerformance() {
        // 确保用户已创建
        if (testUserId == null) {
            testCompleteRecommendationFlowForNewUser();
        }

        // 测试推荐响应时间
        long startTime = System.currentTimeMillis();
        
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .time(lessThan(500L)); // 响应时间应小于500ms

        long endTime = System.currentTimeMillis();
        long responseTime = endTime - startTime;
        
        assertTrue(responseTime < 500, 
                String.format("推荐响应时间应小于500ms，实际：%dms", responseTime));

        System.out.println("✅ 推荐系统性能测试通过，响应时间：" + responseTime + "ms");
    }

    @Test
    @Order(4)
    @DisplayName("缓存机制测试")
    void testCachingMechanism() {
        // 确保用户已创建
        if (testUserId == null) {
            testCompleteRecommendationFlowForNewUser();
        }

        // 第一次请求（缓存未命中）
        long firstRequestTime = System.currentTimeMillis();
        List<Map<String, Object>> firstResponse = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");
        long firstRequestDuration = System.currentTimeMillis() - firstRequestTime;

        // 第二次请求（缓存命中）
        long secondRequestTime = System.currentTimeMillis();
        List<Map<String, Object>> secondResponse = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");
        long secondRequestDuration = System.currentTimeMillis() - secondRequestTime;

        // 验证缓存效果
        assertTrue(secondRequestDuration < firstRequestDuration, 
                "缓存命中的请求应该更快");
        assertEquals(firstResponse.size(), secondResponse.size(), 
                "缓存的推荐结果数量应该一致");

        System.out.println("✅ 缓存机制测试通过");
        System.out.println("第一次请求时间：" + firstRequestDuration + "ms");
        System.out.println("第二次请求时间：" + secondRequestDuration + "ms");
    }

    @Test
    @Order(5)
    @DisplayName("异常情况处理测试")
    void testExceptionHandling() {
        // 测试无效用户ID
        given()
                .queryParam("userId", "invalid-user-id")
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(404)
                .body("error", equalTo("User not found"));

        // 测试无效参数
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", -1)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("Invalid parameter"));

        // 测试超大请求
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 1000)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("Size too large"));

        System.out.println("✅ 异常情况处理测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("服务降级测试")
    void testServiceDegradation() {
        // 模拟算法服务不可用的情况
        // 这里可以通过WireMock或者直接停止某个服务来模拟
        
        // 在算法服务不可用时，应该返回默认推荐
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .header("X-Force-Fallback", "true") // 强制触发降级
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("metadata.recommendationType", equalTo("fallback"));

        System.out.println("✅ 服务降级测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("A/B测试框架测试")
    void testABTestingFramework() {
        // 确保用户已创建
        if (testUserId == null) {
            testCompleteRecommendationFlowForNewUser();
        }

        // 请求推荐并检查A/B测试分组
        Map<String, Object> response = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .queryParam("experiment", "recommendation_algorithm_v2")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("metadata.experimentGroup", anyOf(equalTo("control"), equalTo("treatment")))
                .extract()
                .as(Map.class);

        String experimentGroup = (String) ((Map<String, Object>) response.get("metadata")).get("experimentGroup");
        assertNotNull(experimentGroup, "实验分组不能为空");
        assertTrue(Arrays.asList("control", "treatment").contains(experimentGroup), 
                "实验分组必须是control或treatment");

        System.out.println("✅ A/B测试框架测试通过，用户分组：" + experimentGroup);
    }

    /**
     * 创建测试内容
     */
    private void createTestContents() {
        // 创建文章内容
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> articleRequest = Map.of(
                    "title", "测试文章" + i,
                    "contentType", "article",
                    "contentData", Map.of(
                            "content", "这是测试文章" + i + "的内容",
                            "summary", "文章摘要" + i
                    ),
                    "tags", Arrays.asList("technology", "programming"),
                    "categoryId", 1
            );

            String contentId = given()
                    .contentType(ContentType.JSON)
                    .body(articleRequest)
                    .when()
                    .post("/api/v1/contents")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");

            testContentIds.add(contentId);
        }

        // 创建视频内容
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> videoRequest = Map.of(
                    "title", "测试视频" + i,
                    "contentType", "video",
                    "contentData", Map.of(
                            "duration", 300,
                            "resolution", "1080p",
                            "url", "http://example.com/video" + i + ".mp4"
                    ),
                    "tags", Arrays.asList("entertainment", "sports"),
                    "categoryId", 2
            );

            String contentId = given()
                    .contentType(ContentType.JSON)
                    .body(videoRequest)
                    .when()
                    .post("/api/v1/contents")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");

            testContentIds.add(contentId);
        }

        System.out.println("创建了 " + testContentIds.size() + " 个测试内容");
    }

    /**
     * 模拟用户行为数据
     */
    private void simulateUserBehaviors() {
        // 模拟浏览行为
        for (String contentId : testContentIds.subList(0, 3)) {
            Map<String, Object> behaviorRequest = Map.of(
                    "userId", testUserId,
                    "contentId", contentId,
                    "actionType", "view",
                    "duration", 120,
                    "timestamp", System.currentTimeMillis()
            );

            given()
                    .contentType(ContentType.JSON)
                    .body(behaviorRequest)
                    .when()
                    .post("/api/v1/behaviors")
                    .then()
                    .statusCode(200);
        }

        // 模拟点击行为
        for (String contentId : testContentIds.subList(0, 2)) {
            Map<String, Object> behaviorRequest = Map.of(
                    "userId", testUserId,
                    "contentId", contentId,
                    "actionType", "click",
                    "timestamp", System.currentTimeMillis()
            );

            given()
                    .contentType(ContentType.JSON)
                    .body(behaviorRequest)
                    .when()
                    .post("/api/v1/behaviors")
                    .then()
                    .statusCode(200);
        }

        // 模拟点赞行为
        Map<String, Object> likeRequest = Map.of(
                "userId", testUserId,
                "contentId", testContentIds.get(0),
                "actionType", "like",
                "timestamp", System.currentTimeMillis()
        );

        given()
                .contentType(ContentType.JSON)
                .body(likeRequest)
                .when()
                .post("/api/v1/behaviors")
                .then()
                .statusCode(200);

        System.out.println("模拟了用户行为数据");
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        cleanupTestData();
    }
}