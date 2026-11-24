import type { HeartMetrics, VitalSeries } from '@/types/report'
import { monthOf } from '@/core/dates'
import { mean } from '@/core/stats'

function series(daily: Record<string, number>): VitalSeries {
  const days = Object.keys(daily).sort()
  const buckets: Record<string, number[]> = {}
  let min: VitalSeries['min'] = null
  let max: VitalSeries['max'] = null
  for (const d of days) {
    const v = daily[d]
    const m = monthOf(d)
    ;(buckets[m] ?? (buckets[m] = [])).push(v)
    if (min === null || v < min.value) min = { day: d, value: v }
    if (max === null || v > max.value) max = { day: d, value: v }
  }
  const monthly: Record<string, number> = {}
  for (const m in buckets) monthly[m] = mean(buckets[m])
  return { days: days.length, avg: mean(days.map((d) => daily[d])), min, max, monthly }
}

export function heartMetrics(
  restingHr: Record<string, number>,
  hrv: Record<string, number>,
): HeartMetrics {
  return { resting: series(restingHr), hrv: series(hrv) }
}
