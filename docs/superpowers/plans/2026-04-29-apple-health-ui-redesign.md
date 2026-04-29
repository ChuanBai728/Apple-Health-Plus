# Apple Health+ UI 重设计 — 实施计划

> **For agentic workers:** 逐任务执行，每步使用 checkbox (`- [ ]`) 追踪。此计划仅涉及前端 Tailwind CSS 类名替换，无后端变更。

**Goal:** 将前端 7 个页面 + 全局样式从传统灰白卡片风格重写为 iOS 原生浅色毛玻璃风格

**Architecture:** 纯 CSS 层面替换 — Tailwind 类名更新 + globals.css 全局变量。不改变组件结构、逻辑、API 调用

**Tech Stack:** Next.js 16 + TypeScript + Tailwind CSS v4

---

## 设计 Tokens 速查

```
背景:  bg-[#F2F2F7]
卡片:  bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl shadow-[0_2px_12px_rgba(0,0,0,0.03)]
Hero:  bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl
标题:  text-[#1C1C1E]
辅助:  text-[#8E8E93]
正文:  text-[#3A3A3C]
强调:  text-[#FF3B30] / #FF9500 / #34C759 / #5856D6 / #007AFF
按钮:  bg-[#007AFF] text-white rounded-full font-semibold
```

---

### Task 1: 全局样式 + Layout

**Files:**
- Modify: `frontend/src/app/globals.css`
- Modify: `frontend/src/app/layout.tsx`

- [ ] **Step 1: 更新 globals.css — 添加 Apple 字体 + 背景色**

Tailwind v4 使用 CSS `@theme` 自定义。在现有文件基础上，确保背景和字体生效。当前文件已经很简洁，无需大改，只需确认 `@import "tailwindcss"` 存在且字体栈正确。

实际修改：在 `globals.css` 顶部追加字体栈声明（Tailwind v4 方式）：
```css
@import "tailwindcss";

/* Apple system font stack */
html {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
```

- [ ] **Step 2: 更新 layout.tsx — 毛玻璃顶部导航栏 + 浅灰背景**

将 `body` 背景从 `bg-gray-50` 改为 `bg-[#F2F2F7]`，Header 从纯白 `bg-white border-b` 改为毛玻璃风格。

```tsx
// layout.tsx
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" className="h-full">
      <body className="min-h-full bg-[#F2F2F7] text-[#1C1C1E] flex flex-col antialiased">
        <Providers>
          <header className="sticky top-0 z-50 bg-white/70 backdrop-blur-xl border-b border-black/5">
            <div className="max-w-6xl mx-auto px-4 h-12 flex items-center">
              <a href="/" className="text-lg font-bold tracking-tight text-[#007AFF]">
                Apple Health+
              </a>
            </div>
          </header>
          <main className="flex-1 max-w-[960px] mx-auto w-full px-4 py-6">
            {children}
          </main>
        </Providers>
      </body>
    </html>
  );
}
```

- [ ] **Step 3: 验证** — 启动前端 `cd frontend && npm run dev`，打开 `http://localhost:3000`，确认背景为浅灰、顶部导航有毛玻璃效果

---

### Task 2: 首页

**Files:**
- Modify: `frontend/src/app/page.tsx`

全局替换规则：
- `bg-gray-50` → `bg-[#F2F2F7]`（页面背景已在 layout 处理）
- `bg-white border-gray-200` 卡片 → `bg-white/70 backdrop-blur-xl border-black/5 rounded-2xl`
- `text-gray-900` → `text-[#1C1C1E]`
- `text-gray-500/400` → `text-[#8E8E93]`
- `bg-blue-600` → `bg-[#007AFF]`
- `border-blue-*` → `border-[#007AFF]/20`
- `rounded-xl` → `rounded-2xl`
- `bg-gradient-to-r from-blue-50 to-indigo-50` → `bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF]`
- `bg-gradient-to-r from-blue-600 to-indigo-600` → `bg-[#007AFF]`（统一按钮色）

