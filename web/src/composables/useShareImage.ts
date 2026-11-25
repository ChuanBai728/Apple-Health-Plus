export interface SharePayload {
  no: string
  title: string
  periodLabel: string
  stats: { label: string; value: string }[]
  copy: string
  chart?: string | null
  /** 可选标签，用于人格等模块 */
  tags?: string[]
}

export type ShareRatio = '9:16' | '1:1'

function cssVar(name: string, fallback: string): string {
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = reject
    img.src = src
  })
}

function wrapText(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  maxLines: number,
): string[] {
  // 数字（含小数/千分位/百分号）与英文单词作为整体，不被折行拆散
  const tokens = text.match(/\d[\d,.]*%?|[A-Za-z]+|\s|./g) ?? []
  const lines: string[] = []
  let cur = ''
  let i = 0
  for (; i < tokens.length; i++) {
    const tk = tokens[i]
    if (cur && ctx.measureText(cur + tk).width > maxWidth) {
      lines.push(cur)
      if (lines.length === maxLines) break
      cur = ''
    }
    if (!cur && /^\s+$/.test(tk)) continue
    cur += tk
  }
  if (lines.length < maxLines && cur) lines.push(cur)
  if (lines.length === maxLines && tokens.slice(i).some((t) => !/^\s+$/.test(t))) {
    let last = lines[maxLines - 1]
    while (ctx.measureText(last + '…').width > maxWidth && last.length > 0) last = last.slice(0, -1)
    lines[maxLines - 1] = last + '…'
  }
  return lines
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.quadraticCurveTo(x + w, y, x + w, y + radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h)
  ctx.lineTo(x + radius, y + h)
  ctx.quadraticCurveTo(x, y + h, x, y + h - radius)
  ctx.lineTo(x, y + radius)
  ctx.quadraticCurveTo(x, y, x + radius, y)
  ctx.closePath()
}

/** 数值与单位分离：`8,867 步` → ['8,867', '步']；无法拆分时整体作为数值 */
function splitValue(value: string): [string, string] {
  const range = value.match(/^([\d][\d,.]*\s*[–\-~至]\s*[\d][\d,.]*)(.*)$/)
  if (range) return [range[1].replace(/\s+/g, ' '), range[2].trim()]
  const m = value.match(/^([\d][\d,.:]*)\s*(.*)$/)
  if (m && m[2]) return [m[1], m[2]]
  return [value, '']
}

