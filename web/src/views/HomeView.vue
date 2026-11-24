<script setup lang="ts">
import { computed } from 'vue'
import { useParseStore } from '@/stores/parse'
import UploadZone from '@/components/UploadZone.vue'
import ParseProgress from '@/components/ParseProgress.vue'
import Icon from '@/components/Icon.vue'
import { zhCN } from '@/locales/zh-CN'

const parse = useParseStore()

const errorText = computed(() => {
  if (parse.error === 'NOT_EXPORT') return zhCN.errors.notExport
  if (parse.error === 'CANCELLED') return zhCN.errors.cancelled
  if (parse.error === 'GENERIC') return zhCN.errors.generic
  return ''
})

const modules = [
  { icon: 'spark', title: '年度人格', desc: '一条主人格 + 几个标签，概括这段时间你的样子。', color: 'var(--accent-1)' },
  { icon: 'moon', title: '小睡档案', desc: 'Apple 健康不统计的午睡，这里有次数、时长与最长纪录。', color: 'var(--accent-2)' },
  { icon: 'dumbbell', title: '训练生涯', desc: '不只看总量，更看连续、离开与回来。', color: 'var(--accent-3)' },
  { icon: 'stairs', title: '向上与向前', desc: '爬楼换算成珠峰，里程换算成马拉松。', color: 'var(--good)' },
]
</script>

<template>
  <main>
    <section class="hero container">
      <span class="eyebrow"><i class="dot" />{{ zhCN.hero.eyebrow }}</span>
      <h1>
        {{ zhCN.hero.titleA }}<br />
        <span class="grad">{{ zhCN.hero.titleB }}</span>
      </h1>
      <p class="sub">{{ zhCN.hero.sub }}</p>

      <div class="hero-card glass">
        <ParseProgress v-if="parse.status === 'parsing'" />
        <template v-else>
          <UploadZone @file="parse.start($event)" />
          <div style="text-align: center">
            <span class="privacy">
              <Icon name="shield" :size="14" />
              {{ zhCN.hero.privacy }}
            </span>
          </div>
          <button class="demo-link" @click="parse.startDemo()">{{ zhCN.hero.demo }}</button>
          <div v-if="errorText" class="error-box" role="alert">
            <Icon name="alert" :size="16" />
            <span>{{ errorText }}</span>
          </div>
        </template>
      </div>
    </section>

    <section class="section container">
      <div class="section-title">如何导出</div>
      <div class="steps-grid">
        <div v-for="(s, i) in zhCN.steps" :key="i" v-reveal="i * 90" class="step-card glass">
          <div class="step-num">{{ i + 1 }}</div>
          <b>{{ s.title }}</b>
          <p>{{ s.desc }}</p>
        </div>
      </div>
    </section>

    <section class="section container">
      <div class="section-title">年报里有什么</div>
      <div class="modules-grid">
        <div v-for="(m, i) in modules" :key="m.title" v-reveal="i * 80" class="module-card glass">
          <span class="m-no">0{{ i + 1 }}</span>
          <div class="m-icon" :style="{ background: `linear-gradient(145deg, ${m.color}, color-mix(in srgb, ${m.color} 55%, var(--accent-2)))` }">
            <Icon :name="m.icon" :size="19" />
          </div>
          <b>{{ m.title }}</b>
          <p>{{ m.desc }}</p>
        </div>
      </div>
    </section>

    <section class="section container">
      <div class="section-title">常见问题</div>
      <div class="faq">
        <details v-for="(f, i) in zhCN.faq" :key="i" v-reveal="i * 60">
          <summary>
            {{ f.q }}
            <Icon name="chev" :size="16" class="chev" />
          </summary>
          <div class="answer">{{ f.a }}</div>
        </details>
      </div>
    </section>
  </main>
</template>