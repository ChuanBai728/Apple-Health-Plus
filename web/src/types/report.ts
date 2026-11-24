import type { Streak, Gap } from '@/core/metrics/streaks'
import type { WorkoutActivity } from '@/types/extract'

export interface Period {
  key: string
  label: string
  from: string
  to: string
  days: number
  /** days / 365，用于折算年均阈值 */
  years: number
  incomplete: boolean
}

export interface DayValue {
  day: string
  value: number
}

export interface NapMetrics {
  count: number
  totalHours: number
  avgMin: number
  medianMin: number
  longest: DayValue | null
  byYear: Record<string, { count: number; hours: number }>
  hourHistogram: number[]
  weekday: { count: number; avgMin: number }
  weekend: { count: number; avgMin: number }
  top5: DayValue[]
  nights: {
    count: number
    avgHours: number
    deepPct: number
    remPct: number
  }
}

export interface TrainingGroup {
  activity: WorkoutActivity
  count: number
  hours: number
  medianMin: number
  longest: DayValue | null
}

export interface TrainingMetrics {
  count: number
  hours: number
  medianMin: number
  longest: DayValue | null
  forgotten: number
  groups: TrainingGroup[]
  monthly: Record<string, number>
  yearly: Record<string, number>
  bestYear: { year: string; count: number } | null
  streak: Streak
  gap: (Gap & { comeback: number }) | null
  primarySlot: string
  nightShare: number
  weekday: number[]
  days: string[]
}

export interface StairMetrics {
  total: number
  everest: number
  maxDay: DayValue | null
  streak1: Streak
  streak10: Streak
  monthly: Record<string, number>
  yearly: Record<string, number>
  days: string[]
}

export interface StepMetrics {
  total: number
  median: number
  p90: number
  maxDay: DayValue | null
  days10k: number
  pct10k: number
  streak10k: Streak
  streak7k: Streak
  monthlyAvg: Record<string, number>
  bestMonth: { month: string; value: number } | null
  worstMonth: { month: string; value: number } | null
  totalKm: number
  marathons: number
  equatorPct: number
  recordedDays: number
}

export interface RingMetrics {
  closedDays: number
  daysWithData: number
  rate: number
  streak: Streak
  exerciseHours: number
}

export interface CalendarMetrics {
  from: string
  to: string
  /** 网格起点（from 所在周的周一） */
  gridStart: string
  totalDays: number
  recordedDays: number
  /** 步数四分位阈值 [p25, p50, p75]，用于热力色阶 */
  thresholds: [number, number, number]
  /** 下标 0 = 周日 */
  weekdayAvg: number[]
  topWeekday: number
  bestWeek: { from: string; to: string; total: number } | null
  busiestMonth: { month: string; avg: number } | null
}

export interface RhythmMetrics {
  nights: number
  /** 以下时刻均为距夜晚归属日 12:00 的分钟数 */
  avgBed: number
  avgWake: number
  avgAsleepHours: number
  /** 入睡时刻标准差（分钟） */
  bedSpreadMin: number
  earliestBed: DayValue | null
  latestBed: DayValue | null
  /** 周末夜比工作日夜晚睡多少分钟（可为负） */
  weekendShiftMin: number
  /** 20 点 – 次日 3 点，每小时一格 */
  bedHistogram: number[]
  monthly: Record<string, { bed: number; wake: number; asleepHours: number }>
}

export interface VitalSeries {
  days: number
  avg: number
  min: DayValue | null
  max: DayValue | null
  monthly: Record<string, number>
}

export interface HeartMetrics {
  resting: VitalSeries
  hrv: VitalSeries
}

export interface OutdoorMetrics {
  daylightDays: number
  daylightHours: number
  daylightAvgMin: number
  daylightMax: DayValue | null
  daylightMonthly: Record<string, number>
  standDays: number
  standAvgHours: number
  stand12Days: number
  /** 下标 0 = 周日 */
  standByWeekday: number[]
  kcalTotal: number
  kcalAvg: number
}

export type PersonaKey = 'gym' | 'walker' | 'stairs' | 'steady'

export interface Persona {
  key: PersonaKey
  name: string
  tags: string[]
  stats: { label: string; value: string }[]
}

export interface ReportModel {
  period: Period
  periods: Period[]
  coverageNotice: string | null
  ignored: number
  warnings: string[]
  persona: Persona
  naps: NapMetrics
  training: TrainingMetrics
  stairs: StairMetrics
  steps: StepMetrics
  rings: RingMetrics
  stepsDaily: Record<string, number>
  calendar: CalendarMetrics
  rhythm: RhythmMetrics
  heart: HeartMetrics
  outdoor: OutdoorMetrics
  copy: {
    persona: string
    naps: string
    training: string
    stairs: string
    distance: string
    calendar: string
    rhythm: string
    heart: string
    outdoor: string
  }
}