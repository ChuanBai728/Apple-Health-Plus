import type { Workout, WorkoutActivity } from '@/types/extract'
import type { TrainingMetrics, TrainingGroup, DayValue } from '@/types/report'
import { dayOf, hourOf, monthOf, weekdayOf, yearOf } from '@/core/dates'
import { median, sum } from '@/core/stats'
import { longestStreak, longestGap } from '@/core/metrics/streaks'

const ACTIVITY_ORDER: WorkoutActivity[] = ['strength', 'walk', 'cycling', 'running', 'stairs', 'other']

export const MAX_WORKOUT_MIN = 240

export function slotLabel(hour: number): string {
  if (hour < 6) return '凌晨'
  if (hour < 12) return '上午'
  if (hour < 17) return '下午'
  if (hour < 20) return '傍晚'
  return '晚上'
}

export function trainingMetrics(workouts: Workout[], periodTo: string): TrainingMetrics {
  let forgotten = 0
  const valid: Workout[] = []
  for (const w of workouts) {
    if (w.minutes < 1) continue
    if (w.minutes > MAX_WORKOUT_MIN) {
      forgotten++
      continue
    }
    valid.push(w)
  }

  const minutes = valid.map((w) => w.minutes)
  const days = new Set(valid.map((w) => dayOf(w.start)))
  const monthly: Record<string, number> = {}
  const yearly: Record<string, number> = {}
  const weekday = new Array(7).fill(0)
  const hours: number[] = []
  let nightCount = 0
  for (const w of valid) {
    const day = dayOf(w.start)
    const m = monthOf(day)
    monthly[m] = (monthly[m] ?? 0) + 1
    const y = yearOf(day)
    yearly[y] = (yearly[y] ?? 0) + 1
    weekday[weekdayOf(day)]++
    const h = hourOf(w.start)
    hours.push(h)
    if (h >= 20) nightCount++
  }

  const groups: TrainingGroup[] = []
  for (const activity of ACTIVITY_ORDER) {
    const list = valid.filter((w) => w.activity === activity)
    if (list.length === 0) continue
    const longestW = list.reduce((a, b) => (b.minutes > a.minutes ? b : a))
    groups.push({
      activity,
      count: list.length,
      hours: sum(list.map((w) => w.minutes)) / 60,
      medianMin: median(list.map((w) => w.minutes)),
      longest: { day: dayOf(longestW.start), value: longestW.minutes },
    })
  }

  let longest: DayValue | null = null
  if (valid.length) {
    const w = valid.reduce((a, b) => (b.minutes > a.minutes ? b : a))
    longest = { day: dayOf(w.start), value: w.minutes }
  }

  let bestYear: TrainingMetrics['bestYear'] = null
  for (const y in yearly) {
    if (bestYear === null || yearly[y] > bestYear.count) bestYear = { year: y, count: yearly[y] }
  }

  const rawGap = longestGap(days)
  let gap: TrainingMetrics['gap'] = null
  if (rawGap) {
    const comeback = valid.filter((w) => dayOf(w.start) >= rawGap.to).length
    gap = { ...rawGap, comeback }
  }

  return {
    count: valid.length,
    hours: sum(minutes) / 60,
    medianMin: median(minutes),
    longest,
    forgotten,
    groups,
    monthly,
    yearly,
    bestYear,
    streak: longestStreak(days, () => true),
    gap,
    primarySlot: hours.length ? slotLabel(median(hours)) : '—',
    nightShare: valid.length ? nightCount / valid.length : 0,
    weekday,
    days: [...days].sort(),
  }
}