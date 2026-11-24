import type { CopyRule } from '@/copy/engine'

export const distanceRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.steps.totalKm === 0 && m.steps.total === 0,
    text: '这段时间没有记录到步数与距离。',
  },
  {
    priority: 2,
    when: (m) => m.steps.marathons >= 10,
    text: '你走过的 {steps.totalKm:~0} 公里，约等于 {steps.marathons:~1} 个马拉松首尾相接。',
  },
  {
    priority: 3,
    when: (m) => m.steps.equatorPct >= 1,
    text: '累计 {steps.totalKm:~0} 公里——你已经绕了赤道的 {steps.equatorPct:~1}%。',
  },
  {
    priority: 4,
    when: (m) => m.steps.streak10k.length >= 7,
    text: '从 {steps.streak10k.from:date} 到 {steps.streak10k.to:date}，你连续 {steps.streak10k.length} 天走满一万步。',
  },
  {
    priority: 9,
    when: () => true,
    text: '这段时间你走了 {steps.total:,} 步、{steps.totalKm:~0} 公里，约合 {steps.marathons:~1} 个马拉松。',
  },
]