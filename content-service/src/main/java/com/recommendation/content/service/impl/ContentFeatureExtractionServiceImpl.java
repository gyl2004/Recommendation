package com.recommendation.content.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.dto.ContentFeatures;
import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.service.ContentFeatureExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.text.similarity.CosineSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 内容特征提取服务实现
 * 实现需求6.1, 6.2, 6.3: 特征工程处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentFeatureExtractionServiceImpl implements ContentFeatureExtractionService {

    private final ObjectMapper objectMapper;
    
    // 中文分词正则表达式（简化版）
    private static final Pattern CHINESE_WORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[。！？；]");
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\n\n+");
    
    // 停用词列表（简化版）
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这"
    );

    @Override
    public ContentFeatures extractFeatures(ContentEntity content) {
        log.debug("开始提取内容特征: contentId={}, type={}", content.getId(), content.getContentType());

        ContentFeatures.ContentFeaturesBuilder builder = ContentFeatures.builder()
                .contentId(content.getId())
                .contentType(content.getContentType().getCode())
                .commonFeatures(extractCommonFeatures(content));

        try {
            // 根据内容类型提取特定特征
            switch (content.getContentType()) {
                case ARTICLE:
                    extractArticleFeatures(content, builder);
                    break;
                case VIDEO:
                    builder.videoFeatures(extractVideoFeatures(content));
                    break;
                case PRODUCT:
                    extractProductFeatures(content, builder);
                    break;
                default:
                    log.warn("未知的内容类型: {}", content.getContentType());
            }

            ContentFeatures features = builder.build();
            
            // 标准化特征
            features = normalizeFeatures(features);
            
            log.debug("内容特征提取完成: contentId={}", content.getId());
            return features;
            
        } catch (Exception e) {
            log.error("内容特征提取失败: contentId={}", content.getId(), e);
            // 返回默认特征
            return createDefaultFeatures(content);
        }
    }

    @Override
    public ContentFeatures.TextFeatures extractTextFeatures(ContentEntity content, String text) {
        if (!StringUtils.hasText(text)) {
            return createDefaultTextFeatures();
        }

        try {
            // 文本预处理
            String cleanText = preprocessText(text);
            
            // 分词
            List<String> words = tokenize(cleanText);
            
            // 计算TF-IDF
            Map<String, Double> tfidfVector = calculateTFIDF(words);
            
            // 提取关键词
            List<String> keywords = extractKeywords(tfidfVector, 10);
            
            // 生成词向量（简化版，实际应该使用预训练模型）
            List<Double> wordEmbedding = generateWordEmbedding(words);
            
            // 计算文本统计特征
            int textLength = text.length();
            int sentenceCount = countSentences(text);
            int paragraphCount = countParagraphs(text);
            
            // 简单情感分析
            double sentimentScore = calculateSentiment(text);

            return ContentFeatures.TextFeatures.builder()
                    .tfidfVector(tfidfVector)
                    .wordEmbedding(wordEmbedding)
                    .keywords(keywords)
                    .textLength(textLength)
                    .sentenceCount(sentenceCount)
                    .paragraphCount(paragraphCount)
                    .sentimentScore(sentimentScore)
                    .build();
                    
        } catch (Exception e) {
            log.error("文本特征提取失败", e);
            return createDefaultTextFeatures();
        }
    }

    @Override
    public ContentFeatures.VideoFeatures extractVideoFeatures(ContentEntity content) {
        try {
            Map<String, Object> contentData = parseContentData(content.getContentData());
            
            // 提取视频元数据
            Integer duration = getIntegerValue(contentData, "duration", 0);
            Integer width = getIntegerValue(contentData, "width", 1920);
            Integer height = getIntegerValue(contentData, "height", 1080);
            Long fileSize = getLongValue(contentData, "fileSize", 0L);
            String format = getStringValue(contentData, "format", "mp4");
            Double frameRate = getDoubleValue(contentData, "frameRate", 30.0);
            Integer bitRate = getIntegerValue(contentData, "bitRate", 1000);
            Boolean hasAudio = getBooleanValue(contentData, "hasAudio", true);
            
            // 计算质量分数
            double qualityScore = calculateVideoQualityScore(width, height, bitRate, frameRate);
            
            // 时长分类
            String durationCategory = categorizeDuration(duration);

            return ContentFeatures.VideoFeatures.builder()
                    .duration(duration)
                    .width(width)
                    .height(height)
                    .fileSize(fileSize)
                    .format(format)
                    .frameRate(frameRate)
                    .bitRate(bitRate)
                    .hasAudio(hasAudio)
                    .qualityScore(qualityScore)
                    .durationCategory(durationCategory)
                    .build();
                    
        } catch (Exception e) {
            log.error("视频特征提取失败: contentId={}", content.getId(), e);
            return createDefaultVideoFeatures();
        }
    }

    @Override
    public ContentFeatures.ProductFeatures extractProductFeatures(ContentEntity content) {
        try {
            Map<String, Object> contentData = parseContentData(content.getContentData());
            
            // 提取商品基础属性
            Double price = getDoubleValue(contentData, "price", 0.0);
            String brand = getStringValue(contentData, "brand", "");
            String description = getStringValue(contentData, "description", "");
            Double rating = getDoubleValue(contentData, "rating", 0.0);
            Integer salesCount = getIntegerValue(contentData, "salesCount", 0);
            String stockStatus = getStringValue(contentData, "stockStatus", "in_stock");
            
            // 计算衍生特征
            String priceRange = categorizePriceRange(price);
            Map<String, Object> attributeVector = extractProductAttributes(contentData);
            Integer imageCount = countProductImages(contentData);
            Integer descriptionLength = description.length();
            String productStatus = categorizeProductStatus(salesCount, rating);

            return ContentFeatures.ProductFeatures.builder()
                    .price(price)
                    .priceRange(priceRange)
                    .brand(brand)
                    .attributeVector(attributeVector)
                    .imageCount(imageCount)
                    .descriptionLength(descriptionLength)
                    .rating(rating)
                    .salesCount(salesCount)
                    .stockStatus(stockStatus)
                    .productStatus(productStatus)
                    .build();
                    
        } catch (Exception e) {
            log.error("商品特征提取失败: contentId={}", content.getId(), e);
            return createDefaultProductFeatures();
        }
    }

    @Override
    public ContentFeatures.CommonFeatures extractCommonFeatures(ContentEntity content) {
        try {
            // 解析标签
            List<String> tags = parseTags(content.getTags());
            
            // 计算内容新鲜度
            Integer freshnessHours = calculateFreshnessHours(content.getPublishTime());
            
            // 计算互动率
            Double engagementRate = calculateEngagementRate(
                content.getViewCount(), 
                content.getLikeCount(), 
                content.getShareCount(), 
                content.getCommentCount()
            );

            return ContentFeatures.CommonFeatures.builder()
                    .tags(tags)
                    .categoryId(content.getCategoryId())
                    .categoryPath(buildCategoryPath(content.getCategoryId()))
                    .hotScore(content.getHotScore() != null ? content.getHotScore().doubleValue() : 0.0)
                    .publishTimestamp(content.getPublishTime() != null ? 
                        content.getPublishTime().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() : 0L)
                    .freshnessHours(freshnessHours)
                    .authorId(content.getAuthorId())
                    .viewCount(content.getViewCount())
                    .likeCount(content.getLikeCount())
                    .shareCount(content.getShareCount())
                    .commentCount(content.getCommentCount())
                    .engagementRate(engagementRate)
                    .build();
                    
        } catch (Exception e) {
            log.error("通用特征提取失败: contentId={}", content.getId(), e);
            return createDefaultCommonFeatures(content);
        }
    }

    @Override
    public ContentFeatures normalizeFeatures(ContentFeatures features) {
        try {
            // 标准化文本特征
            if (features.getTextFeatures() != null) {
                normalizeTextFeatures(features.getTextFeatures());
            }
            
            // 标准化视频特征
            if (features.getVideoFeatures() != null) {
                normalizeVideoFeatures(features.getVideoFeatures());
            }
            
            // 标准化商品特征
            if (features.getProductFeatures() != null) {
                normalizeProductFeatures(features.getProductFeatures());
            }
            
            // 标准化通用特征
            if (features.getCommonFeatures() != null) {
                normalizeCommonFeatures(features.getCommonFeatures());
            }
            
            return features;
            
        } catch (Exception e) {
            log.error("特征标准化失败", e);
            return features;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 提取文章特征
     */
    private void extractArticleFeatures(ContentEntity content, ContentFeatures.ContentFeaturesBuilder builder) {
        try {
            Map<String, Object> contentData = parseContentData(content.getContentData());
            String articleContent = getStringValue(contentData, "content", "");
            String summary = getStringValue(contentData, "summary", "");
            
            // 合并标题、摘要和正文进行特征提取
            String fullText = content.getTitle() + " " + summary + " " + articleContent;
            builder.textFeatures(extractTextFeatures(content, fullText));
            
        } catch (Exception e) {
            log.error("文章特征提取失败: contentId={}", content.getId(), e);
            builder.textFeatures(createDefaultTextFeatures());
        }
    }

    /**
     * 提取商品特征（包含文本特征）
     */
    private void extractProductFeatures(ContentEntity content, ContentFeatures.ContentFeaturesBuilder builder) {
        try {
            // 提取商品特定特征
            builder.productFeatures(extractProductFeatures(content));
            
            // 提取商品描述的文本特征
            Map<String, Object> contentData = parseContentData(content.getContentData());
            String description = getStringValue(contentData, "description", "");
            String fullText = content.getTitle() + " " + description;
            builder.textFeatures(extractTextFeatures(content, fullText));
            
        } catch (Exception e) {
            log.error("商品特征提取失败: contentId={}", content.getId(), e);
            builder.productFeatures(createDefaultProductFeatures());
            builder.textFeatures(createDefaultTextFeatures());
        }
    }

    /**
     * 文本预处理
     */
    private String preprocessText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        
        // 移除HTML标签
        text = text.replaceAll("<[^>]+>", "");
        
        // 移除特殊字符，保留中文、英文、数字
        text = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", " ");
        
        // 移除多余空格
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    /**
     * 中文分词（简化版）
     */
    private List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        
        // 提取中文词汇
        java.util.regex.Matcher matcher = CHINESE_WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 2 && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        
        // 提取英文单词
        String[] englishWords = text.replaceAll("[\\u4e00-\\u9fa5]", " ").split("\\s+");
        for (String word : englishWords) {
            if (word.length() >= 3 && word.matches("[a-zA-Z]+")) {
                words.add(word.toLowerCase());
            }
        }
        
        return words;
    }

    /**
     * 计算TF-IDF（简化版）
     */
    private Map<String, Double> calculateTFIDF(List<String> words) {
        Map<String, Integer> termFreq = new HashMap<>();
        
        // 计算词频
        for (String word : words) {
            termFreq.put(word, termFreq.getOrDefault(word, 0) + 1);
        }
        
        // 简化的TF-IDF计算（没有文档集合，只计算TF）
        Map<String, Double> tfidf = new HashMap<>();
        int totalWords = words.size();
        
        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            double tf = (double) entry.getValue() / totalWords;
            // 简化的IDF，实际应该基于整个文档集合
            double idf = Math.log(1.0 + 1.0 / (1.0 + entry.getValue()));
            tfidf.put(entry.getKey(), tf * idf);
        }
        
        return tfidf;
    }

    /**
     * 提取关键词
     */
    private List<String> extractKeywords(Map<String, Double> tfidfVector, int topK) {
        return tfidfVector.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 生成词向量（简化版）
     */
    private List<Double> generateWordEmbedding(List<String> words) {
        // 简化的词向量生成，实际应该使用预训练的词向量模型
        int embeddingSize = 128;
        List<Double> embedding = new ArrayList<>(Collections.nCopies(embeddingSize, 0.0));
        
        if (words.isEmpty()) {
            return embedding;
        }
        
        // 基于词汇的哈希值生成简单的向量表示
        for (String word : words) {
            int hash = Math.abs(word.hashCode());
            for (int i = 0; i < embeddingSize; i++) {
                double value = Math.sin(hash * (i + 1) * 0.01) * 0.1;
                embedding.set(i, embedding.get(i) + value);
            }
        }
        
        // 归一化
        double norm = Math.sqrt(embedding.stream().mapToDouble(x -> x * x).sum());
        if (norm > 0) {
            for (int i = 0; i < embeddingSize; i++) {
                embedding.set(i, embedding.get(i) / norm);
            }
        }
        
        return embedding;
    }

    /**
     * 计算句子数量
     */
    private int countSentences(String text) {
        return SENTENCE_PATTERN.split(text).length;
    }

    /**
     * 计算段落数量
     */
    private int countParagraphs(String text) {
        return Math.max(1, PARAGRAPH_PATTERN.split(text).length);
    }

    /**
     * 简单情感分析
     */
    private double calculateSentiment(String text) {
        // 简化的情感分析，基于情感词典
        Set<String> positiveWords = Set.of("好", "棒", "优秀", "喜欢", "满意", "推荐", "不错", "很好");
        Set<String> negativeWords = Set.of("差", "坏", "糟糕", "讨厌", "不满", "失望", "垃圾", "不好");
        
        List<String> words = tokenize(text);
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (String word : words) {
            if (positiveWords.contains(word)) {
                positiveCount++;
            } else if (negativeWords.contains(word)) {
                negativeCount++;
            }
        }
        
        int totalSentimentWords = positiveCount + negativeCount;
        if (totalSentimentWords == 0) {
            return 0.0; // 中性
        }
        
        return (double) (positiveCount - negativeCount) / totalSentimentWords;
    }

    /**
     * 计算视频质量分数
     */
    private double calculateVideoQualityScore(int width, int height, int bitRate, double frameRate) {
        // 基于分辨率、比特率和帧率计算质量分数
        double resolutionScore = Math.min(1.0, (width * height) / (1920.0 * 1080.0));
        double bitRateScore = Math.min(1.0, bitRate / 5000.0);
        double frameRateScore = Math.min(1.0, frameRate / 60.0);
        
        return (resolutionScore * 0.4 + bitRateScore * 0.4 + frameRateScore * 0.2);
    }

    /**
     * 视频时长分类
     */
    private String categorizeDuration(int duration) {
        if (duration <= 60) {
            return "short"; // 短视频
        } else if (duration <= 600) {
            return "medium"; // 中视频
        } else {
            return "long"; // 长视频
        }
    }

    /**
     * 价格区间分类
     */
    private String categorizePriceRange(double price) {
        if (price <= 100) {
            return "low";
        } else if (price <= 1000) {
            return "medium";
        } else {
            return "high";
        }
    }

    /**
     * 提取商品属性向量
     */
    private Map<String, Object> extractProductAttributes(Map<String, Object> contentData) {
        Map<String, Object> attributes = new HashMap<>();
        
        // 提取标准化的商品属性
        attributes.put("hasDiscount", contentData.containsKey("originalPrice"));
        attributes.put("hasImages", contentData.containsKey("images"));
        attributes.put("hasVideo", contentData.containsKey("videoUrl"));
        attributes.put("hasReviews", getIntegerValue(contentData, "reviewCount", 0) > 0);
        
        return attributes;
    }

    /**
     * 统计商品图片数量
     */
    private int countProductImages(Map<String, Object> contentData) {
        Object images = contentData.get("images");
        if (images instanceof List) {
            return ((List<?>) images).size();
        }
        return 0;
    }

    /**
     * 商品状态分类
     */
    private String categorizeProductStatus(int salesCount, double rating) {
        if (salesCount > 1000 && rating > 4.5) {
            return "hot"; // 热销
        } else if (salesCount < 10) {
            return "new"; // 新品
        } else if (rating < 3.0) {
            return "clearance"; // 清仓
        } else {
            return "normal"; // 普通
        }
    }

    /**
     * 解析标签
     */
    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("标签解析失败: {}", tagsJson, e);
            return new ArrayList<>();
        }
    }

    /**
     * 计算内容新鲜度
     */
    private Integer calculateFreshnessHours(LocalDateTime publishTime) {
        if (publishTime == null) {
            return Integer.MAX_VALUE;
        }
        
        return (int) ChronoUnit.HOURS.between(publishTime, LocalDateTime.now());
    }

    /**
     * 计算互动率
     */
    private Double calculateEngagementRate(Long viewCount, Long likeCount, Long shareCount, Long commentCount) {
        if (viewCount == null || viewCount == 0) {
            return 0.0;
        }
        
        long totalEngagements = (likeCount != null ? likeCount : 0) + 
                               (shareCount != null ? shareCount : 0) + 
                               (commentCount != null ? commentCount : 0);
        
        return (double) totalEngagements / viewCount;
    }

    /**
     * 构建分类路径
     */
    private String buildCategoryPath(Integer categoryId) {
        if (categoryId == null) {
            return "";
        }
        
        // 简化实现，实际应该查询分类表构建完整路径
        return "category_" + categoryId;
    }

    // ==================== 特征标准化方法 ====================

    private void normalizeTextFeatures(ContentFeatures.TextFeatures textFeatures) {
        // 标准化情感分数到0-1范围
        if (textFeatures.getSentimentScore() != null) {
            double normalizedSentiment = (textFeatures.getSentimentScore() + 1.0) / 2.0;
            textFeatures.setSentimentScore(normalizedSentiment);
        }
    }

    private void normalizeVideoFeatures(ContentFeatures.VideoFeatures videoFeatures) {
        // 视频特征已经在计算时进行了标准化
    }

    private void normalizeProductFeatures(ContentFeatures.ProductFeatures productFeatures) {
        // 标准化评分到0-1范围
        if (productFeatures.getRating() != null) {
            double normalizedRating = productFeatures.getRating() / 5.0;
            productFeatures.setRating(Math.min(1.0, Math.max(0.0, normalizedRating)));
        }
    }

    private void normalizeCommonFeatures(ContentFeatures.CommonFeatures commonFeatures) {
        // 标准化互动率
        if (commonFeatures.getEngagementRate() != null) {
            double normalizedRate = Math.min(1.0, commonFeatures.getEngagementRate());
            commonFeatures.setEngagementRate(normalizedRate);
        }
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> parseContentData(String contentDataJson) {
        try {
            return objectMapper.readValue(contentDataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("内容数据解析失败: {}", contentDataJson, e);
            return new HashMap<>();
        }
    }

    private String getStringValue(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Integer getIntegerValue(Map<String, Object> data, String key, Integer defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long getLongValue(Map<String, Object> data, String key, Long defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Double getDoubleValue(Map<String, Object> data, String key, Double defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Boolean getBooleanValue(Map<String, Object> data, String key, Boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        try {
            return Boolean.parseBoolean(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ==================== 默认特征创建方法 ====================

    private ContentFeatures createDefaultFeatures(ContentEntity content) {
        return ContentFeatures.builder()
                .contentId(content.getId())
                .contentType(content.getContentType().getCode())
                .commonFeatures(createDefaultCommonFeatures(content))
                .textFeatures(createDefaultTextFeatures())
                .build();
    }

    private ContentFeatures.TextFeatures createDefaultTextFeatures() {
        return ContentFeatures.TextFeatures.builder()
                .tfidfVector(new HashMap<>())
                .wordEmbedding(new ArrayList<>(Collections.nCopies(128, 0.0)))
                .keywords(new ArrayList<>())
                .textLength(0)
                .sentenceCount(0)
                .paragraphCount(0)
                .sentimentScore(0.0)
                .build();
    }

    private ContentFeatures.VideoFeatures createDefaultVideoFeatures() {
        return ContentFeatures.VideoFeatures.builder()
                .duration(0)
                .width(1920)
                .height(1080)
                .fileSize(0L)
                .format("mp4")
                .frameRate(30.0)
                .bitRate(1000)
                .hasAudio(true)
                .qualityScore(0.5)
                .durationCategory("medium")
                .build();
    }

    private ContentFeatures.ProductFeatures createDefaultProductFeatures() {
        return ContentFeatures.ProductFeatures.builder()
                .price(0.0)
                .priceRange("low")
                .brand("")
                .attributeVector(new HashMap<>())
                .imageCount(0)
                .descriptionLength(0)
                .rating(0.0)
                .salesCount(0)
                .stockStatus("in_stock")
                .productStatus("new")
                .build();
    }

    private ContentFeatures.CommonFeatures createDefaultCommonFeatures(ContentEntity content) {
        return ContentFeatures.CommonFeatures.builder()
                .tags(new ArrayList<>())
                .categoryId(content.getCategoryId())
                .categoryPath("")
                .hotScore(0.0)
                .publishTimestamp(0L)
                .freshnessHours(Integer.MAX_VALUE)
                .authorId(content.getAuthorId())
                .viewCount(content.getViewCount() != null ? content.getViewCount() : 0L)
                .likeCount(content.getLikeCount() != null ? content.getLikeCount() : 0L)
                .shareCount(content.getShareCount() != null ? content.getShareCount() : 0L)
                .commentCount(content.getCommentCount() != null ? content.getCommentCount() : 0L)
                .engagementRate(0.0)
                .build();
    }
}