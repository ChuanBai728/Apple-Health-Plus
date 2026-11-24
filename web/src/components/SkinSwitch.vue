<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useSkin } from '@/composables/useSkin'
import Icon from '@/components/Icon.vue'

const { skins, current, set, shuffle } = useSkin()
const open = ref(false)
const root = ref<HTMLElement>()

function onDocClick(e: MouseEvent) {
  if (open.value && root.value && !root.value.contains(e.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="root" class="skin-switch" style="position: relative">
    <button class="btn btn-ghost btn-sm" :aria-expanded="open" @click="open = !open">
      <Icon name="palette" :size="15" />
      <span class="skin-name">{{ current.name }}</span>
      <span class="skin-dots">
        <i v-for="(d, i) in current.dots" :key="i" :style="{ background: d }" />
      </span>
    </button>
    <div v-if="open" class="skin-pop" role="menu">
      <button
        v-for="s in skins"
        :key="s.key"
        class="skin-item"
        role="menuitemradio"
        :aria-pressed="s.key === current.key"
        @click="set(s)"
      >
        <span class="skin-dots">
          <i v-for="(d, i) in s.dots" :key="i" :style="{ background: d }" />
        </span>
        {{ s.name }}
        <Icon v-if="s.key === current.key" name="check" :size="14" style="margin-left: auto; color: var(--accent-1)" />
      </button>
      <button class="skin-item shuffle" @click="shuffle()">
        <Icon name="refresh" :size="14" />
        随机一款
      </button>
    </div>
  </div>
</template>

<style scoped>
@media (max-width: 480px) {
  .skin-name {
    display: none;
  }
}
</style>