import { seededRandom } from '@/copy/seeded-random'
import { addDays, weekdayOf } from '@/core/dates'

/**
 * 合成示例数据：走与真实导出完全相同的扫描管线。
 * 覆盖多来源重叠、跨午夜睡眠、小睡、异常训练时长、WorkoutStatistics 干扰行。
 */
export function generateSampleXml(): string {
  const rnd = seededRandom(20261124)
  const ri = (min: number, max: number) => min + Math.floor(rnd() * (max - min + 1))
  const watch = 'Apple Watch Series 9 '
  const phone = 'iPhone 15 Pro Max'
  const keep = 'Keep'
  const lines: string[] = ['<?xml version="1.0" encoding="UTF-8"?>', '<HealthData locale="zh_CN">']

  const stamp = (day: string, h: number, m: number) =>
    `${day} ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00 +0800`

  const rec = (type: string, source: string, unit: string, day: string, h: number, value: number | string) => {
    const start = stamp(day, h, 0)
    lines.push(
      `<Record type="${type}" sourceName="${source}" sourceVersion="10.5" unit="${unit}" creationDate="${start}" startDate="${start}" endDate="${stamp(day, h, 59)}" value="${value}"/>`,
    )
  }

  const from = '2025-01-01'
  const to = '2026-11-24'
  const watchFrom = '2025-02-01'
  let day = from
  while (day <= to) {
    const wd = weekdayOf(day)
    const hasWatch = day >= watchFrom
    const base = wd === 0 || wd === 6 ? ri(8500, 14000) : ri(5500, 10500)
    const steps = hasWatch ? base : ri(4500, 9000)
    if (hasWatch) rec('HKQuantityTypeIdentifierStepCount', watch, 'count', day, 9, steps)
    rec('HKQuantityTypeIdentifierStepCount', phone, 'count', day, 9, Math.round(steps * (0.8 + rnd() * 0.12)))
    if (rnd() < 0.15) rec('HKQuantityTypeIdentifierStepCount', '欢太健康', 'count', day, 10, ri(800, 2500))
    const km = steps * 0.00072
    if (hasWatch) rec('HKQuantityTypeIdentifierDistanceWalkingRunning', watch, 'km', day, 9, km.toFixed(3))
    rec('HKQuantityTypeIdentifierDistanceWalkingRunning', phone, 'km', day, 9, (km * 0.85).toFixed(3))

    const stairs = day >= '2026-06-18' ? ri(3, 22) : rnd() < 0.75 ? ri(0, 16) : 0
    if (stairs > 0 && hasWatch) rec('HKQuantityTypeIdentifierFlightsClimbed', watch, 'count', day, 12, stairs)

    if (hasWatch) {
      const kcal = ri(280, 720)
      const ex = ri(18, 62)
      const stand = ri(9, 14)
      lines.push(
        `<ActivitySummary dateComponents="${day}" activeEnergyBurned="${kcal}" activeEnergyBurnedGoal="500" appleExerciseTime="${ex}" appleExerciseTimeGoal="30" appleStandHours="${stand}" appleStandHoursGoal="12"/>`,
      )
      rec('HKQuantityTypeIdentifierTimeInDaylight', watch, 'min', day, 13, ri(8, 95))
      const season = Math.cos(((Number(day.slice(5, 7)) - 1) / 12) * Math.PI * 2)
      rec('HKQuantityTypeIdentifierRestingHeartRate', watch, 'count/min', day, 7, Math.round(58 + season * 2 + ri(-3, 3)))
      rec('HKQuantityTypeIdentifierHeartRateVariabilitySDNN', watch, 'ms', day, 3, (44 - season * 5 + ri(-12, 14)).toFixed(1))
      rec('HKQuantityTypeIdentifierHeartRateVariabilitySDNN', watch, 'ms', day, 15, (40 - season * 5 + ri(-12, 14)).toFixed(1))

      const sleepStart = wd === 5 || wd === 6 ? 23 : 22 + (rnd() < 0.3 ? 1 : 0)
      const segs: Array<[number, number, number, number, string]> = [
        [sleepStart, 30, 0, 0, 'AsleepCore'],
        [1, 0, 2, 10, 'AsleepDeep'],
        [2, 10, 3, 40, 'AsleepREM'],
        [3, 40, 5, 50, 'AsleepCore'],
        [5, 50, 6, 40 + ri(0, 19), 'AsleepREM'],
      ]
      lines.push(
        `<Record type="HKCategoryTypeIdentifierSleepAnalysis" sourceName="${watch}" sourceVersion="10.5" unit="" creationDate="${stamp(day, sleepStart, 20)}" startDate="${stamp(day, sleepStart, 20)}" endDate="${stamp(addDays(day, 1), 7, 0)}" value="HKCategoryValueSleepAnalysisInBed"/>`,
      )
      for (const [h1, m1, h2, m2, stage] of segs) {
        const sDay = h1 >= 22 ? day : addDays(day, 1)
        const eDay = h2 >= 22 ? day : addDays(day, 1)
        lines.push(
          `<Record type="HKCategoryTypeIdentifierSleepAnalysis" sourceName="${watch}" sourceVersion="10.5" unit="" creationDate="${stamp(sDay, h1, m1)}" startDate="${stamp(sDay, h1, m1)}" endDate="${stamp(eDay, h2, m2)}" value="HKCategoryValueSleepAnalysis${stage}"/>`,
        )
      }
      if (rnd() < 0.36) {
        const napLen = rnd() < 0.06 ? 19 : ri(25, 75)
        const nh = ri(12, 15)
        const endM = napLen
        lines.push(
          `<Record type="HKCategoryTypeIdentifierSleepAnalysis" sourceName="${watch}" sourceVersion="10.5" unit="" creationDate="${stamp(day, nh, 10)}" startDate="${stamp(day, nh, 10)}" endDate="${stamp(day, nh + Math.floor((10 + endM) / 60), (10 + endM) % 60)}" value="HKCategoryValueSleepAnalysisAsleepCore"/>`,
        )
      }
    }

    if (hasWatch && (wd === 2 || wd === 4 || wd === 6) && rnd() < 0.88) {
      const dur = ri(70, 95)
      const h = rnd() < 0.55 ? 20 : 18
      const start = stamp(day, h, 10)
      const end = stamp(day, h + 1, 10 + (dur - 60))
      lines.push(
        `<Workout workoutActivityType="HKWorkoutActivityTypeTraditionalStrengthTraining" duration="${dur}" durationUnit="min" sourceName="${watch}" sourceVersion="10.5" creationDate="${end}" startDate="${start}" endDate="${end}">`,
      )
      lines.push(
        ` <WorkoutStatistics type="HKQuantityTypeIdentifierHeartRate" startDate="${start}" endDate="${end}" average="121" minimum="88" maximum="165" unit="count/min"/>`,
      )
    }
    if (rnd() < 0.55) {
      const start = stamp(day, ri(8, 19), ri(0, 50))
      lines.push(
        `<Workout workoutActivityType="HKWorkoutActivityTypeWalking" duration="12" durationUnit="min" sourceName="${keep}" sourceVersion="5.0" creationDate="${start}" startDate="${start}" endDate="${stamp(day, 20, 0)}"/>`,
      )
    }
    if (hasWatch && wd === 0 && rnd() < 0.5) {
      const start = stamp(day, 8, 30)
      lines.push(
        `<Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="${ri(35, 55)}" durationUnit="min" sourceName="${watch}" sourceVersion="10.5" creationDate="${start}" startDate="${start}" endDate="${stamp(day, 9, 30)}"/>`,
      )
    }
    day = addDays(day, 1)
  }

  lines.push(
    `<Workout workoutActivityType="HKWorkoutActivityTypeTraditionalStrengthTraining" duration="250" durationUnit="min" sourceName="${watch}" sourceVersion="10.5" creationDate="2026-04-02 23:00:00 +0800" startDate="2026-04-02 18:50:00 +0800" endDate="2026-04-02 23:00:00 +0800"/>`,
  )
  rec('HKQuantityTypeIdentifierStepCount', phone, 'count', '2025-08-15', 9, 200000)
  lines.push('</HealthData>')
  return lines.join('\n')
}