import { describe, it, expect } from 'vitest'
import { longestStreak, longestValueStreak, longestGap } from '@/core/metrics/streaks'

describe('longestStreak', () => {
  it('中间缺一天即中断', () => {
    const days = new Set(['2025-03-01', '2025-03-02', '2025-03-04', '2025-03-05', '2025-03-06'])
    const s = longestStreak(days, () => true)
    expect(s).toEqual({ length: 3, from: '2025-03-04', to: '2025-03-06' })
  })

  it('跨月', () => {
    const days = new Set(['2025-01-30', '2025-01-31', '2025-02-01', '2025-02-02'])
    expect(longestStreak(days, () => true).length).toBe(4)
  })

  it('跨年', () => {
    const days = new Set(['2025-12-30', '2025-12-31', '2026-01-01'])
    const s = longestStreak(days, () => true)
    expect(s.length).toBe(3)
    expect(s.from).toBe('2025-12-30')
    expect(s.to).toBe('2026-01-01')
  })

  it('闰日', () => {
    const days = new Set(['2024-02-28', '2024-02-29', '2024-03-01'])
    expect(longestStreak(days, () => true).length).toBe(3)
    const nonLeap = new Set(['2025-02-28', '2025-03-01'])
    expect(longestStreak(nonLeap, () => true).length).toBe(2)
  })

  it('只有一天', () => {
    expect(longestStreak(new Set(['2025-05-01']), () => true)).toEqual({
      length: 1,
      from: '2025-05-01',
      to: '2025-05-01',
    })
  })

  it('空集', () => {
    expect(longestStreak(new Set(), () => true).length).toBe(0)
  })

  it('predicate 过滤：≥10000 步', () => {
    const daily = { '2025-03-07': 10000, '2025-03-08': 24134, '2025-03-09': 9999, '2025-03-10': 12000 }
    const s = longestValueStreak(daily, 10000)
    expect(s.length).toBe(2)
    expect(s.from).toBe('2025-03-07')
  })

  it('隔了三个月的两次记录不算连续', () => {
    const days = new Set(['2025-01-01', '2025-04-01'])
    expect(longestStreak(days, () => true).length).toBe(1)
  })
})

describe('longestGap', () => {
  it('找出最长间隔', () => {
    const days = new Set(['2026-01-10', '2026-05-12', '2026-05-13'])
    const g = longestGap(days)!
    expect(g.days).toBe(122)
    expect(g.from).toBe('2026-01-10')
    expect(g.to).toBe('2026-05-12')
  })

  it('少于两天返回 null', () => {
    expect(longestGap(new Set(['2025-01-01']))).toBeNull()
  })
})