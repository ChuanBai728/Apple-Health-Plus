/** Apple 导出日期格式：`2024-03-31 14:38:34 +0800`（本地时间 + 偏移量） */

export const dayOf = (s: string) => s.slice(0, 10)
export const hourOf = (s: string) => Number(s.slice(11, 13))
export const minuteOf = (s: string) => Number(s.slice(14, 16))

/** 转成可 Date.parse 的 ISO：2024-03-31T14:38:34+08:00 */
export function toISO(s: string): string {
  const t = s.replace(' ', 'T')
  const i = t.search(/[+-]\d{4}$/)
  if (i < 0) return t
  return t.slice(0, i).trimEnd() + t.slice(i, i + 3) + ':' + t.slice(i + 3)
}

export const msOf = (s: string) => Date.parse(toISO(s))

export function diffMinutes(start: string, end: string): number {
  return (msOf(end) - msOf(start)) / 60000
}

/** 纯日历日加减，用 UTC 承载避免时区/夏令时干扰 */
export function addDays(day: string, n: number): string {
  const d = new Date(day + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

export function dayDiff(a: string, b: string): number {
  return Math.round(
    (Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 86400000,
  )
}

export function weekdayOf(day: string): number {
  return new Date(day + 'T00:00:00Z').getUTCDay()
}

export const isWeekend = (day: string) => {
  const w = weekdayOf(day)
  return w === 0 || w === 6
}

export const yearOf = (day: string) => day.slice(0, 4)
export const monthOf = (day: string) => day.slice(0, 7)