<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useReportStore } from '@/stores/report'
import type { SharePayload } from '@/composables/useShareImage'
import PeriodSwitch from '@/components/PeriodSwitch.vue'
import ShareModal from '@/components/ShareModal.vue'
import Icon from '@/components/Icon.vue'
import PersonaCard from '@/components/cards/PersonaCard.vue'
import NapCard from '@/components/cards/NapCard.vue'
import TrainingCard from '@/components/cards/TrainingCard.vue'
import StairsCard from '@/components/cards/StairsCard.vue'
import DistanceCard from '@/components/cards/DistanceCard.vue'
import CalendarCard from '@/components/cards/CalendarCard.vue'
import HeartCard from '@/components/cards/HeartCard.vue'
import OutdoorCard from '@/components/cards/OutdoorCard.vue'
import OverviewBand from '@/components/OverviewBand.vue'
import { zhCN } from '@/locales/zh-CN'
import { dateCNFull } from '@/core/format'

const report = useReportStore()
const router = useRouter()

onMounted(() => {
  if (!report.hasData) router.replace('/')
})

const model = computed(() => report.model)

const shareState = ref<{
  payload: Omit<SharePayload, 'chart'>
  getChart?: () => string | null
} | null>(null)

function openShare(s: { payload: Omit<SharePayload, 'chart'>; getChart?: () => string | null }) {
  shareState.value = s
}
</script>

<template>
  <main v-if="model" class="report-page container">
    <div class="report-top">
      <div class="report-head">
        <h1>身体年报</h1>
        <span class="file-name">
          {{ dateCNFull(model.period.from) }} – {{ dateCNFull(model.period.to) }} ·
          <template v-if="report.isDemo">示例数据，走与真实导出相同的解析管线</template>
          <template v-else>{{ report.fileName }} · 本地解析</template>
        </span>
      </div>
      <PeriodSwitch :periods="model.periods" :current="model.period.key" @change="report.setPeriod($event)" />
    </div>

    <OverviewBand :model="model" />

    <div v-if="model.coverageNotice" class="notice">
      <Icon name="info" :size="15" />
      <span>{{ model.coverageNotice }}</span>
    </div>
    <div v-if="model.ignored > 0" class="notice">
      <Icon name="alert" :size="15" />
      <span>{{ zhCN.report.ignored(model.ignored) }}{{ model.warnings.length ? '：' + model.warnings.join('；') : '' }}</span>
    </div>

    <PersonaCard :model="model" @share="openShare" />
    <CalendarCard :model="model" @share="openShare" />
    <NapCard :model="model" @share="openShare" />
    <TrainingCard :model="model" @share="openShare" />
    <HeartCard :model="model" @share="openShare" />
    <StairsCard :model="model" @share="openShare" />
    <DistanceCard :model="model" @share="openShare" />
    <OutdoorCard :model="model" @share="openShare" />

    <ShareModal
      v-if="shareState"
      :payload="shareState.payload"
      :get-chart="shareState.getChart"
      @close="shareState = null"
    />
  </main>
</template>