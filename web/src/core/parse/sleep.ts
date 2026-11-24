import type { SleepSegment } from '@/types/extract'
import { addDays, dayOf, hourOf, minuteOf } from '@/core/dates'

const ASLEEP = new Set(['core', 'deep', 'rem', 'unspecified'])

export const isAsleep = (s: SleepSegment) => ASLEEP.has(s.stage)

/** 小睡段落：Asleep* 且开始时间在 10:00–19:59 */
export function isNapSegment(s: SleepSegment): boolean {
  if (!isAsleep(s)) return false
  const h = hourOf(s.start)
  return h >= 10 && h <= 19
}

export interface NapDay {
  day: string
  minutes: number
  startHour: number
}

/**
 * 同一天同一来源的小睡时长相加；多来源取最大值。
 * 一次有效小睡 = 当天 ≥ 20 分钟，一天最多一次。Awake / InBed 不计。
 */
export function buildNapDays(segments: SleepSegment[]): NapDay[] {
  const byDaySource: Record<string, Record<string, { minutes: number; firstHour: number }>> = {}
  for (const s of segments) {
    if (!isNapSegment(s)) continue
    const day = dayOf(s.start)
    const bucket = byDaySource[day] ?? (byDaySource[day] = {})
    const cur = bucket[s.source] ?? (bucket[s.source] = { minutes: 0, firstHour: 24 })
    cur.minutes += s.minutes
    const h = hourOf(s.start)
    if (h < cur.firstHour) cur.firstHour = h
  }
  const out: NapDay[] = []
  for (const day in byDaySource) {
    let best = { minutes: 0, firstHour: 0 }
    for (const src in byDaySource[day]) {
      const v = byDaySource[day][src]
      if (v.minutes > best.minutes) best = v
    }
    if (best.minutes >= 20) out.push({ day, minutes: best.minutes, startHour: best.firstHour })
  }
  return out.sort((a, b) => (a.day < b.day ? -1 : 1))
}

export interface Night {
  day: string
  minutes: number
  deepMin: number
  remMin: number
}

/** 夜晚归属：凌晨（<10 点）的段落算前一夜；小睡时段（10–19 点）排除在夜晚之外 */
export function nightKeyOf(s: SleepSegment): string {
  const day = dayOf(s.start)
  return hourOf(s.start) < 10 ? addDays(day, -1) : day
}

export function buildNights(segments: SleepSegment[]): Night[] {
  const byNightSource: Record<string, Record<string, Night>> = {}
  for (const s of segments) {
    if (!isAsleep(s) || isNapSegment(s)) continue
    const key = nightKeyOf(s)
    const bucket = byNightSource[key] ?? (byNightSource[key] = {})
    const cur = bucket[s.source] ?? (bucket[s.source] = { day: key, minutes: 0, deepMin: 0, remMin: 0 })
    cur.minutes += s.minutes
    if (s.stage === 'deep') cur.deepMin += s.minutes
    if (s.stage === 'rem') cur.remMin += s.minutes
  }
  const out: Night[] = []
  for (const key in byNightSource) {
    let best: Night | null = null
    for (const src in byNightSource[key]) {
      const v = byNightSource[key][src]
      if (best === null || v.minutes > best.minutes) best = v
    }
    if (best && best.minutes <= 24 * 60) out.push(best)
  }
  return out.sort((a, b) => (a.day < b.day ? -1 : 1))
}
export interface NightWindow {
  day: string
  /** 入睡时刻：距夜晚归属日 12:00 的分钟数（23:30 → 690，次日 01:00 → 780） */
  bedMin: number
  /** 醒来时刻：同样距归属日 12:00（次日 07:00 → 1140） */
  wakeMin: number
  asleepMin: number
}

function minutesFromNoon(key: string, ts: string): number {
  const offsetDays = dayOf(ts) === key ? 0 : 1
  return offsetDays * 1440 + hourOf(ts) * 60 + minuteOf(ts) - 720
}

/** 每晚取时长最大的来源，记录它的最早入睡与最晚醒来 */
export function buildNightWindows(segments: SleepSegment[]): NightWindow[] {
  const by: Record<string, Record<string, { bed: number; wake: number; asleep: number }>> = {}
  for (const s of segments) {
    if (!isAsleep(s) || isNapSegment(s)) continue
    const key = nightKeyOf(s)
    const bucket = by[key] ?? (by[key] = {})
    const bed = minutesFromNoon(key, s.start)
    const wake = minutesFromNoon(key, s.end)
    const cur = bucket[s.source] ?? (bucket[s.source] = { bed, wake, asleep: 0 })
    if (bed < cur.bed) cur.bed = bed
    if (wake > cur.wake) cur.wake = wake
    cur.asleep += s.minutes
  }
  const out: NightWindow[] = []
  for (const key in by) {
    let best: { bed: number; wake: number; asleep: number } | null = null
    for (const src in by[key]) {
      const v = by[key][src]
      if (best === null || v.asleep > best.asleep) best = v
    }
    if (!best || best.asleep < 120 || best.asleep > 24 * 60) continue
    // 入睡在 18:00 – 次日 06:00 之间才计入夜间作息
    if (best.bed < 360 || best.bed > 1080) continue
    out.push({ day: key, bedMin: best.bed, wakeMin: best.wake, asleepMin: best.asleep })
  }
  return out.sort((a, b) => (a.day < b.day ? -1 : 1))
}
