import { describe, it, expect } from 'vitest'
import { everestCount, marathonCount, equatorPct, MARATHON_KM, EVEREST_M, FLOOR_M } from '@/core/compare'
import { stairMetrics } from '@/core/metrics/stairs'
import { stepMetrics } from '@/core/metrics/steps'
import { ringMetrics, isClosed } from '@/core/metrics/rings'

describe('换算常量', () => {
  it('珠峰换算：7224 层 ≈ 2.5 个珠峰', () => {
    expect(everestCount(7224)).toBeCloseTo((7224 * FLOOR_M) / EVEREST_M, 6)
    expect(everestCount(7224).toFixed(1)).toBe('2.5')
  })

  it('马拉松换算：5565.1 km ≈ 131.9 个', () => {
    expect(marathonCount(5565.1).toFixed(1)).toBe('131.9')
    expect(marathonCount(MARATHON_KM)).toBe(1)
  })

  it('赤道占比', () => {
    expect(equatorPct(400.75)).toBeCloseTo(1, 6)
  })
})

describe('stairMetrics', () => {
  it('空数据', () => {
    const m = stairMetrics({})
    expect(m.total).toBe(0)
    expect(m.maxDay).toBeNull()
    expect(m.streak1.length).toBe(0)
  })

  it('连续与多层连续分开算', () => {
    const m = stairMetrics({ '2025-01-01': 5, '2025-01-02': 12, '2025-01-03': 15, '2025-01-04': 3 })
    expect(m.streak1.length).toBe(4)
    expect(m.streak10.length).toBe(2)
    expect(m.total).toBe(35)
    expect(m.maxDay).toEqual({ day: '2025-01-03', value: 15 })
  })
})

describe('stepMetrics', () => {
  it('中位数 / p90 / 万步天数', () => {
    const daily: Record<string, number> = {}
    for (let i = 1; i <= 10; i++) daily[`2025-01-${String(i).padStart(2, '0')}`] = i * 1000
    const m = stepMetrics(daily, {})
    expect(m.median).toBe(5500)
    expect(m.p90).toBe(9000)
    expect(m.days10k).toBe(1)
    expect(m.total).toBe(55000)
  })

  it('空数据不产生 NaN', () => {
    const m = stepMetrics({}, {})
    expect(m.median).toBe(0)
    expect(m.pct10k).toBe(0)
    expect(m.marathons).toBe(0)
  })
})

describe('ringMetrics', () => {
  it('闭合判定：stand≥12 且 exercise≥30 且 kcal>0', () => {
    expect(isClosed({ kcal: 1, exerciseMin: 30, standHours: 12 })).toBe(true)
    expect(isClosed({ kcal: 0, exerciseMin: 30, standHours: 12 })).toBe(false)
    expect(isClosed({ kcal: 1, exerciseMin: 29, standHours: 12 })).toBe(false)
    expect(isClosed({ kcal: 1, exerciseMin: 30, standHours: 11 })).toBe(false)
  })

  it('闭合率与最长连续', () => {
    const rings = {
      '2025-01-10': { kcal: 500, exerciseMin: 40, standHours: 13 },
      '2025-01-11': { kcal: 500, exerciseMin: 40, standHours: 13 },
      '2025-01-12': { kcal: 100, exerciseMin: 10, standHours: 8 },
    }
    const m = ringMetrics(rings)
    expect(m.closedDays).toBe(2)
    expect(m.rate).toBeCloseTo(2 / 3, 5)
    expect(m.streak.length).toBe(2)
    expect(m.exerciseHours).toBeCloseTo(90 / 60, 5)
  })
})