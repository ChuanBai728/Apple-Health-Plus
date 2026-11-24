import type { CalendarMetrics } from '@/types/report'
import { addDays, monthOf, weekdayOf } from '@/core/dates'
import { mean } from '@/core/stats'
import { enumerateDays } from '@/core/metrics/streaks'

/** 全年活动日历：以每日步数为强度，周一为每列起点 */
export function calendarMetrics(
  steps: Record<string, number>,
  from: string,
  to: string,
): CalendarMetrics {
  const days = enumerateDays(from, to)
  const recorded = days.filter((d) => (steps[d] ?? 0) > 0)
  const sorted = recorded.map((d) => steps[d]).sort((a, b) => a - b)
  const q = (p: number) => (sorted.length ? sorted[Math.floor((sorted.length - 1) * p)] : 0)

  const byWeekday = Array.from({ length: 7 }, () => [] as number[])
  const byMonth: Record<string, number[]> = {}
  for (const d of recorded) {
    byWeekday[weekdayOf(d)].push(steps[d])
    const m = monthOf(d)
    ;(byMonth[m] ?? (byMonth[m] = [])).push(steps[d])
  }

  // 步数最多的连续 7 天：逐日遍历日历做滑动窗口
  let bestWeek: CalendarMetrics['bestWeek'] = null
  let win = 0
  for (let i = 0; i < days.length; i++) {
    win += steps[days[i]] ?? 0
    if (i >= 7) win -= steps[days[i - 7]] ?? 0
    if (i >= 6 && (bestWeek === null || win > bestWeek.total)) {
      bestWeek = { from: days[i - 6], to: days[i], total: win }
    }
  }

  let busiestMonth: CalendarMetrics['busiestMonth'] = null
  for (const m in byMonth) {
    const avg = mean(byMonth[m])
    if (busiestMonth === null || avg > busiestMonth.avg) busiestMonth = { month: m, avg }
  }

  const weekdayAvg = byWeekday.map((v) => mean(v))
  let topWeekday = 0
  weekdayAvg.forEach((v, i) => {
    if (v > weekdayAvg[topWeekday]) topWeekday = i
  })

  return {
    from,
    to,
    gridStart: addDays(from, -((weekdayOf(from) + 6) % 7)),
    totalDays: days.length,
    recordedDays: recorded.length,
    thresholds: [q(0.25), q(0.5), q(0.75)],
    weekdayAvg,
    topWeekday,
    bestWeek,
    busiestMonth,
  }
}
