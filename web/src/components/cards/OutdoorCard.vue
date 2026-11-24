<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CountUp from '@/components/CountUp.vue'
import VitalLineChart from '@/components/charts/VitalLineChart.vue'
import { fmtInt, fmt1, dateCN } from '@/core/format'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof VitalLineChart>>()

const o = computed(() => props.model.outdoor)
const ORDER = [1, 2, 3, 4, 5, 6, 0]
const WD = ['日', '一', '二', '三', '四', '五', '六']
const maxStand = computed(() => Math.max(1, ...o.value.standByWeekday))
const minStand = computed(() => Math.min(...o.value.standByWeekday.filter((v) => v > 0), maxStand.value))

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '08 / 08',
      title: '日光与站立',
      periodLabel: m.period.label,
      stats: [
        { label: '户外日光', value: `${fmtInt(m.outdoor.daylightHours)} 小时` },
        { label: '日均日光', value: `${fmtInt(m.outdoor.daylightAvgMin)} 分钟` },
        { label: '日均站立', value: `${fmt1(m.outdoor.standAvgHours)} 小时` },
        { label: '活动能量', value: `${fmtInt(m.outdoor.kcalTotal)} 千卡` },
      ],
      copy: m.copy.outdoor,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="08 / 08" kicker="Daylight" title="日光与站立" :lead="model.copy.outdoor" @share="share">
    <template v-if="o.daylightDays > 0 || o.standDays > 0">
      <template v-if="o.daylightDays > 0">
        <div class="tile s4 accent sun-tile">
          <div class="tile-label">户外日光时长</div>
          <div class="tile-figure"><CountUp :value="o.daylightHours" /><small>小时</small></div>
          <div class="tile-sub">{{ o.daylightDays }} 天有记录，平均每天 {{ fmtInt(o.daylightAvgMin) }} 分钟</div>
          <div class="kv-list" style="margin-top: auto; padding-top: 14px">
            <div>
              <span class="k">日光最久的一天</span>
              <span class="v">{{ o.daylightMax ? fmtInt(o.daylightMax.value) : '—' }}<small>分钟 {{ o.daylightMax ? dateCN(o.daylightMax.day) : '' }}</small></span>
            </div>
          </div>
        </div>
        <div class="tile s8">
          <div class="tile-label">日均日光 · 按月<span class="legend">分钟 / 天</span></div>
          <VitalLineChart ref="chart" :data="o.daylightMonthly" unit="分钟" :color-index="4" />
        </div>
      </template>

      <template v-if="o.standDays > 0">
        <div class="tile s4">
          <div class="tile-label">站立小时<span class="legend">{{ o.standDays }} 天记录</span></div>
          <div class="tile-figure">{{ fmt1(o.standAvgHours) }}<small>小时 / 天</small></div>
          <div class="tile-sub">站满 12 小时的日子有 {{ fmtInt(o.stand12Days) }} 天</div>
        </div>
        <div class="tile s4">
          <div class="tile-label">一周的站立<span class="legend">各星期几的日均站立小时</span></div>
          <div class="mini-bars">
            <div v-for="i in ORDER" :key="i" class="mb">
              <i
                :style="{
                  height: 25 + ((o.standByWeekday[i] - minStand) / Math.max(0.1, maxStand - minStand)) * 75 + '%',
                  '--bar': 'var(--warn)',
                }"
                :title="`周${WD[i]} · ${fmt1(o.standByWeekday[i])} 小时`"
              />
              <span>{{ WD[i] }}</span>
            </div>
          </div>
        </div>
        <div class="tile s4 accent-2">
          <div class="tile-label">活动能量</div>
          <div class="tile-figure"><CountUp :value="o.kcalTotal" /><small>千卡</small></div>
          <div class="tile-sub">平均每天 {{ fmtInt(o.kcalAvg) }} 千卡（活动圆环中的“活动”环）</div>
        </div>
      </template>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ model.copy.outdoor }}</div></div>
  </ReportCard>
</template>

<style scoped>
.sun-tile {
  background:
    radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--warn) 24%, transparent), transparent 60%),
    linear-gradient(150deg, var(--glass-a), var(--glass-b));
  border-color: color-mix(in srgb, var(--warn) 28%, var(--hair));
}
</style>
