export const fmtInt = (n: number) => Math.round(n).toLocaleString('zh-CN')
export const fmt1 = (n: number) =>
  n.toLocaleString('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
export const fmt2 = (n: number) =>
  n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export function dateCN(day: string): string {
  if (!day) return '—'
  return `${Number(day.slice(5, 7))} 月 ${Number(day.slice(8, 10))} 日`
}

export function dateCNFull(day: string): string {
  if (!day) return '—'
  return `${day.slice(0, 4)} 年 ${Number(day.slice(5, 7))} 月 ${Number(day.slice(8, 10))} 日`
}

export function monthCN(month: string): string {
  return `${month.slice(2, 4)}.${Number(month.slice(5, 7))}`
}

export function minutesCN(min: number): string {
  if (min >= 60) {
    const h = Math.floor(min / 60)
    const m = Math.round(min % 60)
    return m > 0 ? `${h} 小时 ${m} 分钟` : `${h} 小时`
  }
  return `${Math.round(min)} 分钟`
}

export function bytesCN(n: number): string {
  if (n >= 1024 * 1024 * 1024) return (n / 1024 / 1024 / 1024).toFixed(2) + ' GB'
  if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + ' MB'
  if (n >= 1024) return (n / 1024).toFixed(0) + ' KB'
  return n + ' B'
}

export function durationCN(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '—'
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`
}