<script setup lang="ts">
import { ref } from 'vue'
import Icon from '@/components/Icon.vue'
import { zhCN } from '@/locales/zh-CN'

const emit = defineEmits<{ file: [File] }>()
const input = ref<HTMLInputElement>()
const dragover = ref(false)
let dragDepth = 0

function accept(f: File | undefined | null) {
  if (f) emit('file', f)
}

function onDrop(e: DragEvent) {
  dragDepth = 0
  dragover.value = false
  accept(e.dataTransfer?.files?.[0])
}
</script>

<template>
  <div
    class="upload-zone"
    :class="{ dragover }"
    role="button"
    tabindex="0"
    :aria-label="zhCN.hero.pick"
    @click="input?.click()"
    @keydown.enter.prevent="input?.click()"
    @keydown.space.prevent="input?.click()"
    @dragenter.prevent="dragDepth++; dragover = true"
    @dragleave.prevent="dragDepth--; if (dragDepth <= 0) dragover = false"
    @dragover.prevent
    @drop.prevent="onDrop"
  >
    <div class="drop-icon">
      <Icon name="upload" :size="24" />
    </div>
    <div class="pick-label">{{ zhCN.hero.pick }}</div>
    <div class="hint">{{ zhCN.hero.dropHint }}</div>
    <input
      ref="input"
      type="file"
      accept=".zip,.xml,application/zip,text/xml"
      hidden
      @change="accept(($event.target as HTMLInputElement).files?.[0]); ($event.target as HTMLInputElement).value = ''"
    />
  </div>
</template>