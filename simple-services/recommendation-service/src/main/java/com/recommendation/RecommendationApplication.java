package com.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class RecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "recommendation-service");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    @GetMapping("/api/v1/recommend/products")
    public Map<String, Object> recommendProducts(
            @RequestParam(defaultValue = "1") String userId,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "all") String category) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("userId", userId);
        response.put("size", size);
        response.put("category", category);
        
        // 电商商品推荐数据
        String[] productNames = {
            "iPhone 15 Pro Max 256GB", "MacBook Pro 14英寸", "AirPods Pro 2代",
            "iPad Air 第5代", "Apple Watch Series 9", "华为Mate 60 Pro",
            "小米14 Ultra", "戴森V15吸尘器", "索尼WH-1000XM5耳机",
            "Nike Air Jordan 1", "Adidas Ultra Boost", "Levi's 501牛仔裤"
        };
        
        String[] categories = {"手机", "电脑", "耳机", "平板", "手表", "家电", "服装", "运动"};
        String[] brands = {"Apple", "华为", "小米", "戴森", "索尼", "Nike", "Adidas", "Levi's"};
        double[] prices = {9999.0, 15999.0, 1899.0, 4599.0, 2999.0, 6999.0, 5999.0, 3999.0, 2299.0, 1299.0, 899.0, 599.0};
        
        java.util.List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
        
        for (int i = 0; i < Math.min(size, productNames.length); i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("productId", 10001 + i);
            product.put("name", productNames[i]);
            product.put("category", categories[i % categories.length]);
            product.put("brand", brands[i % brands.length]);
            product.put("price", prices[i % prices.length]);
            product.put("originalPrice", prices[i % prices.length] * 1.2);
            product.put("discount", 0.83);
            product.put("rating", 4.5 + (Math.random() * 0.5));
            product.put("reviewCount", 100 + (i * 50));
            product.put("imageUrl", "/images/product_" + (10001 + i) + ".jpg");
            product.put("description", "这是一款优质的" + productNames[i] + "，性价比极高");
            product.put("stock", 50 + (i * 10));
            product.put("sales", 1000 + (i * 200));
            product.put("recommendScore", 0.95 - (i * 0.05));
            product.put("tags", Arrays.asList("热销", "推荐", "优质"));
            recommendations.add(product);
        }
        
        response.put("recommendations", recommendations);
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    @GetMapping("/api/v1/recommend/similar/{productId}")
    public Map<String, Object> getSimilarProducts(@PathVariable String productId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("productId", productId);
        
        // 相似商品推荐
        java.util.List<Map<String, Object>> similar = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("productId", Integer.parseInt(productId) + i + 1);
            product.put("name", "相似商品 " + (i + 1));
            product.put("price", 999.0 + (i * 200));
            product.put("rating", 4.3 + (Math.random() * 0.4));
            product.put("imageUrl", "/images/similar_" + (i + 1) + ".jpg");
            product.put("similarity", 0.9 - (i * 0.1));
            similar.add(product);
        }
        
        response.put("similarProducts", similar);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}