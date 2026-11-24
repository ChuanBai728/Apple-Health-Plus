<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CountUp from '@/components/CountUp.vue'
import MonthBarChart from '@/components/charts/MonthBarChart.vue'
import CalendarHeatmap from '@/components/CalendarHeatmap.vue'
import { addDays, weekdayOf } from '@/core/dates'
import Icon from '@/components/Icon.vue'
import { chartPalette } from '@/composables/useSkin'
import { fmtInt, fmt1, dateCN, minutesCN } from '@/core/format'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()
const chart = ref<InstanceType<typeof MonthBarChart>>()

const ACTIVITY_NAMES: Record<string, string> = {
  strength: '力量',
  walk: '散步 / 步行',
  cycling: '骑行',
  running: '跑步',
  stairs: '爬楼',
  other: '其他',
}
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

const palette = computed(() => {
  void props.model.period.key
  return chartPalette()
})

const maxGroupCount = computed(() =>
  Math.max(1, ...props.model.training.groups.map((g) => g.count)),
)
const maxWeekday = computed(() => Math.max(1, ...props.model.training.weekday))
const ORDER = [1, 2, 3, 4, 5, 6, 0]
const topWd = computed(() => props.model.training.weekday.indexOf(maxWeekday.value))
const dayValues = computed(() => Object.fromEntries(props.model.training.days.map((d) => [d, 1])))
const gridStart = computed(() => addDays(props.model.period.from, -((weekdayOf(props.model.period.from) + 6) % 7)))

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '04 / 08',
      title: '训练生涯',
      periodLabel: m.period.label,
      stats: [
        { label: '训练次数', value: `${fmtInt(m.training.count)} 次` },
        { label: '总时长', value: `${fmt1(m.training.hours)} 小时` },
        { label: '最长连续', value: `${m.training.streak.length} 天` },
        { label: '主要时段', value: m.training.primarySlot },
      ],
      copy: m.copy.training,
    },
    getChart: () => chart.value?.getDataURL() ?? null,
  })
}
</script>

<template>
  <ReportCard no="04 / 08" kicker="Training" title="训练生涯" :lead="model.copy.training" @share="share">
    <template v-if="model.training.count > 0">
      <div class="tile s4 accent">
        <div class="tile-label">训练总量</div>
        <div class="tile-figure"><CountUp :value="model.training.count" /><small>次</small></div>
        <div class="tile-sub">累计 {{ fmt1(model.training.hours) }} 小时</div>
        <div class="kv-list" style="margin-top: auto; padding-top: 14px">
          <div><span class="k">单次中位时长</span><span class="v">{{ fmtInt(model.training.medianMin) }}<small>分钟</small></span></div>
          <div>
            <span class="k">最长一次</span>
            <span class="v">{{ model.training.longest ? minutesCN(model.training.longest.value) : '—' }}<small>{{ model.training.longest ? dateCN(model.training.longest.day) : '' }}</small></span>
          </div>
          <div><span class="k">最长连续训练</span><span class="v">{{ model.training.streak.length }}<small>天</small></span></div>
          <div><span class="k">主要时段</span><span class="v">{{ model.training.primarySlot }}</span></div>
        </div>
      </div>

      <div class="tile s8">
        <div class="tile-label">
          按月次数
          <span v-if="model.training.bestYear" class="legend">峰值年：{{ model.training.bestYear.year }} 年 · {{ model.training.bestYear.count }} 次</span>
        </div>
        <MonthBarChart ref="chart" :data="model.training.monthly" :color-index="2" :gap="model.training.gap" />
        <div v-if="model.training.gap && model.training.gap.days > 14" class="tile-foot">
          <Icon name="clock" :size="12" style="vertical-align: -2px; margin-right: 4px" />
          {{ dateCN(model.training.gap.from) }} 之后有 {{ model.training.gap.days }} 天的离开期，{{ dateCN(model.training.gap.to) }} 你回来了，之后又练了 {{ fmtInt(model.training.gap.comeback) }} 次。
        </div>
      </div>

      <div class="tile s7">
        <div class="tile-label">分类明细</div>
        <div class="group-table" style="margin-top: 0">
          <div v-for="(g, i) in model.training.groups" :key="g.activity" class="group-row">
            <span class="g-name">
              <i :style="{ background: palette[i % palette.length] }" />
              {{ ACTIVITY_NAMES[g.activity] }}
            </span>
            <span class="g-bar">
              <i :style="{ width: (g.count / maxGroupCount) * 100 + '%', background: palette[i % palette.length] }" />
            </span>
            <span class="g-val">{{ fmtInt(g.count) }} 次 · {{ fmt1(g.hours) }} h · 中位 {{ fmtInt(g.medianMin) }} min</span>
          </div>
        </div>
        <div v-if="model.training.forgotten > 0" class="tile-foot">
          有 {{ model.training.forgotten }} 次训练记录超过 4 小时，可能忘了按结束，已不计入。
        </div>
      </div>

      <div class="tile s5">
        <div class="tile-label">周几在练<span class="legend">训练次数</span></div>
        <div class="mini-bars">
          <div v-for="i in ORDER" :key="i" class="mb">
            <i
              :class="{ hi: i === topWd }"
              :style="{ height: (model.training.weekday[i] / maxWeekday) * 100 + '%', '--bar': 'var(--accent-3)' }"
              :title="`周${WEEKDAYS[i]} · ${model.training.weekday[i]} 次`"
            />
            <span>{{ WEEKDAYS[i] }}</span>
          </div>
        </div>
      </div>

      <div class="tile">
        <div class="tile-label">训练日历<span class="legend">点亮的日子有训练</span></div>
        <CalendarHeatmap
          :grid-start="gridStart"
          :from="model.period.from"
          :to="model.period.to"
          :values="dayValues"
          :thresholds="[0, 0, 0]"
          color="var(--accent-3)"
          unit="次"
          legend="每格一天，点亮表示当天有训练"
        />
      </div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ zhCN.report.empty }}</div></div>
  </ReportCard>
</template>
