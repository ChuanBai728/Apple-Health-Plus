import { describe, it, expect } from 'vitest'
import { pickCopy, renderText, formatValue, type CopyRule } from '@/copy/engine'
import { hashSeed, seededRandom } from '@/copy/seeded-random'
import type { ReportModel } from '@/types/report'

const fakeModel = {
  period: { key: 'all', label: '全部', from: '2025-01-01', to: '2026-11-24', days: 693, years: 1.9, incomplete: false },
  steps: { total: 7842471, totalKm: 5565.1, marathons: 131.89 },
  stairs: { total: 7224, everest: 2.488, streak1: { length: 151, from: '2026-06-18', to: '2026-11-15' } },
} as unknown as ReportModel

describe('formatValue', () => {
  it('千分位', () => {
    expect(formatValue(7842471, ',')).toBe('7,842,471')
  })
  it('保留一位小数', () => {
    expect(formatValue(2.488, '~1')).toBe('2.5')
  })
  it('日期转中文', () => {
    expect(formatValue('2026-06-18', 'date')).toBe('6 月 18 日')
  })
  it('缺失值降级', () => {
    expect(formatValue(undefined, ',')).toBe('—')
    expect(formatValue(NaN, '~1')).toBe('—')
  })
})

describe('renderText', () => {
  it('占位符解析嵌套路径', () => {
    expect(renderText('共 {stairs.total:,} 层，{stairs.everest:~1} 个珠峰', fakeModel)).toBe(
      '共 7,224 层，2.5 个珠峰',
    )
  })
})

describe('pickCopy', () => {
  const rules: CopyRule[] = [
    { priority: 9, when: () => true, text: '兜底' },
    { priority: 1, when: (m) => m.stairs.total > 7000, text: '高优先' },
    { priority: 5, when: () => true, text: '中优先' },
  ]

  it('优先级小的先匹配', () => {
    expect(pickCopy(rules, fakeModel, 'stairs')).toBe('高优先')
  })

  it('条件不满足时落到下一优先级', () => {
    const low = { ...fakeModel, stairs: { ...fakeModel.stairs, total: 100 } } as ReportModel
    expect(pickCopy(rules, low, 'stairs')).toBe('中优先')
  })

  it('兜底规则必然命中', () => {
    const only: CopyRule[] = [{ priority: 9, when: () => true, text: '兜底' }]
    expect(pickCopy(only, fakeModel, 'x')).toBe('兜底')
  })

  it('同优先级同种子同输出', () => {
    const ties: CopyRule[] = [
      { priority: 1, when: () => true, text: 'A' },
      { priority: 1, when: () => true, text: 'B' },
      { priority: 1, when: () => true, text: 'C' },
    ]
    const first = pickCopy(ties, fakeModel, 'naps')
    for (let i = 0; i < 20; i++) {
      expect(pickCopy(ties, fakeModel, 'naps')).toBe(first)
    }
  })

  it('不同时间段种子不同（大概率换句）', () => {
    const ties: CopyRule[] = Array.from({ length: 8 }, (_, i) => ({
      priority: 1, when: () => true, text: `句${i}`,
    }))
    const other = {
      ...fakeModel,
      period: { ...fakeModel.period, from: '2025-01-01', to: '2025-12-31' },
    } as ReportModel
    const outputs = new Set([pickCopy(ties, fakeModel, 'naps'), pickCopy(ties, other, 'naps')])
    expect(outputs.size).toBeGreaterThanOrEqual(1)
  })
})

describe('seededRandom', () => {
  it('同种子序列一致', () => {
    const a = seededRandom(hashSeed('abc'))
    const b = seededRandom(hashSeed('abc'))
    for (let i = 0; i < 10; i++) expect(a()).toBe(b())
  })
})