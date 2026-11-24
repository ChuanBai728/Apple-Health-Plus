<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'

const props = withDefaults(
  defineProps<{ value: number; decimals?: number; duration?: number }>(),
  { decimals: 0, duration: 900 },
)

const display = ref(0)
let raf = 0
let shown = 0

function fmt(n: number) {
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
  })
}

function animateTo(target: number) {
  cancelAnimationFrame(raf)
  const from = shown
  const start = performance.now()
  const tick = (now: number) => {
    const t = Math.min(1, (now - start) / props.duration)
    const eased = 1 - Math.pow(1 - t, 3)
    shown = from + (target - from) * eased
    display.value = shown
    if (t < 1) raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
}

const text = ref('0')
watch(display, (v) => (text.value = fmt(v)))
watch(
  () => props.value,
  (v) => animateTo(v),
)
onMounted(() => animateTo(props.value))
onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>

<template>
  <span class="count-up">{{ text }}</span>
</template>