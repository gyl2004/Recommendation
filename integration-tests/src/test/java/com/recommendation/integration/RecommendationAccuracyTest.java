package com.recommendation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 推荐准确性测试
 * 测试不同场景下推荐算法的准确性和效果
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecommendationAccuracyTest extends BaseIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 测试用户数据
    private static final Map<String, String> testUsers = new HashMap<>();
    private static final Map<String, List<String>> userPreferences = new HashMap<>();
    private static final List<String> testContentIds = new ArrayList<>();

    @BeforeAll
    static void setUpTestData() {
        // 创建不同类型的测试用户
        userPreferences.put("tech_user", Arrays.asList("technology", "programming", "ai"));
        userPreferences.put("sports_user", Arrays.asList("sports", "fitness", "basketball"));
        userPreferences.put("entertainment_user", Arrays.asList("entertainment", "movies", "music"));
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        waitForServicesReady();
        cleanupTestData();
        createTestUsersAndContent();
    }

    @Test
    @Order(1)
    @DisplayName("基于内容的推荐准确性测试")
    void testContentBasedRecommendationAccuracy() {
        String techUserId = testUsers.get("tech_user");
        
        // 模拟技术用户浏览技术相关内容
        simulateUserInteractions(techUserId, "technology");
        
        // 等待用户画像更新
        waitForUserProfileUpdate(techUserId);
        
        // 获取推荐结果
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", techUserId)
                .queryParam("size", 10)
                .queryParam("contentType", "mixed")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 验证推荐准确性
        long techContentCount = recommendations.stream()
                .filter(rec -> {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) rec.get("tags");
                    return tags != null && tags.stream().anyMatch(tag -> 
                            Arrays.asList("technology", "programming", "ai").contains(tag));
                })
                .count();

        double accuracy = (double) techContentCount / recommendations.size();
        assertTrue(accuracy >= 0.7, 
                String.format("技术用户的推荐准确率应大于70%%，实际：%.2f%%", accuracy * 100));

        System.out.println("✅ 基于内容的推荐准确性测试通过，准确率：" + String.format("%.2f%%", accuracy * 100));
    }

    @Test
    @Order(2)
    @DisplayName("协同过滤推荐准确性测试")
    void testCollaborativeFilteringAccuracy() {
        String user1Id = testUsers.get("tech_user");
        String user2Id = testUsers.get("sports_user");
        
        // 创建相似用户行为模式
        List<String> commonContents = testContentIds.subList(0, 3);
        
        // 两个用户都对相同内容产生交互
        for (String contentId : commonContents) {
            simulateUserInteraction(user1Id, contentId, "like");
            simulateUserInteraction(user2Id, contentId, "like");
        }
        
        // user1额外喜欢一些内容
        List<String> user1ExtraContents = testContentIds.subList(3, 5);
        for (String contentId : user1ExtraContents) {
            simulateUserInteraction(user1Id, contentId, "like");
        }
        
        // 等待协同过滤模型更新
        waitForCollaborativeFilteringUpdate();
        
        // 为user2获取推荐，应该包含user1喜欢的额外内容
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", user2Id)
                .queryParam("size", 10)
                .queryParam("algorithm", "collaborative_filtering")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 检查是否推荐了相似用户喜欢的内容
        Set<String> recommendedContentIds = recommendations.stream()
                .map(rec -> (String) rec.get("contentId"))
                .collect(Collectors.toSet());

        long matchCount = user1ExtraContents.stream()
                .mapToLong(contentId -> recommendedContentIds.contains(contentId) ? 1 : 0)
                .sum();

        double collaborativeAccuracy = (double) matchCount / user1ExtraContents.size();
        assertTrue(collaborativeAccuracy >= 0.5, 
                "协同过滤推荐应该包含相似用户喜欢的内容");

        System.out.println("✅ 协同过滤推荐准确性测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("多样性测试")
    void testRecommendationDiversity() {
        String userId = testUsers.get("entertainment_user");
        
        // 模拟用户对多种类型内容的兴趣
        simulateUserInteractions(userId, "mixed");
        waitForUserProfileUpdate(userId);
        
        // 获取推荐结果
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", userId)
                .queryParam("size", 20)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 计算内容类型多样性
        Set<String> contentTypes = recommendations.stream()
                .map(rec -> (String) rec.get("contentType"))
                .collect(Collectors.toSet());

        assertTrue(contentTypes.size() >= 2, "推荐结果应包含多种内容类型");

        // 计算标签多样性
        Set<String> allTags = new HashSet<>();
        for (Map<String, Object> rec : recommendations) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) rec.get("tags");
            if (tags != null) {
                allTags.addAll(tags);
            }
        }

        assertTrue(allTags.size() >= 5, "推荐结果应包含多样化的标签");

        // 计算分数分布，避免所有推荐都是高分内容
        List<Double> scores = recommendations.stream()
                .map(rec -> ((Number) rec.get("score")).doubleValue())
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        double scoreVariance = calculateVariance(scores);
        assertTrue(scoreVariance > 0.01, "推荐分数应有一定的分布差异");

        System.out.println("✅ 多样性测试通过");
        System.out.println("内容类型数量：" + contentTypes.size());
        System.out.println("标签数量：" + allTags.size());
        System.out.println("分数方差：" + String.format("%.4f", scoreVariance));
    }

    @Test
    @Order(4)
    @DisplayName("新颖性测试")
    void testRecommendationNovelty() {
        String userId = testUsers.get("tech_user");
        
        // 模拟用户历史行为
        List<String> viewedContents = testContentIds.subList(0, 5);
        for (String contentId : viewedContents) {
            simulateUserInteraction(userId, contentId, "view");
        }
        
        waitForUserProfileUpdate(userId);
        
        // 获取推荐结果
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", userId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 检查推荐结果中新内容的比例
        Set<String> recommendedContentIds = recommendations.stream()
                .map(rec -> (String) rec.get("contentId"))
                .collect(Collectors.toSet());

        long novelContentCount = recommendedContentIds.stream()
                .mapToLong(contentId -> viewedContents.contains(contentId) ? 0 : 1)
                .sum();

        double noveltyRate = (double) novelContentCount / recommendations.size();
        assertTrue(noveltyRate >= 0.8, 
                String.format("推荐结果中新内容比例应大于80%%，实际：%.2f%%", noveltyRate * 100));

        System.out.println("✅ 新颖性测试通过，新内容比例：" + String.format("%.2f%%", noveltyRate * 100));
    }

    @Test
    @Order(5)
    @DisplayName("时效性测试")
    void testRecommendationFreshness() {
        String userId = testUsers.get("sports_user");
        
        // 创建不同时间的内容
        String oldContentId = createContentWithTimestamp("旧内容", System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000); // 7天前
        String newContentId = createContentWithTimestamp("新内容", System.currentTimeMillis() - 60 * 60 * 1000); // 1小时前
        
        // 模拟用户对体育内容的兴趣
        simulateUserInteractions(userId, "sports");
        waitForUserProfileUpdate(userId);
        
        // 获取推荐结果
        List<Map<String, Object>> recommendations = given()
                .queryParam("userId", userId)
                .queryParam("size", 10)
                .queryParam("freshness", "high")
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 检查新内容是否排在前面
        boolean newContentRankedHigher = false;
        for (int i = 0; i < Math.min(5, recommendations.size()); i++) {
            String contentId = (String) recommendations.get(i).get("contentId");
            if (newContentId.equals(contentId)) {
                newContentRankedHigher = true;
                break;
            }
        }

        assertTrue(newContentRankedHigher, "新内容应该在推荐结果中排名较高");

        System.out.println("✅ 时效性测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("个性化程度测试")
    void testPersonalizationLevel() {
        String techUserId = testUsers.get("tech_user");
        String sportsUserId = testUsers.get("sports_user");
        
        // 为不同用户建立不同的兴趣画像
        simulateUserInteractions(techUserId, "technology");
        simulateUserInteractions(sportsUserId, "sports");
        
        waitForUserProfileUpdate(techUserId);
        waitForUserProfileUpdate(sportsUserId);
        
        // 获取两个用户的推荐结果
        List<Map<String, Object>> techRecommendations = given()
                .queryParam("userId", techUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        List<Map<String, Object>> sportsRecommendations = given()
                .queryParam("userId", sportsUserId)
                .queryParam("size", 10)
                .when()
                .get("/api/v1/recommend/content")
                .then()
                .statusCode(200)
                .extract()
                .path("data");

        // 计算推荐结果的重叠度
        Set<String> techContentIds = techRecommendations.stream()
                .map(rec -> (String) rec.get("contentId"))
                .collect(Collectors.toSet());

        Set<String> sportsContentIds = sportsRecommendations.stream()
                .map(rec -> (String) rec.get("contentId"))
                .collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(techContentIds);
        intersection.retainAll(sportsContentIds);

        double overlapRate = (double) intersection.size() / Math.max(techContentIds.size(), sportsContentIds.size());
        assertTrue(overlapRate < 0.5, 
                String.format("不同兴趣用户的推荐重叠度应小于50%%，实际：%.2f%%", overlapRate * 100));

        System.out.println("✅ 个性化程度测试通过，重叠度：" + String.format("%.2f%%", overlapRate * 100));
    }

    /**
     * 创建测试用户和内容
     */
    private void createTestUsersAndContent() {
        // 创建测试用户
        for (Map.Entry<String, List<String>> entry : userPreferences.entrySet()) {
            String userType = entry.getKey();
            List<String> interests = entry.getValue();
            
            Map<String, Object> userRequest = Map.of(
                    "username", userType,
                    "email", userType + "@test.com",
                    "profileData", Map.of(
                            "interests", interests,
                            "age", 25 + userType.hashCode() % 20
                    )
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

            testUsers.put(userType, userId);
        }

        // 创建多样化的测试内容
        createDiverseTestContent();
    }

    /**
     * 创建多样化的测试内容
     */
    private void createDiverseTestContent() {
        // 技术类内容
        for (int i = 1; i <= 5; i++) {
            String contentId = createContent("技术文章" + i, "article", 
                    Arrays.asList("technology", "programming"), 1);
            testContentIds.add(contentId);
        }

        // 体育类内容
        for (int i = 1; i <= 5; i++) {
            String contentId = createContent("体育视频" + i, "video", 
                    Arrays.asList("sports", "fitness"), 2);
            testContentIds.add(contentId);
        }

        // 娱乐类内容
        for (int i = 1; i <= 5; i++) {
            String contentId = createContent("娱乐内容" + i, "article", 
                    Arrays.asList("entertainment", "movies"), 3);
            testContentIds.add(contentId);
        }

        // 混合类内容
        for (int i = 1; i <= 3; i++) {
            String contentId = createContent("综合内容" + i, "video", 
                    Arrays.asList("technology", "sports", "entertainment"), 4);
            testContentIds.add(contentId);
        }
    }

    /**
     * 创建内容
     */
    private String createContent(String title, String contentType, List<String> tags, int categoryId) {
        Map<String, Object> contentRequest = Map.of(
                "title", title,
                "contentType", contentType,
                "contentData", Map.of("content", title + "的详细内容"),
                "tags", tags,
                "categoryId", categoryId
        );

        return given()
                .contentType(ContentType.JSON)
                .body(contentRequest)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /**
     * 创建带时间戳的内容
     */
    private String createContentWithTimestamp(String title, long timestamp) {
        Map<String, Object> contentRequest = Map.of(
                "title", title,
                "contentType", "article",
                "contentData", Map.of("content", title + "的内容"),
                "tags", Arrays.asList("sports"),
                "categoryId", 2,
                "publishTime", timestamp
        );

        return given()
                .contentType(ContentType.JSON)
                .body(contentRequest)
                .when()
                .post("/api/v1/contents")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /**
     * 模拟用户交互
     */
    private void simulateUserInteractions(String userId, String interestType) {
        List<String> relevantContents = getRelevantContents(interestType);
        for (String contentId : relevantContents) {
            simulateUserInteraction(userId, contentId, "view");
            if (Math.random() > 0.7) { // 30%概率点赞
                simulateUserInteraction(userId, contentId, "like");
            }
        }
    }

    /**
     * 模拟单个用户交互
     */
    private void simulateUserInteraction(String userId, String contentId, String actionType) {
        Map<String, Object> behaviorRequest = Map.of(
                "userId", userId,
                "contentId", contentId,
                "actionType", actionType,
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

    /**
     * 获取相关内容
     */
    private List<String> getRelevantContents(String interestType) {
        switch (interestType) {
            case "technology":
                return testContentIds.subList(0, 5);
            case "sports":
                return testContentIds.subList(5, 10);
            case "entertainment":
                return testContentIds.subList(10, 15);
            case "mixed":
                return testContentIds.subList(0, 8);
            default:
                return testContentIds.subList(0, 3);
        }
    }

    /**
     * 等待用户画像更新
     */
    private void waitForUserProfileUpdate(String userId) {
        try {
            Thread.sleep(3000); // 等待3秒让用户画像更新
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待协同过滤更新
     */
    private void waitForCollaborativeFilteringUpdate() {
        try {
            Thread.sleep(5000); // 等待5秒让协同过滤模型更新
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 计算方差
     */
    private double calculateVariance(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        return variance;
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }
}