import type { RingDay } from '@/types/extract'
import type { RingMetrics } from '@/types/report'
import { sum } from '@/core/stats'
import { longestStreak } from '@/core/metrics/streaks'

export const isClosed = (r: RingDay) =>
  r.standHours >= 12 && r.exerciseMin >= 30 && r.kcal > 0

export function ringMetrics(rings: Record<string, RingDay>): RingMetrics {
  const days = new Set(Object.keys(rings))
  let closedDays = 0
  for (const d of days) if (isClosed(rings[d])) closedDays++
  return {
    closedDays,
    daysWithData: days.size,
    rate: days.size ? closedDays / days.size : 0,
    streak: longestStreak(days, (d) => isClosed(rings[d])),
    exerciseHours: sum([...days].map((d) => rings[d].exerciseMin)) / 60,
  }
}