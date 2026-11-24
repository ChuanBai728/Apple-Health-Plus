import { describe, it, expect } from 'vitest'
import { attr, numAttr, unescapeXml } from '@/worker/attr'

describe('attr', () => {
  const line = '<Record type="HKQuantityTypeIdentifierStepCount" sourceName="Apple Watch Series 9 " unit="count" value="843"/>'

  it('提取普通属性', () => {
    expect(attr(line, 'type')).toBe('HKQuantityTypeIdentifierStepCount')
    expect(attr(line, 'value')).toBe('843')
  })

  it('保留结尾空格与转义字符原样', () => {
    expect(attr(line, 'sourceName')).toBe('Apple Watch Series 9 ')
    const esc = '<Record sourceName="A&amp;B &lt;watch&gt;"/>'
    expect(attr(esc, 'sourceName')).toBe('A&amp;B &lt;watch&gt;')
    expect(unescapeXml('A&amp;B &lt;watch&gt;')).toBe('A&B <watch>')
  })

  it('属性顺序变化不影响', () => {
    const reordered = '<Record value="12" type="T"/>'
    expect(attr(reordered, 'type')).toBe('T')
    expect(attr(reordered, 'value')).toBe('12')
  })

  it('缺属性返回 null / NaN', () => {
    expect(attr(line, 'device')).toBeNull()
    expect(numAttr(line, 'device')).toBeNaN()
  })

  it('不误匹配前缀相似的属性名', () => {
    const l = '<Workout workoutActivityType="X" duration="5"/>'
    expect(attr(l, 'type')).toBeNull()
  })
})