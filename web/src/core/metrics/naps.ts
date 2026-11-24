import type { NapMetrics, DayValue } from '@/types/report'
import type { SleepSegment } from '@/types/extract'
import { buildNapDays, buildNights } from '@/core/parse/sleep'
import { isWeekend, yearOf } from '@/core/dates'
import { median, mean, sum } from '@/core/stats'

export function napMetrics(segments: SleepSegment[]): NapMetrics {
  const naps = buildNapDays(segments)
  const minutes = naps.map((n) => n.minutes)
  const byYear: NapMetrics['byYear'] = {}
  const hourHistogram = new Array(10).fill(0)
  let weekdayCount = 0
  let weekdayMin: number[] = []
  let weekendCount = 0
  let weekendMin: number[] = []
  for (const n of naps) {
    const y = yearOf(n.day)
    const bucket = byYear[y] ?? (byYear[y] = { count: 0, hours: 0 })
    bucket.count++
    bucket.hours += n.minutes / 60
    const h = Math.min(19, Math.max(10, n.startHour))
    hourHistogram[h - 10]++
    if (isWeekend(n.day)) {
      weekendCount++
      weekendMin.push(n.minutes)
    } else {
      weekdayCount++
      weekdayMin.push(n.minutes)
    }
  }
  const sorted = [...naps].sort((a, b) => b.minutes - a.minutes)
  const longest: DayValue | null = sorted.length
    ? { day: sorted[0].day, value: sorted[0].minutes }
    : null
  const top5 = sorted.slice(0, 5).map((n) => ({ day: n.day, value: n.minutes }))

  const nights = buildNights(segments)
  const nightMin = sum(nights.map((n) => n.minutes))
  return {
    count: naps.length,
    totalHours: sum(minutes) / 60,
    avgMin: mean(minutes),
    medianMin: median(minutes),
    longest,
    byYear,
    hourHistogram,
    weekday: { count: weekdayCount, avgMin: mean(weekdayMin) },
    weekend: { count: weekendCount, avgMin: mean(weekendMin) },
    top5,
    nights: {
      count: nights.length,
      avgHours: nights.length ? nightMin / nights.length / 60 : 0,
      deepPct: nightMin ? (sum(nights.map((n) => n.deepMin)) / nightMin) * 100 : 0,
      remPct: nightMin ? (sum(nights.map((n) => n.remMin)) / nightMin) * 100 : 0,
    },
  }
}