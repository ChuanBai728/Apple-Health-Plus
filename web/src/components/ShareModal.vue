<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { drawShareCard, canvasToBlob, type SharePayload, type ShareRatio } from '@/composables/useShareImage'
import { useSkin } from '@/composables/useSkin'
import Icon from '@/components/Icon.vue'
import { zhCN } from '@/locales/zh-CN'

const props = defineProps<{
  payload: Omit<SharePayload, 'chart'>
  getChart?: () => string | null
}>()
const emit = defineEmits<{ close: [] }>()

const ratio = ref<ShareRatio>('9:16')
const preview = ref('')
const busy = ref(false)
const copied = ref(false)
const copyFailed = ref(false)
let canvas: HTMLCanvasElement | null = null
const { current } = useSkin()

async function regenerate() {
  busy.value = true
  try {
    canvas = await drawShareCard(
      { ...props.payload, chart: props.getChart?.() ?? null },
      ratio.value,
    )
    preview.value = canvas.toDataURL('image/png')
  } finally {
    busy.value = false
  }
}

watch([ratio, () => current.value.key], regenerate)
onMounted(regenerate)

async function save() {
  if (!canvas) return
  const blob = await canvasToBlob(canvas)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `HealthPlus-${props.payload.title}-${ratio.value.replace(':', 'x')}.png`
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 4000)
}

async function copy() {
  if (!canvas) return
  copyFailed.value = false
  try {
    const blob = await canvasToBlob(canvas)
    await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    copyFailed.value = true
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <transition name="modal">
      <div class="modal-scrim" @click.self="emit('close')">
        <div class="modal" role="dialog" aria-modal="true" :aria-label="zhCN.share.title">
          <h3>
            {{ zhCN.share.title }} · {{ payload.title }}
            <button class="close-x" aria-label="关闭" @click="emit('close')">
              <Icon name="x" :size="18" />
            </button>
          </h3>
          <div class="share-row">
            <button class="chip" :aria-pressed="ratio === '9:16'" @click="ratio = '9:16'">
              {{ zhCN.share.vertical }}
            </button>
            <button class="chip" :aria-pressed="ratio === '1:1'" @click="ratio = '1:1'">
              {{ zhCN.share.square }}
            </button>
          </div>
          <div class="share-preview">
            <img v-if="preview" :src="preview" :alt="`${payload.title} 分享图预览`" />
            <div v-else style="padding: 60px; color: var(--faint)">生成中…</div>
          </div>
          <div class="share-actions">
            <button class="btn" :disabled="busy" @click="save">
              <Icon name="download" :size="16" />
              {{ zhCN.share.save }}
            </button>
            <button class="btn btn-ghost" :disabled="busy" @click="copy">
              <Icon :name="copied ? 'check' : 'copy'" :size="16" />
              {{ copied ? zhCN.share.copied : zhCN.share.copy }}
            </button>
          </div>
          <p class="share-hint">
            {{ copyFailed ? '当前浏览器不支持复制图片，请使用「保存图片」。' : zhCN.share.hint }}
          </p>
        </div>
      </div>
    </transition>
  </Teleport>
</template>