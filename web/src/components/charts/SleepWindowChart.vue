<script setup lang="ts">
import { ref } from 'vue'
import { useECharts, baseTooltip, baseAxis, barGradient } from '@/composables/useECharts'
import { chartPalette, chartInk } from '@/composables/useSkin'
import { monthCN } from '@/core/format'
import { noonMinToClock } from '@/core/metrics/rhythm'

/** 每月平均入睡 → 醒来的浮动柱；纵轴是钟面时刻（距中午分钟数） */
const props = defineProps<{ data: Record<string, { bed: number; wake: number; asleepHours: number }> }>()
const el = ref<HTMLElement>()

const { getDataURL } = useECharts(el, () => {
  const palette = chartPalette()
  const ink = chartInk()
  const months = Object.keys(props.data).sort()
  const beds = months.map((m) => Math.round(props.data[m].bed))
  const spans = months.map((m) => Math.round(props.data[m].wake - props.data[m].bed))
  const lo = Math.floor((Math.min(...beds, 600) - 30) / 60) * 60
  const hi = Math.ceil((Math.max(...months.map((m) => props.data[m].wake), 1140) + 30) / 60) * 60
  return {
    animationDuration: 700,
    tooltip: {
      ...baseTooltip(),
      formatter: (ps: Array<{ dataIndex: number }>) => {
        const i = ps[0].dataIndex
        const d = props.data[months[i]]
        return `${months[i]}<br/>入睡 ${noonMinToClock(d.bed)} · 醒来 ${noonMinToClock(d.wake)}<br/>平均睡着 ${d.asleepHours.toFixed(1)} 小时`
      },
    },
    grid: { left: 8, right: 12, top: 14, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: months.map(monthCN),
      ...baseAxis(),
      splitLine: { show: false },
      axisLabel: { color: ink.faint, fontSize: 10, interval: months.length > 14 ? 2 : 0 },
    },
    yAxis: {
      type: 'value',
      inverse: true,
      min: lo,
      max: hi,
      interval: 120,
      ...baseAxis(),
      axisLine: { show: false },
      axisLabel: { color: ink.faint, fontSize: 10, formatter: (v: number) => noonMinToClock(v) },
    },
    series: [
      { type: 'bar', stack: 'w', data: beds, itemStyle: { color: 'transparent' }, emphasis: { disabled: true }, silent: true },
      {
        type: 'bar',
        stack: 'w',
        data: spans,
        barMaxWidth: 18,
        itemStyle: { borderRadius: 999, color: barGradient(palette[1], palette[0]) },
      },
    ],
  }
}, () => props.data)

defineExpose({ getDataURL })
</script>

<template>
  <div ref="el" class="chart" />
</template>
