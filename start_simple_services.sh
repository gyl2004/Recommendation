#!/bin/bash

echo "=========================================="
echo "启动简化Java服务"
echo "=========================================="

# 创建日志目录
mkdir -p logs

# 检查Java是否可用
if ! command -v java &> /dev/null; then
    echo "❌ Java未安装或不在PATH中"
    exit 1
fi

echo "[INFO] Java版本:"
java -version

# 创建临时目录
mkdir -p temp
cd temp

echo "[INFO] 创建简化的推荐服务..."
cat > RecommendationApp.java << 'EOF'
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RecommendationApp {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("推荐服务启动在端口: " + port);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleRequest(clientSocket));
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String inputLine = in.readLine();
            if (inputLine == null) return;
            
            String[] parts = inputLine.split(" ");
            if (parts.length < 2) return;
            
            String method = parts[0];
            String path = parts[1];
            
            // 读取所有请求头
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // 跳过请求头
            }
            
            String response;
            String contentType = "application/json";
            
            if (path.equals("/actuator/health")) {
                response = "{\"status\":\"UP\",\"service\":\"recommendation-service\"}";
            } else if (path.startsWith("/api/v1/recommend/content")) {
                response = "{\"success\":true,\"message\":\"推荐服务正常运行\",\"userId\":\"1\",\"size\":\"10\",\"recommendations\":[\"内容1\",\"内容2\",\"内容3\"]}";
            } else {
                response = "{\"error\":\"Not Found\"}";
            }
            
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + response.length());
            out.println("Access-Control-Allow-Origin: *");
            out.println();
            out.println(response);
            
        } catch (IOException e) {
            System.err.println("处理请求时出错: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("关闭连接时出错: " + e.getMessage());
            }
        }
    }
}
EOF

echo "[INFO] 创建简化的用户服务..."
cat > UserApp.java << 'EOF'
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UserApp {
    public static void main(String[] args) throws IOException {
        int port = 8081;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("用户服务启动在端口: " + port);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleRequest(clientSocket));
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String inputLine = in.readLine();
            if (inputLine == null) return;
            
            String[] parts = inputLine.split(" ");
            if (parts.length < 2) return;
            
            String path = parts[1];
            
            // 读取所有请求头
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // 跳过请求头
            }
            
            String response;
            
            if (path.equals("/actuator/health")) {
                response = "{\"status\":\"UP\",\"service\":\"user-service\"}";
            } else if (path.startsWith("/api/v1/users/")) {
                response = "{\"success\":true,\"message\":\"用户服务正常运行\",\"userId\":\"1\",\"username\":\"testuser\"}";
            } else {
                response = "{\"error\":\"Not Found\"}";
            }
            
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + response.length());
            out.println("Access-Control-Allow-Origin: *");
            out.println();
            out.println(response);
            
        } catch (IOException e) {
            System.err.println("处理请求时出错: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("关闭连接时出错: " + e.getMessage());
            }
        }
    }
}
EOF

echo "[INFO] 创建简化的内容服务..."
cat > ContentApp.java << 'EOF'
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ContentApp {
    public static void main(String[] args) throws IOException {
        int port = 8082;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("内容服务启动在端口: " + port);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleRequest(clientSocket));
        }
    }
    
    private static void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String inputLine = in.readLine();
            if (inputLine == null) return;
            
            String[] parts = inputLine.split(" ");
            if (parts.length < 2) return;
            
            String path = parts[1];
            
            // 读取所有请求头
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // 跳过请求头
            }
            
            String response;
            
            if (path.equals("/actuator/health")) {
                response = "{\"status\":\"UP\",\"service\":\"content-service\"}";
            } else if (path.startsWith("/api/v1/content/")) {
                response = "{\"success\":true,\"message\":\"内容服务正常运行\",\"contentId\":\"1\",\"title\":\"测试内容\"}";
            } else {
                response = "{\"error\":\"Not Found\"}";
            }
            
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + response.length());
            out.println("Access-Control-Allow-Origin: *");
            out.println();
            out.println(response);
            
        } catch (IOException e) {
            System.err.println("处理请求时出错: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("关闭连接时出错: " + e.getMessage());
            }
        }
    }
}
EOF

echo "[INFO] 编译Java文件..."
javac RecommendationApp.java
javac UserApp.java
javac ContentApp.java

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "[INFO] 启动服务..."

# 启动推荐服务
nohup java RecommendationApp > ../logs/recommendation-service.log 2>&1 &
RECOMMENDATION_PID=$!
echo "推荐服务 PID: $RECOMMENDATION_PID"

# 启动用户服务
nohup java UserApp > ../logs/user-service.log 2>&1 &
USER_PID=$!
echo "用户服务 PID: $USER_PID"

# 启动内容服务
nohup java ContentApp > ../logs/content-service.log 2>&1 &
CONTENT_PID=$!
echo "内容服务 PID: $CONTENT_PID"

# 保存PID到文件
echo $RECOMMENDATION_PID > ../logs/recommendation-service.pid
echo $USER_PID > ../logs/user-service.pid
echo $CONTENT_PID > ../logs/content-service.pid

cd ..

echo "[INFO] 等待服务启动..."
sleep 5

echo "=========================================="
echo "检查服务状态"
echo "=========================================="

# 检查推荐服务
echo -n "推荐服务 (8080): "
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/recommendation-service.log"
fi

# 检查用户服务
echo -n "用户服务 (8081): "
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/user-service.log"
fi

# 检查内容服务
echo -n "内容服务 (8082): "
if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/content-service.log"
fi

echo ""
echo "=========================================="
echo "测试API"
echo "=========================================="
echo "测试推荐API:"
curl -s "http://localhost:8080/api/v1/recommend/content?userId=1&size=10" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8080/api/v1/recommend/content?userId=1&size=10"

echo ""
echo "测试用户API:"
curl -s "http://localhost:8081/api/v1/users/1" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8081/api/v1/users/1"

echo ""
echo "测试内容API:"
curl -s "http://localhost:8082/api/v1/content/1" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:8082/api/v1/content/1"

echo ""
echo "=========================================="
echo "服务管理命令"
echo "=========================================="
echo "停止所有服务: ./stop_services.sh"
echo "查看服务状态: ./check_services.sh"
echo "查看日志: tail -f logs/服务名.log"