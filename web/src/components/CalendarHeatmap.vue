<script setup lang="ts">
import { computed } from 'vue'
import { addDays } from '@/core/dates'
import { fmtInt } from '@/core/format'

const props = defineProps<{
  gridStart: string
  from: string
  to: string
  values: Record<string, number>
  thresholds: [number, number, number]
  color: string
  unit: string
  legend?: string
}>()

interface Cell {
  day: string
  level: number
  out: boolean
  title: string
}

const columns = computed(() => {
  const cols: { month: string; cells: Cell[] }[] = []
  let d = props.gridStart
  let lastMonth = ''
  while (d <= props.to) {
    const cells: Cell[] = []
    let month = ''
    for (let i = 0; i < 7; i++) {
      const out = d < props.from || d > props.to
      const v = props.values[d] ?? 0
      const [a, b, c] = props.thresholds
      const level = out || v <= 0 ? 0 : v > c ? 4 : v > b ? 3 : v > a ? 2 : 1
      cells.push({ day: d, level, out, title: out ? '' : `${d} · ${fmtInt(v)} ${props.unit}` })
      const m = d.slice(0, 7)
      if (!out && m !== lastMonth && d.slice(8) <= '07') {
        month = Number(d.slice(5, 7)) === 1 ? `${d.slice(2, 4)}年1月` : `${Number(d.slice(5, 7))}月`
        lastMonth = m
      }
      d = addDays(d, 1)
    }
    cols.push({ month, cells })
  }
  return cols
})

function bg(level: number): string {
  if (level === 0) return 'var(--hair)'
  const pct = [0, 28, 52, 76, 100][level]
  return `color-mix(in srgb, ${props.color} ${pct}%, transparent)`
}
</script>

<template>
  <div class="hm">
    <div class="hm-weekdays">
      <span />
      <span>一</span><span /><span>三</span><span /><span>五</span><span /><span>日</span>
    </div>
    <div class="hm-scroll">
      <div class="hm-cols" :style="{ '--n': columns.length }">
        <div v-for="(c, i) in columns" :key="i" class="hm-col">
          <span class="hm-month">{{ c.month }}</span>
          <i
            v-for="cell in c.cells"
            :key="cell.day"
            :class="{ out: cell.out }"
            :title="cell.title"
            :style="{ background: cell.out ? 'transparent' : bg(cell.level) }"
          />
        </div>
      </div>
    </div>
  </div>
  <div class="hm-legend">
    少
    <i v-for="l in [0, 1, 2, 3, 4]" :key="l" :style="{ background: bg(l) }" />
    多 · {{ legend ?? `每格一天，按当日${unit}数在你自己记录中的分位着色` }}
  </div>
</template>

<style scoped>
.hm {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px;
}
.hm-weekdays {
  display: grid;
  grid-template-rows: 16px repeat(7, 1fr);
  gap: 3px;
  font-size: 9.5px;
  color: var(--faint);
  align-items: center;
}
.hm-scroll {
  overflow-x: auto;
  scrollbar-width: thin;
  padding-bottom: 4px;
}
.hm-cols {
  display: grid;
  grid-template-columns: repeat(var(--n), minmax(9px, 1fr));
  gap: 3px;
  min-width: calc(var(--n) * 12px);
}
.hm-col {
  display: grid;
  grid-template-rows: 16px repeat(7, auto);
  gap: 3px;
}
.hm-col i {
  aspect-ratio: 1;
  border-radius: 22%;
  transition: transform 0.12s ease;
}
.hm-col i:not(.out):hover {
  transform: scale(1.5);
  position: relative;
  z-index: 1;
}
.hm-month {
  font-size: 10px;
  color: var(--faint);
  white-space: nowrap;
  overflow: visible;
  width: 0;
}
.hm-legend {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--faint);
  margin-top: 10px;
  flex-wrap: wrap;
}
.hm-legend i {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
</style>
