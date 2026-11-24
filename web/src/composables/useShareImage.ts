export interface SharePayload {
  no: string
  title: string
  periodLabel: string
  stats: { label: string; value: string }[]
  copy: string
  chart?: string | null
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

export async function drawShareCard(payload: SharePayload, ratio: ShareRatio): Promise<HTMLCanvasElement> {
  const W = 1080
  const H = ratio === '9:16' ? 1920 : 1080
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
  const a1 = cssVar('--accent-1', '#7dd3fc')
  const a2 = cssVar('--accent-2', '#a5b4fc')
  const a3 = cssVar('--accent-3', '#f0abfc')
  const glow = cssVar('--glow', 'rgba(165,180,252,.45)')
  const font = (weight: number, size: number) =>
    `${weight} ${size}px system-ui, "PingFang SC", "Microsoft YaHei", sans-serif`

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

  const M = 84
  let y = 110

  ctx.strokeStyle = a1
  ctx.lineWidth = 10
  ctx.beginPath()
  ctx.arc(M + 26, y + 8, 24, 0, Math.PI * 2)
  ctx.stroke()
  ctx.strokeStyle = a2
  ctx.beginPath()
  ctx.arc(M + 26, y + 8, 12, 0, Math.PI * 2)
  ctx.stroke()
  ctx.fillStyle = text
  ctx.font = font(700, 34)
  ctx.textBaseline = 'middle'
  ctx.fillText('HealthPlus · 身体年报', M + 72, y + 8)
  ctx.fillStyle = faint
  ctx.font = font(500, 28)
  ctx.textAlign = 'right'
  ctx.fillText(payload.periodLabel, W - M, y + 8)
  ctx.textAlign = 'left'
  y += 96

  ctx.fillStyle = a1
  ctx.font = font(800, 30)
  ctx.fillText(payload.no, M, y)
  y += 56
  ctx.fillStyle = text
  ctx.font = font(800, 76)
  ctx.fillText(payload.title, M, y)
  y += 90

  const stats = payload.stats.slice(0, 4)
  const colW = (W - M * 2) / 2
  const rowH = 168
  stats.forEach((s, i) => {
    const col = i % 2
    const row = Math.floor(i / 2)
    const x = M + col * colW
    const sy = y + row * rowH
    ctx.fillStyle = hair
    ctx.fillRect(x, sy - 44, colW - 40, 1)
    ctx.save()
    ctx.shadowColor = glow
    ctx.shadowBlur = 34
    ctx.fillStyle = i === 0 ? a1 : text
    ctx.font = font(800, 72)
    let v = s.value
    while (ctx.measureText(v).width > colW - 60 && v.length > 2) {
      const size = parseInt(ctx.font.match(/(\d+)px/)![1]) - 6
      ctx.font = font(800, size)
      if (size < 40) break
    }
    ctx.fillText(v, x, sy + 16)
    ctx.restore()
    ctx.fillStyle = muted
    ctx.font = font(500, 30)
    ctx.fillText(s.label, x, sy + 78)
  })
  y += Math.ceil(stats.length / 2) * rowH + 30

  let fontSize = 42
  ctx.font = font(500, fontSize)
  let lines = wrapText(ctx, payload.copy, W - M * 2 - 30, 3)
  if (lines.some((l) => l.endsWith('…')) && fontSize > 34) {
    fontSize = 34
    ctx.font = font(500, fontSize)
    lines = wrapText(ctx, payload.copy, W - M * 2 - 30, 3)
  }
  ctx.fillStyle = a1
  ctx.fillRect(M, y - 26, 6, lines.length * (fontSize + 22) + 6)
  ctx.fillStyle = muted
  lines.forEach((line, i) => {
    ctx.fillText(line, M + 30, y + i * (fontSize + 22))
  })
  y += lines.length * (fontSize + 22) + 50

  if (payload.chart && ratio === '9:16') {
    try {
      const img = await loadImage(payload.chart)
      const availH = H - 190 - y
      if (availH > 200) {
        const scale = Math.min((W - M * 2) / img.width, availH / img.height)
        const dw = img.width * scale
        const dh = img.height * scale
        ctx.drawImage(img, M + (W - M * 2 - dw) / 2, y, dw, dh)
      }
    } catch {
      /* 图表加载失败时跳过 */
    }
  }

  ctx.fillStyle = hair
  ctx.fillRect(M, H - 120, W - M * 2, 1)
  ctx.fillStyle = faint
  ctx.font = font(500, 27)
  ctx.fillText('数据来自 Apple 健康导出 · 本地生成 · 不上传', M, H - 72)

  return canvas
}

export function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('toBlob failed'))), 'image/png')
  })
}