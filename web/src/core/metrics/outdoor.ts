import type { OutdoorMetrics } from '@/types/report'
import type { RingDay } from '@/types/extract'
import { monthOf, weekdayOf } from '@/core/dates'
import { mean, sum } from '@/core/stats'

/** 户外日光 + 站立 + 活动能量 */
export function outdoorMetrics(
  daylight: Record<string, number>,
  rings: Record<string, RingDay>,
): OutdoorMetrics {
  const dDays = Object.keys(daylight).filter((d) => daylight[d] > 0).sort()
  const buckets: Record<string, number[]> = {}
  let maxDay: OutdoorMetrics['daylightMax'] = null
  for (const d of dDays) {
    const m = monthOf(d)
    ;(buckets[m] ?? (buckets[m] = [])).push(daylight[d])
    if (maxDay === null || daylight[d] > maxDay.value) maxDay = { day: d, value: daylight[d] }
  }
  const daylightMonthly: Record<string, number> = {}
  for (const m in buckets) daylightMonthly[m] = mean(buckets[m])

  const rDays = Object.keys(rings).sort()
  const standByWeekday = Array.from({ length: 7 }, () => [] as number[])
  for (const d of rDays) standByWeekday[weekdayOf(d)].push(rings[d].standHours)
  return {
    daylightDays: dDays.length,
    daylightHours: sum(dDays.map((d) => daylight[d])) / 60,
    daylightAvgMin: mean(dDays.map((d) => daylight[d])),
    daylightMax: maxDay,
    daylightMonthly,
    standDays: rDays.length,
    standAvgHours: mean(rDays.map((d) => rings[d].standHours)),
    stand12Days: rDays.filter((d) => rings[d].standHours >= 12).length,
    standByWeekday: standByWeekday.map((v) => mean(v)),
    kcalTotal: sum(rDays.map((d) => rings[d].kcal)),
    kcalAvg: mean(rDays.map((d) => rings[d].kcal)),
  }
}
