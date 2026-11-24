import { defineStore } from 'pinia'
import type { RawExtract } from '@/types/extract'
import type { ReportModel } from '@/types/report'
import { buildReport, buildPeriods } from '@/core/report'

export const useReportStore = defineStore('report', {
  state: () => ({
    extract: null as RawExtract | null,
    ignored: 0,
    warnings: [] as string[],
    periodKey: 'all',
    fileName: '',
    isDemo: false,
  }),
  getters: {
    hasData: (s) => s.extract !== null,
    periods: (s) => (s.extract ? buildPeriods(s.extract) : []),
    model(s): ReportModel | null {
      if (!s.extract) return null
      return buildReport(s.extract, s.periodKey, { ignored: s.ignored, warnings: s.warnings })
    },
  },
  actions: {
    setExtract(data: RawExtract, ignored: number, warnings: string[], fileName: string, isDemo: boolean) {
      this.extract = data
      this.ignored = ignored
      this.warnings = warnings
      this.periodKey = 'all'
      this.fileName = fileName
      this.isDemo = isDemo
    },
    setPeriod(key: string) {
      this.periodKey = key
    },
    clear() {
      this.extract = null
      this.warnings = []
      this.ignored = 0
      this.periodKey = 'all'
      this.fileName = ''
      this.isDemo = false
    },
  },
})