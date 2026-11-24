<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CountUp from '@/components/CountUp.vue'
import NapHourChart from '@/components/charts/NapHourChart.vue'
import SleepWindowChart from '@/components/charts/SleepWindowChart.vue'
import { noonMinToClock } from '@/core/metrics/rhythm'
import { fmtInt, fmt1, dateCN, minutesCN } from '@/core/format'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof SleepWindowChart>>()

const r = computed(() => props.model.rhythm)
const n = computed(() => props.model.naps)
const BED_HOURS = ['20', '21', '22', '23', '0', '1', '2', '3']
const maxBed = computed(() => Math.max(1, ...r.value.bedHistogram))
const topBed = computed(() => r.value.bedHistogram.indexOf(maxBed.value))

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '03 / 08',
      title: '睡眠与作息',
      periodLabel: m.period.label,
      stats: [
        { label: '平均入睡', value: m.rhythm.nights ? noonMinToClock(m.rhythm.avgBed) : '—' },
        { label: '平均醒来', value: m.rhythm.nights ? noonMinToClock(m.rhythm.avgWake) : '—' },
        { label: '每晚睡着', value: `${fmt1(m.rhythm.avgAsleepHours)} 小时` },
        { label: '有效小睡', value: `${fmtInt(m.naps.count)} 次` },
      ],
      copy: m.copy.rhythm,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="03 / 08" kicker="Sleep" title="睡眠与作息" :lead="model.copy.rhythm" @share="share">
    <template v-if="r.nights > 0">
      <div class="tile s4 accent">
        <div class="tile-label">一晚的样子<span class="legend">{{ r.nights }} 个夜晚的平均</span></div>
        <div class="clock-pair">
          <div>
            <div class="tile-figure">{{ noonMinToClock(r.avgBed) }}</div>
            <div class="tile-sub">入睡</div>
          </div>
          <span class="clock-arrow">→</span>
          <div>
            <div class="tile-figure">{{ noonMinToClock(r.avgWake) }}</div>
            <div class="tile-sub">醒来</div>
          </div>
        </div>
        <div class="kv-list" style="margin-top: auto">
          <div><span class="k">每晚睡着</span><span class="v">{{ fmt1(r.avgAsleepHours) }}<small>小时</small></span></div>
          <div><span class="k">深睡 / REM 占比</span><span class="v">{{ fmt1(n.nights.deepPct) }}% / {{ fmt1(n.nights.remPct) }}%</span></div>
        </div>
      </div>

      <div class="tile s8">
        <div class="tile-label">每月的睡眠窗口<span class="legend">柱子上沿 = 平均入睡，下沿 = 平均醒来</span></div>
        <SleepWindowChart ref="chart" :data="r.monthly" />
      </div>

      <div class="tile s4">
        <div class="tile-label">几点睡着<span class="legend">入睡时刻分布</span></div>
        <div class="mini-bars">
          <div v-for="(v, i) in r.bedHistogram" :key="i" class="mb">
            <i
              :class="{ hi: i === topBed }"
              :style="{ height: (v / maxBed) * 100 + '%', '--bar': i === topBed ? 'var(--accent-1)' : 'var(--accent-2)' }"
              :title="`${BED_HOURS[i]} 点 · ${v} 晚`"
            />
            <span>{{ BED_HOURS[i] }}</span>
          </div>
        </div>
      </div>

      <div class="tile s4">
        <div class="tile-label">作息的边界</div>
        <div class="kv-list">
          <div>
            <span class="k">最早入睡</span>
            <span class="v">{{ r.earliestBed ? noonMinToClock(r.earliestBed.value) : '—' }}<small>{{ r.earliestBed ? dateCN(r.earliestBed.day) : '' }}</small></span>
          </div>
          <div>
            <span class="k">最晚入睡</span>
            <span class="v">{{ r.latestBed ? noonMinToClock(r.latestBed.value) : '—' }}<small>{{ r.latestBed ? dateCN(r.latestBed.day) : '' }}</small></span>
          </div>
          <div><span class="k">入睡时间波动</span><span class="v">± {{ fmtInt(r.bedSpreadMin) }}<small>分钟</small></span></div>
          <div>
            <span class="k">周末夜入睡</span>
            <span class="v">{{ r.weekendShiftMin >= 0 ? '晚' : '早' }} {{ fmtInt(Math.abs(r.weekendShiftMin)) }}<small>分钟</small></span>
          </div>
        </div>
      </div>

      <div class="tile s4 accent-2">
        <div class="tile-label">午间小睡<span class="legend">10 – 19 点、≥ 20 分钟</span></div>
        <template v-if="n.count > 0">
          <div class="tile-figure"><CountUp :value="n.count" /><small>次</small></div>
          <div class="tile-sub">共 {{ fmt1(n.totalHours) }} 小时 · 平均每次 {{ fmtInt(n.avgMin) }} 分钟</div>
          <div class="kv-list" style="margin-top: auto">
            <div>
              <span class="k">最长一次</span>
              <span class="v">{{ n.longest ? minutesCN(n.longest.value) : '—' }}<small>{{ n.longest ? dateCN(n.longest.day) : '' }}</small></span>
            </div>
            <div><span class="k">发生在周末</span><span class="v">{{ n.weekend.count }}<small>次</small></span></div>
          </div>
        </template>
        <div v-else class="tile-sub">{{ model.copy.naps }}</div>
      </div>

      <div v-if="n.count > 0" class="tile">
        <div class="tile-label">小睡开始时段<span class="legend">{{ model.copy.naps }}</span></div>
        <NapHourChart :data="n.hourHistogram" />
      </div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ zhCN.report.empty }}</div></div>
  </ReportCard>
</template>

<style scoped>
.clock-pair {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 6px 0 18px;
  flex-wrap: wrap;
}
.clock-arrow {
  font-size: 22px;
  color: var(--faint);
}
</style>
