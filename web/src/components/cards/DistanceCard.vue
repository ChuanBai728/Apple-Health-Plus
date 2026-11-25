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

/** 三环都基于有圆环记录的天数，口径一致 */
const rings = computed(() => {
  const r = props.model.rings
  const days = Math.max(1, r.daysWithData)
  return [
    { key: 'close', name: '三环全闭合', pct: Math.min(1, r.closedDays / days), c: 'var(--accent-1)' },
    {
      key: 'move',
      name: '运动时长达标',
      pct: Math.min(1, (r.exerciseHours * 60) / (days * 30)),
      c: 'var(--accent-2)',
    },
    {
      key: 'stand',
      name: '站立满 12 小时',
      pct: Math.min(1, props.model.outdoor.stand12Days / days),
      c: 'var(--accent-3)',
    },
  ]
})

function arc(r: number, pct: number) {
  const c = 2 * Math.PI * r
  return `${c * Math.min(1, Math.max(0, pct))} ${c}`
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
        { label: '步数中位数', value: `${fmtInt(m.steps.median)} 步` },
        { label: '三环闭合率', value: `${fmtInt(m.rings.rate * 100)}%` },
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
          <div class="tile-label">活动圆环<span class="legend">按有记录的天数统计</span></div>
          <div class="rings-visual">
            <svg class="rings-svg" width="168" height="168" viewBox="0 0 168 168" aria-hidden="true">
              <g transform="rotate(-90 84 84)">
                <circle class="rk-track" cx="84" cy="84" r="72" fill="none" stroke-width="12" />
                <circle class="rk-bar" cx="84" cy="84" r="72" fill="none" stroke-width="12" stroke-linecap="round" :stroke="rings[0].c" :stroke-dasharray="arc(72, rings[0].pct)" />
                <circle class="rk-track" cx="84" cy="84" r="54" fill="none" stroke-width="12" />
                <circle class="rk-bar" cx="84" cy="84" r="54" fill="none" stroke-width="12" stroke-linecap="round" :stroke="rings[1].c" :stroke-dasharray="arc(54, rings[1].pct)" />
                <circle class="rk-track" cx="84" cy="84" r="36" fill="none" stroke-width="12" />
                <circle class="rk-bar" cx="84" cy="84" r="36" fill="none" stroke-width="12" stroke-linecap="round" :stroke="rings[2].c" :stroke-dasharray="arc(36, rings[2].pct)" />
              </g>
              <circle class="rk-core" cx="84" cy="84" r="30" />
              <text class="rk-figure" x="84" y="77" text-anchor="middle" dominant-baseline="middle">{{ ringPct }}%</text>
              <text class="rk-caption" x="84" y="97" text-anchor="middle" dominant-baseline="middle">全闭合率</text>
            </svg>
            <ul class="rings-legend">
              <li v-for="r in rings" :key="r.key">
                <i :style="{ background: r.c }" />
                <span>{{ r.name }}</span>
                <b>{{ Math.round(r.pct * 100) }}%</b>
              </li>
            </ul>
          </div>
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
.rings-visual {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 16px;
  padding-top: 4px;
}
.rings-svg {
  flex: none;
  filter: drop-shadow(0 10px 22px rgba(0, 0, 0, 0.18));
}
.rk-track {
  stroke: color-mix(in srgb, var(--text) 8%, transparent);
}
.rk-core {
  fill: var(--card-solid);
}
.rk-figure {
  fill: var(--text);
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.rk-caption {
  fill: var(--muted);
  font-size: 11px;
}
.rings-legend {
  list-style: none;
  display: grid;
  gap: 9px;
  width: 100%;
}
.rings-legend li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12.5px;
  color: var(--muted);
}
.rings-legend i {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex: none;
  box-shadow: 0 0 8px currentColor;
}
.rings-legend span {
  flex: 1;
  min-width: 0;
}
.rings-legend b {
  flex: none;
  margin-left: 8px;
  font-weight: 800;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
</style>
