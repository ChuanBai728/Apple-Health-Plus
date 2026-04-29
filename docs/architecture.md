# Apple Health+ 技术文档

## 一、项目概述

Apple Health+ 是一款面向个人用户的 Apple 健康数据智能分析 Web 应用。用户从 iPhone/Apple Watch 导出 `export.zip` 上传后，系统自动完成 XML 解析、多维度聚合、可视化呈现，并通过 DeepSeek AI 提供跨维度健康洞察。

**核心设计理念**：个人数据不上云 — 所有数据存储于本地 PostgreSQL，AI 调用仅发送脱敏后的聚合指标文本，原始健康记录不离开用户环境。

---

## 二、架构总览

```
┌──────────────┐     ┌─────────────────────────────────────┐
│  Next.js 16  │────▶│  Spring Boot 3.4 (backend-api :8080) │
│  (Turbopack) │     └──────────┬──────────────────────────┘
│  :3000       │               │ HTTP REST
└──────────────┘               ▼
                    ┌───────────────────┐
                    │    PostgreSQL 16   │
                    │    (health_records │
                    │     health_metric_ │
                    │     daily/weekly/  │
                    │     monthly)       │
                    └───────────────────┘
                              ▲
┌─────────────┐     ┌────────┴────────┐     ┌──────────────────┐
│   MinIO     │     │   RabbitMQ 3    │     │     Redis 7      │
│ (S3 兼容)   │     │  (异步任务队列)  │     │  (会话缓存/记忆) │
└─────────────┘     └────────┬────────┘     └──────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
        ┌──────────┐  ┌────────────┐  ┌──────────────┐
        │ parse-   │  │aggregation-│  │  AI 分析     │
        │ worker   │  │  worker    │  │ (Spring AI + │
        │ XML→COPY │  │ 日/周/月   │  │  DeepSeek)   │
        └──────────┘  └────────────┘  └──────────────┘
```

---

## 三、前端技术选型

### 3.1 Next.js 16 (Turbopack)

**选型理由**：在 React 生态中，Next.js 16 + Turbopack 是目前开发体验最快的组合。相比 Vite，Next.js 提供更完整的路由系统（App Router）、SSR/SSG 支持和 API Routes。选择 Next.js 而非纯 CSR（Create React App）的原因是：

| 候选方案 | 不选的理由 |
|----------|-----------|
| Vite + React | 纯 CSR，SEO 差，需额外配置路由 |
| Create React App | 已停止维护，构建速度慢 |
| Remix | 生态小于 Next.js，社区资源少 |

**关键技术细节**：
- **App Router**：使用文件系统路由，`[reportId]` 动态参数
- **Turbopack**：Rust 编写的增量编译器，HMR 速度比 Webpack 快 10x
- **API Rewrites**：开发时通过 `next.config.ts` 将 `/api/v1/*` 代理到 Spring Boot，避免前端直接请求后端引发 CORS 问题（唯一例外：文件上传使用直连 XHR 绕过 Turbopack 对 multipart 的 bug）

### 3.2 Tailwind CSS v4

**选型理由**：Tailwind v4 相比 v3 的重大改进是基于 CSS 原生 `@theme` 的配置方式，无需独立 `tailwind.config.js`。相比 Styled Components / CSS Modules：

| 候选方案 | 不选的理由 |
|----------|-----------|
| styled-components | 运行时开销，SSR 配置复杂 |
| CSS Modules | 命名管理繁琐，缺乏设计系统约束 |
| Ant Design | 组件库风格固定，难以定制为 iOS 原生风格 |

**关键技术细节**：
- 使用 `@tailwindcss/postcss` 插件，配置全部通过 CSS `@theme` 完成
- 自定义设计 tokens：Apple HIG 调色板（`#F2F2F7` 背景、`#007AFF` 强调、`#FF3B30` 活动等），替代标准 Tailwind 灰度/蓝色
- 毛玻璃效果：`bg-white/70 backdrop-blur-xl` — 使用 CSS `backdrop-filter` 实现 iOS 风格半透明卡片

### 3.3 Recharts

**选型理由**：相比 ECharts 和 Chart.js：

| 候选方案 | 不选的理由 |
|----------|-----------|
| ECharts | Apache 协议，包体积大（~1MB），React 集成需包装 |
| Chart.js | 功能较少，自定义困难 |
| D3.js | 自由度极高但代码量巨大，不适用于标准折线图场景 |

