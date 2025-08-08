package com.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@SpringBootApplication
@RestController
@CrossOrigin(origins = "*")
public class ContentApplication {

    public static void main(String[] args) {
        System.setProperty("server.port", "8082");
        SpringApplication.run(ContentApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "content-service");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    @GetMapping("/api/v1/products")
    public Map<String, Object> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String brand,
            @RequestParam(defaultValue = "0") double minPrice,
            @RequestParam(defaultValue = "999999") double maxPrice,
            @RequestParam(defaultValue = "sales") String sortBy) {
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> products = new ArrayList<>();
        
        String[] productNames = {
            "iPhone 15 Pro Max 256GB 深空黑", "MacBook Pro 14英寸 M3芯片", "AirPods Pro 2代 主动降噪",
            "iPad Air 第5代 64GB WiFi", "Apple Watch Series 9 GPS", "华为Mate 60 Pro 512GB",
            "小米14 Ultra 16GB+512GB", "戴森V15 Detect无线吸尘器", "索尼WH-1000XM5无线耳机",
            "Nike Air Jordan 1 Retro High", "Adidas Ultra Boost 22跑鞋", "Levi's 501经典牛仔裤",
            "Samsung Galaxy S24 Ultra", "Dell XPS 13笔记本电脑", "Bose QuietComfort 45耳机",
            "Nintendo Switch OLED游戏机", "Canon EOS R6 Mark II相机", "Dyson Airwrap美发造型器",
            "Tesla Model Y车载充电器", "Rolex Submariner潜航者", "Hermès Birkin手提包",
            "LV Neverfull购物袋", "Gucci Ace小白鞋", "Chanel No.5香水"
        };
        
        String[] categories = {"手机", "电脑", "耳机", "平板", "手表", "家电", "服装", "运动", "相机", "奢侈品"};
        String[] brands = {"Apple", "华为", "小米", "戴森", "索尼", "Nike", "Adidas", "Levi's", "Samsung", "Dell", "Bose", "Nintendo", "Canon", "Tesla", "Rolex", "Hermès", "LV", "Gucci", "Chanel"};
        double[] prices = {9999, 15999, 1899, 4599, 2999, 6999, 5999, 3999, 2299, 1299, 899, 599, 8999, 12999, 1999, 2599, 18999, 4999, 1299, 89999, 125000, 18000, 3500, 1200};
        
        for (int i = 0; i < size && i < productNames.length; i++) {
            int index = (page * size + i) % productNames.length;
            double price = prices[index % prices.length];
            
            // 应用价格过滤
            if (price < minPrice || price > maxPrice) continue;
            
            Map<String, Object> product = new HashMap<>();
            product.put("productId", 10001 + index);
            product.put("name", productNames[index]);
            product.put("category", categories[index % categories.length]);
            product.put("brand", brands[index % brands.length]);
            product.put("price", price);
            product.put("originalPrice", price * 1.2);
            product.put("discount", Math.round((1 - price / (price * 1.2)) * 100));
            product.put("rating", Math.round((4.0 + Math.random() * 1.0) * 10) / 10.0);
            product.put("reviewCount", 50 + (index * 25));
            product.put("imageUrl", "/images/product_" + (10001 + index) + ".jpg");
            product.put("description", "精选优质商品 " + productNames[index] + "，品质保证，售后无忧");
            product.put("stock", 20 + (index * 5));
            product.put("sales", 500 + (index * 100));
            product.put("isHot", index % 3 == 0);
            product.put("isNew", index % 4 == 0);
            product.put("freeShipping", price > 99);
            product.put("tags", Arrays.asList("正品保证", "快速发货", "7天退换"));
            products.add(product);
        }
        
        response.put("success", true);
        response.put("products", products);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", productNames.length);
        response.put("totalPages", (productNames.length + size - 1) / size);
        response.put("filters", createFilters());
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }
    
