import type { CopyRule } from '@/copy/engine'

export const trainingRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.training.count === 0,
    text: '这段时间没有记录到训练。',
  },
  {
    priority: 2,
    when: (m) => m.training.gap !== null && m.training.gap.days > 14,
    text: '最长的一次离开是 {training.gap.days} 天。回来之后，你又练了 {training.gap.comeback:,} 次。',
  },
  {
    priority: 3,
    when: (m) => m.training.bestYear !== null && Object.keys(m.training.yearly).length > 1,
    text: '{training.bestYear.year} 年你练了 {training.bestYear.count:,} 次，是纪录最多的一年。',
  },
  {
    priority: 4,
    when: (m) => m.training.streak.length >= 3,
    text: '你最长的连续训练是 {training.streak.length} 天，从 {training.streak.from:date} 到 {training.streak.to:date}。',
  },
  {
    priority: 9,
    when: () => true,
    text: '这段时间你完成了 {training.count:,} 次训练，共 {training.hours:~1} 小时，多数安排在{training.primarySlot}。',
  },
]