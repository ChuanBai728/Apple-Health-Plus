import type { RawExtract } from '@/types/extract'
import type { Period, ReportModel } from '@/types/report'
import { dayDiff, yearOf } from '@/core/dates'
import { dailyMax, dailyAvg, filterExtract, extractBounds } from '@/core/normalize'
import { calendarMetrics } from '@/core/metrics/calendar'
import { rhythmMetrics } from '@/core/metrics/rhythm'
import { heartMetrics } from '@/core/metrics/heart'
import { outdoorMetrics } from '@/core/metrics/outdoor'
import { calendarRules, rhythmRules, heartRules, outdoorRules } from '@/copy/rules/extra'
import { napMetrics } from '@/core/metrics/naps'
import { trainingMetrics } from '@/core/metrics/training'
import { stairMetrics } from '@/core/metrics/stairs'
import { stepMetrics } from '@/core/metrics/steps'
import { ringMetrics } from '@/core/metrics/rings'
import { longestStreak } from '@/core/metrics/streaks'
import { decidePersona } from '@/core/persona'
import { pickCopy } from '@/copy/engine'
import { personaRules } from '@/copy/rules/persona'
import { napRules } from '@/copy/rules/naps'
import { trainingRules } from '@/copy/rules/training'
import { stairRules } from '@/copy/rules/stairs'
import { distanceRules } from '@/copy/rules/distance'
import { dayOf } from '@/core/dates'

export function buildPeriods(extract: RawExtract): Period[] {
  const bounds = extractBounds(extract)
  if (!bounds) return []
  const make = (key: string, label: string, from: string, to: string): Period => {
    const days = dayDiff(from, to) + 1
    return {
      key,
      label,
      from,
      to,
      days,
      years: days / 365,
      incomplete: key !== 'all' && to < `${key}-12-31`,
    }
  }
  const periods: Period[] = [make('all', '全部', bounds.from, bounds.to)]
  const years = new Set<string>()
  for (let y = Number(yearOf(bounds.from)); y <= Number(yearOf(bounds.to)); y++) years.add(String(y))
  for (const y of [...years].sort()) {
    const from = bounds.from > `${y}-01-01` ? bounds.from : `${y}-01-01`
    const to = bounds.to < `${y}-12-31` ? bounds.to : `${y}-12-31`
    const p = make(y, y + ' 年', from, to)
    if (p.incomplete) {
      p.label = `${y} 年（截至 ${Number(to.slice(5, 7))} 月 ${Number(to.slice(8, 10))} 日）`
    }
    periods.push(p)
  }
  return periods
}

export interface BuildMeta {
  ignored: number
  warnings: string[]
}

export function buildReport(extract: RawExtract, periodKey: string, meta: BuildMeta): ReportModel {
  const periods = buildPeriods(extract)
  const period = periods.find((p) => p.key === periodKey) ?? periods[0]
  const scoped = filterExtract(extract, period.from, period.to)

  const steps = dailyMax(scoped.stepCount)
  const distance = dailyMax(scoped.distanceKm)
  const flights = dailyMax(scoped.flightsClimbed)

  const naps = napMetrics(scoped.sleepSegments)
  const training = trainingMetrics(scoped.workouts, period.to)
  const stairs = stairMetrics(flights)
  const stepM = stepMetrics(steps, distance)
  const rings = ringMetrics(scoped.rings)
  const calendar = calendarMetrics(steps, period.from, period.to)
  const rhythm = rhythmMetrics(scoped.sleepSegments)
  const heart = heartMetrics(dailyAvg(scoped.restingHr), dailyAvg(scoped.hrv))
  const outdoor = outdoorMetrics(dailyMax(scoped.daylightMin), scoped.rings)

  const strengthDays = new Set(
    scoped.workouts
      .filter((w) => w.activity === 'strength' && w.minutes >= 1 && w.minutes <= 240)
      .map((w) => dayOf(w.start)),
  )
  const strengthStreak = longestStreak(strengthDays, () => true)

  const persona = decidePersona({
    years: period.years,
    naps,
    training,
    stairs,
    steps: stepM,
    rings,
    strengthStreak,
    stairRecordedDays: stairs.days.length,
  })

  const allBounds = extractBounds(extract)
  const ringDays = Object.keys(extract.rings).sort()
  let coverageNotice: string | null = null
  if (allBounds && ringDays.length > 0 && dayDiff(allBounds.from, ringDays[0]) > 30) {
    const d = ringDays[0]
    coverageNotice = `活动圆环等手表记录从 ${d.slice(0, 4)} 年 ${Number(d.slice(5, 7))} 月才开始，之前的数据主要来自 iPhone 和其他 App。`
  }

  const model: ReportModel = {
    period,
    periods,
    coverageNotice,
    ignored: meta.ignored,
    warnings: meta.warnings,
    persona,
    naps,
    training,
    stairs,
    steps: stepM,
    rings,
    stepsDaily: steps,
    calendar,
    rhythm,
    heart,
    outdoor,
    copy: {
      persona: '',
      naps: '',
      training: '',
      stairs: '',
      distance: '',
      calendar: '',
      rhythm: '',
      heart: '',
      outdoor: '',
    },
  }
  model.copy.persona = pickCopy(personaRules, model, 'persona')
  model.copy.naps = pickCopy(napRules, model, 'naps')
  model.copy.training = pickCopy(trainingRules, model, 'training')
  model.copy.stairs = pickCopy(stairRules, model, 'stairs')
  model.copy.distance = pickCopy(distanceRules, model, 'distance')
  model.copy.calendar = pickCopy(calendarRules, model, 'calendar')
  model.copy.rhythm = pickCopy(rhythmRules, model, 'rhythm')
  model.copy.heart = pickCopy(heartRules, model, 'heart')
  model.copy.outdoor = pickCopy(outdoorRules, model, 'outdoor')
  return model
}