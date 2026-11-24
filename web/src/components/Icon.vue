<script setup lang="ts">
const props = defineProps<{ name: string; size?: number }>()
const s = props.size ?? 18

const PATHS: Record<string, string> = {
  upload: 'M12 16V4m0 0 5 5m-5-5L7 9M4 20h16',
  shield: 'M12 3l7 3v5c0 4.5-3 8.5-7 10-4-1.5-7-5.5-7-10V6l7-3z',
  moon: 'M20 13.5A8 8 0 0 1 10.5 4 8 8 0 1 0 20 13.5z',
  dumbbell: 'M6.5 6.5v11M17.5 6.5v11M3 9v6M21 9v6M6.5 12h11',
  stairs: 'M4 20h4v-4h4v-4h4V8h4',
  route: 'M5 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm14-10a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM7 17h7a4 4 0 0 0 0-8H9a3 3 0 0 1 0-6h8',
  share: 'M12 3v12m0-12 4 4m-4-4L8 7M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7',
  x: 'M6 6l12 12M18 6L6 18',
  info: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zm0-14v.5m0 3v5',
  alert: 'M12 9v4m0 4v.5M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z',
  chev: 'M6 9l6 6 6-6',
  palette: 'M12 22a10 10 0 1 1 10-10c0 2.2-1.8 3-3 3h-2.5a2.5 2.5 0 0 0-1.8 4.3c.6.6.3 2.7-2.7 2.7zM7.5 11a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm4-3.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm5 1a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  refresh: 'M20 11a8 8 0 1 0-2.3 6.3M20 5v6h-6',
  rings: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zm0-5a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  check: 'M4 12.5l5 5L20 6.5',
  copy: 'M9 9h10v12H9zM5 15H3V3h12v2',
  download: 'M12 4v12m0 0 5-5m-5 5-5-5M4 20h16',
  spark: 'M12 2l2.2 6.6L21 11l-6.8 2.4L12 20l-2.2-6.6L3 11l6.8-2.4L12 2z',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm0-15v2m0 16v2M2 12h2m16 0h2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4',
  file: 'M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8l-5-5zm0 0v5h5',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zm0-14v6l4 2',
}
</script>

<template>
  <svg
    :width="s"
    :height="s"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.9"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="PATHS[name] ?? PATHS.info" />
  </svg>
</template>