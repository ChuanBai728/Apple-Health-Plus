import type { RhythmMetrics } from '@/types/report'
import type { SleepSegment } from '@/types/extract'
import { buildNightWindows } from '@/core/parse/sleep'
import { monthOf, weekdayOf } from '@/core/dates'
import { mean } from '@/core/stats'

function stdev(values: number[]): number {
  if (values.length < 2) return 0
  const m = mean(values)
  return Math.sqrt(mean(values.map((v) => (v - m) ** 2)))
}

/** 距中午的分钟数 -> 钟面时刻 "23:40" */
export function noonMinToClock(min: number): string {
  const t = (((Math.round(min) + 720) % 1440) + 1440) % 1440
  return `${String(Math.floor(t / 60)).padStart(2, '0')}:${String(t % 60).padStart(2, '0')}`
}

export function rhythmMetrics(segments: SleepSegment[]): RhythmMetrics {
  const nights = buildNightWindows(segments)
  // 入睡小时分布：20 点 – 次日 3 点，共 8 格（bedMin 480 = 20:00）
  const bedHistogram = new Array(8).fill(0)
  const monthly: Record<string, { bed: number[]; wake: number[]; asleep: number[] }> = {}
  const wkBed: number[] = []
  const weBed: number[] = []
  let earliest: RhythmMetrics['earliestBed'] = null
  let latest: RhythmMetrics['latestBed'] = null
  for (const n of nights) {
    const idx = Math.floor((n.bedMin - 480) / 60)
    bedHistogram[Math.min(7, Math.max(0, idx))]++
    const b = monthly[monthOf(n.day)] ?? (monthly[monthOf(n.day)] = { bed: [], wake: [], asleep: [] })
    b.bed.push(n.bedMin)
    b.wake.push(n.wakeMin)
    b.asleep.push(n.asleepMin)
    // 周五、周六晚上算周末夜
    const wd = weekdayOf(n.day)
    if (wd === 5 || wd === 6) weBed.push(n.bedMin)
    else wkBed.push(n.bedMin)
    if (earliest === null || n.bedMin < earliest.value) earliest = { day: n.day, value: n.bedMin }
    if (latest === null || n.bedMin > latest.value) latest = { day: n.day, value: n.bedMin }
  }
  const monthlyOut: RhythmMetrics['monthly'] = {}
  for (const m in monthly) {
    monthlyOut[m] = {
      bed: mean(monthly[m].bed),
      wake: mean(monthly[m].wake),
      asleepHours: mean(monthly[m].asleep) / 60,
    }
  }
  const beds = nights.map((n) => n.bedMin)
  return {
    nights: nights.length,
    avgBed: mean(beds),
    avgWake: mean(nights.map((n) => n.wakeMin)),
    avgAsleepHours: mean(nights.map((n) => n.asleepMin)) / 60,
    bedSpreadMin: stdev(beds),
    earliestBed: earliest,
    latestBed: latest,
    weekendShiftMin: wkBed.length && weBed.length ? mean(weBed) - mean(wkBed) : 0,
    bedHistogram,
    monthly: monthlyOut,
  }
}
