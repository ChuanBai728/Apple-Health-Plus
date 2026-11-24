import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/report', name: 'report', component: () => import('@/views/ReportView.vue') },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

export default router