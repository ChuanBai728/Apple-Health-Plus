#!/bin/bash
set -e

# ============================================
# Apple Health+ 一键部署脚本
# ============================================

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${RED}[!]${NC} $1"; }

# ── 1. 检查依赖 ──
log "检查 Docker..."
docker --version >/dev/null 2>&1 || { warn "请先安装 Docker"; exit 1; }
docker compose version >/dev/null 2>&1 || { warn "请安装 Docker Compose v2"; exit 1; }

# ── 2. 检查环境变量 ──
if [ ! -f .env ]; then
    warn "未找到 .env 文件，从 .env.production 模板创建..."
    cp .env.production .env
    warn "请编辑 .env 填入你的域名和密钥后重新运行此脚本"
    exit 1
fi
source .env
log "域名: ${DOMAIN}"

# ── 3. 目录准备 ──
mkdir -p nginx/certbot/www nginx/certbot/conf
chmod +x deploy.sh

# ── 4. 构建镜像 ──
log "构建 Docker 镜像（首次需要 5-10 分钟）..."
docker compose -f docker-compose.prod.yml build --parallel

# ── 5. 启动服务 ──
log "启动所有服务..."
docker compose -f docker-compose.prod.yml up -d
sleep 5
log "服务状态："
docker compose -f docker-compose.prod.yml ps

# ── 6. 申请 SSL 证书 ──
log "申请 Let's Encrypt SSL 证书..."
docker compose -f docker-compose.prod.yml run --rm certbot \
    certonly --webroot \
    --webroot-path=/var/www/certbot \
    --email "${CERT_EMAIL}" \
    --agree-tos \
    --no-eff-email \
    -d "${DOMAIN}" || warn "证书申请失败，请检查域名 DNS 是否指向本服务器"

# ── 7. 启用 HTTPS ──
if [ -f "nginx/certbot/conf/live/${DOMAIN}/fullchain.pem" ]; then
    log "SSL 证书已生成，启用 HTTPS..."

    # 复制 HTTPS nginx 配置
    cat > nginx/nginx-https.conf << 'NGINX_EOF'
events { worker_connections 1024; }
http {
    include /etc/nginx/mime.types;
    default_type application/json;
    access_log /var/log/nginx/access.log;
    error_log /var/log/nginx/error.log;
    client_max_body_size 500M;
    gzip on;
    gzip_types text/plain application/json text/css application/javascript;

    # HTTP → HTTPS 重定向
    server {
        listen 80;
        server_name ${DOMAIN};
        location /.well-known/acme-challenge/ { root /var/www/certbot; }
        location / { return 301 https://$host$request_uri; }
    }

    # HTTPS
    server {
        listen 443 ssl;
        server_name ${DOMAIN};
        ssl_certificate /etc/letsencrypt/live/${DOMAIN}/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/${DOMAIN}/privkey.pem;

        location /api/ {
            proxy_pass http://backend:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_read_timeout 300s;
            proxy_send_timeout 300s;
        }
        location / {
            proxy_pass http://frontend:3000;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
NGINX_EOF

    docker compose -f docker-compose.prod.yml down
    docker compose -f docker-compose.prod.yml up -d
    log "部署完成！访问 https://${DOMAIN}"
else
    log "SSL 证书暂未生成，当前通过 HTTP 访问: http://${DOMAIN}"
    log "手动申请证书后重新部署即可启用 HTTPS"
fi

log "查看日志: docker compose -f docker-compose.prod.yml logs -f"
