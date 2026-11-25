<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CountUp from '@/components/CountUp.vue'
import MonthBarChart from '@/components/charts/MonthBarChart.vue'
import CalendarHeatmap from '@/components/CalendarHeatmap.vue'
import { addDays, weekdayOf } from '@/core/dates'
import { fmtInt, fmt1, dateCN } from '@/core/format'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof MonthBarChart>>()
const dayValues = computed(() => Object.fromEntries(props.model.stairs.days.map((d) => [d, 1])))
const gridStart = computed(() => addDays(props.model.period.from, -((weekdayOf(props.model.period.from) + 6) % 7)))
const avgPerActive = computed(() => (props.model.stairs.days.length ? props.model.stairs.total / props.model.stairs.days.length : 0))

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '06 / 08',
      title: '向上纪录',
      periodLabel: m.period.label,
      stats: [
        { label: '总层数', value: `${fmtInt(m.stairs.total)} 层` },
        { label: '约合珠峰', value: `${fmt1(m.stairs.everest)} 倍` },
        { label: '最长连续', value: `${m.stairs.streak1.length} 天` },
        { label: '单日最高', value: m.stairs.maxDay ? `${fmtInt(m.stairs.maxDay.value)} 层` : '—' },
        { label: '十层连续', value: `${m.stairs.streak10.length} 天` },
        { label: '有记录的天数', value: `${fmtInt(m.stairs.days.length)} 天` },
      ],
      copy: m.copy.stairs,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="06 / 08" kicker="Upward" title="向上纪录" :lead="model.copy.stairs" @share="share">
    <template v-if="model.stairs.total > 0">
      <div class="tile s4 accent">
        <div class="tile-label">爬楼总层数</div>
        <div class="tile-figure"><CountUp :value="model.stairs.total" /><small>层</small></div>
        <div class="tile-sub">约等于 {{ fmt1(model.stairs.everest) }} 座珠穆朗玛峰的高度</div>
        <div class="kv-list" style="margin-top: auto; padding-top: 14px">
          <div>
            <span class="k">单日最高</span>
            <span class="v">{{ model.stairs.maxDay ? fmtInt(model.stairs.maxDay.value) : '—' }}<small>层 {{ model.stairs.maxDay ? dateCN(model.stairs.maxDay.day) : '' }}</small></span>
          </div>
          <div><span class="k">爬楼的日子</span><span class="v">{{ fmtInt(model.stairs.days.length) }}<small>天</small></span></div>
          <div><span class="k">这些日子平均</span><span class="v">{{ fmt1(avgPerActive) }}<small>层</small></span></div>
          <div><span class="k">最长连续爬楼</span><span class="v">{{ model.stairs.streak1.length }}<small>天</small></span></div>
          <div><span class="k">最长连续 ≥ 10 层</span><span class="v">{{ model.stairs.streak10.length }}<small>天</small></span></div>
        </div>
      </div>
      <div class="tile s8">
        <div class="tile-label">
          按月层数
          <span v-if="model.stairs.streak1.length > 1" class="legend">最长连续 {{ dateCN(model.stairs.streak1.from) }} – {{ dateCN(model.stairs.streak1.to) }}</span>
        </div>
        <MonthBarChart ref="chart" :data="model.stairs.monthly" :color-index="3" unit="层" />
      </div>
      <div class="tile">
        <div class="tile-label">爬楼日历</div>
        <CalendarHeatmap
          :grid-start="gridStart"
          :from="model.period.from"
          :to="model.period.to"
          :values="dayValues"
          :thresholds="[0, 0, 0]"
          color="var(--good)"
          unit="天"
          legend="每格一天，点亮表示当天爬过楼"
        />
      </div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ zhCN.report.empty }}</div></div>
  </ReportCard>
</template>
