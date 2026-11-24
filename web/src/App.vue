<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReportStore } from '@/stores/report'
import { useParseStore } from '@/stores/parse'
import SkinSwitch from '@/components/SkinSwitch.vue'
import Icon from '@/components/Icon.vue'
import { zhCN } from '@/locales/zh-CN'

const route = useRoute()
const router = useRouter()
const report = useReportStore()
const parse = useParseStore()

const onReport = computed(() => route.name === 'report')

function restart() {
  report.clear()
  parse.reset()
  router.push('/')
}
</script>

<template>
  <div class="backdrop" aria-hidden="true">
    <div class="orb orb-a" />
    <div class="orb orb-b" />
    <div class="orb orb-c" />
    <div class="orb orb-d" />
  </div>
  <div class="grain" aria-hidden="true" />

  <header class="site-header">
    <div class="container">
      <router-link to="/" class="brand" @click="parse.status === 'parsing' && parse.cancel()">
        <span class="brand-mark">
          <svg width="18" height="18" viewBox="0 0 64 64" aria-hidden="true">
            <circle cx="32" cy="32" r="24" fill="none" stroke="var(--accent-1)" stroke-width="8" />
            <circle cx="32" cy="32" r="13" fill="none" stroke="var(--accent-2)" stroke-width="8" />
            <circle cx="32" cy="32" r="4" fill="var(--accent-3)" />
          </svg>
        </span>
        {{ zhCN.brand }}
        <small>{{ zhCN.tagline }}</small>
      </router-link>
      <div class="header-spacer" />
      <button v-if="onReport" class="btn btn-ghost btn-sm header-restart" @click="restart">
        <Icon name="refresh" :size="14" />
        <span class="btn-label">{{ zhCN.report.restart }}</span>
      </button>
      <SkinSwitch />
    </div>
  </header>

  <router-view v-slot="{ Component }">
    <transition name="route" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>

  <footer class="site-footer">
    <div class="container">
      <span>{{ zhCN.footer.disclaimer }}</span>
      <span style="display: inline-flex; align-items: center; gap: 6px">
        <Icon name="shield" :size="13" />
        {{ zhCN.footer.local }}
      </span>
    </div>
  </footer>
</template>