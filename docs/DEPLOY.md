# Apple Health+ 部署指南

## 前置条件

- **服务器**：Linux (Ubuntu 20.04+ / Debian 11+ / CentOS 8+) , 建议 4GB+ 内存
- **软件**：Docker 24+ + Docker Compose v2
- **域名**：已配置 DNS A 记录指向服务器 IP

## 快速部署（5 步）

### 1. 安装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER  # 免 sudo 运行 docker
newgrp docker
```

### 2. 上传项目

```bash
git clone https://github.com/ChuanBai728/Apple-Health-Plus.git
cd Apple-Health-Plus
```

### 3. 配置环境变量

```bash
cp .env.production .env
nano .env
```

必填项：
```env
DOMAIN=health.your-domain.com    # 你的域名
DB_PASSWORD=your-secure-password  # 数据库密码
DEEPSEEK_API_KEY=sk-your-key     # DeepSeek API Key
CERT_EMAIL=your@email.com        # SSL 证书通知邮箱
```

### 4. 一键部署

```bash
chmod +x deploy.sh
./deploy.sh
```

脚本自动完成：构建镜像 → 启动服务 → 申请 SSL 证书 → 启用 HTTPS。

### 5. 访问

打开 `https://your-domain.com` 即可使用。

---

## 服务架构

```
                    ┌──────────────┐
                    │   Nginx      │  ← 80 (HTTP) / 443 (HTTPS)
                    │  反向代理     │
                    └──┬────────┬──┘
                       │        │
              /api/*   │        │   /*
                       ▼        ▼
               ┌────────┐  ┌──────────┐
               │backend │  │ frontend │
               │ :8080  │  │ :3000    │
               └───┬────┘  └──────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   ┌─────────┐ ┌──────┐ ┌────────┐
   │PostgreSQL│ │Redis │ │RabbitMQ│
   │:5432    │ │:6379 │ │:5672   │
   └─────────┘ └──────┘ └────────┘
        ▲
   ┌────┴────┐  ┌──────────┐
   │ MinIO   │  │parse/agg │
   │ :9000   │  │ workers  │
   └─────────┘  └──────────┘
```

所有内部服务（PostgreSQL, RabbitMQ, Redis, MinIO, workers）仅 Docker 内网可访问，不暴露公网端口。

---

## 常用运维命令

```bash
# 查看所有服务状态
docker compose -f docker-compose.prod.yml ps

# 查看日志
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f --tail=100

# 重启单个服务
docker compose -f docker-compose.prod.yml restart backend

# 更新代码后重新部署
git pull
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d

# 停止所有服务
docker compose -f docker-compose.prod.yml down

# SSL 证书自动续期 (每月执行一次)
docker compose -f docker-compose.prod.yml run --rm certbot renew
docker compose -f docker-compose.prod.yml restart nginx
```

---

## 数据备份

```bash
# PostgreSQL 数据库备份
docker exec healthplus-postgres pg_dump -U healthplus healthplus > backup-$(date +%Y%m%d).sql

# 上传文件备份
tar -czf storage-backup-$(date +%Y%m%d).tar.gz ./storage/

# 恢复数据库
docker exec -i healthplus-postgres psql -U healthplus healthplus < backup-20260429.sql
```

建议设置 crontab 每日自动备份：
```bash
0 3 * * * cd /path/to/Apple-Health-Plus && docker exec healthplus-postgres pg_dump -U healthplus healthplus > backups/$(date +\%Y\%m\%d).sql
```

---

## 升级指南

每次后端 Java 代码或前端 TypeScript 代码变更后：

```bash
git pull
docker compose -f docker-compose.prod.yml build --no-cache
docker compose -f docker-compose.prod.yml up -d
```

仅数据库 migration 变更时，backend-api 的 Flyway 会在启动时自动执行。

---

## 故障排查

| 问题 | 检查 |
|------|------|
| 502 Bad Gateway | `docker compose logs backend` 查看后端是否正常启动 |
| 无法上传大文件 | Nginx `client_max_body_size` 设为 500M，Spring `max-file-size` 设为 200MB |
| AI 分析不可用 | `docker compose logs aggregation-worker` 查看 API Key 是否正确 |
| SSL 证书过期 | `docker compose run --rm certbot renew` |
| 数据库连接失败 | 检查 `.env` 中 `DB_PASSWORD` 是否正确 |