- [ ] **Step 1: 更新 Hero 区域**（约第 75-88 行）

```tsx
{/* Hero */}
<div className="text-center space-y-4 pt-8 pb-6">
  <div className="inline-flex items-center gap-2 bg-[#007AFF]/8 text-[#007AFF] rounded-full px-3 py-1 text-sm font-medium">
    <span className="w-1.5 h-1.5 bg-[#007AFF] rounded-full animate-pulse" />
    基于 DeepSeek AI 深度分析
  </div>
  <h1 className="text-4xl font-extrabold tracking-tight text-[#1C1C1E] leading-tight">
    Apple Health+
  </h1>
  <p className="text-lg text-[#8E8E93] max-w-xl mx-auto leading-relaxed">
    苹果健康数据可视化与 AI 洞察
  </p>
</div>
```

- [ ] **Step 2: 更新导出指南卡片**（约第 93-115 行）

```tsx
<div className="max-w-lg mx-auto bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-2xl p-6 border border-black/5">
  {/* 步骤圆圈: bg-blue-100 → bg-[#007AFF]/10, text-blue-600 → text-[#007AFF] */}
  <div className="w-8 h-8 bg-[#007AFF]/10 rounded-lg flex items-center justify-center text-[#007AFF] text-sm font-bold">
    {i + 1}
  </div>
</div>
```

- [ ] **Step 3: 更新上传区**（约第 118-195 行）

```tsx
{/* 闲置状态 */}
<div className={`max-w-lg mx-auto rounded-2xl p-8 text-center transition-all duration-300 ${
  dragOver
    ? 'bg-[#007AFF]/5 border-2 border-[#007AFF]/30 shadow-lg'
    : 'bg-white/70 backdrop-blur-xl border-2 border-dashed border-black/10 hover:border-black/20 hover:shadow-md'
}`}>
  {/* 按钮: bg-blue-600 → bg-[#007AFF] */}
  <button className="px-8 py-3 bg-[#007AFF] text-white rounded-full hover:bg-[#0077EE] shadow-lg shadow-[#007AFF]/20 font-semibold">
    选择文件
  </button>
</div>

{/* 文件已选中 */}
<div className="bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] border-2 border-[#007AFF]/20 rounded-2xl p-8 text-center shadow-lg">
  {/* 开始分析: bg-gradient → bg-[#007AFF] */}
  <button className="px-8 py-3 bg-[#007AFF] text-white rounded-full font-semibold hover:bg-[#0077EE] shadow-xl shadow-[#007AFF]/20">
    开始分析
  </button>
</div>

{/* 上传中 */}
<div className="bg-gradient-to-br from-[#F8F8FC] to-[#EEF0FF] border-2 border-[#007AFF]/30 rounded-2xl p-8 shadow-lg">
  {/* 进度条: from-blue-500 to-indigo-500 → bg-[#007AFF] */}
  <div className="bg-[#007AFF] h-2 rounded-full transition-all" style={{ width: `${progress}%` }} />
</div>

{/* 错误: bg-red-50 → bg-[#FF3B30]/5, text-red-600 → text-[#FF3B30], border-red-100 → border-[#FF3B30]/10 */}
```

- [ ] **Step 4: 更新演示按钮 + 功能卡片**（约第 197-222 行）

```tsx
{/* 演示按钮: border-purple-200 → border-[#5856D6]/20, text-purple-700 → text-[#5856D6] */}
<button className="px-6 py-2.5 bg-white/70 backdrop-blur-xl border-2 border-[#5856D6]/20 text-[#5856D6] rounded-2xl hover:bg-[#5856D6]/5 hover:border-[#5856D6]/30 font-semibold shadow-sm transition-all">
  🚀 快速预览示例数据
</button>

{/* 功能卡片: rounded-2xl → rounded-2xl, bg-gradient → white glass */}
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-5 text-center shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
```

- [ ] **Step 5: 验证** — 打开首页，确认所有元素使用新 iOS 风格

---

### Task 3: 概览仪表盘

