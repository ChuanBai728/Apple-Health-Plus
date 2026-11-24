import { defineStore } from 'pinia'
import { useReportStore } from '@/stores/report'
import router from '@/router'

export type ParseStatus = 'idle' | 'parsing' | 'error'

export const useParseStore = defineStore('parse', {
  state: () => ({
    status: 'idle' as ParseStatus,
    phase: 'zip' as 'zip' | 'inflate' | 'scan',
    ratio: 0,
    bytesDone: 0,
    bytesTotal: 0,
    lines: 0,
    startedAt: 0,
    now: 0,
    fileName: '',
    isDemo: false,
    error: '' as '' | 'NOT_EXPORT' | 'GENERIC' | 'CANCELLED',
    worker: null as Worker | null,
    timer: 0,
  }),
  getters: {
    elapsedSec: (s) => (s.startedAt ? Math.max(0, (s.now - s.startedAt) / 1000) : 0),
    etaSec(): number {
      if (this.ratio <= 0.02 || this.ratio >= 1) return NaN
      return (this.elapsedSec / this.ratio) * (1 - this.ratio)
    },
  },
  actions: {
    spawn() {
      this.teardown()
      const worker = new Worker(new URL('../worker/parser.worker.ts', import.meta.url), {
        type: 'module',
      })
      this.worker = worker
      worker.onmessage = (ev) => {
        const msg = ev.data
        switch (msg.type) {
          case 'phase':
            this.phase = msg.phase
            break
          case 'progress':
            this.ratio = msg.ratio
            this.bytesDone = msg.bytesDone
            this.bytesTotal = msg.bytesTotal
            this.lines = msg.lines
            break
          case 'done': {
            const report = useReportStore()
            report.setExtract(msg.data, msg.ignored, msg.warnings, this.fileName, this.isDemo)
            this.teardown()
            this.status = 'idle'
            router.push('/report')
            break
          }
          case 'error':
            this.error = msg.message === 'NOT_EXPORT' ? 'NOT_EXPORT' : 'GENERIC'
            this.status = 'error'
            this.teardown()
            break
        }
      }
      worker.onerror = () => {
        this.error = 'GENERIC'
        this.status = 'error'
        this.teardown()
      }
      return worker
    },
    begin(fileName: string, isDemo: boolean) {
      this.status = 'parsing'
      this.phase = 'zip'
      this.ratio = 0
      this.bytesDone = 0
      this.bytesTotal = 0
      this.lines = 0
      this.error = ''
      this.fileName = fileName
      this.isDemo = isDemo
      this.startedAt = Date.now()
      this.now = Date.now()
      this.timer = window.setInterval(() => {
        this.now = Date.now()
      }, 500)
    },
    start(file: File) {
      this.begin(file.name, false)
      this.spawn().postMessage({ type: 'parse', file })
    },
    startDemo() {
      this.begin('示例数据', true)
      this.spawn().postMessage({ type: 'demo' })
    },
    cancel() {
      this.worker?.terminate()
      this.teardown()
      this.status = 'error'
      this.error = 'CANCELLED'
    },
    reset() {
      this.teardown()
      this.status = 'idle'
      this.error = ''
      this.ratio = 0
    },
    teardown() {
      if (this.timer) clearInterval(this.timer)
      this.timer = 0
      this.worker?.terminate()
      this.worker = null
    },
  },
})