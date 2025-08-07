package com.recommendation.content.service;

import com.recommendation.content.dto.ContentFeatures;
import com.recommendation.content.entity.ContentEntity;

/**
 * 内容特征提取服务接口
 * 实现需求6.1, 6.2, 6.3: 特征工程处理
 */
public interface ContentFeatureExtractionService {

    /**
     * 提取内容特征
     * 根据内容类型自动选择合适的特征提取方法
     * 
     * @param content 内容实体
     * @return 提取的特征数据
     */
    ContentFeatures extractFeatures(ContentEntity content);

    /**
     * 提取文本特征（TF-IDF和词向量）
     * 用于文章内容和商品描述
     * 
     * @param content 内容实体
     * @param text 要分析的文本
     * @return 文本特征
     */
    ContentFeatures.TextFeatures extractTextFeatures(ContentEntity content, String text);

    /**
     * 提取视频特征（元数据特征）
     * 
     * @param content 内容实体
     * @return 视频特征
     */
    ContentFeatures.VideoFeatures extractVideoFeatures(ContentEntity content);

    /**
     * 提取商品特征（属性特征和标准化）
     * 
     * @param content 内容实体
     * @return 商品特征
     */
    ContentFeatures.ProductFeatures extractProductFeatures(ContentEntity content);

    /**
     * 提取通用特征
     * 
     * @param content 内容实体
     * @return 通用特征
     */
    ContentFeatures.CommonFeatures extractCommonFeatures(ContentEntity content);

    /**
     * 标准化特征数据
     * 对数值特征进行归一化处理
     * 
     * @param features 原始特征
     * @return 标准化后的特征
     */
    ContentFeatures normalizeFeatures(ContentFeatures features);
}