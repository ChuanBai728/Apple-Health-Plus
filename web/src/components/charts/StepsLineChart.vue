<script setup lang="ts">
import { ref } from 'vue'
import { useECharts, baseTooltip, baseAxis, barGradient, echarts } from '@/composables/useECharts'
import { chartPalette, chartInk } from '@/composables/useSkin'
import { monthCN, fmtInt } from '@/core/format'

const props = defineProps<{ data: Record<string, number> }>()
const el = ref<HTMLElement>()

const { getDataURL } = useECharts(el, () => {
  const palette = chartPalette()
  const ink = chartInk()
  const months = Object.keys(props.data).sort()
  const values = months.map((m) => Math.round(props.data[m]))
  let maxI = 0
  let minI = 0
  values.forEach((v, i) => {
    if (v > values[maxI]) maxI = i
    if (v < values[minI]) minI = i
  })
  return {
    animationDuration: 700,
    tooltip: { ...baseTooltip(), valueFormatter: (v: unknown) => `${fmtInt(Number(v))} 步 / 天` },
    grid: { left: 8, right: 12, top: 30, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: months.map(monthCN),
      ...baseAxis(),
      splitLine: { show: false },
      boundaryGap: false,
      axisLabel: { color: ink.faint, fontSize: 10, interval: months.length > 14 ? 2 : 0 },
    },
    yAxis: { type: 'value', ...baseAxis(), axisLine: { show: false } },
    series: [
      {
        type: 'line',
        data: values,
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 2.5, color: palette[0] },
        itemStyle: { color: palette[0] },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: palette[0] + '55' },
            { offset: 1, color: palette[0] + '00' },
          ]),
        },
        markPoint: {
          symbolSize: 46,
          label: { fontSize: 10, color: ink.text, formatter: (p: { value?: unknown }) => fmtInt(Number(p.value)) },
          data: [
            { coord: [maxI, values[maxI]], value: values[maxI], itemStyle: { color: palette[1] + 'cc' } },
            { coord: [minI, values[minI]], value: values[minI], itemStyle: { color: ink.hair } },
          ],
        },
      },
    ],
  }
}, () => props.data)

defineExpose({ getDataURL })
</script>

<template>
  <div ref="el" class="chart" />
</template>