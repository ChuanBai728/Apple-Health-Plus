/** 手写 zip 中央目录解析：EOCD / Zip64 / UTF-8 文件名 / local header 定位 */

export interface ZipEntry {
  name: string
  method: number
  compressedSize: number
  uncompressedSize: number
  localOffset: number
}

const SIG_EOCD = 0x06054b50
const SIG_EOCD64 = 0x06064b50
const SIG_EOCD64_LOCATOR = 0x07064b50
const SIG_CENTRAL = 0x02014b50
const SIG_LOCAL = 0x04034b50

function decodeName(bytes: Uint8Array, utf8Flag: boolean): string {
  if (utf8Flag) return new TextDecoder('utf-8').decode(bytes)
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    try {
      return new TextDecoder('gbk').decode(bytes)
    } catch {
      return new TextDecoder('utf-8').decode(bytes)
    }
  }
}

export async function readCentralDirectory(file: Blob): Promise<ZipEntry[]> {
  const tailSize = Math.min(file.size, 65557 + 22)
  const tail = new DataView(await file.slice(file.size - tailSize).arrayBuffer())
  let eocd = -1
  for (let i = tail.byteLength - 22; i >= 0; i--) {
    if (tail.getUint32(i, true) === SIG_EOCD) {
      eocd = i
      break
    }
  }
  if (eocd < 0) throw new Error('NOT_ZIP')

  let count = tail.getUint16(eocd + 10, true)
  let cdOffset = tail.getUint32(eocd + 16, true)

  if (cdOffset === 0xffffffff || count === 0xffff) {
    const locPos = eocd - 20
    if (locPos >= 0 && tail.getUint32(locPos, true) === SIG_EOCD64_LOCATOR) {
      const eocd64Abs = Number(tail.getBigUint64(locPos + 8, true))
      const rec = new DataView(await file.slice(eocd64Abs, eocd64Abs + 56).arrayBuffer())
      if (rec.getUint32(0, true) !== SIG_EOCD64) throw new Error('NOT_ZIP')
      count = Number(rec.getBigUint64(32, true))
      cdOffset = Number(rec.getBigUint64(48, true))
    } else {
      throw new Error('NOT_ZIP')
    }
  }

  const cd = new DataView(await file.slice(cdOffset, cdOffset + Math.min(file.size - cdOffset, 64 * 1024 * 1024)).arrayBuffer())
  const bytes = new Uint8Array(cd.buffer)
  const entries: ZipEntry[] = []
  let p = 0
  for (let n = 0; n < count && p + 46 <= cd.byteLength; n++) {
    if (cd.getUint32(p, true) !== SIG_CENTRAL) break
    const flags = cd.getUint16(p + 8, true)
    const method = cd.getUint16(p + 10, true)
    let compressedSize = cd.getUint32(p + 20, true)
    let uncompressedSize = cd.getUint32(p + 24, true)
    const nameLen = cd.getUint16(p + 28, true)
    const extraLen = cd.getUint16(p + 30, true)
    const commentLen = cd.getUint16(p + 32, true)
    let localOffset = cd.getUint32(p + 42, true)
    const name = decodeName(bytes.subarray(p + 46, p + 46 + nameLen), (flags & 0x800) !== 0)

    if (compressedSize === 0xffffffff || uncompressedSize === 0xffffffff || localOffset === 0xffffffff) {
      let q = p + 46 + nameLen
      const end = q + extraLen
      while (q + 4 <= end) {
        const id = cd.getUint16(q, true)
        const size = cd.getUint16(q + 2, true)
        if (id === 0x0001) {
          let r = q + 4
          if (uncompressedSize === 0xffffffff) {
            uncompressedSize = Number(cd.getBigUint64(r, true))
            r += 8
          }
          if (compressedSize === 0xffffffff) {
            compressedSize = Number(cd.getBigUint64(r, true))
            r += 8
          }
          if (localOffset === 0xffffffff) {
            localOffset = Number(cd.getBigUint64(r, true))
          }
          break
        }
        q += 4 + size
      }
    }
    entries.push({ name, method, compressedSize, uncompressedSize, localOffset })
    p += 46 + nameLen + extraLen + commentLen
  }
  return entries
}

export function pickEntry(entries: ZipEntry[]): ZipEntry | null {
  const norm = (s: string) => s.replace(/\\/g, '/')
  const preferred = entries.find(
    (e) => norm(e.name) === 'apple_health_export/导出.xml' || norm(e.name) === 'apple_health_export/export.xml',
  )
  if (preferred) return preferred
  const candidates = entries.filter((e) => {
    const n = norm(e.name)
    return (
      n.endsWith('.xml') &&
      !n.endsWith('export_cda.xml') &&
      (n.startsWith('apple_health_export/') || !n.includes('/'))
    )
  })
  if (candidates.length === 0) return null
  return candidates.reduce((a, b) => (b.uncompressedSize > a.uncompressedSize ? b : a))
}

export async function entryDataStart(file: Blob, entry: ZipEntry): Promise<number> {
  const head = new DataView(await file.slice(entry.localOffset, entry.localOffset + 30).arrayBuffer())
  if (head.getUint32(0, true) !== SIG_LOCAL) throw new Error('NOT_ZIP')
  const nameLen = head.getUint16(26, true)
  const extraLen = head.getUint16(28, true)
  return entry.localOffset + 30 + nameLen + extraLen
}