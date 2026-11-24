import { describe, it, expect } from 'vitest'
import { decidePersona, type PersonaInput } from '@/core/persona'
import type { NapMetrics, TrainingMetrics, StairMetrics, StepMetrics, RingMetrics } from '@/types/report'

const naps: NapMetrics = {
  count: 0, totalHours: 0, avgMin: 0, medianMin: 0, longest: null,
  byYear: {}, hourHistogram: new Array(10).fill(0),
  weekday: { count: 0, avgMin: 0 }, weekend: { count: 0, avgMin: 0 }, top5: [],
  nights: { count: 0, avgHours: 0, deepPct: 0, remPct: 0 },
}
const training: TrainingMetrics = {
  count: 0, hours: 0, medianMin: 0, longest: null, forgotten: 0, groups: [],
  monthly: {}, yearly: {}, bestYear: null,
  streak: { length: 0, from: '', to: '' }, gap: null,
  primarySlot: '—', nightShare: 0, weekday: new Array(7).fill(0), days: [],
}
const stairs: StairMetrics = {
  total: 0, everest: 0, maxDay: null,
  streak1: { length: 0, from: '', to: '' }, streak10: { length: 0, from: '', to: '' },
  monthly: {}, yearly: {}, days: [],
}
const steps: StepMetrics = {
  total: 0, median: 0, p90: 0, maxDay: null, days10k: 0, pct10k: 0,
  streak10k: { length: 0, from: '', to: '' }, streak7k: { length: 0, from: '', to: '' },
  monthlyAvg: {}, bestMonth: null, worstMonth: null,
  totalKm: 0, marathons: 0, equatorPct: 0, recordedDays: 0,
}
const rings: RingMetrics = {
  closedDays: 0, daysWithData: 0, rate: 0,
  streak: { length: 0, from: '', to: '' }, exerciseHours: 0,
}

function input(over: Partial<PersonaInput> = {}): PersonaInput {
  return {
    years: 1, naps, training, stairs, steps, rings,
    strengthStreak: { length: 0, from: '', to: '' },
    stairRecordedDays: 0,
    ...over,
  }
}

describe('decidePersona', () => {
  it('铁馆常驻：力量 ≥ 52×N，阈值刚好相等', () => {
    const p = decidePersona(input({
      training: { ...training, groups: [{ activity: 'strength', count: 52, hours: 70, medianMin: 80, longest: null }] },
    }))
    expect(p.key).toBe('gym')
  })

  it('城市步行者：步数中位 ≥ 8000', () => {
    const p = decidePersona(input({ steps: { ...steps, median: 8000 } }))
    expect(p.key).toBe('walker')
  })

  it('台阶常客：日均 ≥ 8 层', () => {
    const p = decidePersona(input({
      steps: { ...steps, median: 7999 },
      stairs: { ...stairs, total: 800 },
      stairRecordedDays: 100,
    }))
    expect(p.key).toBe('stairs')
  })

  it('台阶常客：最长连续 ≥ 60 天', () => {
    const p = decidePersona(input({
      stairs: { ...stairs, streak1: { length: 60, from: 'a', to: 'b' } },
    }))
    expect(p.key).toBe('stairs')
  })

  it('稳步前行兜底', () => {
    expect(decidePersona(input()).key).toBe('steady')
  })

  it('不完整年份折算：N=0.5 时力量 26 次即命中', () => {
    const p = decidePersona(input({
      years: 0.5,
      training: { ...training, groups: [{ activity: 'strength', count: 26, hours: 35, medianMin: 80, longest: null }] },
    }))
    expect(p.key).toBe('gym')
  })

  it('优先级：力量达标时压过步行', () => {
    const p = decidePersona(input({
      steps: { ...steps, median: 12000 },
      training: { ...training, groups: [{ activity: 'strength', count: 100, hours: 130, medianMin: 80, longest: null }] },
    }))
    expect(p.key).toBe('gym')
  })

  it('标签最多 3 个，按顺序取', () => {
    const p = decidePersona(input({
      naps: { ...naps, count: 40 },
      training: { ...training, count: 20, nightShare: 0.6 },
      rings: { ...rings, rate: 0.5 },
      steps: { ...steps, totalKm: 1500, pct10k: 30 },
    }))
    expect(p.tags).toEqual(['午睡爱好者', '夜训型', '三环闭合达人'])
  })
})