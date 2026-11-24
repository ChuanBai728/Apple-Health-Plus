<script setup lang="ts">
import { ref } from 'vue'
import { useECharts, baseTooltip, baseAxis, barGradient } from '@/composables/useECharts'
import { chartPalette, chartInk } from '@/composables/useSkin'
import { monthCN } from '@/core/format'

const props = defineProps<{
  data: Record<string, number>
  colorIndex?: number
  gap?: { from: string; to: string; days?: number } | null
  unit?: string
}>()

const el = ref<HTMLElement>()

const { getDataURL } = useECharts(el, () => {
  const palette = chartPalette()
  const ink = chartInk()
  const color = palette[(props.colorIndex ?? 0) % palette.length]
  const months = Object.keys(props.data).sort()
  const values = months.map((m) => props.data[m])
  const gap = props.gap
  const markArea =
    gap && (gap.days ?? 0) > 14
      ? {
          silent: true,
          itemStyle: { color: ink.hair },
          label: { show: true, color: ink.faint, fontSize: 10, position: 'insideTop' as const, formatter: '离开期' },
          data: [[{ xAxis: gap.from.slice(0, 7) }, { xAxis: gap.to.slice(0, 7) }]],
        }
      : undefined
  return {
    animationDuration: 700,
    tooltip: { ...baseTooltip(), valueFormatter: (v: unknown) => `${v} ${props.unit ?? '次'}` },
    grid: { left: 8, right: 12, top: 26, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: months.map(monthCN),
      ...baseAxis(),
      splitLine: { show: false },
      axisLabel: { color: ink.faint, fontSize: 10, interval: months.length > 14 ? 2 : 0 },
    },
    yAxis: { type: 'value', ...baseAxis(), axisLine: { show: false } },
    series: [
      {
        type: 'bar',
        data: values,
        barMaxWidth: 26,
        itemStyle: {
          borderRadius: [5, 5, 0, 0],
          color: barGradient(color, color + '44'),
        },
        markArea,
      },
    ],
  }
}, () => props.data)

defineExpose({ getDataURL })
</script>

<template>
  <div ref="el" class="chart" />
</template>