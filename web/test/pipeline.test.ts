import { describe, it, expect } from 'vitest'
import { zipSync, strToU8, inflateSync } from 'fflate'
import { generateSampleXml } from '@/demo/sample'
import { scanLine, newScanStats } from '@/core/parse/records'
import { emptyExtract, type RawExtract } from '@/types/extract'
import { sanitizeDaily } from '@/core/normalize'
import { buildReport } from '@/core/report'
import { readCentralDirectory, pickEntry, entryDataStart } from '@/worker/zip-reader'

function scanXml(xml: string): { extract: RawExtract; ignored: number } {
  const extract = emptyExtract()
  const stats = newScanStats()
  for (const line of xml.split('\n')) scanLine(line, extract, stats)
  const san = sanitizeDaily(extract)
  return { extract, ignored: stats.ignored + san.ignored }
}

describe('合成数据全管线', () => {
  const xml = generateSampleXml()
  const { extract, ignored } = scanXml(xml)

  it('扫到足量记录', () => {
    expect(Object.keys(extract.stepCount).length).toBeGreaterThan(600)
    expect(extract.sleepSegments.length).toBeGreaterThan(2000)
    expect(extract.workouts.length).toBeGreaterThan(300)
    expect(Object.keys(extract.rings).length).toBeGreaterThan(500)
  })

  it('异常记录被忽略（200000 步 + 250 分钟训练在指标层排除）', () => {
    expect(ignored).toBeGreaterThanOrEqual(1)
  })

  it('zip 管线与直接扫描结果一致', async () => {
    const zip = new Blob([zipSync({ 'apple_health_export/导出.xml': strToU8(xml) }) as unknown as BlobPart])
    const entries = await readCentralDirectory(zip)
    const entry = pickEntry(entries)!
    const start = await entryDataStart(zip, entry)
    const compressed = new Uint8Array(await zip.slice(start, start + entry.compressedSize).arrayBuffer())
    const decoded = new TextDecoder().decode(inflateSync(compressed))
    const viaZip = scanXml(decoded)
    expect(viaZip.extract.stepCount).toEqual(extract.stepCount)
    expect(viaZip.extract.workouts.length).toBe(extract.workouts.length)
    expect(viaZip.extract.sleepSegments.length).toBe(extract.sleepSegments.length)
  })

  it('报告生成：数字合理、文案无 NaN', () => {
    const model = buildReport(extract, 'all', { ignored, warnings: [] })
    expect(model.steps.total).toBeGreaterThan(1000000)
    expect(model.stairs.total).toBeGreaterThan(1000)
    expect(model.naps.count).toBeGreaterThan(50)
    expect(model.training.count).toBeGreaterThan(200)
    expect(model.training.forgotten).toBe(1)
    expect(model.persona.key).toBe('gym')
    for (const text of Object.values(model.copy)) {
      expect(text).not.toContain('NaN')
      expect(text).not.toContain('undefined')
      expect(text.length).toBeGreaterThan(0)
    }
  })

  it('切换年份后数字变化且无 NaN', () => {
    const model = buildReport(extract, '2025', { ignored, warnings: [] })
    expect(model.period.key).toBe('2025')
    expect(model.period.from).toBe('2025-01-01')
    expect(model.period.to).toBe('2025-12-31')
    const all = buildReport(extract, 'all', { ignored, warnings: [] })
    expect(model.steps.total).toBeLessThan(all.steps.total)
    for (const text of Object.values(model.copy)) {
      expect(text).not.toContain('NaN')
    }
  })

  it('同一份数据解析两次报告完全一致（文案种子稳定）', () => {
    const a = buildReport(extract, 'all', { ignored, warnings: [] })
    const b = buildReport(scanXml(xml).extract, 'all', { ignored, warnings: [] })
    expect(a.copy).toEqual(b.copy)
    expect(a.persona).toEqual(b.persona)
    expect(a.steps.total).toBe(b.steps.total)
  })

  it('不完整年份标注截至日期', () => {
    const model = buildReport(extract, '2026', { ignored, warnings: [] })
    expect(model.period.incomplete).toBe(true)
    expect(model.period.label).toContain('截至')
  })

  it('数据覆盖提示：手表记录晚于首条记录 30 天以上', () => {
    const model = buildReport(extract, 'all', { ignored, warnings: [] })
    expect(model.coverageNotice).toContain('2025 年 2 月')
  })
})