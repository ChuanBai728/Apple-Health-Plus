import type { CopyRule } from '@/copy/engine'

export const stairRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.stairs.total === 0,
    text: '这段时间没有记录到爬楼。',
  },
  {
    priority: 2,
    when: (m) => m.stairs.streak1.length >= 60,
    text: '从 {stairs.streak1.from:date} 到 {stairs.streak1.to:date}，连续 {stairs.streak1.length} 天，你每天都向上走过。',
  },
  {
    priority: 3,
    when: (m) => m.stairs.everest >= 1,
    text: '你一共爬了 {stairs.total:,} 层，高度约等于 {stairs.everest:~1} 个珠穆朗玛峰。',
  },
  {
    priority: 9,
    when: () => true,
    text: '你一共向上走了 {stairs.total:,} 层楼，约合 {stairs.everest:~2} 个珠峰的高度。',
  },
]