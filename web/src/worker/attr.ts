/** indexOf 版属性提取：不做正则、不 split 整行 */
export function attr(line: string, name: string): string | null {
  const key = name + '="'
  const i = line.indexOf(key)
  if (i < 0) return null
  const start = i + key.length
  const end = line.indexOf('"', start)
  if (end < 0) return null
  return line.slice(start, end)
}

export function numAttr(line: string, name: string): number {
  const v = attr(line, name)
  if (v === null) return NaN
  return Number(v)
}

const ENTITIES: Record<string, string> = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&apos;': "'",
}

export function unescapeXml(s: string): string {
  if (s.indexOf('&') < 0) return s
  return s.replace(/&(amp|lt|gt|quot|apos);/g, (m) => ENTITIES[m] ?? m)
}