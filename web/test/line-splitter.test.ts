import { describe, it, expect } from 'vitest'
import { LineSplitter } from '@/worker/line-splitter'

function collect(chunks: string[]): string[] {
  const s = new LineSplitter()
  const out: string[] = []
  for (const c of chunks) s.push(c, (l) => out.push(l))
  s.flush((l) => out.push(l))
  return out
}

describe('LineSplitter', () => {
  it('一行切成两块', () => {
    expect(collect(['<Rec', 'ord value="1"/>\n'])).toEqual(['<Record value="1"/>'])
  })

  it('一行切成三块', () => {
    expect(collect(['ab', 'cd', 'ef\ngh\n'])).toEqual(['abcdef', 'gh'])
  })

  it('兼容 \\r\\n', () => {
    expect(collect(['a\r\nb\r\n'])).toEqual(['a', 'b'])
  })

  it('末尾无换行时 flush 输出', () => {
    expect(collect(['a\nb'])).toEqual(['a', 'b'])
  })

  it('UTF-8 多字节字符跨块（配合流式解码器）', () => {
    const text = '来源="欢太健康"\n下一行\n'
    const bytes = new TextEncoder().encode(text)
    const dec = new TextDecoder('utf-8')
    const s = new LineSplitter()
    const out: string[] = []
    const cut = 9 // 落在多字节字符中间
    s.push(dec.decode(bytes.subarray(0, cut), { stream: true }), (l) => out.push(l))
    s.push(dec.decode(bytes.subarray(cut), { stream: true }), (l) => out.push(l))
    s.flush((l) => out.push(l))
    expect(out).toEqual(['来源="欢太健康"', '下一行'])
  })

  it('空块不产生空行', () => {
    expect(collect(['', 'a\n', '', '\n'])).toEqual(['a', ''])
  })
})