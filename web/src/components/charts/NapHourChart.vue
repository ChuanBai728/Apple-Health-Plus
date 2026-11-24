<script setup lang="ts">
import { ref } from 'vue'
import { useECharts, baseTooltip, baseAxis, barGradient } from '@/composables/useECharts'
import { chartPalette, chartInk } from '@/composables/useSkin'

const props = defineProps<{ data: number[] }>()
const el = ref<HTMLElement>()

const { getDataURL } = useECharts(el, () => {
  const palette = chartPalette()
  const ink = chartInk()
  const hours = Array.from({ length: 10 }, (_, i) => `${i + 10} 点`)
  return {
    animationDuration: 700,
    tooltip: { ...baseTooltip(), valueFormatter: (v: unknown) => `${v} 次` },
    grid: { left: 8, right: 12, top: 20, bottom: 4, outerBoundsMode: 'same', outerBoundsContain: 'axisLabel' },
    xAxis: {
      type: 'category',
      data: hours,
      ...baseAxis(),
      splitLine: { show: false },
      axisLabel: { color: ink.faint, fontSize: 10.5 },
    },
    yAxis: { type: 'value', ...baseAxis(), axisLine: { show: false } },
    series: [
      {
        type: 'bar',
        data: props.data,
        barMaxWidth: 30,
        itemStyle: { borderRadius: [5, 5, 0, 0], color: barGradient(palette[1], palette[1] + '44') },
      },
    ],
  }
}, () => props.data)

defineExpose({ getDataURL })
</script>

<template>
  <div ref="el" class="chart" style="height: 200px" />
</template>