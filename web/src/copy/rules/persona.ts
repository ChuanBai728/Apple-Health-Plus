import type { CopyRule } from '@/copy/engine'

export const personaRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.persona.key === 'gym',
    text: '这段时间你去了 {training.groups.0.count:,} 次力量训练，每次大约 {training.groups.0.medianMin:~0} 分钟。',
  },
  {
    priority: 1,
    when: (m) => m.persona.key === 'walker',
    text: '你的步数中位数是 {steps.median:,} 步——城市是你的跑步机。',
  },
  {
    priority: 1,
    when: (m) => m.persona.key === 'stairs',
    text: '电梯在你生活里只是个摆设：{stairs.total:,} 层楼，都是你一步一步走上去的。',
  },
  {
    priority: 9,
    when: () => true,
    text: '不声不响，你攒下了 {steps.total:,} 步和 {training.count:,} 次训练。稳步前行，也是一种风格。',
  },
]