**Files:**
- Modify: `frontend/src/app/reports/[reportId]/overview/page.tsx`

这是最复杂的页面（259 行），核心改动：

- [ ] **Step 1: Hero 卡片重写**（约第 23-71 行）

当前深色 Hero → 白渐变 Hero + 步数 + 活动环

```tsx
{/* Hero 卡片 */}
<div className="col-span-2 row-span-2 bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl p-5 border border-black/5 shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
  <div className="flex items-start justify-between mb-3">
    <div>
      <p className="text-[11px] font-semibold text-[#8E8E93] uppercase tracking-[0.15em]">摘要仪表盘</p>
      <p className="text-sm text-[#3A3A3C] mt-1">{healthStatusText}</p>
    </div>
    <span className="px-2 py-0.5 bg-[#34C759]/10 text-[#34C759] text-[10px] font-semibold rounded-full">状态良好</span>
  </div>

  {/* 步数大数字 + 进度条 */}
  <div className="text-center mb-4">
    <div className="text-[10px] font-semibold text-[#8E8E93] uppercase tracking-wider">今日步数</div>
    <div className="text-4xl font-extrabold text-[#1C1C1E] tracking-tight">{steps}</div>
    <div className="text-[11px] text-[#8E8E93]">目标 10,000</div>
    <div className="mt-2 mx-auto max-w-[200px] bg-black/5 rounded-full h-1.5">
      <div className="bg-gradient-to-r from-[#FF9500] to-[#FF3B30] h-full rounded-full" style={{ width: `${stepPct}%` }} />
    </div>
  </div>

  {/* 活动环 + 图例 */}
  <div className="flex items-center justify-center gap-5 mb-4">
    {/* SVG 活动环 */}
    <svg viewBox="0 0 88 88" className="w-[72px] h-[72px] -rotate-90">
      <circle cx="44" cy="44" r="34" fill="none" stroke="black" strokeOpacity="0.06" strokeWidth="9"/>
      <circle cx="44" cy="44" r="34" fill="none" stroke="#FF3B30" strokeWidth="9"
        strokeDasharray={`${movePct * 2.14} ${214 - movePct * 2.14}`} strokeLinecap="round"/>
      <circle cx="44" cy="44" r="23" fill="none" stroke="black" strokeOpacity="0.06" strokeWidth="9"/>
      <circle cx="44" cy="44" r="23" fill="none" stroke="#34C759" strokeWidth="9"
        strokeDasharray={`${exercisePct * 1.45} ${145 - exercisePct * 1.45}`} strokeLinecap="round"/>
      <circle cx="44" cy="44" r="12" fill="none" stroke="black" strokeOpacity="0.06" strokeWidth="9"/>
      <circle cx="44" cy="44" r="12" fill="none" stroke="#007AFF" strokeWidth="9"
        strokeDasharray={`${standPct * 0.75} ${75 - standPct * 0.75}`} strokeLinecap="round"/>
    </svg>
    <div className="text-[11px] text-[#8E8E93] leading-relaxed">
      <div><span className="inline-block w-2 h-2 rounded-full bg-[#FF3B30] mr-1.5"/>活动 680 kcal</div>
      <div><span className="inline-block w-2 h-2 rounded-full bg-[#34C759] mr-1.5"/>锻炼 45 min</div>
      <div><span className="inline-block w-2 h-2 rounded-full bg-[#007AFF] mr-1.5"/>站立 10 h</div>
    </div>
  </div>

  {/* 底部三列体征 */}
  <div className="grid grid-cols-3 gap-2 mb-3">
    <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
      <div className="text-[10px] text-[#8E8E93]">静息心率</div>
      <div className="text-lg font-bold text-[#1C1C1E]">{rhr}<span className="text-[10px] font-normal text-[#8E8E93]"> bpm</span></div>
    </div>
    <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
      <div className="text-[10px] text-[#8E8E93]">HRV</div>
      <div className="text-lg font-bold text-[#1C1C1E]">{hrv}<span className="text-[10px] font-normal text-[#8E8E93]"> ms</span></div>
    </div>
    <div className="bg-black/[0.02] rounded-xl p-2.5 text-center">
      <div className="text-[10px] text-[#8E8E93]">睡眠</div>
      <div className="text-lg font-bold text-[#1C1C1E]">{sleep}<span className="text-[10px] font-normal text-[#8E8E93]"> h</span></div>
    </div>
  </div>

  {/* AI 报告入口 */}
  <Link href={`/reports/${reportId}/report`}
    className="flex items-center justify-between bg-black/[0.02] rounded-xl px-3 py-2 text-[11px] text-[#8E8E93] hover:bg-black/[0.04]">
    <span>📋 AI 健康报告</span>
    <span className="text-[#007AFF] font-medium">查看 →</span>
  </Link>
</div>
```