**关键技术细节**：
- 使用 `<ResponsiveContainer>` 实现响应式尺寸
- 线条颜色根据数据健康方向动态变化（绿色=改善，红色=恶化）
- 配合 `tabular-nums` 等宽数字确保数值对齐

### 3.4 字体与动效

- **字体栈**：`-apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif` — 优先使用 Apple 系统字体，保持与 iOS 原生一致的阅读体验
- **自定义 CSS 动画**：`@keyframes shimmer` 用于上传进度条的流动光影效果
- **过渡**：所有交互状态使用 `transition-all duration-300` 保持流畅

---

## 四、后端架构设计

### 4.1 Spring Boot 3.4 + Maven 多模块

**选型理由**：Spring Boot 3.4 要求 JDK 17+，提供虚拟线程支持（Project Loom）。Maven 多模块架构将不同职责拆分为独立 JAR，实现了编译隔离和依赖管理。

**模块结构**：

| 模块 | 职责 | 端口 | 关键依赖 |
|------|------|------|----------|
| `backend-api` | REST API，文件上传，报告生成 | 8080 | Spring Web, Spring Data JPA |
| `parse-worker` | Apple Health XML 解析 | — | Aalto XML, RabbitMQ |
| `aggregation-worker` | 日/周/月聚合计算 | — | RabbitMQ, PostgreSQL COPY |
| `shared-domain` | JPA Entity、DTO 枚举 | — | Jakarta Persistence |
| `shared-messaging` | RabbitMQ 消息体定义 | — | Jackson |
| `shared-ai` | AI 知识库、意图分类 | — | Spring AI |
| `shared-storage` | 存储抽象层 | — | MinIO SDK |

**为什么不用微服务/Spring Cloud**：项目定位是个人部署使用，不需要服务发现、配置中心等微服务基础设施。多模块 Maven 项目即可获得代码隔离的好处，同时保持单体部署的简单性。

### 4.2 RabbitMQ 3

**选型理由**：相比 Kafka 和 Redis 队列：

| 候选方案 | 不选的理由 |
|----------|-----------|
| Kafka | 偏重大数据流，需要 Zookeeper，部署重，个人场景过度设计 |
| Redis Streams | 持久化和重试机制弱，更适合轻量任务 |
| AWS SQS | 云绑定，不符合本地上云原则 |

**关键技术细节**：
- 使用 `RabbitTemplate.convertAndSend()` 发送消息，Jackson 自动序列化为 JSON
- 配置死信队列（DLQ）+ 重试策略：解析失败的消息进入 DLQ，可手动重试
- 消息体定义在 `shared-messaging` 模块中，生产者和消费者共享同一份消息契约

**消息流**：
```
上传完成 → ParseJobMessage → parse-worker 消费
解析完成 → AggregateJobMessage → aggregation-worker 消费
聚合完成 → AiAnalysisCommand → AI 分析触发
```

### 4.3 PostgreSQL 16

**选型理由**：相比 MySQL 和 SQLite：

| 候选方案 | 不选的理由 |
|----------|-----------|
| MySQL | JSONB 支持弱，COPY 命令性能差，时区处理复杂 |
| SQLite | 不适合多 worker 并发写入场景 |
| MongoDB | 健康数据天然结构化，文档模型不适合时序聚合查询 |

**关键技术细节**：
- **COPY 命令**：PostgreSQL 独有的高性能批量导入，parse-worker 直接将百万级 Record 通过 `CopyManager.copyIn()` 写入，避免逐行 INSERT 的性能开销（实测提升 20-50x）
- **JSONB**：`health_records.raw_payload_json` 使用 JSONB 存储原始 XML 属性，支持按 sourceName 等字段直接查询
- **窗口函数**：`aggregation-worker` 使用 `avg(d.value_avg) OVER (PARTITION BY ... ROWS BETWEEN 30 PRECEDING AND 1 PRECEDING)` 计算 30 天滚动基线，配合 `lag()` 计算 7 天/30 天趋势增量
- **Flyway 迁移**：版本化管理 DDL，从 `V1__init_schema.sql` 到 `V5__health_knowledge.sql`

### 4.4 Redis 7

**选型理由**：用于 AI 对话的会话缓存和滑动窗口记忆管理。

