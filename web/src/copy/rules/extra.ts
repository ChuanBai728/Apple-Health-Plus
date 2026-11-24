import type { CopyRule } from '@/copy/engine'

export const calendarRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.calendar.recordedDays === 0,
    text: '这段时间没有记录到步数，日历暂时是空白的。',
  },
  {
    priority: 2,
    when: (m) => m.calendar.bestWeek !== null && m.calendar.busiestMonth !== null,
    text: '{calendar.bestWeek.from:date} 起的那一周，你走了 {calendar.bestWeek.total:,} 步，是这段时间最热闹的七天。{calendar.topWeekday:wd}通常是你走得最多的日子。',
  },
  {
    priority: 9,
    when: () => true,
    text: '{calendar.totalDays} 天里，有 {calendar.recordedDays} 天留下了步数记录。',
  },
]

export const rhythmRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.rhythm.nights === 0,
    text: '这段时间没有足够的夜间睡眠记录来描绘作息。',
  },
  {
    priority: 2,
    when: (m) => m.rhythm.nights >= 30 && m.rhythm.weekendShiftMin >= 20,
    text: '你通常在 {rhythm.avgBed:clock} 前后入睡、{rhythm.avgWake:clock} 醒来。周五和周六晚上，入睡时间会往后挪 {rhythm.weekendShiftMin:~0} 分钟左右。',
  },
  {
    priority: 3,
    when: (m) => m.rhythm.nights >= 30 && m.rhythm.bedSpreadMin <= 30,
    text: '{rhythm.nights} 个夜晚里，你的入睡时间大多落在 {rhythm.avgBed:clock} 前后半小时内。',
  },
  {
    priority: 9,
    when: () => true,
    text: '记录到的 {rhythm.nights} 个夜晚，平均入睡 {rhythm.avgBed:clock}，醒来 {rhythm.avgWake:clock}，每晚约 {rhythm.avgAsleepHours:~1} 小时。',
  },
]

export const heartRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.heart.resting.days === 0 && m.heart.hrv.days === 0,
    text: '这段时间没有静息心率或心率变异性记录（通常需要佩戴 Apple Watch 入睡或静坐）。',
  },
  {
    priority: 2,
    when: (m) => m.heart.resting.days > 0 && m.heart.hrv.days > 0,
    text: '{heart.resting.days} 天的静息心率平均 {heart.resting.avg:~0} 次/分，心率变异性平均 {heart.hrv.avg:~0} 毫秒。这些数字只是你自己的刻度，留着和未来的自己对照。',
  },
  {
    priority: 9,
    when: () => true,
    text: '这段时间记录到 {heart.resting.days} 天静息心率，平均 {heart.resting.avg:~0} 次/分。',
  },
]

export const outdoorRules: CopyRule[] = [
  {
    priority: 1,
    when: (m) => m.outdoor.daylightDays === 0 && m.outdoor.standDays === 0,
    text: '这段时间没有日光与站立记录。',
  },
  {
    priority: 2,
    when: (m) => m.outdoor.daylightDays > 0 && m.outdoor.daylightMax !== null,
    text: '你在户外日光下度过了 {outdoor.daylightHours:~0} 小时。{outdoor.daylightMax.day:date} 那天最久，有 {outdoor.daylightMax.value:~0} 分钟。',
  },
  {
    priority: 9,
    when: () => true,
    text: '平均每天有 {outdoor.standAvgHours:~1} 个小时起身活动过。',
  },
]
