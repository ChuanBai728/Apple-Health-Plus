import { describe, it, expect } from 'vitest'
import { zipSync, strToU8 } from 'fflate'
import { readCentralDirectory, pickEntry, entryDataStart } from '@/worker/zip-reader'

function makeZip(files: Record<string, string>): Blob {
  const data: Record<string, Uint8Array> = {}
  for (const k in files) data[k] = strToU8(files[k])
  return new Blob([zipSync(data) as unknown as BlobPart])
}

describe('zip-reader', () => {
  it('读取普通 zip 并定位数据起点', async () => {
    const content = '<HealthData>\n<Record value="1"/>\n</HealthData>'
    const zip = makeZip({ 'apple_health_export/导出.xml': content })
    const entries = await readCentralDirectory(zip)
    expect(entries.length).toBe(1)
    const entry = pickEntry(entries)!
    expect(entry.name).toBe('apple_health_export/导出.xml')
    const start = await entryDataStart(zip, entry)
    const raw = new Uint8Array(await zip.slice(start, start + entry.compressedSize).arrayBuffer())
    const { inflateSync } = await import('fflate')
    expect(new TextDecoder().decode(inflateSync(raw))).toBe(content)
  })

  it('中文文件名按 UTF-8 解码', async () => {
    const zip = makeZip({ 'apple_health_export/导出.xml': 'x', '其他/说明.txt': 'y' })
    const entries = await readCentralDirectory(zip)
    expect(entries.map((e) => e.name)).toContain('apple_health_export/导出.xml')
    expect(entries.map((e) => e.name)).toContain('其他/说明.txt')
  })

  it('优先选 导出.xml，排除 export_cda.xml', async () => {
    const zip = makeZip({
      'apple_health_export/export_cda.xml': 'x'.repeat(5000),
      'apple_health_export/export.xml': 'small',
    })
    const entry = pickEntry(await readCentralDirectory(zip))!
    expect(entry.name).toBe('apple_health_export/export.xml')
  })

  it('没有 导出.xml 时选最大的非 CDA xml', async () => {
    const zip = makeZip({
      'apple_health_export/export_cda.xml': 'x'.repeat(9000),
      'apple_health_export/健康数据.xml': 'y'.repeat(2000),
      'apple_health_export/小.xml': 'z',
    })
    const entry = pickEntry(await readCentralDirectory(zip))!
    expect(entry.name).toBe('apple_health_export/健康数据.xml')
  })

  it('找不到入口返回 null', async () => {
    const zip = makeZip({ 'readme.txt': 'hi' })
    expect(pickEntry(await readCentralDirectory(zip))).toBeNull()
  })

  it('非 zip 文件抛错', async () => {
    await expect(readCentralDirectory(new Blob(['not a zip at all']))).rejects.toThrow('NOT_ZIP')
  })
})