export async function drawShareCard(payload: SharePayload, ratio: ShareRatio): Promise<HTMLCanvasElement> {
  const W = 1080
  const H = ratio === '9:16' ? 1920 : 1080
  const tall = ratio === '9:16'
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')!

  const bgTop = cssVar('--bg-top', '#0a0f1d')
  const bgLow = cssVar('--bg-low', '#05070e')
  const text = cssVar('--text', '#f7f8fb')
  const muted = cssVar('--muted', 'rgba(255,255,255,.68)')
  const faint = cssVar('--faint', 'rgba(255,255,255,.44)')
  const hair = cssVar('--hair', 'rgba(255,255,255,.12)')
  const glass = cssVar('--glass-a', 'rgba(255,255,255,.1)')
  const a1 = cssVar('--accent-1', '#7dd3fc')
  const a2 = cssVar('--accent-2', '#a5b4fc')
  const a3 = cssVar('--accent-3', '#f0abfc')
  const accents = [a1, a2, a3, a1, a2, a3]
  const font = (weight: number, size: number) =>
    `${weight} ${size}px system-ui, "PingFang SC", "Microsoft YaHei", sans-serif`

  const M = tall ? 76 : 64

  /* ---------- 背景 ---------- */
  const bg = ctx.createLinearGradient(0, 0, 0, H)
  bg.addColorStop(0, bgTop)
  bg.addColorStop(1, bgLow)
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, W, H)

  const orb = (x: number, y: number, r: number, color: string, alpha: number) => {
    const g = ctx.createRadialGradient(x, y, 0, x, y, r)
    g.addColorStop(0, color)
    g.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.globalAlpha = alpha
    ctx.fillStyle = g
    ctx.fillRect(x - r, y - r, r * 2, r * 2)
    ctx.globalAlpha = 1
  }
  orb(120, 140, 560, a1, 0.22)
  orb(W - 80, H * 0.32, 520, a2, 0.18)
  orb(W * 0.3, H - 100, 600, a3, 0.14)

  /* ---------- 外框 ---------- */
  ctx.strokeStyle = hair
  ctx.lineWidth = 2
  roundRect(ctx, 26, 26, W - 52, H - 52, 34)
  ctx.stroke()

  /* ---------- 顶栏 ---------- */
  let y = tall ? 116 : 96
  ctx.textBaseline = 'middle'
  ctx.textAlign = 'left'
  ctx.strokeStyle = a1
  ctx.lineWidth = 8
  ctx.beginPath()
  ctx.arc(M + 20, y, 20, 0, Math.PI * 2)
  ctx.stroke()
  ctx.strokeStyle = a2
  ctx.lineWidth = 6
  ctx.beginPath()
  ctx.arc(M + 20, y, 10, 0, Math.PI * 2)
  ctx.stroke()
  ctx.fillStyle = text
  ctx.font = font(700, tall ? 32 : 29)
  ctx.fillText('HealthPlus · 身体年报', M + 58, y + 1)
  ctx.fillStyle = faint
  ctx.font = font(500, tall ? 27 : 24)
  ctx.textAlign = 'right'
  ctx.fillText(payload.periodLabel, W - M, y + 1)
  ctx.textAlign = 'left'
  y += tall ? 88 : 74

  /* ---------- 编号 + 标题 ---------- */
  ctx.fillStyle = a1
  ctx.font = font(800, tall ? 28 : 24)
  ctx.fillText(payload.no, M, y)
  y += tall ? 74 : 62

  const titleSize0 = tall ? 78 : 60
  let titleSize = titleSize0
  ctx.fillStyle = text
  ctx.font = font(800, titleSize)
  while (ctx.measureText(payload.title).width > W - M * 2 && titleSize > 34) {
    titleSize -= 2
    ctx.font = font(800, titleSize)
  }
  ctx.fillText(payload.title, M, y)
  y += tall ? 74 : 62

  /* ---------- 标签 ---------- */
  const tags = (payload.tags ?? []).slice(0, 3)
  if (tags.length) {
    const tagSize = tall ? 26 : 23
    const pillH = tagSize + 26
    ctx.font = font(700, tagSize)
    let tx = M
    for (const t of tags) {
      const tw = ctx.measureText(t).width
      const pw = tw + 44
      ctx.fillStyle = a1
      ctx.globalAlpha = 0.1
      roundRect(ctx, tx, y - pillH / 2, pw, pillH, 999)
      ctx.fill()
      ctx.globalAlpha = 1
      ctx.strokeStyle = a1
      ctx.lineWidth = 1.5
      roundRect(ctx, tx, y - pillH / 2, pw, pillH, 999)
      ctx.stroke()
      ctx.fillStyle = a1
      ctx.textBaseline = 'middle'
      ctx.fillText(t, tx + 22, y)
      tx += pw + 14
    }
    y += tall ? 68 : 58
  }

  /* ---------- 统计卡（高度随剩余空间自适应） ---------- */
  const img = payload.chart ? await loadImage(payload.chart).catch(() => null) : null
  // 方形带图表时保留更多空间给图表
  const maxStats = img && !tall ? 4 : 6
  const stats = payload.stats.slice(0, maxStats)
  const gap = tall ? 24 : 18

  // 先量出文案与图表需要的高度，剩余空间再分给统计卡
  let fontSize = tall ? 42 : 34
  ctx.font = font(500, fontSize)
  const copyWidth = W - M * 2 - 40
  let lines = wrapText(ctx, payload.copy, copyWidth, 3)
  if (lines.some((l) => l.endsWith('…')) && fontSize > 32) {
    fontSize = 32
    ctx.font = font(500, fontSize)
    lines = wrapText(ctx, payload.copy, copyWidth, 3)
  }
  const lineH = fontSize + (tall ? 24 : 18)
  const copyH = lines.length * lineH

  const chartW = W - M * 2
  const chartRatio = img ? img.height / img.width : 0
  const maxChartH = tall ? 520 : 300

  // 竖版没有图表时改成单列大卡，避免版面空荡
  const cols = tall && !img && stats.length >= 4 ? 1 : 2
  const rows = Math.ceil(stats.length / cols)
  const cardW = (W - M * 2 - gap * (cols - 1)) / cols
  const footerTop = H - (tall ? 156 : 116)

  const blockGap = tall ? 46 : 24
  const chartReserve = img ? Math.min(chartW * chartRatio, maxChartH) + 40 : 0
  const statsSpace = footerTop - y - copyH - chartReserve - blockGap
  let cardH = (statsSpace - (rows - 1) * gap) / rows
  cardH = Math.max(tall ? 132 : 124, Math.min(cardH, tall ? 230 : 170))

  // 内容整体在剩余空间里上下均衡，避免底部大片留白
  const statsH = rows * (cardH + gap) - gap
  const contentH = statsH + copyH + chartReserve
  const slack = Math.max(0, footerTop - y - contentH)
  y += Math.min(slack / 2, tall ? 120 : 34)

  // 图表按实际可用高度收敛，保证不压到页脚
  let chartH = 0
  if (img) {
    const chartY = y + statsH + blockGap + copyH + (tall ? 46 : 26)
    chartH = Math.min(chartW * chartRatio, footerTop - chartY - 28)
    if (chartH < 150) chartH = 0
  }

  const valueBase = tall ? 58 : 48
  stats.forEach((s, i) => {
    const col = i % cols
    const row = Math.floor(i / cols)
    const x = M + col * (cardW + gap)
    const cy = y + row * (cardH + gap)
    const accent = accents[i % accents.length]

    ctx.save()
    roundRect(ctx, x, cy, cardW, cardH, 22)
    ctx.clip()
    ctx.fillStyle = glass
    ctx.fillRect(x, cy, cardW, cardH)
    const tint = ctx.createRadialGradient(x + cardW, cy, 0, x + cardW, cy, cardW)
    tint.addColorStop(0, accent)
    tint.addColorStop(1, 'rgba(0,0,0,0)')
    ctx.globalAlpha = 0.16
    ctx.fillStyle = tint
    ctx.fillRect(x, cy, cardW, cardH)
    ctx.globalAlpha = 1
    ctx.restore()

    ctx.strokeStyle = hair
    ctx.lineWidth = 1.5
    roundRect(ctx, x, cy, cardW, cardH, 22)
    ctx.stroke()

    const [num, unit] = splitValue(s.value)
    const unitFactor = 0.42
    let size = Math.min(valueBase + (cardH - (tall ? 152 : 126)) * 0.12, tall ? 76 : 56)
    const valueLimit = cols === 1 ? cardW - 250 : cardW - 56
    ctx.font = font(800, size)
    while (
      ctx.measureText(num).width + (unit ? ctx.measureText(unit).width * unitFactor : 0) > valueLimit &&
      size > 26
    ) {
      size -= 2
      ctx.font = font(800, size)
    }

    if (cols === 1) {
      // 单列：标签在左，数值右对齐
      ctx.fillStyle = accent
      ctx.beginPath()
      ctx.arc(x + 32, cy + cardH / 2, 7, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = muted
      ctx.font = font(600, tall ? 28 : 22)
      ctx.textBaseline = 'middle'
      ctx.fillText(s.label, x + 52, cy + cardH / 2)

      ctx.font = font(700, Math.round(size * unitFactor))
      const unitW = unit ? ctx.measureText(unit).width : 0
      const right = x + cardW - 28
      const baseline = cy + cardH / 2 + size * 0.34
      ctx.textAlign = 'right'
      ctx.textBaseline = 'alphabetic'
      ctx.fillStyle = text
      ctx.font = font(800, size)
      ctx.fillText(num, right - (unit ? unitW + 14 : 0), baseline)
      if (unit) {
        ctx.font = font(700, Math.round(size * unitFactor))
        ctx.fillStyle = muted
        ctx.fillText(unit, right, baseline)
      }
      ctx.textAlign = 'left'
      ctx.textBaseline = 'middle'
    } else {
      const numW = ctx.measureText(num).width
      ctx.fillStyle = accent
      ctx.beginPath()
      ctx.arc(x + 30, cy + 32, 7, 0, Math.PI * 2)
      ctx.fill()
      ctx.fillStyle = muted
      ctx.font = font(600, tall ? 25 : 22)
      ctx.fillText(s.label, x + 48, cy + 33)
      ctx.textBaseline = 'alphabetic'
      ctx.fillStyle = text
      ctx.font = font(800, size)
      ctx.fillText(num, x + 28, cy + cardH - 30)
      if (unit) {
        ctx.font = font(700, Math.round(size * unitFactor))
        ctx.fillStyle = muted
        ctx.fillText(unit, x + 28 + numW + 10, cy + cardH - 30)
      }
      ctx.textBaseline = 'middle'
    }
  })
  y += rows * (cardH + gap) - gap + (tall ? 46 : 24)

  /* ---------- 文案 ---------- */
  ctx.fillStyle = a1
  ctx.fillRect(M, y - fontSize * 0.72, 6, lines.length * lineH + 6)
  ctx.fillStyle = muted
  ctx.font = font(500, fontSize)
  lines.forEach((line, i) => {
    ctx.fillText(line, M + 30, y + i * lineH + fontSize * 0.42)
  })
  y += lines.length * lineH + (tall ? 46 : 26)

  /* ---------- 图表 ---------- */
  if (img && chartH > 0) {
    const scale = Math.min(chartW / img.width, chartH / img.height)
    const dw = img.width * scale
    const dh = img.height * scale
    ctx.save()
    roundRect(ctx, M, y, chartW, dh, 20)
    ctx.clip()
    ctx.drawImage(img, M + (chartW - dw) / 2, y, dw, dh)
    ctx.restore()
    ctx.strokeStyle = hair
    ctx.lineWidth = 1.5
    roundRect(ctx, M, y, chartW, dh, 20)
    ctx.stroke()
  }

  /* ---------- 页脚 ---------- */
  ctx.strokeStyle = hair
  ctx.lineWidth = 1.5
  ctx.beginPath()
  ctx.moveTo(M, H - (tall ? 118 : 96))
  ctx.lineTo(W - M, H - (tall ? 118 : 96))
  ctx.stroke()
  ctx.fillStyle = faint
  ctx.font = font(500, tall ? 26 : 23)
  ctx.textBaseline = 'middle'
  ctx.fillText('数据来自你的健康 App 导出 · 浏览器本地生成 · 不上传', M, H - (tall ? 72 : 58))
  ctx.textAlign = 'right'
  ctx.fillText('HealthPlus', W - M, H - (tall ? 72 : 58))
  ctx.textAlign = 'left'

  return canvas
}

export function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('toBlob failed'))), 'image/png')
  })
}
