import { addDays, dayDiff } from '@/core/dates'

export interface Streak {
  length: number
  from: string
  to: string
}

export const EMPTY_STREAK: Streak = { length: 0, from: '', to: '' }

/**
 * 日历连续天数：逐日遍历日历，缺失的日子算中断。
 * 不在「有记录的日子」之间数个数，否则隔了三个月的两次记录会被算成连续。
 */
export function longestStreak(
  days: Set<string>,
  predicate: (day: string) => boolean,
): Streak {
  const qualifying = [...days].filter(predicate).sort()
  let best: Streak = { ...EMPTY_STREAK }
  let cur: Streak = { ...EMPTY_STREAK }
  for (const day of qualifying) {
    if (cur.length > 0 && dayDiff(cur.to, day) === 1) {
      cur = { length: cur.length + 1, from: cur.from, to: day }
    } else {
      cur = { length: 1, from: day, to: day }
    }
    if (cur.length > best.length) best = { ...cur }
  }
  return best
}

/** 数值版连续：days 里每天的值 ≥ threshold */
export function longestValueStreak(
  daily: Record<string, number>,
  threshold: number,
): Streak {
  return longestStreak(new Set(Object.keys(daily)), (d) => daily[d] >= threshold)
}

export interface Gap {
  days: number
  from: string
  to: string
}

/** 相邻两个记录日之间的最长间隔（含端点差值，间隔天数 = 中间空白天数 + 1 的日差） */
export function longestGap(days: Set<string>): Gap | null {
  const sorted = [...days].sort()
  if (sorted.length < 2) return null
  let best: Gap | null = null
  for (let i = 1; i < sorted.length; i++) {
    const d = dayDiff(sorted[i - 1], sorted[i])
    if (best === null || d > best.days) {
      best = { days: d, from: sorted[i - 1], to: sorted[i] }
    }
  }
  return best
}

export function enumerateDays(from: string, to: string): string[] {
  const out: string[] = []
  let d = from
  while (d <= to) {
    out.push(d)
    d = addDays(d, 1)
  }
  return out
}