package com.recommendation.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界条件和异常情况测试
 * 测试系统在各种极端情况下的表现
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EdgeCaseTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static String testUserId;
    private static final List<String> testContentIds = new ArrayList<>();

    @BeforeEach
    void setUp() throws InterruptedException {
        waitForServicesReady();
        cleanupTestData();
        createBasicTestData();
    }

    @Test
    @Order(1)
    @DisplayName("空数据库推荐测试")
    void testRecommendationWithEmptyDatabase() {
        // 创建用户但不创建任何内容
        Map<String, Object> userRequest = Map.of(
                "username", "empty_db_user",
                "email", "empty@test.com"
        );

        String userId = given()
                .contentType(ContentType.JSON)
                .body(userRequest)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 请求推荐，应该返回空结果或默认内容
        given()
                .queryParam("userId", userId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", anyOf(empty(), hasSize(0)))
                .body("metadata.recommendationType", equalTo("empty_fallback"));

        System.out.println("✅ 空数据库推荐测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("新用户无历史数据推荐测试")
    void testRecommendationForNewUserWithoutHistory() {
        // 创建新用户
        Map<String, Object> userRequest = Map.of(
                "username", "new_user",
                "email", "new@test.com",
                "profileData", Map.of("age", 25)
        );

        String userId = given()
                .contentType(ContentType.JSON)
                .body(userRequest)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 请求推荐，应该返回热门内容或基于人口统计学的推荐
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", userId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("metadata.recommendationType", equalTo("cold_start"))
                .extract()
                .path("data");

        // 验证推荐结果的质量
        assertFalse(recommendations.isEmpty(), "新用户应该获得推荐结果");
        
        // 检查推荐分数是否合理
        for (Map<String, Object> rec : recommendations) {
            Double score = ((Number) rec.get("score")).doubleValue();
            assertTrue(score > 0 && score <= 1.0, "推荐分数应该在0-1之间");
        }

        System.out.println("✅ 新用户无历史数据推荐测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("极端参数测试")
    void testExtremeParameters() {
        // 测试size=0
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 0)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("Size must be greater than 0"));

        // 测试负数size
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", -5)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("Invalid parameter"));

        // 测试超大size
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10000)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("Size too large"));

        // 测试空用户ID
        given()
                .queryParam("userId", "")
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("User ID cannot be empty"));

        // 测试null用户ID
        given()
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(400)
                .body("error", containsString("User ID is required"));

        System.out.println("✅ 极端参数测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("并发请求测试")
    void testConcurrentRequests() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        // 发起100个并发请求
        for (int i = 0; i < 100; i++) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return given()
                            .queryParam("userId", testUserId)
                            .queryParam("size", 10)
                            .when()
                            .get("/api/v1/recommend/content")
                            .then()
                            .extract()
                            .statusCode();
                } catch (Exception e) {
                    return 500; // 异常情况返回500
                }
            }, executor);
            futures.add(future);
        }

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 统计结果
        long successCount = futures.stream()
                .mapToInt(CompletableFuture::join)
                .filter(status -> status == 200)
                .count();

        double successRate = (double) successCount / futures.size();
        assertTrue(successRate >= 0.95, 
                String.format("并发请求成功率应大于95%%，实际：%.2f%%", successRate * 100));

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("✅ 并发请求测试通过，成功率：" + String.format("%.2f%%", successRate * 100));
    }

    @Test
    @Order(5)
    @DisplayName("数据一致性测试")
    void testDataConsistency() {
        // 创建内容
        Map<String, Object> contentRequest = Map.of(
                "title", "一致性测试内容",
                "contentType", "article",
                "contentData", Map.of("content", "测试内容"),
                "tags", Arrays.asList("test"),
                "categoryId", 1
        );

        String contentId = given()
                .contentType(ContentType.JSON)
                .body(contentRequest)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 立即请求推荐，新内容可能还未被索引
        List<Map<String, Object>> immediateRecommendations = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 等待索引更新
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 再次请求推荐
        List<Map<String, Object>> delayedRecommendations = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 检查新内容是否出现在推荐中
        boolean foundInDelayed = delayedRecommendations.stream()
                .anyMatch(rec -> contentId.equals(rec.get("contentId")));

        assertTrue(foundInDelayed, "新创建的内容应该在延迟后的推荐中出现");

        System.out.println("✅ 数据一致性测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("缓存失效测试")
    void testCacheInvalidation() {
        // 第一次请求，建立缓存
        List<Map<String, Object>> firstRecommendations = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 模拟用户行为，应该触发缓存失效
        Map<String, Object> behaviorRequest = Map.of(
                "userId", testUserId,
                "contentId", testContentIds.get(0),
                "actionType", "like",
                "timestamp", System.currentTimeMillis()
        );

        given()
                .contentType(ContentType.JSON)
                .body(behaviorRequest)
                .when()
                .post("/api/v1/behaviors")
                .then()
                .statusCode(200);

        // 等待缓存失效和用户画像更新
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 第二次请求，应该获得更新的推荐
        List<Map<String, Object>> secondRecommendations = given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 推荐结果应该有所变化（至少排序会变化）
        assertNotEquals(firstRecommendations, secondRecommendations, 
                "用户行为后推荐结果应该发生变化");

        System.out.println("✅ 缓存失效测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("内存泄漏测试")
    void testMemoryLeak() {
        // 创建大量临时用户和内容，测试内存是否正常释放
        List<String> tempUserIds = new ArrayList<>();
        List<String> tempContentIds = new ArrayList<>();

        try {
            // 创建100个临时用户
            for (int i = 0; i < 100; i++) {
                Map<String, Object> userRequest = Map.of(
                        "username", "temp_user_" + i,
                        "email", "temp" + i + "@test.com"
                );

                String userId = given()
                        .contentType(ContentType.JSON)
                        .body(userRequest)
                        .when()
                        .post("/api/v1/users")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");

                tempUserIds.add(userId);
            }

            // 创建200个临时内容
            for (int i = 0; i < 200; i++) {
                Map<String, Object> contentRequest = Map.of(
                        "title", "临时内容" + i,
                        "contentType", "article",
                        "contentData", Map.of("content", "内容" + i),
                        "tags", Arrays.asList("temp"),
                        "categoryId", 1
                );

                String contentId = given()
                        .contentType(ContentType.JSON)
                        .body(contentRequest)
                        .when()
                        .post("/api/v1/contents")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id");

                tempContentIds.add(contentId);
            }

            // 为每个用户生成推荐
            for (String userId : tempUserIds) {
                given()
                        .queryParam("userId", userId)
                        .queryParam("size", 10)
                        .when()
                        .get("/api/v1/recommend/content")
                        .then()
                        .statusCode(200);
            }

            // 检查系统是否仍然响应正常
            given()
                    .queryParam("userId", testUserId)
                    .queryParam("size", 10)
                    .when()
                    .get("/api/v1/recommend/content")
                    .then()
                    .statusCode(200)
                    .time(lessThan(1000L)); // 响应时间应该仍然正常

            System.out.println("✅ 内存泄漏测试通过");

        } finally {
            // 清理临时数据
            cleanupTempData(tempUserIds, tempContentIds);
        }
    }

    @Test
    @Order(8)
    @DisplayName("网络异常恢复测试")
    void testNetworkFailureRecovery() {
        // 模拟网络异常情况下的推荐请求
        // 这里通过设置超时来模拟网络问题
        
        // 正常请求作为基准
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200);

        // 模拟网络恢复后的请求
        given()
                .queryParam("userId", testUserId)
                .queryParam("size", 10)
                .header("X-Simulate-Network-Recovery", "true")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)));

        System.out.println("✅ 网络异常恢复测试通过");
    }

    @Test
    @Order(9)
    @DisplayName("数据格式异常测试")
    void testInvalidDataFormat() {
        // 测试无效的JSON格式
        given()
                .contentType(ContentType.JSON)
                .body("invalid json")
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(400)
                .body("error", containsString("Invalid JSON format"));

        // 测试缺少必填字段
        Map<String, Object> incompleteUser = Map.of("email", "incomplete@test.com");
        given()
                .contentType(ContentType.JSON)
                .body(incompleteUser)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(400)
                .body("error", containsString("Username is required"));

        // 测试无效的内容类型
        Map<String, Object> invalidContent = Map.of(
                "title", "测试内容",
                "contentType", "invalid_type",
                "contentData", Map.of("content", "内容"),
                "categoryId", 1
        );

        given()
                .contentType(ContentType.JSON)
                .body(invalidContent)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(400)
                .body("error", containsString("Invalid content type"));

        System.out.println("✅ 数据格式异常测试通过");
    }

    @Test
    @Order(10)
    @DisplayName("资源限制测试")
    void testResourceLimits() {
        // 测试超长标题
        String longTitle = "a".repeat(1000);
        Map<String, Object> longTitleContent = Map.of(
                "title", longTitle,
                "contentType", "article",
                "contentData", Map.of("content", "内容"),
                "categoryId", 1
        );

        given()
                .contentType(ContentType.JSON)
                .body(longTitleContent)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(400)
                .body("error", containsString("Title too long"));

        // 测试超大内容
        String largeContent = "content ".repeat(10000);
        Map<String, Object> largeContentRequest = Map.of(
                "title", "大内容测试",
                "contentType", "article",
                "contentData", Map.of("content", largeContent),
                "categoryId", 1
        );

        given()
                .contentType(ContentType.JSON)
                .body(largeContentRequest)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(anyOf(equalTo(413), equalTo(400))) // Payload too large or bad request
                .body("error", anyOf(
                        containsString("Content too large"),
                        containsString("Payload too large")
                ));

        System.out.println("✅ 资源限制测试通过");
    }

    /**
     * 创建基础测试数据
     */
    private void createBasicTestData() {
        // 创建测试用户
        Map<String, Object> userRequest = Map.of(
                "username", "edge_case_user",
                "email", "edge@test.com",
                "profileData", Map.of("age", 30)
        );

        testUserId = given()
                .contentType(ContentType.JSON)
                .body(userRequest)
                .when()
                .post("/api/v1/users")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // 创建测试内容
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> contentRequest = Map.of(
                    "title", "边界测试内容" + i,
                    "contentType", i % 2 == 0 ? "article" : "video",
                    "contentData", Map.of("content", "内容" + i),
                    "tags", Arrays.asList("test", "edge_case"),
                    "categoryId", (i % 3) + 1
            );

            String contentId = given()
                    .contentType(ContentType.JSON)
                    .body(contentRequest)
                    .when()
                    .post("/api/v1/contents")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");

            testContentIds.add(contentId);
        }
    }

    /**
     * 清理临时数据
     */
    private void cleanupTempData(List<String> userIds, List<String> contentIds) {
        // 删除临时用户
        for (String userId : userIds) {
            try {
                given()
                        .when()
                        .delete("/api/v1/users/" + userId)
                        .then()
                        .statusCode(anyOf(equalTo(200), equalTo(204), equalTo(404)));
            } catch (Exception e) {
                // 忽略删除错误
            }
        }

        // 删除临时内容
        for (String contentId : contentIds) {
            try {
                given()
                        .when()
                        .delete("/api/v1/contents/" + contentId)
                        .then()
                        .statusCode(anyOf(equalTo(200), equalTo(204), equalTo(404)));
            } catch (Exception e) {
                // 忽略删除错误
            }
        }
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }
}