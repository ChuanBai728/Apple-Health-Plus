import { describe, it, expect } from 'vitest'
import { trainingMetrics } from '@/core/metrics/training'
import { scanLine, newScanStats } from '@/core/parse/records'
import { emptyExtract } from '@/types/extract'
import type { Workout } from '@/types/extract'

const w = (activity: Workout['activity'], day: string, minutes: number, hour = 19, source = 'Watch'): Workout => ({
  activity,
  start: `${day} ${String(hour).padStart(2, '0')}:00:00 +0800`,
  end: `${day} ${String(hour).padStart(2, '0')}:59:00 +0800`,
  minutes,
  source,
})

describe('trainingMetrics', () => {
  it('241 分钟被排除并计入疑似忘记结束', () => {
    const m = trainingMetrics([w('strength', '2025-03-01', 80), w('strength', '2025-03-03', 241)], '2025-12-31')
    expect(m.count).toBe(1)
    expect(m.forgotten).toBe(1)
    expect(m.hours).toBeCloseTo(80 / 60, 5)
  })

  it('刚好 240 分钟计入', () => {
    const m = trainingMetrics([w('strength', '2025-03-01', 240)], '2025-12-31')
    expect(m.count).toBe(1)
    expect(m.forgotten).toBe(0)
  })

  it('小于 1 分钟丢弃', () => {
    const m = trainingMetrics([w('walk', '2025-03-01', 0.5)], '2025-12-31')
    expect(m.count).toBe(0)
  })

  it('Keep 散步与力量训练分开统计', () => {
    const m = trainingMetrics(
      [w('strength', '2025-03-01', 80), w('walk', '2025-03-02', 12, 10, 'Keep'), w('walk', '2025-03-03', 12, 10, 'Keep')],
      '2025-12-31',
    )
    const strength = m.groups.find((g) => g.activity === 'strength')!
    const walk = m.groups.find((g) => g.activity === 'walk')!
    expect(strength.count).toBe(1)
    expect(walk.count).toBe(2)
    expect(walk.medianMin).toBe(12)
    expect(m.count).toBe(3)
  })

  it('最长间隔与回归次数', () => {
    const m = trainingMetrics(
      [w('strength', '2026-01-10', 80), w('strength', '2026-05-12', 80), w('strength', '2026-05-14', 80)],
      '2026-11-24',
    )
    expect(m.gap!.days).toBe(122)
    expect(m.gap!.comeback).toBe(2)
  })

  it('夜训占比与主要时段', () => {
    const m = trainingMetrics(
      [w('strength', '2025-03-01', 80, 21), w('strength', '2025-03-03', 80, 20), w('strength', '2025-03-05', 80, 15)],
      '2025-12-31',
    )
    expect(m.nightShare).toBeCloseTo(2 / 3, 5)
    expect(m.primarySlot).toBe('晚上')
  })

  it('空数据', () => {
    const m = trainingMetrics([], '2025-12-31')
    expect(m.count).toBe(0)
    expect(m.longest).toBeNull()
    expect(m.gap).toBeNull()
  })
})

describe('scanLine · Workout', () => {
  it('秒单位换算', () => {
    const e = emptyExtract()
    const s = newScanStats()
    scanLine(
      '<Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="3600" durationUnit="s" sourceName="Watch" startDate="2025-03-01 08:00:00 +0800" endDate="2025-03-01 09:00:00 +0800">',
      e, s,
    )
    expect(e.workouts[0].minutes).toBe(60)
  })

  it('WorkoutStatistics 不被误读为训练或记录', () => {
    const e = emptyExtract()
    const s = newScanStats()
    scanLine(
      ' <WorkoutStatistics type="HKQuantityTypeIdentifierHeartRate" startDate="2025-03-01 08:00:00 +0800" endDate="2025-03-01 09:00:00 +0800" average="121" minimum="88" maximum="165" unit="count/min"/>',
      e, s,
    )
    expect(e.workouts.length).toBe(0)
    expect(Object.keys(e.stepCount).length).toBe(0)
  })

  it('未知时长单位跳过并记警告', () => {
    const e = emptyExtract()
    const s = newScanStats()
    scanLine(
      '<Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="5" durationUnit="furlong" startDate="2025-03-01 08:00:00 +0800" endDate="2025-03-01 09:00:00 +0800">',
      e, s,
    )
    expect(e.workouts.length).toBe(0)
    expect(s.warnings.size).toBe(1)
  })

  it('白名单外的 Record 类型被丢弃', () => {
    const e = emptyExtract()
    const s = newScanStats()
    scanLine('<Record type="HKQuantityTypeIdentifierHeadphoneAudioExposure" startDate="2025-03-01 08:00:00 +0800" value="80"/>', e, s)
    scanLine('<Record type="HKQuantityTypeIdentifierStepCount" sourceName="A" startDate="2025-03-01 08:00:00 +0800" value="100"/>', e, s)
    expect(s.kept).toBe(1)
    expect(e.stepCount['2025-03-01'].A).toBe(100)
  })

  it('ActivitySummary 按 dateComponents 归日', () => {
    const e = emptyExtract()
    const s = newScanStats()
    scanLine('<ActivitySummary dateComponents="2025-03-08" activeEnergyBurned="520" appleExerciseTime="45" appleStandHours="13"/>', e, s)
    expect(e.rings['2025-03-08']).toEqual({ kcal: 520, exerciseMin: 45, standHours: 13 })
  })
})