import type { ReportModel } from '@/types/report'
import { seededPick } from '@/copy/seeded-random'
import { noonMinToClock } from '@/core/metrics/rhythm'

export interface CopyRule {
  priority: number
  when: (m: ReportModel) => boolean
  text: string
}

function resolvePath(m: unknown, path: string): unknown {
  let cur: unknown = m
  for (const part of path.split('.')) {
    if (cur === null || typeof cur !== 'object') return undefined
    cur = (cur as Record<string, unknown>)[part]
  }
  return cur
}

function formatDateCN(day: string): string {
  const m = Number(day.slice(5, 7))
  const d = Number(day.slice(8, 10))
  return `${m} 月 ${d} 日`
}

export function formatValue(v: unknown, fmt: string | null): string {
  if (v === undefined || v === null) return '—'
  if (fmt === 'date' && typeof v === 'string') return formatDateCN(v)
  if (fmt === 'month' && typeof v === 'string') return `${v.slice(0, 4)} 年 ${Number(v.slice(5, 7))} 月`
  if (typeof v === 'number') {
    if (!Number.isFinite(v)) return '—'
    if (fmt === 'clock') return noonMinToClock(v)
    if (fmt === 'wd') return '周' + '日一二三四五六'[v]
    if (fmt === 'month') return `${Number(String(v).slice(5, 7))} 月`
    if (fmt === ',') return Math.round(v).toLocaleString('zh-CN')
    if (fmt && fmt.startsWith('~')) {
      const digits = Number(fmt.slice(1)) || 1
      return v.toLocaleString('zh-CN', {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
      })
    }
    return Number.isInteger(v) ? v.toLocaleString('zh-CN') : v.toFixed(1)
  }
  return String(v)
}

export function renderText(text: string, m: ReportModel): string {
  return text.replace(/\{([\w.]+)(?::([^}]+))?\}/g, (_, path: string, fmt: string | undefined) =>
    formatValue(resolvePath(m, path), fmt ?? null),
  )
}

/** 按 priority 遍历，第一条 when 为真的规则胜出；同优先级用种子随机选一条 */
export function pickCopy(rules: CopyRule[], m: ReportModel, moduleName: string): string {
  const sorted = [...rules].sort((a, b) => a.priority - b.priority)
  let i = 0
  while (i < sorted.length) {
    const priority = sorted[i].priority
    const group: CopyRule[] = []
    while (i < sorted.length && sorted[i].priority === priority) {
      if (sorted[i].when(m)) group.push(sorted[i])
      i++
    }
    if (group.length > 0) {
      const seedKey = `${m.period.from}|${m.period.to}|${moduleName}`
      const rule = group.length === 1 ? group[0] : seededPick(group, seedKey)
      return renderText(rule.text, m)
    }
  }
  return ''
}