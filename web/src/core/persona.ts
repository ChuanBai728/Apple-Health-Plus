import type { Persona, PersonaKey, NapMetrics, TrainingMetrics, StairMetrics, StepMetrics, RingMetrics } from '@/types/report'
import type { Streak } from '@/core/metrics/streaks'

export interface PersonaInput {
  years: number
  naps: NapMetrics
  training: TrainingMetrics
  stairs: StairMetrics
  steps: StepMetrics
  rings: RingMetrics
  strengthStreak: Streak
  stairRecordedDays: number
}

export const PERSONA_NAMES: Record<PersonaKey, string> = {
  gym: '铁馆常驻',
  walker: '城市步行者',
  stairs: '台阶常客',
  steady: '稳步前行',
}

const fmtInt = (n: number) => Math.round(n).toLocaleString('zh-CN')

export function decidePersona(i: PersonaInput): Persona {
  const N = i.years
  const strength = i.training.groups.find((g) => g.activity === 'strength')
  const strengthCount = strength?.count ?? 0
  const stairDailyAvg = i.stairRecordedDays > 0 ? i.stairs.total / i.stairRecordedDays : 0

  let key: PersonaKey = 'steady'
  if (strengthCount >= 52 * N) key = 'gym'
  else if (i.steps.median >= 8000) key = 'walker'
  else if (stairDailyAvg >= 8 || i.stairs.streak1.length >= 60) key = 'stairs'

  const tagCandidates: Array<[string, boolean]> = [
    ['午睡爱好者', i.naps.count >= 30 * N || i.naps.totalHours >= 40 * N],
    ['夜训型', i.training.nightShare >= 0.5 && i.training.count >= 12 * N],
    ['三环闭合达人', i.rings.rate >= 0.4],
    ['里程收集者', i.steps.totalKm >= 1000 * N],
    ['万步常客', i.steps.pct10k >= 25],
  ]
  const tags = tagCandidates.filter(([, ok]) => ok).slice(0, 3).map(([t]) => t)

  let stats: Persona['stats']
  switch (key) {
    case 'gym':
      stats = [
        { label: '力量训练', value: `${fmtInt(strengthCount)} 次` },
        { label: '中位时长', value: `${fmtInt(strength?.medianMin ?? 0)} 分钟` },
        { label: '最长连续', value: `${i.strengthStreak.length} 天` },
      ]
      break
    case 'walker':
      stats = [
        { label: '步数中位', value: `${fmtInt(i.steps.median)} 步` },
        { label: '万步天数', value: `${fmtInt(i.steps.days10k)} 天` },
        { label: '总距离', value: `${fmtInt(i.steps.totalKm)} km` },
      ]
      break
    case 'stairs':
      stats = [
        { label: '总层数', value: `${fmtInt(i.stairs.total)} 层` },
        { label: '最长连续', value: `${i.stairs.streak1.length} 天` },
        { label: '约合珠峰', value: `${i.stairs.everest.toFixed(1)} 倍` },
      ]
      break
    default:
      stats = [
        { label: '总步数', value: `${fmtInt(i.steps.total)} 步` },
        { label: '训练', value: `${fmtInt(i.training.count)} 次` },
        { label: '三环闭合', value: `${fmtInt(i.rings.closedDays)} 天` },
      ]
  }
  return { key, name: PERSONA_NAMES[key], tags, stats }
}