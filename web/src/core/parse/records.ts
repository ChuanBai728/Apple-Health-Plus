import type { RawExtract, SleepStage, WorkoutActivity } from '@/types/extract'
import { attr, numAttr, unescapeXml } from '@/worker/attr'
import { dayOf, diffMinutes } from '@/core/dates'

const QUANTITY_WHITELIST = new Set([
  'HKQuantityTypeIdentifierStepCount',
  'HKQuantityTypeIdentifierDistanceWalkingRunning',
  'HKQuantityTypeIdentifierFlightsClimbed',
  'HKQuantityTypeIdentifierTimeInDaylight',
  'HKQuantityTypeIdentifierRestingHeartRate',
  'HKQuantityTypeIdentifierHeartRateVariabilitySDNN',
])

const SLEEP_TYPE = 'HKCategoryTypeIdentifierSleepAnalysis'

const SLEEP_STAGE: Record<string, SleepStage> = {
  HKCategoryValueSleepAnalysisAsleepCore: 'core',
  HKCategoryValueSleepAnalysisAsleepDeep: 'deep',
  HKCategoryValueSleepAnalysisAsleepREM: 'rem',
  HKCategoryValueSleepAnalysisAsleepUnspecified: 'unspecified',
  HKCategoryValueSleepAnalysisAsleep: 'unspecified',
  HKCategoryValueSleepAnalysisAwake: 'awake',
  HKCategoryValueSleepAnalysisInBed: 'inbed',
}

const ACTIVITY_MAP: Record<string, WorkoutActivity> = {
  HKWorkoutActivityTypeTraditionalStrengthTraining: 'strength',
  HKWorkoutActivityTypeFunctionalStrengthTraining: 'strength',
  HKWorkoutActivityTypeCrossTraining: 'strength',
  HKWorkoutActivityTypeWalking: 'walk',
  HKWorkoutActivityTypeHiking: 'walk',
  HKWorkoutActivityTypeCycling: 'cycling',
  HKWorkoutActivityTypeRunning: 'running',
  HKWorkoutActivityTypeStairs: 'stairs',
  HKWorkoutActivityTypeStairClimbing: 'stairs',
}

export interface ScanStats {
  lines: number
  kept: number
  ignored: number
  warnings: Set<string>
}

export function newScanStats(): ScanStats {
  return { lines: 0, kept: 0, ignored: 0, warnings: new Set() }
}

function addDaily(
  target: { [day: string]: { [src: string]: number } },
  day: string,
  source: string,
  value: number,
) {
  const bucket = target[day] ?? (target[day] = {})
  bucket[source] = (bucket[source] ?? 0) + value
}

function addAvg(target: { [day: string]: { sum: number; n: number } }, day: string, value: number) {
  const b = target[day] ?? (target[day] = { sum: 0, n: 0 })
  b.sum += value
  b.n++
}

/**
 * 逐行扫描（性能关键）：先用 indexOf 粗过滤，白名单 Set 精过滤。
 * 1.24 GB 里需要的行不到 2%，丢弃一行的成本必须尽量低。
 */
export function scanLine(line: string, out: RawExtract, stats: ScanStats): void {
  stats.lines++
  const isRecord =
    line.indexOf('type="HKQuantityTypeIdentifier') >= 0 ||
    line.indexOf('type="HKCategoryTypeIdentifier') >= 0
  const isSummary = line.indexOf('<ActivitySummary') >= 0
  const isWorkout = line.indexOf('<Workout ') >= 0
  if (!isRecord && !isSummary && !isWorkout) return

  if (isWorkout) {
    scanWorkout(line, out, stats)
    return
  }
  if (isSummary) {
    scanSummary(line, out, stats)
    return
  }

  const type = attr(line, 'type')
  if (type === null) return

  if (type === SLEEP_TYPE) {
    const rawValue = attr(line, 'value')
    const stage = rawValue === null ? undefined : SLEEP_STAGE[rawValue]
    if (stage === undefined) return
    const start = attr(line, 'startDate')
    const end = attr(line, 'endDate')
    if (start === null || end === null) return
    const minutes = diffMinutes(start, end)
    if (!(minutes > 0) || minutes > 24 * 60) {
      stats.ignored++
      return
    }
    out.sleepSegments.push({
      start,
      end,
      minutes,
      stage,
      source: unescapeXml(attr(line, 'sourceName') ?? '未知来源'),
    })
    stats.kept++
    return
  }

  if (!QUANTITY_WHITELIST.has(type)) return
  const value = numAttr(line, 'value')
  const start = attr(line, 'startDate')
  if (start === null || !Number.isFinite(value) || value < 0) {
    stats.ignored++
    return
  }
  const day = dayOf(start)
  const source = unescapeXml(attr(line, 'sourceName') ?? '未知来源')
  switch (type) {
    case 'HKQuantityTypeIdentifierStepCount':
      addDaily(out.stepCount, day, source, value)
      break
    case 'HKQuantityTypeIdentifierDistanceWalkingRunning':
      addDaily(out.distanceKm, day, source, value)
      break
    case 'HKQuantityTypeIdentifierFlightsClimbed':
      addDaily(out.flightsClimbed, day, source, value)
      break
    case 'HKQuantityTypeIdentifierTimeInDaylight':
      addDaily(out.daylightMin, day, source, value)
      break
    case 'HKQuantityTypeIdentifierRestingHeartRate':
      if (value >= 25 && value <= 220) addAvg(out.restingHr, day, value)
      else stats.ignored++
      break
    case 'HKQuantityTypeIdentifierHeartRateVariabilitySDNN':
      if (value > 0 && value <= 400) addAvg(out.hrv, day, value)
      else stats.ignored++
      break
  }
  stats.kept++
}

function scanSummary(line: string, out: RawExtract, stats: ScanStats): void {
  const dc = attr(line, 'dateComponents')
  if (dc === null || dc.length < 10) return
  const day = dc.slice(0, 10)
  const kcal = numAttr(line, 'activeEnergyBurned')
  const exerciseMin = numAttr(line, 'appleExerciseTime')
  const standHours = numAttr(line, 'appleStandHours')
  if (!Number.isFinite(kcal) && !Number.isFinite(exerciseMin) && !Number.isFinite(standHours))
    return
  out.rings[day] = {
    kcal: Number.isFinite(kcal) ? kcal : 0,
    exerciseMin: Number.isFinite(exerciseMin) ? exerciseMin : 0,
    standHours: Number.isFinite(standHours) ? standHours : 0,
  }
  stats.kept++
}

function scanWorkout(line: string, out: RawExtract, stats: ScanStats): void {
  const type = attr(line, 'workoutActivityType')
  const start = attr(line, 'startDate')
  if (type === null || start === null) return
  const end = attr(line, 'endDate') ?? start
  let minutes = numAttr(line, 'duration')
  const unit = attr(line, 'durationUnit')
  if (!Number.isFinite(minutes)) {
    minutes = diffMinutes(start, end)
  } else if (unit === 's') {
    minutes = minutes / 60
  } else if (unit === 'hr' || unit === 'h') {
    minutes = minutes * 60
  } else if (unit !== 'min') {
    stats.warnings.add(`遇到未知训练时长单位「${unit}」，已跳过该条记录`)
    stats.ignored++
    return
  }
  if (!(minutes > 0)) {
    stats.ignored++
    return
  }
  out.workouts.push({
    activity: ACTIVITY_MAP[type] ?? 'other',
    start,
    end,
    minutes,
    source: unescapeXml(attr(line, 'sourceName') ?? '未知来源'),
  })
  stats.kept++
}