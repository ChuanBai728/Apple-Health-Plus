<script setup lang="ts">
import { computed } from 'vue'
import { enumerateDays } from '@/core/metrics/streaks'

const props = defineProps<{
  from: string
  to: string
  values?: Record<string, number>
  activeDays?: string[]
  color: string
  unit?: string
}>()

const cells = computed(() => {
  const days = enumerateDays(props.from, props.to)
  const active = props.activeDays ? new Set(props.activeDays) : null
  let max = 1
  if (props.values) {
    for (const d of days) max = Math.max(max, props.values[d] ?? 0)
  }
  return days.map((day) => {
    let level = 0
    let label = day
    if (props.values) {
      const v = props.values[day] ?? 0
      label = `${day} · ${Math.round(v)} ${props.unit ?? ''}`
      level = v <= 0 ? 0 : v / max > 0.66 ? 3 : v / max > 0.33 ? 2 : 1
    } else if (active) {
      level = active.has(day) ? 3 : 0
      label = active.has(day) ? `${day} · 有记录` : day
    }
    return { day, level, label }
  })
})

function bg(level: number): string {
  if (level === 0) return 'var(--hair)'
  const pct = level === 3 ? 100 : level === 2 ? 62 : 32
  return `color-mix(in srgb, ${props.color} ${pct}%, transparent)`
}
</script>

<template>
  <div>
    <div class="day-strip">
      <i v-for="c in cells" :key="c.day" :title="c.label" :style="{ background: bg(c.level) }" />
    </div>
    <div class="strip-legend">
      少
      <i style="background: var(--hair)" />
      <i :style="{ background: bg(1) }" />
      <i :style="{ background: bg(2) }" />
      <i :style="{ background: bg(3) }" />
      多 · 每格一天
    </div>
  </div>
</template>