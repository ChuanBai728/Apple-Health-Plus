<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Next.js-16.2-000000?logo=next.js" alt="Next.js">
  <img src="https://img.shields.io/badge/TypeScript-5.7-3178C6?logo=typescript" alt="TypeScript">
  <img src="https://img.shields.io/badge/Tailwind-4-06B6D4?logo=tailwindcss" alt="Tailwind">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/DeepSeek-AI-4D6BFE" alt="DeepSeek AI">
</p>

# Apple Health+

<p align="center">
  <b>苹果健康数据可视化与 AI 智能分析平台</b>
</p>

<p align="center">
  上传 Apple Health 导出的 <code>export.zip</code>，自动解析百万级健康记录，<br>
  生成多维度可视化报告，通过 DeepSeek AI 获得跨维度健康洞察。
</p>

<p align="center">
  <sub>🔒 数据不上云 · 本地部署 · 隐私优先</sub>
</p>

---

## 功能特性

<table>
<tr>
<td width="50%">

###  一键导入
- 拖拽上传 Apple Health `export.zip`
- 流式解析百万级 XML Record（支持 500MB+ 文件）
- 实时进度追踪 + 处理阶段可视化
- 内置示例数据，无需上传即可体验

###  健康仪表盘
- Bento-Grid 布局概览页
- 40+ 健康指标可视化（日/周/月粒度）
- 三色活动环（Move / Exercise / Stand）
- 7 日趋势迷你图 + 异常告警
- 指标分类折叠 + 热力图日历视图

</td>
<td width="50%">

###  AI 智能分析
- 周报/月报自动生成（含核心指标变化 + 状态分布）
- DeepSeek 驱动多轮健康对话
- RAG 知识库增强（运动医学 + 健康指标参考）
- 健康状态自动分类（高压疲劳 / 高效恢复 / 运动爆发 / 平稳日常）

###  数据隐私
- 所有数据存储在本地 PostgreSQL
- AI 仅接收脱敏后的聚合指标文本
- 原始健康记录不离开服务器
- Docker Compose 一键部署

</td>
</tr>
</table>

---

## 快速开始

### 前置条件

- **JDK 21**+ · **Maven 3.9**+ · **Node.js 22**+
- **Docker Desktop**（用于基础设施容器）

### 启动步骤

```bash
# 1. 启动基础设施（PostgreSQL + RabbitMQ + Redis + MinIO）
docker compose -f docker-compose.dev.yml up -d

# 2. 启动后端服务（3 个终端窗口）

# 终端 1 — REST API (端口 8080)
mvn -pl backend-api spring-boot:run -Dspring-boot.run.profiles=dev

# 终端 2 — 解析 Worker
mvn -pl parse-worker spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0"

# 终端 3 — 聚合 Worker
mvn -pl aggregation-worker spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动前端 (端口 3000)
cd frontend && npm install && npm run dev
```

打开 **http://localhost:3000** 即可使用。

---

## 云端部署

推荐使用 Docker Compose 一键部署到云服务器（1 核 2G 即可运行）。

### 1. 服务器准备

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 克隆项目
git clone https://github.com/ChuanBai728/Apple-Health-Plus.git
cd Apple-Health-Plus
```

### 2. 配置密钥

```bash
# 创建环境变量文件
cat > .env << 'EOF'
DB_PASSWORD=your-secure-password
DEEPSEEK_API_KEY=sk-your-api-key
EOF
```

### 3. 创建 docker-compose.yml

```bash
cat > docker-compose.yml << 'EOF'
services:
  postgres:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: healthplus
      POSTGRES_USER: healthplus
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: [pgdata:/var/lib/postgresql/data]

  rabbitmq:
    image: rabbitmq:3-management
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    restart: unless-stopped

  backend:
    build:
      context: .
      dockerfile: Dockerfile
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_PASSWORD: ${DB_PASSWORD}
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
    depends_on: [postgres, rabbitmq, redis]

  frontend:
    build: frontend/
    restart: unless-stopped
    depends_on: [backend]

  nginx:
    image: nginx:alpine
    restart: unless-stopped
    ports: ["80:80"]
    volumes: ["./nginx.conf:/etc/nginx/nginx.conf:ro"]
    depends_on: [frontend, backend]

volumes: [pgdata]
EOF
```

### 4. 创建 Dockerfile 和 Nginx 配置

```bash
# Spring Boot 多模块 Dockerfile
cat > Dockerfile << 'EOF'
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml settings.xml ./
COPY shared-*/pom.xml ./
RUN for d in shared-*; do mkdir -p $d && cp pom.xml $d/; done; mkdir -p backend-api
COPY backend-api/pom.xml backend-api/
COPY settings.xml /root/.m2/settings.xml
RUN mvn -pl backend-api -am dependency:go-offline -B || true
COPY . .
RUN mvn package -pl backend-api -am -DskipTests -B && mv backend-api/target/backend-api-*.jar app.jar
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
EOF

