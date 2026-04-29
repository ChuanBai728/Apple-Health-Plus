<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.5-6DB33F?logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Spring%20Security-JWT-6DB33F?logo=springsecurity" alt="Spring Security">
  <img src="https://img.shields.io/badge/Next.js-16.2-000000?logo=next.js" alt="Next.js">
  <img src="https://img.shields.io/badge/TypeScript-5.7-3178C6?logo=typescript" alt="TypeScript">
  <img src="https://img.shields.io/badge/Tailwind-4-06B6D4?logo=tailwindcss" alt="Tailwind">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis" alt="Redis">
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
- **虚拟线程 + 环形缓冲区** 高并发解析引擎（Producer-Consumer 模型）
- PostgreSQL COPY 协议批量写入（10K 条/批次，动态触发）
- 实时进度追踪 + 内置示例数据

###  健康仪表盘
- Apple HIG 风格三栏布局（报告 | 仪表盘 | AI 对话）
- 40+ 健康指标可视化（日/周/月粒度）
- 三色活动环 + 趋势迷你图 + 异常告警
- 2.5σ 异常检测 + 基线偏离说明

</td>
<td width="50%">

###  AI 智能分析
- 周报/月报自动生成 + 数据高亮 + 关键发现
- DeepSeek 多轮健康对话 + RAG 知识库增强
- 健康状态自动分类（高压疲劳 / 高效恢复 / 运动爆发 / 平稳日常）
- Redis 缓存加速（24h 报告 / 10min 指标）

###  安全与隐私
- **JWT 无状态认证** + Spring Security RBAC 权限控制
- 所有数据存储在本地 PostgreSQL
- AI 仅接收脱敏后的聚合指标文本
- BCrypt 密码加密 + Token 过期自动处理

</td>
</tr>
</table>

---

## 快速开始

### 前置条件

- **JDK 21**+ · **Maven 3.9**+ · **Node.js 22**+
- **Docker Desktop**（用于基础设施容器）

### 启动步骤

**一键启动（推荐）：**

```powershell
# Windows PowerShell
.\start.ps1
```

自动拉起 Docker 基础设施 → 3 个后端服务 → 前端 → 打开浏览器。

**手动启动：**

```bash
# 1. 基础设施
docker compose -f docker-compose.dev.yml up -d

# 2. 后端（3 个终端）
mvn -pl backend-api spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl parse-worker spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0"
mvn -pl aggregation-worker spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 前端
cd frontend && npm install && npm run dev
```

打开 **http://localhost:3000** 即可使用。

---

## 架构

```
┌─────────────────┐     ┌──────────────────────────────────────────┐
│   Next.js 16    │ ──▶ │  JWT Filter → Spring Security → REST API │
│   Tailwind v4   │     │         Spring Boot 3.4 :8080            │
│   Recharts      │     │   backend-api · Report · Chat · Auth     │
│   :3000         │     └────────────┬─────────────────────────────┘
└─────────────────┘                  │
                          ┌──────────┼──────────┐
                          ▼          ▼          ▼
                    ┌──────────┐ ┌───────┐ ┌──────────┐
                    │PostgreSQL│ │Redis 7│ │ RabbitMQ │
                    │   16     │ │缓存/  │ │  异步    │
                    │ 健康数据 │ │会话   │ │  解耦    │
                    └──────────┘ └───────┘ └────┬─────┘
                          ▲                     │
              ┌───────────┴───────────┐  ┌──────┴──────┐
              │  aggregation-worker   │  │ parse-worker│
              │  日/周/月聚合 · 基线  │  │ Virtual     │
              │  趋势 · 异常检测      │  │ Threads     │
              └───────────────────────┘  │ RingBuffer  │
                                         │ COPY 批量   │
                                         └─────────────┘
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
├── backend-api/
│   ├── controller/        # REST 端点（Reports / Uploads / Chat / Auth）
│   ├── security/          # JWT Token Provider · Auth Filter · SecurityConfig
│   ├── service/           # 业务逻辑（Report · Auth · Chat · Upload）
│   ├── config/            # CacheConfig (Redis TTL) · Cors · ExceptionHandler
│   └── repository/        # JPA Repository（User · Upload · Chat）
├── parse-worker/
│   └── core/              # StAX Producer · VirtualThreadConsumer
│                          # RecordRingBuffer · PgCopyBatchInserter
│                          # HighThroughputParsePipeline
├── aggregation-worker/    # 聚合 Worker — 日/周/月聚合 + 基线/趋势/异常
├── shared-domain/         # JPA 实体（User · Upload · Chat）· DTO · 枚举
├── shared-messaging/      # RabbitMQ 消息契约
├── shared-ai/             # AI 知识库 · 意图分类
├── shared-storage/        # 存储抽象 (LocalFile / MinIO S3)
├── frontend/              # Next.js 16 + Tailwind v4 + Recharts
│   └── src/app/
│       ├── page.tsx                    # 首页 — 双列布局上传
│       ├── uploads/[id]/page.tsx       # 上传进度追踪
│       └── reports/[reportId]/
│           ├── overview/page.tsx        # 三栏仪表盘 + 活动环 + 侧边面板
│           ├── report/page.tsx          # AI 周报/月报
│           ├── chat/page.tsx            # AI 多轮对话
│           ├── metrics/[key]/page.tsx   # 单指标详情 + 异常说明
│           └── heatmap/page.tsx         # 日历热力图
└── docs/                  # 架构文档 · 部署指南
```

---

## 技术选型

| 层级 | 技术 | 选型理由 |
|------|------|----------|
| **前端框架** | Next.js 16 + Turbopack | App Router 文件路由，HMR 极速 |
| **样式** | Tailwind CSS v4 | Apple HIG 色板 + Slate 色系 + 毛玻璃 |
| **图表** | Recharts | React 原生，轻量，声明式 API |
| **后端框架** | Spring Boot 3.4 | JPA + Flyway + Actuator，Java 21 虚拟线程 |
| **安全认证** | Spring Security + JJWT | JWT 无状态认证 · BCrypt 密码 · RBAC |
| **XML 解析** | Aalto StAX + Virtual Threads | Producer-Consumer 环形缓冲区，2GB 不 OOM |
| **消息队列** | RabbitMQ 3 | 异步解耦解析/聚合/AI 三阶段管道 |
| **数据库** | PostgreSQL 16 | JSONB + COPY + 窗口函数（rolling baseline） |
| **缓存** | Redis 7 + Spring Cache | 24h 报告 / 10min 指标 / 防穿透/击穿 |
| **存储** | MinIO | S3 兼容，本地部署 |
| **AI** | DeepSeek + Spring AI | 中文医疗健康场景强，API 兼容 OpenAI |

---

## License

仅供个人健康数据管理与分析使用，不构成医疗诊断建议。
