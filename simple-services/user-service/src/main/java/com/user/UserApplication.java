package com.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class UserApplication {

    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        SpringApplication.run(UserApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "user-service");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    @GetMapping("/api/v1/users/{userId}")
    public Map<String, Object> getUser(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("userId", userId);
        response.put("username", "user_" + userId);
        response.put("email", "user" + userId + "@example.com");
        response.put("age", 25 + (userId.hashCode() % 30));
        response.put("gender", userId.hashCode() % 2 == 0 ? "male" : "female");
        response.put("interests", Arrays.asList("技术", "阅读", "音乐", "旅行"));
        response.put("registrationDate", "2023-01-15");
        response.put("lastLoginTime", System.currentTimeMillis() - 3600000);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/api/v1/users/{userId}/preferences")
    public Map<String, Object> getUserPreferences(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("userId", userId);
        response.put("favoriteCategories", Arrays.asList("手机", "电脑", "耳机", "服装"));
        response.put("favoriteBrands", Arrays.asList("Apple", "华为", "Nike", "Adidas"));
        response.put("priceRange", Map.of("min", 100, "max", 5000));
        response.put("shippingAddress", Map.of(
            "province", "北京市",
            "city", "北京市", 
            "district", "朝阳区",
            "address", "三里屯街道1号",
            "zipCode", "100000"
        ));
        response.put("paymentMethods", Arrays.asList("支付宝", "微信支付", "银行卡"));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/api/v1/users/{userId}/orders")
    public Map<String, Object> getUserOrders(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> orders = new ArrayList<>();
        
        String[] statuses = {"已完成", "配送中", "待付款", "已取消"};
        
        for (int i = 0; i < 5; i++) {
            Map<String, Object> order = new HashMap<>();
            order.put("orderId", "ORD" + System.currentTimeMillis() + i);
            order.put("status", statuses[i % statuses.length]);
            order.put("totalAmount", 1999.0 + (i * 500));
            order.put("itemCount", 1 + i);
            order.put("orderTime", System.currentTimeMillis() - (i * 86400000L));
            order.put("productName", "订单商品 " + (i + 1));
            order.put("productImage", "/images/order_" + i + ".jpg");
            orders.add(order);
        }
        
        response.put("success", true);
        response.put("userId", userId);
        response.put("orders", orders);
        response.put("totalOrders", orders.size());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/api/v1/users/{userId}/favorites")
    public Map<String, Object> getUserFavorites(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> favorites = new ArrayList<>();
        
        for (int i = 0; i < 6; i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("productId", 40000 + i);
            product.put("name", "收藏商品 " + (i + 1));
            product.put("price", 599.0 + (i * 300));
            product.put("imageUrl", "/images/favorite_" + i + ".jpg");
            product.put("addTime", System.currentTimeMillis() - (i * 3600000L));
            favorites.add(product);
        }
        
        response.put("success", true);
        response.put("userId", userId);
        response.put("favorites", favorites);
        response.put("totalFavorites", favorites.size());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}