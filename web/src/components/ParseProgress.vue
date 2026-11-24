<script setup lang="ts">
import { computed } from 'vue'
import { useParseStore } from '@/stores/parse'
import { zhCN } from '@/locales/zh-CN'
import { bytesCN, durationCN, fmtInt } from '@/core/format'
import Icon from '@/components/Icon.vue'

const parse = useParseStore()
const pct = computed(() => Math.floor(parse.ratio * 100))
const phaseLabel = computed(() => zhCN.parse[parse.phase])
</script>

<template>
  <div class="parse-panel">
    <div class="parse-top">
      <div class="parse-pct">{{ pct }}<small>%</small></div>
      <div class="parse-phase">{{ phaseLabel }}</div>
    </div>
    <div class="parse-bar" role="progressbar" :aria-valuenow="pct" aria-valuemin="0" aria-valuemax="100">
      <i :style="{ width: Math.max(2, pct) + '%' }" />
    </div>
    <div class="parse-meta">
      <span>{{ bytesCN(parse.bytesDone) }} / {{ bytesCN(parse.bytesTotal) }}</span>
      <span>{{ fmtInt(parse.lines) }} 行已扫描</span>
      <span>{{ zhCN.parse.elapsed }} {{ durationCN(parse.elapsedSec) }}</span>
      <span v-if="Number.isFinite(parse.etaSec)">{{ zhCN.parse.eta }} {{ durationCN(parse.etaSec) }}</span>
    </div>
    <div class="parse-actions">
      <span class="parse-hint">{{ zhCN.parse.hint }}</span>
      <button class="btn btn-ghost btn-sm" @click="parse.cancel()">
        <Icon name="x" :size="14" />
        {{ zhCN.parse.cancel }}
      </button>
    </div>
  </div>
</template>