# Nginx 反向代理
cat > nginx.conf << 'EOF'
events { worker_connections 1024; }
http {
    client_max_body_size 500M;
    server {
        listen 80;
        location /api/ { proxy_pass http://backend:8080; proxy_read_timeout 300s; }
        location / { proxy_pass http://frontend:3000; }
    }
}
EOF

# 前端 Dockerfile
cat > frontend/Dockerfile << 'EOF'
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM node:22-alpine
WORKDIR /app
COPY --from=build /app/.next .next/
COPY --from=build /app/package.json .
COPY --from=build /app/node_modules node_modules/
EXPOSE 3000
CMD ["npx","next","start"]
EOF
```

### 5. Maven 国内镜像（可选，国内服务器加速）

```bash
cat > settings.xml << 'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
```

### 6. 配置生产环境

```bash
cat > backend-api/src/main/resources/application-prod.yml << 'EOF'
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/healthplus
    username: healthplus
    password: ${DB_PASSWORD}
  rabbitmq: { host: rabbitmq }
  data.redis: { host: redis }
  ai.openai:
    api-key: ${DEEPSEEK_API_KEY}
    base-url: https://api.deepseek.com
    chat.options.model: deepseek-chat
EOF
```

### 7. 启动

```bash
docker compose up -d --build
```

部署完成后，打开 `http://<服务器公网IP>` 即可访问。

> **注意**：国内服务器首次构建需要在 Docker 和 Maven 中配置代理或镜像源。若构建失败，参考 [Maven 阿里云镜像](https://developer.aliyun.com/mvn/guide) 和 [Docker 镜像加速](https://www.runoob.com/docker/docker-mirror-acceleration.html)。

---

## 架构

```
┌─────────────────┐     ┌─────────────────────────────────────────┐
│   Next.js 16    │ ──▶ │         Spring Boot 3.4 :8080           │
│   Tailwind v4   │     │   backend-api  ·  REST  ·  Report Gen   │
│   Recharts      │     └────────────┬────────────────────────────┘
│   :3000         │                  │
└─────────────────┘                  ▼
                          ┌──────────────────┐
                          │   PostgreSQL 16   │
                          │  health_records   │
                          │  health_metric_   │
                          │  daily/weekly/    │
                          │  monthly          │
                          └──────────────────┘
                                    ▲
              ┌─────────────────────┼─────────────────────┐
              │                     │                     │
        ┌─────┴──────┐    ┌───────┴───────┐    ┌───────┴───────┐
        │  RabbitMQ  │    │    Redis 7    │    │    MinIO      │
        │  异步解耦   │    │  会话/缓存    │    │  对象存储     │
        └─────┬──────┘    └───────────────┘    └───────────────┘
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
 parse-   aggregation-  AI
 worker    worker     分析
 流式XML   日/周/月   Spring AI
 StAX→COPY  聚合      DeepSeek
```

### 数据处理管道

```
export.zip → parse-worker → health_records (COPY 批量写入)
                           ↓
          aggregation-worker → health_metric_daily (日聚合)
                             → health_metric_weekly (周聚合)
                             → health_metric_monthly (月聚合)
                             → baseline + trend_delta + anomaly_flag
```

---

## 项目结构

```
├── backend-api/           # REST API (8080) — 上传/报告/聊天/AI 对话
├── parse-worker/          # XML 解析 Worker — StAX 流式解析 + PostgreSQL COPY
├── aggregation-worker/    # 聚合 Worker — 日/周/月聚合 + 基线/趋势/异常
├── shared-domain/         # JPA 实体 · DTO · 枚举
├── shared-messaging/      # RabbitMQ 消息契约（ParseJob / AggregateJob）
├── shared-ai/             # AI 知识库 · 意图分类 · 健康参考
├── shared-storage/        # 存储抽象 (LocalFile / MinIO S3)
├── frontend/              # Next.js 16 + Tailwind v4 + Recharts
│   └── src/app/
│       ├── page.tsx                    # 首页 — 上传 + 演示入口
│       ├── uploads/[id]/page.tsx       # 上传进度追踪
│       └── reports/[reportId]/
│           ├── overview/page.tsx        # 仪表盘 (Bento Grid + 活动环)
│           ├── report/page.tsx          # AI 周报/月报
│           ├── chat/page.tsx            # AI 多轮对话
│           ├── metrics/[key]/page.tsx   # 单指标详情 + 图表
│           └── heatmap/page.tsx         # 日历热力图
└── docs/                  # 架构文档 · 部署指南
```

---

## 技术选型

| 层级 | 技术 | 选型理由 |
|------|------|----------|
| **前端框架** | Next.js 16 + Turbopack | App Router 文件路由，HMR 极速 |
| **样式** | Tailwind CSS v4 | CSS `@theme` 配置，Apple HIG 色板定制 |
| **图表** | Recharts | React 原生，轻量，声明式 API |
| **后端框架** | Spring Boot 3.4 | Java 生态标准，JPA + Flyway + Actuator |
| **XML 解析** | Aalto StAX | 拉模式流式解析，2GB 文件不 OOM |
| **消息队列** | RabbitMQ 3 | 异步解耦解析/聚合/AI 三阶段管道 |
| **数据库** | PostgreSQL 16 | JSONB + COPY + 窗口函数（rolling baseline） |
| **缓存** | Redis 7 | AI 对话滑动窗口 + 会话摘要 |
| **存储** | MinIO | S3 兼容，本地部署，API 与 AWS S3 一致 |
| **AI** | DeepSeek + Spring AI | 中文医疗健康场景强，API 兼容 OpenAI格式 |

---

## 端口映射

| 服务 | 开发端口 | 说明 |
|------|---------|------|
| 前端 (Next.js) | `3000` | Web UI |
| backend-api | `8080` | REST API |
| PostgreSQL | `15432` | 数据库（避免与本地 PG 冲突） |
| RabbitMQ | `5672` / `15672` | AMQP / Management UI |
| Redis | `6379` | 缓存 |
| MinIO | `9000` / `9001` | S3 API / Console |

---

## License

仅供个人健康数据管理与分析使用，不构成医疗诊断建议。
