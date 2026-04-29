# Apple Health+ 一键启动脚本 (Windows PowerShell)
Write-Host "=== Apple Health+ ===" -ForegroundColor Blue

# 1. Docker 基础设施
Write-Host "[1/4] 启动 Docker 基础设施..." -ForegroundColor Yellow
docker compose -f docker-compose.dev.yml up -d 2>&1 | Out-Null

# 2. 后端服务
Write-Host "[2/4] 启动后端服务..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; mvn -pl backend-api spring-boot:run '-Dspring-boot.run.profiles=dev'" -WindowStyle Minimized
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; mvn -pl parse-worker spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.jvmArguments=-Djdk.xml.maxGeneralEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0'" -WindowStyle Minimized
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD'; mvn -pl aggregation-worker spring-boot:run '-Dspring-boot.run.profiles=dev'" -WindowStyle Minimized

# 3. 前端
Write-Host "[3/4] 启动前端..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PWD/frontend'; npm run dev" -WindowStyle Minimized

# 4. 等待就绪
Write-Host "[4/4] 等待服务就绪..." -ForegroundColor Yellow
do {
    Start-Sleep 3
    try { $ready = (Invoke-WebRequest -Uri http://localhost:3000 -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { $ready = $false }
} until ($ready)

Write-Host "=== 启动完成！http://localhost:3000 ===" -ForegroundColor Green
Start-Process http://localhost:3000
