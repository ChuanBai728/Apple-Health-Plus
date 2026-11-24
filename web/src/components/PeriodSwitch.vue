<script setup lang="ts">
import type { Period } from '@/types/report'

defineProps<{ periods: Period[]; current: string }>()
const emit = defineEmits<{ change: [string] }>()
</script>

<template>
  <div class="period-row" role="tablist" aria-label="时间段">
    <button
      v-for="p in periods"
      :key="p.key"
      class="chip"
      role="tab"
      :aria-pressed="p.key === current"
      :aria-selected="p.key === current"
      @click="emit('change', p.key)"
    >
      {{ p.label }}
    </button>
  </div>
</template>