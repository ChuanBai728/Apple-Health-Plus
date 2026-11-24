<script setup lang="ts">
import { ref } from 'vue'
import { useECharts, baseTooltip, baseAxis, echarts } from '@/composables/useECharts'
import { chartPalette, chartInk } from '@/composables/useSkin'
import { monthCN } from '@/core/format'

/** 月度均值折线：纵轴不从 0 开始，贴合数据范围 */
const props = defineProps<{
  data: Record<string, number>
  unit: string
  colorIndex?: number
  decimals?: number
  height?: number
}>()
const el = ref<HTMLElement>()

const { getDataURL } = useECharts(el, () => {
  const palette = chartPalette()
  const ink = chartInk()
  const color = palette[(props.colorIndex ?? 0) % palette.length]
  const months = Object.keys(props.data).sort()
  const digits = props.decimals ?? 0
  const values = months.map((m) => Number(props.data[m].toFixed(digits)))
  const avg = values.length ? values.reduce((a, b) => a + b, 0) / values.length : 0
  return {
    animationDuration: 700,
    tooltip: { ...baseTooltip(), axisPointer: { type: 'line' }, valueFormatter: (v: unknown) => `${v} ${props.unit}` },
    grid: { left: 8, right: 12, top: 18, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: months.map(monthCN),
      ...baseAxis(),
      splitLine: { show: false },
      boundaryGap: false,
      axisLabel: { color: ink.faint, fontSize: 10, interval: months.length > 14 ? 2 : 0 },
    },
    yAxis: {
      type: 'value',
      scale: true,
      ...baseAxis(),
      axisLine: { show: false },
      splitNumber: 3,
    },
    series: [
      {
        type: 'line',
        data: values,
        smooth: 0.4,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2.5, color },
        itemStyle: { color },
        areaStyle: {
          origin: 'start',
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '44' },
            { offset: 1, color: color + '00' },
          ]),
        },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: ink.faint, type: 'dashed', width: 1 },
          label: { color: ink.faint, fontSize: 10, formatter: `均值 ${avg.toFixed(digits)}` },
          data: [{ yAxis: Number(avg.toFixed(digits)) }],
        },
      },
    ],
  }
}, () => props.data)

defineExpose({ getDataURL })
</script>

<template>
  <div ref="el" class="chart" :style="{ height: (height ?? 200) + 'px', minHeight: (height ?? 200) + 'px' }" />
</template>
