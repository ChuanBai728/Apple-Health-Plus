<script setup lang="ts">
import { computed } from 'vue'
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import ReportCard from '@/components/ReportCard.vue'
import CalendarHeatmap from '@/components/CalendarHeatmap.vue'
import { fmtInt, fmt1, dateCN } from '@/core/format'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()

/** 周一开头展示 */
const ORDER = [1, 2, 3, 4, 5, 6, 0]
const WD = ['日', '一', '二', '三', '四', '五', '六']
const c = computed(() => props.model.calendar)
const maxWd = computed(() => Math.max(1, ...c.value.weekdayAvg))
const coverage = computed(() => (c.value.totalDays ? (c.value.recordedDays / c.value.totalDays) * 100 : 0))

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '02 / 08',
      title: '全年活动日历',
      periodLabel: m.period.label,
      stats: [
        { label: '有记录的日子', value: `${fmtInt(m.calendar.recordedDays)} 天` },
        { label: '最热闹的一周', value: m.calendar.bestWeek ? `${fmtInt(m.calendar.bestWeek.total)} 步` : '—' },
        { label: '走得最多的', value: '周' + WD[m.calendar.topWeekday] },
        { label: '日均最高的月', value: m.calendar.busiestMonth ? `${Number(m.calendar.busiestMonth.month.slice(5))} 月` : '—' },
      ],
      copy: m.copy.calendar,
    },
  })
}
</script>

<template>
  <ReportCard no="02 / 08" kicker="Calendar" title="全年活动日历" :lead="model.copy.calendar" @share="share">
    <template v-if="c.recordedDays > 0">
      <div class="tile">
        <div class="tile-label">
          每日步数
          <span class="legend">{{ dateCN(c.from) }} – {{ dateCN(c.to) }} · {{ c.totalDays }} 天</span>
        </div>
        <CalendarHeatmap
          :grid-start="c.gridStart"
          :from="c.from"
          :to="c.to"
          :values="model.stepsDaily"
          :thresholds="c.thresholds"
          color="var(--accent-1)"
          unit="步"
        />
      </div>

      <div class="tile s4">
        <div class="tile-label">一周节奏<span class="legend">各星期几的日均步数</span></div>
        <div class="mini-bars">
          <div v-for="i in ORDER" :key="i" class="mb">
            <i
              :class="{ hi: i === c.topWeekday }"
              :style="{ height: (c.weekdayAvg[i] / maxWd) * 100 + '%', '--bar': i === c.topWeekday ? 'var(--accent-1)' : 'var(--accent-2)' }"
              :title="`周${WD[i]} · ${fmtInt(c.weekdayAvg[i])} 步`"
            />
            <span>{{ WD[i] }}</span>
          </div>
        </div>
      </div>

      <div class="tile s4 accent-2">
        <div class="tile-label">最热闹的七天</div>
        <template v-if="c.bestWeek">
          <div class="tile-figure">{{ fmtInt(c.bestWeek.total) }}<small>步</small></div>
          <div class="tile-sub">{{ dateCN(c.bestWeek.from) }} – {{ dateCN(c.bestWeek.to) }}，日均 {{ fmtInt(c.bestWeek.total / 7) }} 步</div>
        </template>
        <div v-else class="tile-sub">记录不足 7 天</div>
        <div class="tile-foot">按日历逐日滑动统计，任意连续 7 天中步数总和最高的一段。</div>
      </div>

      <div class="tile s4">
        <div class="tile-label">记录覆盖</div>
        <div class="kv-list">
          <div><span class="k">有步数的日子</span><span class="v">{{ fmtInt(c.recordedDays) }}<small>/ {{ c.totalDays }} 天</small></span></div>
          <div><span class="k">覆盖率</span><span class="v">{{ fmt1(coverage) }}<small>%</small></span></div>
          <div>
            <span class="k">日均最高的月份</span>
            <span class="v">{{ c.busiestMonth ? `${c.busiestMonth.month.slice(0, 4)}.${Number(c.busiestMonth.month.slice(5))}` : '—' }}</span>
          </div>
          <div><span class="k">那个月的日均</span><span class="v">{{ c.busiestMonth ? fmtInt(c.busiestMonth.avg) : '—' }}<small>步</small></span></div>
        </div>
      </div>
    </template>
    <div v-else class="tile"><div class="empty-note">{{ zhCN.report.empty }}</div></div>
  </ReportCard>
</template>
