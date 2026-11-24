<script setup lang="ts">
import Icon from '@/components/Icon.vue'
import { zhCN } from '@/locales/zh-CN'

withDefaults(
  defineProps<{ no: string; title: string; kicker?: string; lead?: string; shareable?: boolean }>(),
  { shareable: true, kicker: '', lead: '' },
)
const emit = defineEmits<{ share: [] }>()
</script>

<template>
  <section class="report-section" v-reveal>
    <header class="section-head">
      <div class="section-index">{{ no.split('/')[0].trim() }}</div>
      <div class="section-titles">
        <span v-if="kicker" class="section-kicker">{{ kicker }}</span>
        <h2>{{ title }}</h2>
        <p v-if="lead" class="section-lead">{{ lead }}</p>
      </div>
      <button v-if="shareable !== false" class="btn btn-ghost btn-sm section-share" @click="emit('share')">
        <Icon name="share" :size="14" />
        {{ zhCN.report.share }}
      </button>
    </header>
    <div class="bento">
      <slot />
    </div>
  </section>
</template>
