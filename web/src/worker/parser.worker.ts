import { Inflate } from 'fflate'
import type { WorkerRequest, WorkerResponse, RawExtract } from '@/types/extract'
import { emptyExtract } from '@/types/extract'
import { readCentralDirectory, pickEntry, entryDataStart } from './zip-reader'
import { LineSplitter } from './line-splitter'
import { scanLine, newScanStats } from '@/core/parse/records'
import { sanitizeDaily } from '@/core/normalize'
import { generateSampleXml } from '@/demo/sample'

const post = (msg: WorkerResponse) => (self as unknown as Worker).postMessage(msg)

const CHUNK = 1024 * 1024

let cancelled = false

function makeScanner() {
  const out = emptyExtract()
  const stats = newScanStats()
  const splitter = new LineSplitter()
  const decoder = new TextDecoder('utf-8')
  return {
    out,
    stats,
    push(bytes: Uint8Array, final: boolean) {
      const text = decoder.decode(bytes, { stream: !final })
      splitter.push(text, (line) => scanLine(line, out, stats))
      if (final) splitter.flush((line) => scanLine(line, out, stats))
    },
  }
}

let lastProgress = 0
function reportProgress(bytesDone: number, bytesTotal: number, lines: number, force = false) {
  const now = Date.now()
  if (!force && now - lastProgress < 120) return
  lastProgress = now
  post({
    type: 'progress',
    ratio: bytesTotal > 0 ? Math.min(1, bytesDone / bytesTotal) : 0,
    bytesDone,
    bytesTotal,
    lines,
  })
}

function finish(scanner: ReturnType<typeof makeScanner>) {
  const san = sanitizeDaily(scanner.out)
  const warnings = [...scanner.stats.warnings]
  if (san.ignored > 0) warnings.push(`其中 ${san.ignored} 条为超出合理范围的单日步数 / 距离 / 爬楼记录`)
  post({
    type: 'done',
    data: scanner.out,
    warnings,
    ignored: scanner.stats.ignored + san.ignored,
  })
}

async function parseZip(file: File) {
  post({ type: 'phase', phase: 'zip' })
  const entries = await readCentralDirectory(file)
  const entry = pickEntry(entries)
  if (!entry) {
    post({ type: 'error', message: 'NOT_EXPORT' })
    return
  }
  const dataStart = await entryDataStart(file, entry)
  const compressed = new Uint8Array(
    await file.slice(dataStart, dataStart + entry.compressedSize).arrayBuffer(),
  )
  if (cancelled) return
  post({ type: 'phase', phase: 'inflate' })
  const scanner = makeScanner()

  if (entry.method === 0) {
    for (let i = 0; i < compressed.length; i += CHUNK) {
      if (cancelled) return
      const end = Math.min(i + CHUNK, compressed.length)
      scanner.push(compressed.subarray(i, end), end >= compressed.length)
      reportProgress(end, compressed.length, scanner.stats.lines)
    }
  } else if (entry.method === 8) {
    let error: string | null = null
    const inflate = new Inflate((data, final) => {
      scanner.push(data, final)
    })
    for (let i = 0; i < compressed.length; i += CHUNK) {
      if (cancelled || error) return
      const end = Math.min(i + CHUNK, compressed.length)
      try {
        inflate.push(compressed.subarray(i, end), end >= compressed.length)
      } catch {
        error = 'NOT_EXPORT'
        post({ type: 'error', message: error })
        return
      }
      reportProgress(end, compressed.length, scanner.stats.lines)
    }
  } else {
    post({ type: 'error', message: 'NOT_EXPORT' })
    return
  }
  reportProgress(compressed.length, compressed.length, scanner.stats.lines, true)
  finish(scanner)
}

async function parseXml(file: File) {
  post({ type: 'phase', phase: 'scan' })
  const scanner = makeScanner()
  for (let i = 0; i < file.size; i += CHUNK * 4) {
    if (cancelled) return
    const end = Math.min(i + CHUNK * 4, file.size)
    const bytes = new Uint8Array(await file.slice(i, end).arrayBuffer())
    scanner.push(bytes, end >= file.size)
    reportProgress(end, file.size, scanner.stats.lines)
  }
  reportProgress(file.size, file.size, scanner.stats.lines, true)
  finish(scanner)
}

async function parseDemo() {
  post({ type: 'phase', phase: 'scan' })
  const xml = generateSampleXml()
  const bytes = new TextEncoder().encode(xml)
  const scanner = makeScanner()
  for (let i = 0; i < bytes.length; i += CHUNK) {
    if (cancelled) return
    const end = Math.min(i + CHUNK, bytes.length)
    scanner.push(bytes.subarray(i, end), end >= bytes.length)
    reportProgress(end, bytes.length, scanner.stats.lines)
    await new Promise((r) => setTimeout(r, 160))
  }
  reportProgress(bytes.length, bytes.length, scanner.stats.lines, true)
  finish(scanner)
}

self.onmessage = async (ev: MessageEvent<WorkerRequest>) => {
  const msg = ev.data
  if (msg.type === 'cancel') {
    cancelled = true
    return
  }
  cancelled = false
  try {
    if (msg.type === 'demo') {
      await parseDemo()
      return
    }
    const name = msg.file.name.toLowerCase()
    if (name.endsWith('.zip')) await parseZip(msg.file)
    else if (name.endsWith('.xml')) await parseXml(msg.file)
    else post({ type: 'error', message: 'NOT_EXPORT' })
  } catch (e) {
    const code = e instanceof Error && e.message === 'NOT_ZIP' ? 'NOT_EXPORT' : 'GENERIC'
    post({ type: 'error', message: code })
  }
}