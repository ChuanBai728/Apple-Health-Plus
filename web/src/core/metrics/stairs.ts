import type { StairMetrics } from '@/types/report'
import { monthOf, yearOf } from '@/core/dates'
import { sum } from '@/core/stats'
import { everestCount } from '@/core/compare'
import { longestValueStreak } from '@/core/metrics/streaks'

export function stairMetrics(daily: Record<string, number>): StairMetrics {
  const days = Object.keys(daily).filter((d) => daily[d] > 0)
  const total = sum(days.map((d) => daily[d]))
  const monthly: Record<string, number> = {}
  const yearly: Record<string, number> = {}
  let maxDay: StairMetrics['maxDay'] = null
  for (const d of days) {
    const m = monthOf(d)
    monthly[m] = (monthly[m] ?? 0) + daily[d]
    const y = yearOf(d)
    yearly[y] = (yearly[y] ?? 0) + daily[d]
    if (maxDay === null || daily[d] > maxDay.value) maxDay = { day: d, value: daily[d] }
  }
  return {
    total,
    everest: everestCount(total),
    maxDay,
    streak1: longestValueStreak(daily, 1),
    streak10: longestValueStreak(daily, 10),
    monthly,
    yearly,
    days: days.sort(),
  }
}