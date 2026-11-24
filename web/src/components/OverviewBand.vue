<script setup lang="ts">
import { computed } from 'vue'
import type { ReportModel } from '@/types/report'
import { fmtInt, fmt1 } from '@/core/format'
import { noonMinToClock } from '@/core/metrics/rhythm'

const props = defineProps<{ model: ReportModel }>()

const items = computed(() => {
  const m = props.model
  const avgSteps = m.calendar.recordedDays ? m.steps.total / m.calendar.recordedDays : 0
  return [
    { k: '日均步数', v: fmtInt(avgSteps), u: '步', s: `${fmtInt(m.steps.total)} 步合计`, c: 'var(--accent-1)' },
    { k: '总距离', v: fmtInt(m.steps.totalKm), u: 'km', s: `约 ${fmt1(m.steps.marathons)} 个马拉松`, c: 'var(--accent-2)' },
    {
      k: '平均入睡',
      v: m.rhythm.nights ? noonMinToClock(m.rhythm.avgBed) : '—',
      u: '',
      s: m.rhythm.nights ? `每晚 ${fmt1(m.rhythm.avgAsleepHours)} 小时` : '暂无睡眠记录',
      c: 'var(--accent-3)',
    },
    { k: '训练', v: fmtInt(m.training.count), u: '次', s: `${fmt1(m.training.hours)} 小时`, c: 'var(--good)' },
    {
      k: '静息心率',
      v: m.heart.resting.days ? fmtInt(m.heart.resting.avg) : '—',
      u: m.heart.resting.days ? '次/分' : '',
      s: m.heart.hrv.days ? `HRV ${fmtInt(m.heart.hrv.avg)} 毫秒` : '暂无 HRV 记录',
      c: 'var(--danger)',
    },
    {
      k: '户外日光',
      v: m.outdoor.daylightDays ? fmtInt(m.outdoor.daylightHours) : '—',
      u: m.outdoor.daylightDays ? '小时' : '',
      s: m.outdoor.daylightDays ? `日均 ${fmtInt(m.outdoor.daylightAvgMin)} 分钟` : '暂无日光记录',
      c: 'var(--warn)',
    },
  ]
})
</script>

<template>
  <div class="overview-band">
    <div v-for="it in items" :key="it.k" class="ov-item" :style="{ '--c': it.c }">
      <div class="ov-k">{{ it.k }}</div>
      <div class="ov-v">{{ it.v }}<small v-if="it.u">{{ it.u }}</small></div>
      <div class="ov-s">{{ it.s }}</div>
    </div>
  </div>
</template>
