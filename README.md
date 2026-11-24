# HealthPlus · 身体年报

把健康数据导出变成一份可分享的身体年报，仿 podhours.app 的深色玻璃拟态风格。

**纯前端**：Vite + Vue 3 + TypeScript，文件只在浏览器本地解析，全程无网络请求、不上传、不留存。

## 快速开始

```bash
cd web
npm install
npm run dev     # http://localhost:5173
```

## 功能

- 浏览器内流式解析 1 GB+ 健康导出（Web Worker + fflate，峰值内存几十 MB，可取消）
- 五张报告卡片：年度人格 / 小睡档案 / 训练生涯 / 向上纪录 / 里程与圆环
- 时间段切换（全部 / 各年份）、确定性文案引擎、6 套随机皮肤
- 9:16 与 1:1 分享图，Canvas 本地生成
- 无导出文件时的示例模式

## 测试

```bash
cd web
npm test        # 92 个单元测试（合成数据）
npm run build   # 类型检查 + 生产构建
```

详细说明见 [web/README.md](web/README.md)。

> 仓库中不包含任何真实健康数据。Apple 健康是 Apple Inc. 的商标。