    private Map<String, Object> createFilters() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("categories", Arrays.asList("手机", "电脑", "耳机", "平板", "手表", "家电", "服装", "运动"));
        filters.put("brands", Arrays.asList("Apple", "华为", "小米", "戴森", "索尼", "Nike", "Adidas"));
        filters.put("priceRanges", Arrays.asList(
            Map.of("label", "100以下", "min", 0, "max", 100),
            Map.of("label", "100-500", "min", 100, "max", 500),
            Map.of("label", "500-1000", "min", 500, "max", 1000),
            Map.of("label", "1000-5000", "min", 1000, "max", 5000),
            Map.of("label", "5000以上", "min", 5000, "max", 999999)
        ));
        return filters;
    }

    @GetMapping("/api/v1/products/{productId}")
    public Map<String, Object> getProduct(@PathVariable String productId) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("productId", productId);
        response.put("name", "iPhone 15 Pro Max 256GB");
        response.put("category", "手机");
        response.put("brand", "Apple");
        response.put("price", 9999.0);
        response.put("originalPrice", 11999.0);
        response.put("discount", 17);
        response.put("rating", 4.8);
        response.put("reviewCount", 1250);
        response.put("description", "全新iPhone 15 Pro Max，搭载A17 Pro芯片，钛金属设计，专业级摄像系统");
        response.put("specifications", Map.of(
            "屏幕尺寸", "6.7英寸",
            "存储容量", "256GB", 
            "颜色", "深空黑",
            "网络", "5G",
            "电池", "4441mAh"
        ));
        response.put("images", Arrays.asList(
            "/images/iphone15_1.jpg",
            "/images/iphone15_2.jpg", 
            "/images/iphone15_3.jpg"
        ));
        response.put("stock", 50);
        response.put("sales", 2580);
        response.put("freeShipping", true);
        response.put("warranty", "1年官方保修");
        response.put("tags", Arrays.asList("5G", "A17芯片", "钛金属", "专业摄影"));
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/api/v1/products/search")
    public Map<String, Object> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        
        String[] searchResults = {
            "iPhone 15 Pro搜索结果", "MacBook Pro搜索结果", "AirPods搜索结果",
            "iPad搜索结果", "Apple Watch搜索结果", "华为手机搜索结果"
        };
        
        for (int i = 0; i < Math.min(size, searchResults.length); i++) {
            Map<String, Object> product = new HashMap<>();
            product.put("productId", 20000 + i);
            product.put("name", query + " - " + searchResults[i]);
            product.put("category", "手机");
            product.put("brand", "Apple");
            product.put("price", 1999.0 + (i * 500));
            product.put("rating", 4.5 - (i * 0.1));
            product.put("reviewCount", 100 + (i * 50));
            product.put("imageUrl", "/images/search_" + i + ".jpg");
            product.put("relevanceScore", 0.95 - (i * 0.1));
            product.put("snippet", "搜索到的" + query + "相关商品，高品质保证");
            results.add(product);
        }
        
        response.put("success", true);
        response.put("query", query);
        response.put("results", results);
        response.put("page", page);
        response.put("size", size);
        response.put("totalResults", 50);
        response.put("suggestions", Arrays.asList(query + " Pro", query + " Max", query + " Mini"));
        response.put("timestamp", System.currentTimeMillis());
        
        return response;
    }

    @PostMapping("/api/v1/cart/add")
    public Map<String, Object> addToCart(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "商品已添加到购物车");
        response.put("productId", request.get("productId"));
        response.put("quantity", request.get("quantity"));
        response.put("cartItemCount", 3);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/api/v1/cart/{userId}")
    public Map<String, Object> getCart(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        
        // 模拟购物车商品
        for (int i = 0; i < 3; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("cartItemId", 30000 + i);
            item.put("productId", 10001 + i);
            item.put("name", "购物车商品 " + (i + 1));
            item.put("price", 999.0 + (i * 500));
            item.put("quantity", 1 + i);
            item.put("imageUrl", "/images/cart_" + i + ".jpg");
            item.put("selected", true);
            items.add(item);
        }
        
        response.put("success", true);
        response.put("userId", userId);
        response.put("items", items);
        response.put("totalItems", items.size());
        response.put("totalAmount", 3497.0);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}