| 候选方案 | 不选的理由 |
|----------|-----------|
| Memcached | 不支持数据结构，无法存储列表/哈希 |
| 直接存 PostgreSQL | 频繁读写对话历史的延迟不可接受 |
| 内存 Map | 服务重启丢失所有会话 |

**关键技术细节**：
- 对话历史使用 List 结构存储，配合 `LTRIM` 维持滑动窗口（最近 20 轮）
- 超出窗口的历史通过 `ConversationMemory` 自动生成摘要注入上下文，平衡 token 消耗与对话连贯性

### 4.5 MinIO

**选型理由**：AWS S3 兼容的本地对象存储。用户上传的 ZIP 文件存储在 MinIO，API 与 S3 完全一致，代码通过 `shared-storage` 模块抽象接口。

| 候选方案 | 不选的理由 |
|----------|-----------|
| 本地文件系统 | 无版本控制，扩容困难 |
| AWS S3 | 云绑定，费用高，个人数据不入云 |
| MinIO | S3 兼容，Docker 一键部署，本地运行 |

**关键技术细节**：
- `StorageService` 接口定义 `store/delete/exists` 方法，`LocalStorageService` 和 `S3StorageService` 分别实现
- Spring Profile 切换：`dev` → 本地文件，`prod` → MinIO S3

---

## 五、数据管道设计

### 5.1 XML 解析 (parse-worker)

**选型理由**：Apple Health 导出的 XML 是单文件超大 XML（典型 100MB-2GB），包含百万级 `<Record>` 元素。DOM 解析会 OOM，必须使用流式解析。

| 候选方案 | 不选的理由 |
|----------|-----------|
| DOM (JDOM/DOM4J) | 全部加载到内存，2GB 文件必 OOM |
| SAX | 基于事件回调，代码结构混乱（callback hell） |
| StAX (Aalto) | 拉模式，代码控制读取节奏，内存占用恒定 |

**关键技术细节**：
- **Aalto XML**：Jackson 团队开发的 StAX 实现，比 JDK 内置的 Woodstox 更快
- **拉模式**：通过 `XMLStreamReader.next()` 逐个读取元素，消耗者通过 `Consumer<ParsedHealthRecord>` 回调函数在流中处理每条记录
- **特殊处理**：
  - 睡眠记录（`HKCategoryTypeIdentifierSleepAnalysis`）的值是分类字符串而非数字，解析器自动计算 `(endDate - startDate)` 转换为 `sleep_duration`（小时）
  - 百分比类指标（体脂率、血氧等）从 0-1 小数自动转换为 0-100 百分比
  - 时间戳支持两种 Apple Health 格式：ISO 8601 和 `2024-01-15 08:30:00 +0800`
- **批量写入**：解析完成后通过 PostgreSQL 的 `COPY` 命令一次性将内存缓冲区中的记录批量写入 `health_records` 表

### 5.2 多粒度聚合 (aggregation-worker)

**关键技术细节**：
- **三级聚合管道**：
  1. `rebuildDailyMetrics()` — 从 `health_records` 按 `(metric_key, date)` 分组聚合 → `health_metric_daily`
  2. `rebuildWeeklyMetrics()` — 按 `(metric_key, week_start)` 聚合 → `health_metric_weekly`
  3. `rebuildMonthlyMetrics()` — 按 `(metric_key, month_start)` 聚合 → `health_metric_monthly`
- **累计指标 vs 均值指标**：累计型指标（步数、活动能量）使用 `sum(value_numeric)` 存为 `value_sum`，均值型指标（心率）使用 `avg(value_numeric)` 存为 `value_avg`
- **基线计算**：`computeBaselinesAndTrends()` 使用 PostgreSQL 窗口函数计算 30 天滚动平均 + 7/30 天 lag 差值，更新至 `baseline_avg_30d / trend_delta_7d / trend_delta_30d` 列
- **异常检测**：`trend_delta_7d` 超过基线 2 个标准差时标记 `anomaly_flag = true`

---

## 六、AI 系统设计

### 6.1 Spring AI + DeepSeek

**选型理由**：相比直接调用 HTTP API：

| 候选方案 | 不选的理由 |
|----------|-----------|
| 直接 HTTP 调用 | 代码冗余，重试/超时需要手动管理 |
| LangChain4j | 功能更丰富但抽象层重，Spring AI 与现有栈集成更自然 |
| OpenAI SDK | API 不兼容 DeepSeek |

