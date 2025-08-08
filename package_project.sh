#!/bin/bash

echo "📦 打包智能电商推荐平台"
echo "=================================="

# 项目名称和版本
PROJECT_NAME="intelligent-ecommerce-recommendation"
VERSION="1.0.0"
PACKAGE_NAME="${PROJECT_NAME}-${VERSION}"

# 创建打包目录
echo "创建打包目录..."
rm -rf dist
mkdir -p dist/$PACKAGE_NAME

# 复制核心文件
echo "复制项目文件..."

# 复制微服务代码
cp -r simple-services dist/$PACKAGE_NAME/

# 复制前端文件
cp -r web dist/$PACKAGE_NAME/

# 复制配置文件
cp docker-compose.yml dist/$PACKAGE_NAME/
cp redis.conf dist/$PACKAGE_NAME/ 2>/dev/null || true

# 复制脚本文件
cp quick_start.sh dist/$PACKAGE_NAME/
cp stop_all.sh dist/$PACKAGE_NAME/
cp check_status.sh dist/$PACKAGE_NAME/

# 复制文档
cp README_ECOMMERCE.md dist/$PACKAGE_NAME/README.md
cp PROJECT_STARTUP_GUIDE.md dist/$PACKAGE_NAME/ 2>/dev/null || true

# 创建目录结构
mkdir -p dist/$PACKAGE_NAME/logs

# 设置脚本执行权限
chmod +x dist/$PACKAGE_NAME/*.sh

# 清理不需要的文件
echo "清理临时文件..."
find dist/$PACKAGE_NAME -name "target" -type d -exec rm -rf {} + 2>/dev/null || true
find dist/$PACKAGE_NAME -name "*.log" -delete 2>/dev/null || true
find dist/$PACKAGE_NAME -name "*.pid" -delete 2>/dev/null || true
find dist/$PACKAGE_NAME -name ".DS_Store" -delete 2>/dev/null || true

# 创建安装说明
cat > dist/$PACKAGE_NAME/INSTALL.md << 'EOF'
# 安装指南

## 快速安装

1. **解压项目文件**
```bash
unzip intelligent-ecommerce-recommendation-1.0.0.zip
cd intelligent-ecommerce-recommendation-1.0.0
```

2. **运行快速启动脚本**
```bash
chmod +x quick_start.sh
./quick_start.sh
```

3. **访问系统**
```
http://localhost:3000/ecommerce.html
```

## 环境要求

- Java 11+
- Maven 3.6+
- Python 3.8+
- 8GB+ 内存
- 2GB+ 磁盘空间

## 端口使用

- 8080: 推荐服务
- 8081: 用户服务  
- 8082: 商品服务
- 3000: 前端服务

## 管理命令

- 启动系统: `./quick_start.sh`
- 停止系统: `./stop_all.sh`
- 检查状态: `./check_status.sh`

## 故障排除

如遇问题，请查看日志文件：
- `logs/recommendation.log`
- `logs/products.log`
- `logs/users.log`
- `logs/web-server.log`
EOF

# 创建版本信息
cat > dist/$PACKAGE_NAME/VERSION << EOF
项目名称: 智能电商推荐平台
版本号: $VERSION
构建时间: $(date '+%Y-%m-%d %H:%M:%S')
构建环境: $(uname -s) $(uname -m)
Java版本: $(java -version 2>&1 | head -1)
Maven版本: $(mvn -version 2>&1 | head -1)
EOF

# 创建压缩包
echo "创建压缩包..."
cd dist
tar -czf ${PACKAGE_NAME}.tar.gz $PACKAGE_NAME
zip -r ${PACKAGE_NAME}.zip $PACKAGE_NAME > /dev/null 2>&1

# 计算文件大小
TAR_SIZE=$(du -h ${PACKAGE_NAME}.tar.gz | cut -f1)
ZIP_SIZE=$(du -h ${PACKAGE_NAME}.zip | cut -f1)

echo ""
echo "✅ 打包完成！"
echo "=================================="
echo "📁 打包目录: dist/$PACKAGE_NAME"
echo "📦 压缩文件:"
echo "  - ${PACKAGE_NAME}.tar.gz ($TAR_SIZE)"
echo "  - ${PACKAGE_NAME}.zip ($ZIP_SIZE)"
echo ""
echo "📋 包含内容:"
echo "  ✓ 微服务源码 (Java + Maven)"
echo "  ✓ 前端界面 (HTML + CSS + JS)"
echo "  ✓ 启动脚本 (一键启动)"
echo "  ✓ 管理工具 (状态检查、停止服务)"
echo "  ✓ 完整文档 (README + 安装指南)"
echo ""
echo "🚀 上传建议:"
echo "  - GitHub: 上传整个项目目录"
echo "  - 网盘: 使用 ${PACKAGE_NAME}.zip"
echo "  - 服务器: 使用 ${PACKAGE_NAME}.tar.gz"
echo ""
echo "📝 上传后用户只需:"
echo "  1. 解压文件"
echo "  2. 运行 ./quick_start.sh"
echo "  3. 访问 http://localhost:3000/ecommerce.html"

cd ..