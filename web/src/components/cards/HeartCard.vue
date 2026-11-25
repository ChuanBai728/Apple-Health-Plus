<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import VitalLineChart from '@/components/charts/VitalLineChart.vue'
import { fmtInt, dateCN } from '@/core/format'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof VitalLineChart>>()

const h = computed(() => props.model.heart)
const hasAny = computed(() => h.value.resting.days > 0 || h.value.hrv.days > 0)

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '05 / 08',
      title: '心率与 HRV',
      periodLabel: m.period.label,
      stats: [
        { label: '静息心率均值', value: m.heart.resting.days ? `${fmtInt(m.heart.resting.avg)} 次/分` : '—' },
        { label: '静息心率记录', value: `${fmtInt(m.heart.resting.days)} 天` },
        { label: 'HRV 均值', value: m.heart.hrv.days ? `${fmtInt(m.heart.hrv.avg)} 毫秒` : '—' },
        { label: 'HRV 记录', value: `${fmtInt(m.heart.hrv.days)} 天` },
        { label: '心率区间', value: m.heart.resting.min && m.heart.resting.max ? `${fmtInt(m.heart.resting.min.value)} – ${fmtInt(m.heart.resting.max.value)} 次/分` : '—' },
      ],
      copy: m.copy.heart,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="05 / 08" kicker="Heart" title="心率与 HRV" :lead="model.copy.heart" @share="share">
    <template v-if="hasAny">
      <div class="tile s4 r2 heart-side">
        <div v-if="h.resting.days > 0" class="heart-block">
          <div class="tile-label">静息心率<span class="legend">{{ h.resting.days }} 天记录</span></div>
          <div class="tile-figure">{{ fmtInt(h.resting.avg) }}<small>次/分</small></div>
          <div class="kv-list">
            <div>
              <span class="k">最低的一天</span>
              <span class="v">{{ h.resting.min ? fmtInt(h.resting.min.value) : '—' }}<small>{{ h.resting.min ? dateCN(h.resting.min.day) : '' }}</small></span>
            </div>
            <div>
              <span class="k">最高的一天</span>
              <span class="v">{{ h.resting.max ? fmtInt(h.resting.max.value) : '—' }}<small>{{ h.resting.max ? dateCN(h.resting.max.day) : '' }}</small></span>
            </div>
          </div>
        </div>
        <div v-if="h.hrv.days > 0" class="heart-block">
          <div class="tile-label">心率变异性 HRV<span class="legend">{{ h.hrv.days }} 天记录</span></div>
          <div class="tile-figure">{{ fmtInt(h.hrv.avg) }}<small>毫秒</small></div>
          <div class="kv-list">
            <div>
              <span class="k">最低的一天</span>
              <span class="v">{{ h.hrv.min ? fmtInt(h.hrv.min.value) : '—' }}<small>{{ h.hrv.min ? dateCN(h.hrv.min.day) : '' }}</small></span>
            </div>
            <div>
              <span class="k">最高的一天</span>
              <span class="v">{{ h.hrv.max ? fmtInt(h.hrv.max.value) : '—' }}<small>{{ h.hrv.max ? dateCN(h.hrv.max.day) : '' }}</small></span>
            </div>
          </div>
        </div>
        <div class="tile-foot">数值来自设备的自动测量，受佩戴、作息和测量时机影响，仅作个人记录对照。</div>
      </div>

      <div v-if="h.resting.days > 0" class="tile s8">
        <div class="tile-label">静息心率 · 月均<span class="legend">次/分</span></div>
        <VitalLineChart ref="chart" :data="h.resting.monthly" unit="次/分" :color-index="5" :height="170" />
      </div>
      <div v-if="h.hrv.days > 0" class="tile s8">
        <div class="tile-label">HRV · 月均<span class="legend">毫秒（SDNN）</span></div>
        <VitalLineChart :data="h.hrv.monthly" unit="毫秒" :color-index="3" :height="170" />
      </div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ model.copy.heart }}</div></div>
  </ReportCard>
</template>

<style scoped>
.heart-side {
  gap: 18px;
}
.heart-block + .heart-block {
  padding-top: 16px;
  border-top: 1px solid var(--hair);
}
.heart-block .kv-list {
  margin-top: 8px;
}
</style>