**关键技术细节**：
- Spring AI 的 `ChatClient` 通过配置 `spring.ai.openai.base-url` 指向 DeepSeek API，实现 API 兼容（DeepSeek 兼容 OpenAI 格式）
- 使用 DeepSeek 而非 GPT-4 的原因：中文医疗健康场景表现优秀，API 价格仅为 GPT-4 的 1/10

### 6.2 RAG 知识库

**选型理由**：通用大模型缺乏运动医学和健康指标的领域知识，需要通过检索增强生成补充。

**关键技术细节**：
- `health_knowledge` 表存储领域知识条目：`category / title / content / keywords`
- 用户提问时，通过关键词匹配检索 Top-3 相关知识条目，注入 system prompt 作为专业参考
- 知识内容涵盖心率、HRV、睡眠、最大摄氧量、步态等 20+ 指标的健康范围和医学解释

### 6.3 对话记忆系统

**关键技术细节**：
- **滑动窗口**：Redis 中保留最近 20 轮对话
- **自动摘要**：超出窗口的历史对话通过 AI 自动生成为摘要（`ConversationMemory.summarize()`），压缩后注入上下文
- **会话持久化**：对话记录同步写入 PostgreSQL `chat_sessions / chat_messages` 表，支持历史回查

### 6.4 健康状态分类器

**设计理念**：不依赖 AI 的规则引擎，保证分类结果确定性、可解释、零延迟。

**分类规则**：
- **高压疲劳**：静息心率 > 基线 × 1.08 且 HRV < 基线 × 0.85 且 睡眠 < 6h
- **高效恢复**：静息心率 < 基线 × 0.95 且 HRV > 基线 × 1.1 且 睡眠 ≥ 7h
- **运动爆发**：步数 > 10,000 或 活动能量 > 3.0 kcal 且非高压状态
- **平稳日常**：不符合以上任一条件

---

## 七、基础设施

### 7.1 Docker Compose

所有基础设施（PostgreSQL, RabbitMQ, Redis, MinIO）通过 `docker-compose.dev.yml` 一键启动，开发环境完全容器化。

**端口映射**：

| 服务 | 主机端口 | 容器端口 |
|------|---------|---------|
| PostgreSQL | 15432 | 5432 |
| RabbitMQ | 5672 / 15672 | 5672 / 15672 |
| Redis | 6379 | 6379 |
| MinIO | 9000 / 9001 | 9000 / 9001 |

### 7.2 本地存储策略

- `shared-storage` 模块抽象存储接口，通过 `StorageService` 接口解耦
- 开发环境：`LocalStorageService` 写入 `local-storage/` 目录
- 生产环境：`S3StorageService` 通过 MinIO SDK 上传到本地 MinIO
- 使用 Spring Profile (`dev` / `prod`) 切换实现

---

## 八、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 前端框架 | Next.js 16 | App Router + Turbopack HMR + API Rewrites |
| CSS 方案 | Tailwind v4 | 零运行时开销 + 灵活定制 Apple HIG 色板 |
| 国际化 | 简体中文全界面 | 目标用户为中国 Apple Watch 用户 |
| 数据存储 | PostgreSQL 本地 | 健康数据不出用户环境，PG 的 COPY + JSONB + 窗口函数无可替代 |
| 异步解耦 | RabbitMQ | 解析/聚合/AI 分析是天然的三阶段流水线，各自独立扩缩 |
| AI 模型 | DeepSeek | 中文医疗健康场景能力强，性价比高 |
| 流式解析 | StAX (Aalto) | 唯一能处理 2GB 单文件 XML 而不 OOM 的方案 |

---

## 九、部署指南

### 开发环境启动

```powershell
# 1. 基础设施
docker compose -f docker-compose.dev.yml up -d

# 2. 后端 (3 个终端)
mvn -pl backend-api spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl parse-worker spring-boot:run -Dspring-boot.run.profiles=dev
mvn -pl aggregation-worker spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 前端
cd frontend && npm run dev
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | Spring Profile | `dev` |
| `ANTHROPIC_BASE_URL` | AI API 地址 | DeepSeek API |
| `ANTHROPIC_AUTH_TOKEN` | AI API 密钥 | — |
| `ANTHROPIC_MODEL` | AI 模型名 | `deepseek-chat` |
