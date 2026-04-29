# Apple Health+ 开发文档

## 目录
1. [项目概览](#1-项目概览)
2. [快速启动](#2-快速启动)
3. [架构设计](#3-架构设计)
4. [数据库设计](#4-数据库设计)
5. [API 接口](#5-api-接口)
6. [前端页面](#6-前端页面)

---

## 1. 项目概览

Apple Health+ 是一款面向个人用户的 Apple 健康数据智能分析 Web 应用。
用户上传从 iPhone/Apple Watch 导出的 `export.zip`，系统自动解析、聚合、可视化，
并通过 AI 对话提供跨维度健康洞察。

### 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Next.js 16 + TypeScript + Tailwind CSS + Recharts |
| 后端 | Spring Boot 3.4 + Maven 多模块 |
| 数据库 | PostgreSQL 16 (端口 15432) |
| 消息队列 | RabbitMQ 3 |
| 缓存 | Redis 7 |
| AI | Spring AI + DeepSeek (RAG 知识库) |
| 存储 | MinIO (本地 S3 兼容) / LocalStorage |

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

---

## 2. 快速启动

### 依赖
- Java 21+ / Maven 3.9+ / Docker Desktop

### 启动步骤

```powershell
# 1. Docker 基础设施 (PostgreSQL + RabbitMQ + Redis + MinIO)
docker compose -f docker-compose.dev.yml up -d

# 2. 后端 (3 个终端)
mvn -pl backend-api spring-boot:run "-Dspring-boot.run.profiles=dev"
mvn -pl parse-worker spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.jvmArguments=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0"
mvn -pl aggregation-worker spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3. 前端
cd frontend && npm install && npm run dev
```

- 前端: http://localhost:3000
- 后端: http://localhost:8080
- RabbitMQ 管理: http://localhost:15672 (guest/guest)

### 关键端口

| 服务 | 端口 |
|---|---|
| backend-api | 8080 |
| 前端 | 3000 |
| PostgreSQL | 15432 |
| RabbitMQ | 5672 / 15672 |
| Redis | 6379 |
| MinIO | 9000 / 9001 |

---

## 3. 架构设计

### 数据流转

```
用户上传 export.zip
  → UploadService 保存到 Storage
  → RabbitMQ: ParseJobMessage
  → parse-worker: ZipInputStream → Aalto StAX → CSV流 → PostgreSQL COPY
  → RabbitMQ: AggregateJobMessage
  → aggregation-worker: 日/周/月聚合 + 基线/趋势/异常
  → 状态: READY → 前端可查阅
```

### AI 调用链路

```
ChatController → ChatService → DeepSeek API (Spring AI)
  ├── ConversationMemory (Redis 滑动窗口 + 摘要)
  ├── HealthKnowledgeBase (PostgreSQL RAG 知识检索)
  └── 健康数据上下文 (sleep↔HRV, activity↔RHR 交叉分析)
```

### 性能数据

| 指标 | 数值 |
|---|---|
| 980MB XML 解析 | ~65s (Aalto + COPY) |
| 2,151,079 条记录入库 | ~29s (卸索引 COPY) |
| 日/周/月聚合 | ~10s |

---

## 4. 数据库设计

### 核心表

| 表 | 说明 |
|---|---|
| `uploads` | 上传任务信息 |
| `parse_jobs` | 解析任务追踪 |
| `health_records` | 原始解析记录 (200万+条/次) |
| `health_metric_daily` | 每日聚合指标 |
| `health_metric_weekly` | 每周聚合指标 |
| `health_metric_monthly` | 每月聚合指标 |
| `health_report_summaries` | AI 自动健康总结 |
| `chat_sessions` | 聊天会话 |
| `chat_messages` | 聊天消息 |
| `health_knowledge` | AI 知识库 (RAG) |

### health_records 表结构

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigserial PK | |
| user_id | uuid | |
| upload_id | uuid → uploads | |
| record_type | text | record / workout |
| metric_key | text | 如 heart_rate, step_count |
| category_key | text | 如 heart_cardio, daily_activity |
| source_name | text | Apple Watch / iPhone |
| value_numeric | double | |
| value_text | text | |
| unit | text | bpm, kcal, kg |
| start_at / end_at | timestamptz | |
| raw_payload_json | text | |

---

## 5. API 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/uploads` | 上传 ZIP (multipart) |
| POST | `/api/v1/uploads/local` | 本地路径上传 |
| POST | `/api/v1/uploads/{id}/complete` | 完成上传，触发解析 |
| GET | `/api/v1/uploads/{id}` | 轮询上传状态 |
| DELETE | `/api/v1/uploads/{id}` | 删除数据 |
| GET | `/api/v1/reports/{id}/overview` | 健康概览 |
| GET | `/api/v1/reports/{id}/metrics/{key}?granularity=DAILY\|WEEKLY\|MONTHLY` | 指标详情 |
| GET | `/api/v1/reports/{id}/insight?type=weekly\|monthly` | AI 周报/月报 |
| GET | `/api/v1/chat/sessions` | 列出会话 |
| GET | `/api/v1/chat/sessions/{id}` | 获取会话历史 |
| POST | `/api/v1/chat/sessions/{id}/messages` | 发送消息 |

### 状态流转

`uploaded` → `queued` → `parsing` → `parsed` → `aggregating` → `ready` → (或 `failed`)

---

## 6. 前端页面

| 路由 | 功能 |
|---|---|
| `/` | 首页 — 拖拽/本地路径上传 |
| `/uploads/[id]` | 进度页 — 6阶段步骤+实时计时 |
| `/reports/[id]/overview` | 仪表盘 — Bento Grid 卡片布局 + 异常预警 |
| `/reports/[id]/metrics/[key]` | 指标详情 — 趋势图+日/周/月切换+健康参考 |
| `/reports/[id]/chat` | AI 对话 — RAG 知识库增强 + 滑动窗口记忆 |
| `/reports/[id]/report` | AI 报告 — 周报/月报卡片滑动 |
| `/reports/[id]/heatmap` | 热力图 — 日历活动量视图 |