- [ ] **Step 2: 快照卡片**（约第 75-105 行）

```tsx
{/* 指标快照卡片 */}
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-3.5 shadow-[0_2px_12px_rgba(0,0,0,0.03)] hover:shadow-md hover:-translate-y-0.5 transition-all">
  <div className="flex items-center gap-1.5 mb-1">
    <div className={`w-1.5 h-1.5 rounded-full ${catDotColor(card.metricKey)}`} />
    <span className="text-[10px] font-semibold text-[#8E8E93] uppercase tracking-wider">{card.label}</span>
  </div>
  <div className="text-2xl font-extrabold text-[#1C1C1E] tabular-nums">{card.latest}</div>
  {/* 趋势徽章 */}
  <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-md ${
    trend > 0 ? 'bg-[#34C759]/10 text-[#34C759]' :
    trend < 0 ? 'bg-[#FF3B30]/10 text-[#FF3B30]' :
    'bg-black/5 text-[#8E8E93]'
  }`}>
    {trend > 0 ? '↑' : trend < 0 ? '↓' : '→'} {Math.abs(trend)}%
  </span>
</div>
```

需要添加辅助函数 `catDotColor`：

```tsx
function catDotColor(key: string): string {
  const c = getCategory(key);
  if (c === 'activity') return 'bg-[#FF3B30]';
  if (c === 'heart') return 'bg-[#FF3B30]';
  if (c === 'body') return 'bg-[#34C759]';
  if (c === 'sleep_env') return 'bg-[#5856D6]';
  return 'bg-[#007AFF]';
}
```

- [ ] **Step 3: 异常告警**（约第 209-216 行）

```tsx
{anomalyCards.length > 0 && (
  <div className="col-span-2 bg-[#FFFBF5] border border-[#FF9500]/15 rounded-2xl p-4">
    <div className="flex items-center gap-2">
      <span className="text-base">⚠️</span>
      <div>
        <div className="text-xs font-semibold text-[#FF9500]">
          {anomalyCards.length} 项指标异常
        </div>
        <div className="text-[11px] text-[#8E8E93] mt-0.5">
          建议关注相关指标，点击查看详情
        </div>
      </div>
    </div>
  </div>
)}
```

- [ ] **Step 4: 折叠分组 + 趋势卡片**（约第 135-255 行）

所有内联卡片统一为毛玻璃风格：

```tsx
{/* 折叠区域 */}
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-4 shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
  <button className="flex items-center gap-2 text-xs font-semibold text-[#8E8E93] uppercase tracking-wider">
    <span className={`transition-transform ${expanded ? 'rotate-90' : ''}`}>▸</span>
    {catLabel(category)}
  </button>
  {expanded && (
    <div className="mt-3 space-y-1">
      {cards.map(card => (
        <div key={card.metricKey} className="flex items-center justify-between px-3 py-2 rounded-xl hover:bg-black/[0.02] transition-colors">
          <span className="text-sm text-[#1C1C1E]">{card.label}</span>
          <span className="text-sm font-semibold tabular-nums">{card.latest}{unit}</span>
        </div>
      ))}
    </div>
  )}
