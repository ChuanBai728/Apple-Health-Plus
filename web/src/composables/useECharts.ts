import { onMounted, onBeforeUnmount, watch, type Ref } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  MarkAreaComponent,
  MarkPointComponent,
  MarkLineComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import { useSkin } from '@/composables/useSkin'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, MarkAreaComponent, MarkPointComponent, MarkLineComponent, CanvasRenderer])

export function useECharts(
  el: Ref<HTMLElement | undefined>,
  build: () => EChartsCoreOption,
  dataDep: () => unknown,
) {
  let chart: echarts.ECharts | null = null
  let ro: ResizeObserver | null = null
  const { current } = useSkin()

  const render = () => {
    if (chart) chart.setOption(build(), true)
  }

  onMounted(() => {
    if (!el.value) return
    chart = echarts.init(el.value)
    render()
    ro = new ResizeObserver(() => chart?.resize())
    ro.observe(el.value)
  })

  watch([dataDep, () => current.value.key], render)

  onBeforeUnmount(() => {
    ro?.disconnect()
    chart?.dispose()
    chart = null
  })

  return {
    getDataURL(): string | null {
      return chart ? chart.getDataURL({ pixelRatio: 2, backgroundColor: 'transparent' }) : null
    },
  }
}

export function baseTooltip() {
  const cs = getComputedStyle(document.documentElement)
  return {
    trigger: 'axis' as const,
    backgroundColor: cs.getPropertyValue('--card-solid').trim(),
    borderColor: cs.getPropertyValue('--hair-strong').trim(),
    textStyle: { color: cs.getPropertyValue('--text').trim(), fontSize: 12 },
    axisPointer: { type: 'shadow' as const },
  }
}

export function baseAxis() {
  const cs = getComputedStyle(document.documentElement)
  const faint = cs.getPropertyValue('--faint').trim()
  const hair = cs.getPropertyValue('--hair').trim()
  return {
    axisLine: { lineStyle: { color: hair } },
    axisTick: { show: false },
    axisLabel: { color: faint, fontSize: 11 },
    splitLine: { lineStyle: { color: hair, type: 'dashed' as const } },
  }
}

export function barGradient(c1: string, c2: string) {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: c1 },
    { offset: 1, color: c2 },
  ])
}

export { echarts }