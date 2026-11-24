import type { StepMetrics } from '@/types/report'
import { monthOf } from '@/core/dates'
import { median, percentile, sum, mean } from '@/core/stats'
import { marathonCount, equatorPct } from '@/core/compare'
import { longestValueStreak } from '@/core/metrics/streaks'

export function stepMetrics(
  steps: Record<string, number>,
  distance: Record<string, number>,
): StepMetrics {
  const days = Object.keys(steps).filter((d) => steps[d] > 0)
  const values = days.map((d) => steps[d])
  const total = sum(values)
  let maxDay: StepMetrics['maxDay'] = null
  let days10k = 0
  const byMonth: Record<string, number[]> = {}
  for (const d of days) {
    if (maxDay === null || steps[d] > maxDay.value) maxDay = { day: d, value: steps[d] }
    if (steps[d] >= 10000) days10k++
    const m = monthOf(d)
    ;(byMonth[m] ?? (byMonth[m] = [])).push(steps[d])
  }
  const monthlyAvg: Record<string, number> = {}
  let bestMonth: StepMetrics['bestMonth'] = null
  let worstMonth: StepMetrics['worstMonth'] = null
  for (const m of Object.keys(byMonth).sort()) {
    const v = mean(byMonth[m])
    monthlyAvg[m] = v
    if (bestMonth === null || v > bestMonth.value) bestMonth = { month: m, value: v }
    if (worstMonth === null || v < worstMonth.value) worstMonth = { month: m, value: v }
  }
  const totalKm = sum(Object.values(distance))
  return {
    total,
    median: median(values),
    p90: percentile(values, 90),
    maxDay,
    days10k,
    pct10k: days.length ? (days10k / days.length) * 100 : 0,
    streak10k: longestValueStreak(steps, 10000),
    streak7k: longestValueStreak(steps, 7000),
    monthlyAvg,
    bestMonth,
    worstMonth,
    totalKm,
    marathons: marathonCount(totalKm),
    equatorPct: equatorPct(totalKm),
    recordedDays: days.length,
  }
}