</div>
```

- [ ] **Step 5: 顶部操作栏**（约第 198-206 行）

```tsx
{/* 顶部 */}
<div className="flex items-center justify-between mb-5">
  <Link href="/" className="text-[#007AFF] hover:underline text-sm font-medium">← 首页</Link>
  <div className="flex gap-2">
    <Link href={`/reports/${reportId}/chat`}
      className="px-4 py-2 bg-gradient-to-r from-[#5856D6] to-[#AF52DE] text-white rounded-full text-xs font-semibold shadow-md shadow-[#5856D6]/20">
      🤖 AI 对话
    </Link>
    <Link href={`/reports/${reportId}/heatmap`}
      className="px-4 py-2 bg-white/70 backdrop-blur-xl border border-black/5 rounded-full text-xs font-medium text-[#3A3A3C] hover:bg-black/[0.02]">
      🗓 热力图
    </Link>
  </div>
</div>
```

- [ ] **Step 6: 验证** — 进入概览页，确认 Hero、卡片、告警全部使用新风格

---

### Task 4: AI 报告页

**Files:**
- Modify: `frontend/src/app/reports/[reportId]/report/page.tsx`

- [ ] **Step 1: 封面改为浅色渐变**（约第 43-48 行）

```tsx
<div className="bg-gradient-to-br from-white via-[#F8F8FC] to-[#EEF0FF] rounded-3xl border border-black/5 p-8 text-center shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
  <div className="text-[10px] font-semibold text-black/20 uppercase tracking-[0.2em] mb-3">
    {type === 'weekly' ? 'Weekly Report' : 'Monthly Report'}
  </div>
  <h2 className="text-3xl font-extrabold text-[#1C1C1E] mb-2">
    {type === 'weekly' ? '健康周报' : '健康月报'}
  </h2>
  <p className="text-sm text-[#8E8E93]">{data.startDate} ~ {data.endDate}</p>
  <p className="text-xs text-[#8E8E93]/60 mt-2">
    共 {Object.values(data.stateDistribution).reduce((a:any,b:any) => a+b, 0) as number} 天数据
  </p>

  {/* 状态分布标签 */}
  <div className="flex justify-center gap-3 mt-4">
    {Object.entries(data.stateDistribution).map(([state, days]: [string, any]) => {
      const colors: Record<string, string> = {
        '高压疲劳': 'bg-[#FF3B30]/8 text-[#FF3B30]',
        '高效恢复': 'bg-[#34C759]/8 text-[#34C759]',
        '运动爆发': 'bg-[#FF9500]/8 text-[#FF9500]',
        '平稳日常': 'bg-[#8E8E93]/8 text-[#8E8E93]',
      };
      return (
        <div key={state} className={`${colors[state] || 'bg-black/5 text-[#8E8E93]'} rounded-xl px-3 py-2 text-center`}>
          <div className="text-[10px]">{state}</div>
          <div className="text-base font-bold">{days}<span className="text-[10px]">天</span></div>
        </div>
      );
    })}
  </div>
