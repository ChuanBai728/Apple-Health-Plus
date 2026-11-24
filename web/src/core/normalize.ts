import type { DailyBySource, RawExtract } from '@/types/extract'

/** 同一天多个来源时取总量最大的那个来源，不做相加（Watch 与 iPhone 高度重叠） */
export function dailyMax(bySource: DailyBySource): Record<string, number> {
  const out: Record<string, number> = {}
  for (const day in bySource) {
    let max = 0
    const sources = bySource[day]
    for (const src in sources) {
      const v = sources[src]
      if (v > max) max = v
    }
    out[day] = max
  }
  return out
}

export interface SanitizeResult {
  ignored: number
}

const DAILY_CAPS: Array<[keyof RawExtract, number]> = [
  ['stepCount', 100000],
  ['distanceKm', 200],
  ['flightsClimbed', 500],
]

/** 单日异常值：超过上限则丢弃当天该来源 */
export function sanitizeDaily(extract: RawExtract): SanitizeResult {
  let ignored = 0
  for (const [key, cap] of DAILY_CAPS) {
    const bySource = extract[key] as DailyBySource
    for (const day in bySource) {
      const sources = bySource[day]
      for (const src in sources) {
        if (sources[src] > cap) {
          delete sources[src]
          ignored++
        }
      }
      if (Object.keys(sources).length === 0) delete bySource[day]
    }
  }
  return { ignored }
}

export function filterDaily(bySource: DailyBySource, from: string, to: string): DailyBySource {
  const out: DailyBySource = {}
  for (const day in bySource) {
    if (day >= from && day <= to) out[day] = bySource[day]
  }
  return out
}

function filterKeyed<T>(obj: { [day: string]: T }, from: string, to: string): { [day: string]: T } {
  const out: { [day: string]: T } = {}
  for (const day in obj) if (day >= from && day <= to) out[day] = obj[day]
  return out
}

/** 均值型日数据 -> day -> 当日均值 */
export function dailyAvg(src: { [day: string]: { sum: number; n: number } }): Record<string, number> {
  const out: Record<string, number> = {}
  for (const day in src) if (src[day].n > 0) out[day] = src[day].sum / src[day].n
  return out
}

export function filterExtract(extract: RawExtract, from: string, to: string): RawExtract {
  const rings: RawExtract['rings'] = {}
  for (const day in extract.rings) {
    if (day >= from && day <= to) rings[day] = extract.rings[day]
  }
  return {
    stepCount: filterDaily(extract.stepCount, from, to),
    distanceKm: filterDaily(extract.distanceKm, from, to),
    flightsClimbed: filterDaily(extract.flightsClimbed, from, to),
    daylightMin: filterDaily(extract.daylightMin, from, to),
    rings,
    restingHr: filterKeyed(extract.restingHr ?? {}, from, to),
    hrv: filterKeyed(extract.hrv ?? {}, from, to),
    sleepSegments: extract.sleepSegments.filter((s) => {
      const d = s.start.slice(0, 10)
      return d >= from && d <= to
    }),
    workouts: extract.workouts.filter((w) => {
      const d = w.start.slice(0, 10)
      return d >= from && d <= to
    }),
  }
}

export function extractBounds(extract: RawExtract): { from: string; to: string } | null {
  let from = '9999-12-31'
  let to = '0000-01-01'
  const visit = (day: string) => {
    if (day < from) from = day
    if (day > to) to = day
  }
  for (const key of ['stepCount', 'distanceKm', 'flightsClimbed', 'daylightMin'] as const) {
    for (const day in extract[key]) visit(day)
  }
  for (const day in extract.rings) visit(day)
  for (const day in extract.restingHr ?? {}) visit(day)
  for (const s of extract.sleepSegments) visit(s.start.slice(0, 10))
  for (const w of extract.workouts) visit(w.start.slice(0, 10))
  return from <= to ? { from, to } : null
}