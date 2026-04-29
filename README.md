# Apple Health+

上传 Apple Health 导出数据，获取可视化健康洞察与 AI 深度分析。

## 技术栈

- **前端**: Next.js 16 + TypeScript + Tailwind CSS + Recharts
- **后端**: Spring Boot 3.4 + Maven (多模块)
- **数据库**: PostgreSQL 16
- **消息队列**: RabbitMQ 3
- **缓存**: Redis 7
- **AI**: Spring AI + DeepSeek (RAG 知识库增强)

## 快速启动

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
- 后端 API: http://localhost:8080

## 项目结构

```
├── backend-api/         # REST API 服务 (8080) — 上传/报告/聊天
├── parse-worker/        # XML 解析服务 — Zip→StAX→PostgreSQL COPY
├── aggregation-worker/  # 聚合服务 — 日/周/月指标 + 基线/趋势/异常
├── shared-domain/       # 实体/DTO/枚举
├── shared-messaging/    # RabbitMQ 消息体
├── shared-ai/           # AI 知识库 + 意图分类
├── shared-storage/      # 存储抽象层 (Local/S3)
├── frontend/            # Next.js Web 前端
└── docs/                # 开发文档
```

## 文档

- [后端本地运行说明](docs/Backend_Dev_Runbook_CN.md)
- [产品需求文档 (PRD)](docs/PRD_Web_App_CN.md)
- [技术方案设计](docs/Technical_Design_Web_App_CN.md)