</div>
```

- [ ] **Step 2: 指标变化亮点卡片**（约第 51-68 行）

```tsx
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-5 shadow-[0_2px_12px_rgba(0,0,0,0.03)]">
  <h3 className="text-sm font-extrabold text-[#1C1C1E] mb-3">核心指标变化</h3>
  <div className="space-y-1.5">
    {data.highlights.map((h: any) => {
      const up = h.changePct > 0;
      const label = METRIC_LABELS[h.metricKey] || h.metricKey;
      const unit = METRIC_UNITS[h.metricKey] || '';
      return (
        <div key={h.metricKey} className="flex items-center gap-3 bg-[#F2F2F7] rounded-xl px-4 py-2.5">
          <div className={`w-1.5 h-1.5 rounded-full ${catDotColor(h.metricKey)}`} />
          <span className="text-sm font-medium text-[#1C1C1E] flex-1">{label}</span>
          <span className="text-sm font-bold tabular-nums">{h.weeklyAvg.toFixed(1)}{unit}</span>
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
            h.changePct > 0 ? 'bg-[#34C759]/10 text-[#34C759]' :
            h.changePct < 0 ? 'bg-[#FF3B30]/10 text-[#FF3B30]' :
            'bg-black/5 text-[#8E8E93]'
          }`}>
            {h.trend} {Math.abs(h.changePct).toFixed(0)}%
          </span>
        </div>
      );
    })}
  </div>
</div>
```

- [ ] **Step 3: AI 叙述卡片**（约第 72-85 行）

```tsx
<div className="bg-gradient-to-br from-[#FAFAFE] to-[#F4F4FC] rounded-2xl border border-black/5 p-6">
  <div className="flex items-center gap-2 mb-4">
    <span className="text-xl">🤖</span>
    <h3 className="text-sm font-extrabold text-[#1C1C1E]">AI {type==='weekly'?'周':'月'}度洞察</h3>
  </div>
  <div className="text-sm leading-relaxed space-y-1">
    {data.aiNarrative.split('\n').map((line: string, i: number) => {
      const t = line.trim();
      if (!t) return <div key={i} className="h-2" />;
      if (SECTION_TITLES.test(t)) {
        // 章节标题赋予不同颜色
        const sectionColors: Record<string, string> = {
          '整体评估': 'text-[#007AFF]',
          '关键发现': 'text-[#34C759]',
          '风险提示': 'text-[#FF9500]',
          '可执行建议': 'text-[#5856D6]',
        };
        const color = Object.entries(sectionColors).find(([k]) => t.includes(k))?.[1] || 'text-[#007AFF]';
        return <h4 key={i} className={`text-sm font-extrabold ${color} mt-3 mb-1`}>{t}</h4>;
      }
      return <p key={i} className="text-[#3A3A3C] mb-1">{t}</p>;
    })}
  </div>
  <p className="text-[10px] text-[#8E8E93]/60 mt-4">本结果仅基于用户上传数据进行非医疗分析</p>
</div>
```

- [ ] **Step 4: 底部 CTA 按钮**（约第 88-91 行）

```tsx
<Link href={`/reports/${reportId}/chat`}
  className="block w-full text-center mt-5 py-3 bg-[#007AFF] text-white rounded-full font-bold hover:bg-[#0077EE] transition-colors shadow-lg shadow-[#007AFF]/20">
  🤖 深入对话
</Link>
```

- [ ] **Step 5: 开关组件**（约第 35-39 行）

```tsx
<div className="flex gap-0.5 bg-black/[0.04] rounded-xl p-0.5">
  <button onClick={() => setType('weekly')}
    className={`px-3 py-1 text-xs rounded-[10px] font-medium transition ${
      type==='weekly' ? 'bg-white text-[#1C1C1E] shadow-sm' : 'text-[#8E8E93]'
    }`}>周报</button>
  <button onClick={() => setType('monthly')}
    className={`px-3 py-1 text-xs rounded-[10px] font-medium transition ${
      type==='monthly' ? 'bg-white text-[#1C1C1E] shadow-sm' : 'text-[#8E8E93]'
    }`}>月报</button>
</div>
```

- [ ] **Step 6: 验证** — 查看周报/月报，确认所有元素统一浅色

---

### Task 5: AI 聊天页

**Files:**
- Modify: `frontend/src/app/reports/[reportId]/chat/page.tsx`

改动量小，主要是替换颜色类名：

- [ ] **Step 1: 建议问题按钮**

```tsx
{suggestions.map((q, i) => (
  <button key={i} onClick={() => handleSend(q)}
    className="px-3 py-1.5 bg-[#F2F2F7] hover:bg-black/[0.06] rounded-full text-xs text-[#3A3A3C] transition-colors">
    {q}
  </button>
))}
```

- [ ] **Step 2: 输入区域**

```tsx
<div className="flex gap-2">
  <input value={input} onChange={e => setInput(e.target.value)}
    onKeyDown={e => e.key === 'Enter' && handleSend(input)}
    placeholder="输入你的健康问题..."
    className="flex-1 px-4 py-2.5 bg-white/70 backdrop-blur-xl border border-black/10 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-[#007AFF]/30 focus:border-[#007AFF]/30"
  />
  <button onClick={() => handleSend(input)} disabled={!input.trim()}
    className="px-5 py-2.5 bg-[#007AFF] text-white rounded-full text-sm font-semibold hover:bg-[#0077EE] disabled:opacity-40 transition-all">
    发送
  </button>
</div>
```

- [ ] **Step 3: 聊天气泡 — 更新 ChatBubble 组件**（`frontend/src/components/ChatBubble.tsx`）

```tsx
{/* 用户气泡 */}
<div className="flex justify-end">
  <div className="max-w-[80%] bg-[#007AFF] text-white rounded-2xl rounded-br-sm px-4 py-2.5 text-sm">
    {message.content}
  </div>
</div>

{/* AI 气泡 */}
<div className="flex justify-start">
  <div className="max-w-[80%] bg-[#E9E9EF] text-[#1C1C1E] rounded-2xl rounded-bl-sm px-4 py-2.5 text-sm">
    {message.content}
  </div>
</div>
```

- [ ] **Step 4: 验证** — 进入聊天页，发一条消息查看气泡效果

---

### Task 6: 指标详情页

**Files:**
- Modify: `frontend/src/app/reports/[reportId]/metrics/[metricKey]/page.tsx`

- [ ] **Step 1: 类名替换**

全局：
- `bg-white border border-gray-200` → `bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl`
- 图表容器：`rounded-xl` → `rounded-2xl`
- 统计卡片的左边框保持彩色，背景改为毛玻璃

```tsx
{/* 统计卡片 */}
<div className="grid grid-cols-2 md:grid-cols-4 gap-3">
  {stats.map((s, i) => {
    const borders = ['border-l-[#FF3B30]', 'border-l-[#34C759]', 'border-l-[#FF9500]', 'border-l-[#5856D6]'];
    return (
      <div key={i} className={`bg-white/70 backdrop-blur-xl border border-black/5 border-l-4 ${borders[i]} rounded-2xl p-4`}>
        <div className="text-[10px] text-[#8E8E93] uppercase font-semibold">{s.label}</div>
        <div className="text-2xl font-extrabold text-[#1C1C1E] mt-1">{s.value}</div>
      </div>
    );
  })}
</div>
```

- [ ] **Step 2: 健康参考卡片**

```tsx
<div className="bg-gradient-to-br from-[#007AFF]/5 to-[#5856D6]/5 rounded-2xl border border-[#007AFF]/10 p-5">
  {/* 内容不变，改背景渐变 */}
</div>
```

- [ ] **Step 3: 验证** — 进入指标详情，确认图表、卡片风格统一

---

### Task 7: 热力图页

**Files:**
- Modify: `frontend/src/app/reports/[reportId]/heatmap/page.tsx`

- [ ] **Step 1: 指标选择器**

```tsx
<div className="flex flex-wrap gap-1.5">
  {metrics.map(mk => (
    <button key={mk} onClick={() => setMetric(mk)}
      className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
        mk === selectedMetric
          ? 'bg-[#1C1C1E] text-white'
          : 'bg-white/70 backdrop-blur-xl text-[#3A3A3C] hover:bg-black/[0.04] border border-black/5'
      }`}>
      {METRIC_LABELS[mk] || mk}
    </button>
  ))}
