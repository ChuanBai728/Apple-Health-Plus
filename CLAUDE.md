请始终使用简体中文与我对话，并在回答时保持专业、简洁。

## Apple Health+ 项目概览

Apple Health+ 是一款面向个人用户的 Apple 健康数据智能分析 Web 应用。
用户上传从 iPhone/Apple Watch 导出的 `export.zip`，系统自动解析、聚合、可视化，
并通过 AI 对话提供跨维度健康洞察。

### 技术栈

- **前端**: Next.js 16 (Turbopack) + TypeScript + Tailwind CSS + Recharts
- **后端**: Spring Boot 3.4.5 + Maven (多模块)
- **数据库**: PostgreSQL 16 (端口 15432)
- **消息队列**: RabbitMQ 3
- **缓存**: Redis 7
- **AI**: Spring AI + DeepSeek (RAG 增强)
- **存储**: MinIO (本地 S3 兼容) / LocalStorageService

### 模块结构

```
├── backend-api/         # REST API (8080) — 上传/报告/聊天
├── parse-worker/        # XML 解析服务 — Zip→StAX→COPY
├── aggregation-worker/  # 聚合服务 — 日/周/月指标计算
├── shared-domain/       # 实体/DTO/枚举
├── shared-messaging/    # RabbitMQ 消息体
├── shared-ai/           # AI 知识库/意图分类
├── shared-storage/      # 存储抽象 (Local/S3)
├── frontend/            # Next.js Web UI
└── docs/                # 开发文档
```

### 启动命令

```powershell
# 1. Docker 基础设施
docker compose -f docker-compose.dev.yml up -d

# 2. 后端 (3 个终端)
mvn -pl backend-api spring-boot:run "-Dspring-boot.run.profiles=dev"
mvn -pl parse-worker spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.jvmArguments=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0"
mvn -pl aggregation-worker spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3. 前端
cd frontend && npm run dev
```

- 前端: http://localhost:3000
- 后端: http://localhost:8080
- RabbitMQ: http://localhost:15672 (guest/guest)

### 关键端口

| 服务 | 端口 |
|---|---|
| backend-api | 8080 |
| 前端 | 3000 |
| PostgreSQL | 15432 |
| RabbitMQ | 5672 / 15672 |
| Redis | 6379 |
| MinIO | 9000 / 9001 |
