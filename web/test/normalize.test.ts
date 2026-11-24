import { describe, it, expect } from 'vitest'
import { dailyMax, sanitizeDaily, filterExtract } from '@/core/normalize'
import { emptyExtract } from '@/types/extract'

describe('dailyMax', () => {
  it('多来源同一天取最大值，不相加', () => {
    const bySource = {
      '2025-03-08': { 'Apple Watch': 9000, iPhone: 8200, Keep: 1000 },
      '2025-03-09': { iPhone: 5000 },
    }
    expect(dailyMax(bySource)).toEqual({ '2025-03-08': 9000, '2025-03-09': 5000 })
  })

  it('空输入', () => {
    expect(dailyMax({})).toEqual({})
  })
})

describe('sanitizeDaily', () => {
  it('单日步数超 100,000 丢弃该来源', () => {
    const e = emptyExtract()
    e.stepCount['2025-08-15'] = { iPhone: 200000, Watch: 8000 }
    const r = sanitizeDaily(e)
    expect(r.ignored).toBe(1)
    expect(e.stepCount['2025-08-15']).toEqual({ Watch: 8000 })
  })

  it('单日距离超 200km、爬楼超 500 层丢弃', () => {
    const e = emptyExtract()
    e.distanceKm['2025-01-01'] = { A: 201 }
    e.flightsClimbed['2025-01-01'] = { A: 501 }
    const r = sanitizeDaily(e)
    expect(r.ignored).toBe(2)
    expect(e.distanceKm['2025-01-01']).toBeUndefined()
    expect(e.flightsClimbed['2025-01-01']).toBeUndefined()
  })

  it('刚好等于上限不丢弃', () => {
    const e = emptyExtract()
    e.stepCount['2025-01-01'] = { A: 100000 }
    expect(sanitizeDaily(e).ignored).toBe(0)
  })
})

describe('filterExtract', () => {
  it('按时间段过滤所有结构', () => {
    const e = emptyExtract()
    e.stepCount = { '2024-12-31': { A: 1 }, '2025-01-01': { A: 2 }, '2026-01-01': { A: 3 } }
    e.rings = { '2024-12-31': { kcal: 1, exerciseMin: 1, standHours: 1 }, '2025-06-01': { kcal: 2, exerciseMin: 2, standHours: 2 } }
    e.workouts = [
      { activity: 'strength', start: '2025-05-01 10:00:00 +0800', end: '2025-05-01 11:00:00 +0800', minutes: 60, source: 'A' },
      { activity: 'walk', start: '2024-05-01 10:00:00 +0800', end: '2024-05-01 10:12:00 +0800', minutes: 12, source: 'A' },
    ]
    const f = filterExtract(e, '2025-01-01', '2025-12-31')
    expect(Object.keys(f.stepCount)).toEqual(['2025-01-01'])
    expect(Object.keys(f.rings)).toEqual(['2025-06-01'])
    expect(f.workouts.length).toBe(1)
  })
})