</div>
```

- [ ] **Step 2: 热力图网格容器**

```tsx
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-4">
  {/* 网格 + 图例保持不变，仅改容器 */}
</div>
```

- [ ] **Step 3: 验证** — 打开热力图，确认选择器和网格容器风格统一

---

### Task 8: 上传进度页

**Files:**
- Modify: `frontend/src/app/uploads/[id]/page.tsx`

- [ ] **Step 1: 类名替换**

```tsx
{/* 阶段步骤 */}
<div className={`flex items-center gap-3 px-4 py-2.5 rounded-xl transition-colors ${
  active ? 'bg-[#007AFF]/5 border border-[#007AFF]/20' :
  done ? 'bg-[#34C759]/5' : 'bg-[#F2F2F7]'
}`}>

{/* 进度条 */}
<div className="bg-white/70 backdrop-blur-xl rounded-2xl border border-black/5 shadow-sm p-5">
  {/* 运行中: bg-gradient 改为 bg-[#007AFF] */}
  {isRunning ? (
    <div className="h-2.5 bg-[#007AFF]/20 rounded-full overflow-hidden">
      <div className="h-full bg-[#007AFF] rounded-full animate-shimmer" style={{ backgroundSize: '200% 100%' }}/>
    </div>
  ) : (
    <div className={`h-2.5 rounded-full ${status.status === 'failed' ? 'bg-[#FF3B30]' : 'bg-[#34C759]'}`}
      style={{ width: `${progress}%` }}/>
  )}
