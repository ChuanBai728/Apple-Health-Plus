import type { CopyRule } from '@/copy/engine'

export const napRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.naps.count === 0,
    text: '这段时间没有记录到小睡。',
  },
  {
    priority: 2,
    when: (m) => m.naps.count >= 30 && m.naps.longest !== null && m.naps.longest.value >= 120,
    text: '你有 {naps.count:,} 天午间合过眼，一共 {naps.totalHours:~1} 小时。最长的一次有 {naps.longest.value:~0} 分钟——那天你显然很需要它。',
  },
  {
    priority: 3,
    when: (m) => m.naps.count >= 30,
    text: '你给自己安排了 {naps.count:,} 次小睡，加起来 {naps.totalHours:~1} 小时。',
  },
  {
    priority: 3,
    when: (m) => m.naps.count >= 30,
    text: '这段时间你有 {naps.count:,} 天午间合过眼，一共 {naps.totalHours:~1} 小时。',
  },
  {
    priority: 9,
    when: () => true,
    text: '这段时间你记录到 {naps.count:,} 次小睡，共 {naps.totalHours:~1} 小时。',
  },
]