<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CountUp from '@/components/CountUp.vue'
import StepsLineChart from '@/components/charts/StepsLineChart.vue'
import { fmtInt, fmt1, fmt2, dateCN } from '@/core/format'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof StepsLineChart>>()

const ringPct = computed(() => Math.round(props.model.rings.rate * 100))

function arc(r: number, pct: number, color: string) {
  const c = 2 * Math.PI * r
  return { dash: `${c * Math.min(1, pct)} ${c}`, color }
}

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '07 / 08',
      title: '里程与圆环',
      periodLabel: m.period.label,
      stats: [
        { label: '步行跑步距离', value: `${fmtInt(m.steps.totalKm)} km` },
        { label: '约合马拉松', value: `${fmt1(m.steps.marathons)} 个` },
        { label: '总步数', value: `${fmtInt(m.steps.total)} 步` },
        { label: '三环全闭合', value: `${fmtInt(m.rings.closedDays)} 天` },
      ],
      copy: m.copy.distance,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="07 / 08" kicker="Distance" title="里程与圆环" :lead="model.copy.distance" @share="share">
    <template v-if="model.steps.total > 0 || model.steps.totalKm > 0">
      <div class="tile s4 accent">
        <div class="tile-label">步行 + 跑步总距离</div>
        <div class="tile-figure"><CountUp :value="model.steps.totalKm" /><small>km</small></div>
        <div class="tile-sub">
          约 {{ fmt1(model.steps.marathons) }} 个马拉松首尾相接，相当于赤道的
          {{ model.steps.equatorPct < 1 ? fmt2(model.steps.equatorPct) : fmt1(model.steps.equatorPct) }}%
        </div>
        <div class="kv-list" style="margin-top: auto; padding-top: 14px">
          <div><span class="k">总步数</span><span class="v"><CountUp :value="model.steps.total" /></span></div>
          <div><span class="k">日中位数</span><span class="v">{{ fmtInt(model.steps.median) }}<small>步</small></span></div>
          <div><span class="k">p90</span><span class="v">{{ fmtInt(model.steps.p90) }}<small>步</small></span></div>
        </div>
      </div>

      <div class="tile s8">
        <div class="tile-label">
          月度日均步数
          <span v-if="model.steps.bestMonth && model.steps.worstMonth" class="legend">
            最高 {{ model.steps.bestMonth.month }} · 最低 {{ model.steps.worstMonth.month }}
          </span>
        </div>
        <StepsLineChart ref="chart" :data="model.steps.monthlyAvg" />
      </div>

      <div class="tile s4">
        <div class="tile-label">步数里程碑</div>
        <div class="kv-list">
          <div>
            <span class="k">单日最高</span>
            <span class="v">{{ model.steps.maxDay ? fmtInt(model.steps.maxDay.value) : '—' }}<small>{{ model.steps.maxDay ? dateCN(model.steps.maxDay.day) : '' }}</small></span>
          </div>
          <div><span class="k">≥ 10,000 步</span><span class="v">{{ fmtInt(model.steps.days10k) }}<small>天 · {{ fmt1(model.steps.pct10k) }}%</small></span></div>
          <div><span class="k">最长连续万步</span><span class="v">{{ model.steps.streak10k.length }}<small>天</small></span></div>
          <div><span class="k">最长连续 7,000 步</span><span class="v">{{ model.steps.streak7k.length }}<small>天</small></span></div>
        </div>
      </div>

      <template v-if="model.rings.daysWithData > 0">
        <div class="tile s4 rings-tile">
          <div class="tile-label">活动圆环<span class="legend">三环全闭合率</span></div>
          <svg class="rings-svg" width="150" height="150" viewBox="0 0 150 150" aria-hidden="true">
            <g transform="rotate(-90 75 75)">
              <circle cx="75" cy="75" r="62" fill="none" stroke="var(--hair)" stroke-width="11" />
              <circle cx="75" cy="75" r="62" fill="none" stroke="var(--accent-1)" stroke-width="11" stroke-linecap="round" :stroke-dasharray="arc(62, model.rings.rate, '').dash" />
              <circle cx="75" cy="75" r="46" fill="none" stroke="var(--hair)" stroke-width="11" />
              <circle cx="75" cy="75" r="46" fill="none" stroke="var(--accent-2)" stroke-width="11" stroke-linecap="round" :stroke-dasharray="arc(46, Math.min(1, (model.rings.exerciseHours * 60) / Math.max(1, model.rings.daysWithData * 30)), '').dash" />
              <circle cx="75" cy="75" r="30" fill="none" stroke="var(--hair)" stroke-width="11" />
              <circle cx="75" cy="75" r="30" fill="none" stroke="var(--accent-3)" stroke-width="11" stroke-linecap="round" :stroke-dasharray="arc(30, Math.min(1, model.rings.closedDays / Math.max(1, model.rings.daysWithData)), '').dash" />
            </g>
            <text x="75" y="70" text-anchor="middle" fill="var(--text)" font-size="26" font-weight="800">{{ ringPct }}%</text>
            <text x="75" y="92" text-anchor="middle" fill="var(--faint)" font-size="11">全闭合率</text>
          </svg>
        </div>
        <div class="tile s4">
          <div class="tile-label">圆环明细</div>
          <div class="kv-list">
            <div><span class="k">三环全闭合</span><span class="v">{{ fmtInt(model.rings.closedDays) }}<small>天</small></span></div>
            <div><span class="k">有圆环记录</span><span class="v">{{ fmtInt(model.rings.daysWithData) }}<small>天</small></span></div>
            <div><span class="k">最长连续闭合</span><span class="v">{{ model.rings.streak.length }}<small>天</small></span></div>
            <div><span class="k">累计运动时长</span><span class="v">{{ fmt1(model.rings.exerciseHours) }}<small>小时</small></span></div>
          </div>
        </div>
      </template>
      <div v-else class="tile s8"><div class="empty-note">这段时间没有活动圆环记录（需要 Apple Watch）。</div></div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ zhCN.report.empty }}</div></div>
  </ReportCard>
</template>

<style scoped>
.rings-tile {
  align-items: center;
}
.rings-tile .tile-label {
  align-self: stretch;
}
.rings-svg {
  margin: auto 0;
}
</style>