</div>

{/* 就绪状态 */}
<div className="bg-gradient-to-br from-[#34C759]/5 to-[#34C759]/10 border border-[#34C759]/20 rounded-2xl p-6 text-center">
  <button onClick={() => router.push(`/reports/${id}/overview`)}
    className="w-full py-3 bg-[#34C759] text-white rounded-full font-bold hover:bg-[#30B350] shadow-lg shadow-[#34C759]/20">
    查看健康报告
  </button>
</div>

{/* 失败状态 */}
<div className="bg-gradient-to-br from-[#FF3B30]/5 to-[#FF3B30]/10 border border-[#FF3B30]/20 rounded-2xl p-6">
  <button onClick={() => router.push('/')}
    className="flex-1 py-2.5 bg-[#1C1C1E] text-white rounded-full font-medium">
    重新上传
  </button>
</div>
```

- [ ] **Step 2: 验证** — 上传进度页各状态视觉效果统一

---

### Task 9: 组件微调 + 最终验证

**Files:**
- Modify: `frontend/src/components/TrendChart.tsx`
- Modify: `frontend/src/components/Sparkline.tsx`
- Modify: `frontend/src/components/MetricCard.tsx`

- [ ] **Step 1: TrendChart** — 图表容器白卡改毛玻璃

```tsx
<div className="bg-white/70 backdrop-blur-xl border border-black/5 rounded-2xl p-5">
  {/* Recharts 内部不变，改外层容器 */}
</div>
```

线条颜色从 `#2563eb` (blue-600) 改为 `#FF3B30` 或根据指标类别动态设置。如果动态设置太复杂，暂且使用 `#007AFF`。

- [ ] **Step 2: Sparkline** — 默认颜色从 `#3b82f6` 改为 `#8E8E93`

- [ ] **Step 3: MetricCard** — 卡片背景改毛玻璃

```tsx
{/* MetricCard */}
<div className={`rounded-2xl border overflow-hidden hover:shadow-md hover:-translate-y-0.5 transition-all ${
  anomaly ? 'border-[#FF9500]/30 ring-1 ring-[#FF9500]/20' : 'border-black/5'
} bg-white/70 backdrop-blur-xl`}>
  {/* 顶部颜色条保持，内部 padding/字体微调 */}
</div>
```

- [ ] **Step 4: 全流程验证**

```bash
cd frontend && npm run dev
```

检查清单：
1. 首页 — Hero、上传区、演示按钮、功能卡片
2. 上传进度 — 各阶段状态
3. 概览仪表盘 — Hero + 卡片 + 告警
4. 指标详情 — 图表 + 统计卡片
5. 热力图 — 选择器 + 网格
6. AI 报告 — 封面 + 指标 + 叙述
7. AI 聊天 — 气泡 + 输入区

---

## 实施顺序

推荐顺序（依赖关系最小）：Task 1 → Task 2 → Task 3 → Task 5 → Task 4 → Task 6 → Task 7 → Task 8 → Task 9

每个 Task 完成后可独立验证对应页面。
