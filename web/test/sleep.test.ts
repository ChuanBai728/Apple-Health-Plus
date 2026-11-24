import { describe, it, expect } from 'vitest'
import { buildNapDays, buildNights, isNapSegment, nightKeyOf } from '@/core/parse/sleep'
import { napMetrics } from '@/core/metrics/naps'
import type { SleepSegment } from '@/types/extract'

const seg = (start: string, end: string, minutes: number, stage: SleepSegment['stage'], source = 'Watch'): SleepSegment => ({
  start, end, minutes, stage, source,
})

describe('夜晚归属', () => {
  it('凌晨 2 点算前一夜', () => {
    const s = seg('2025-03-09 02:00:00 +0800', '2025-03-09 03:00:00 +0800', 60, 'deep')
    expect(nightKeyOf(s)).toBe('2025-03-08')
  })

  it('晚上 23 点算当晚', () => {
    const s = seg('2025-03-08 23:30:00 +0800', '2025-03-09 00:30:00 +0800', 60, 'core')
    expect(nightKeyOf(s)).toBe('2025-03-08')
  })

  it('多来源取最大，不相加', () => {
    const nights = buildNights([
      seg('2025-03-08 23:00:00 +0800', '2025-03-09 06:00:00 +0800', 420, 'core', 'Watch'),
      seg('2025-03-08 23:10:00 +0800', '2025-03-09 05:50:00 +0800', 400, 'unspecified', 'iPhone'),
    ])
    expect(nights.length).toBe(1)
    expect(nights[0].minutes).toBe(420)
  })

  it('小睡排除在夜晚之外，Awake/InBed 不计', () => {
    const nights = buildNights([
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 13:40:00 +0800', 40, 'core'),
      seg('2025-03-08 23:00:00 +0800', '2025-03-09 06:00:00 +0800', 420, 'inbed'),
      seg('2025-03-08 23:30:00 +0800', '2025-03-09 06:30:00 +0800', 420, 'awake'),
    ])
    expect(nights.length).toBe(0)
  })

  it('超过 24 小时的夜晚丢弃', () => {
    const nights = buildNights([
      seg('2025-03-08 23:00:00 +0800', '2025-03-10 06:00:00 +0800', 1900, 'core'),
    ])
    expect(nights.length).toBe(0)
  })
})

describe('小睡判定', () => {
  it('10:00–19:59 的 Asleep 段落是小睡', () => {
    expect(isNapSegment(seg('2025-03-08 10:00:00 +0800', '2025-03-08 10:30:00 +0800', 30, 'core'))).toBe(true)
    expect(isNapSegment(seg('2025-03-08 19:59:00 +0800', '2025-03-08 20:20:00 +0800', 21, 'rem'))).toBe(true)
    expect(isNapSegment(seg('2025-03-08 09:59:00 +0800', '2025-03-08 10:20:00 +0800', 21, 'core'))).toBe(false)
    expect(isNapSegment(seg('2025-03-08 20:00:00 +0800', '2025-03-08 20:30:00 +0800', 30, 'core'))).toBe(false)
  })

  it('19 分钟不算，20 分钟算', () => {
    const naps = buildNapDays([
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 13:19:00 +0800', 19, 'core'),
      seg('2025-03-09 13:00:00 +0800', '2025-03-09 13:20:00 +0800', 20, 'core'),
    ])
    expect(naps.length).toBe(1)
    expect(naps[0].day).toBe('2025-03-09')
  })

  it('同一天多来源取最大；同来源多段相加', () => {
    const naps = buildNapDays([
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 13:15:00 +0800', 15, 'core', 'Watch'),
      seg('2025-03-08 13:20:00 +0800', '2025-03-08 13:35:00 +0800', 15, 'core', 'Watch'),
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 13:25:00 +0800', 25, 'unspecified', 'iPhone'),
    ])
    expect(naps.length).toBe(1)
    expect(naps[0].minutes).toBe(30)
  })

  it('Awake 不计入小睡', () => {
    const naps = buildNapDays([
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 14:00:00 +0800', 60, 'awake'),
    ])
    expect(naps.length).toBe(0)
  })
})

describe('napMetrics', () => {
  it('空数据不产生 NaN', () => {
    const m = napMetrics([])
    expect(m.count).toBe(0)
    expect(m.totalHours).toBe(0)
    expect(m.longest).toBeNull()
    expect(m.nights.avgHours).toBe(0)
  })

  it('直方图按开始小时分桶', () => {
    const m = napMetrics([
      seg('2025-03-08 13:00:00 +0800', '2025-03-08 13:40:00 +0800', 40, 'core'),
      seg('2025-03-09 13:10:00 +0800', '2025-03-09 13:50:00 +0800', 40, 'core'),
      seg('2025-03-10 15:00:00 +0800', '2025-03-10 15:30:00 +0800', 30, 'core'),
    ])
    expect(m.hourHistogram[3]).toBe(2) // 13 点
    expect(m.hourHistogram[5]).toBe(1) // 15 点
    expect(m.count).toBe(3)
  })
})