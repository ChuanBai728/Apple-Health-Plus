<script setup lang="ts">
import type { ReportModel } from '@/types/report'
import type { SharePayload } from '@/composables/useShareImage'
import { fmtInt } from '@/core/format'
import ReportCard from '@/components/ReportCard.vue'
import Icon from '@/components/Icon.vue'

const props = defineProps<{ model: ReportModel }>()
const emit = defineEmits<{ share: [{ payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }] }>()

const DESCS: Record<string, string> = {
  gym: '杠铃片碰撞的声音，是这段时间你的背景音。',
  walker: '马路、地铁和人行道，都是你的跑道。',
  stairs: '别人等电梯的时候，你已经在楼上了。',
  steady: '没有夸张的峰值，但每一天都算数。',
}

function share() {
  const m = props.model
  emit('share', {
    payload: {
      no: '01 / 08',
      title: '年度人格 · ' + m.persona.name,
      periodLabel: m.period.label,
      stats: [
        ...m.persona.stats,
        { label: '总步数', value: `${fmtInt(m.steps.total)} 步` },
        { label: '训练', value: `${fmtInt(m.training.count)} 次` },
        { label: '三环闭合', value: `${fmtInt(m.rings.closedDays)} 天` },
      ],
      tags: m.persona.tags,
      copy: m.copy.persona,
    },
  })
}
</script>

<template>
  <ReportCard no="01 / 08" kicker="Persona" title="年度人格" @share="share">
    <div class="tile s8 accent persona-tile">
      <div class="tile-label">这段时间的你</div>
      <div class="persona-name">{{ model.persona.name }}</div>
      <p class="persona-desc">{{ DESCS[model.persona.key] }}</p>
      <div v-if="model.persona.tags.length" class="persona-tags">
        <span v-for="t in model.persona.tags" :key="t" class="tag">
          <Icon name="spark" :size="12" style="margin-right: 5px" />{{ t }}
        </span>
      </div>
      <p class="persona-copy">{{ model.copy.persona }}</p>
    </div>
    <div class="tile s4">
      <div class="tile-label">人格依据</div>
      <div class="kv-list persona-kv">
        <div v-for="s in model.persona.stats" :key="s.label">
          <span class="k">{{ s.label }}</span>
          <span class="v">{{ s.value }}</span>
        </div>
      </div>
      <div class="tile-foot">人格由训练、步数、爬楼与圆环的相对强度决定，只描述记录本身。</div>
    </div>
  </ReportCard>
</template>

<style scoped>
.persona-tile {
  min-height: 280px;
}
.persona-desc {
  margin-top: 10px;
  color: var(--muted);
  font-size: 15px;
}
.persona-copy {
  margin-top: auto;
  padding-top: 22px;
  font-size: 14.5px;
  line-height: 1.85;
  color: var(--muted);
}
.persona-kv .v {
  font-size: 18px;
}
</style>
