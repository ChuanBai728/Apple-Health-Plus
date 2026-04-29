#!/bin/bash
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${RED}[!]${NC} $1"; }

# ── 1. 检查依赖 ──
log "检查 Docker..."
docker --version >/dev/null 2>&1 || { warn "请先安装 Docker"; exit 1; }
docker compose version >/dev/null 2>&1 || { warn "请安装 Docker Compose v2"; exit 1; }

# ── 2. 检查环境变量 ──
if [ ! -f .env ]; then
    cp .env.production .env
    warn ".env 已创建，请编辑填入 DB_PASSWORD 和 DEEPSEEK_API_KEY 后重新运行"
    exit 1
fi

# ── 3. 构建镜像 ──
log "构建 Docker 镜像..."
docker compose -f docker-compose.prod.yml build --parallel

# ── 4. 启动服务 ──
log "启动所有服务..."
docker compose -f docker-compose.prod.yml up -d

sleep 3
log "服务状态："
docker compose -f docker-compose.prod.yml ps

# ── 5. 获取服务器 IP ──
SERVER_IP=$(curl -s ifconfig.me 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')
log "部署完成！"
log "访问地址: http://${SERVER_IP}"
log ""
log "常用命令："
log "  查看日志: docker compose -f docker-compose.prod.yml logs -f"
log "  重启服务: docker compose -f docker-compose.prod.yml restart"
log "  停止服务: docker compose -f docker-compose.prod.yml down"
