import { ref } from 'vue'

export interface Skin {
  key: string
  name: string
  dots: [string, string, string]
  dark: boolean
}

export const SKINS: Skin[] = [
  { key: 'aurora', name: '夜航', dots: ['#7dd3fc', '#a5b4fc', '#f0abfc'], dark: true },
  { key: 'rings', name: '环彩', dots: ['#ff375f', '#a8f02a', '#00e5ff'], dark: true },
  { key: 'sunset', name: '落日', dots: ['#ffb347', '#ff6b6b', '#ff8fab'], dark: true },
  { key: 'moss', name: '苔原', dots: ['#a3e635', '#34d399', '#fbbf24'], dark: true },
  { key: 'paper', name: '晨纸', dots: ['#d4472a', '#0e7a6d', '#b98a1f'], dark: false },
  { key: 'tide', name: '青花', dots: ['#1d4ed8', '#0891b2', '#db2777'], dark: false },
]

const STORAGE_KEY = 'healthplus:skin'
const DEFAULT_SKIN = 'paper'

function readStoredSkin(): Skin {
  const fallback = SKINS.find((s) => s.key === DEFAULT_SKIN) ?? SKINS[0]
  try {
    const key = localStorage.getItem(STORAGE_KEY)
    return SKINS.find((s) => s.key === key) ?? fallback
  } catch {
    return fallback
  }
}

const initial = readStoredSkin()
const current = ref<Skin>(initial)

export function applySkin(skin: Skin) {
  current.value = skin
  document.documentElement.dataset.skin = skin.key
  try {
    localStorage.setItem(STORAGE_KEY, skin.key)
  } catch {
    /* 隐私模式下不可写，忽略 */
  }
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg-low').trim()
    if (bg) meta.setAttribute('content', bg)
  }
}

applySkin(initial)

export function useSkin() {
  return {
    skins: SKINS,
    current,
    set: applySkin,
    shuffle() {
      const others = SKINS.filter((s) => s.key !== current.value.key)
      applySkin(others[Math.floor(Math.random() * others.length)])
    },
  }
}

/** 图表用调色板：从当前皮肤的 CSS 变量读取 */
export function chartPalette(): string[] {
  const cs = getComputedStyle(document.documentElement)
  const read = (name: string, fallback: string) => cs.getPropertyValue(name).trim() || fallback
  return [
    read('--accent-1', '#7dd3fc'),
    read('--accent-2', '#a5b4fc'),
    read('--accent-3', '#f0abfc'),
    read('--good', '#86efac'),
    read('--warn', '#fcd34d'),
    read('--danger', '#fb7185'),
  ]
}

export function chartInk() {
  const cs = getComputedStyle(document.documentElement)
  return {
    text: cs.getPropertyValue('--text').trim() || '#f7f8fb',
    muted: cs.getPropertyValue('--muted').trim() || 'rgba(255,255,255,.68)',
    faint: cs.getPropertyValue('--faint').trim() || 'rgba(255,255,255,.44)',
    hair: cs.getPropertyValue('--hair').trim() || 'rgba(255,255,255,.12)',
    card: cs.getPropertyValue('--card-solid').trim() || '#161c30',
  }
}